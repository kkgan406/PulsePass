from __future__ import annotations
import argparse, datetime as dt, json, os, statistics, subprocess, threading, time, urllib.request, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / 'benchmarks' / 'results'
K6 = ROOT / 'target' / 'k6-unpacked' / 'k6' / 'k6.exe'
DOCKER = r'F:\Docker\Docker\resources\bin\docker.exe'
BASE = 'http://127.0.0.1:18081'


def api(path, method='GET', payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method, headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req, timeout=5) as response:
        return json.load(response)


def sql(query, root=False):
    # SQL contains only fixed text and locally generated integer IDs.
    user = 'root' if root else 'pulsepass'
    variable = 'MYSQL_ROOT_PASSWORD' if root else 'MYSQL_PASSWORD'
    cmd = [DOCKER, 'compose', 'exec', '-T', 'mysql', 'sh', '-c',
           f'MYSQL_PWD="${variable}" mysql -h127.0.0.1 -u {user} -D pulsepass_bench -N -B']
    p = subprocess.run(cmd, input=query, text=True, cwd=ROOT, capture_output=True, timeout=20)
    if p.returncode: raise RuntimeError('Isolated MySQL query failed: ' + p.stderr[:300])
    return p.stdout.strip().splitlines()


def fixture(stock):
    now=dt.datetime.now(dt.timezone.utc)
    iso=lambda t:t.isoformat(timespec='milliseconds').replace('+00:00','Z')
    marker='phase22-'+uuid.uuid4().hex
    a=api('/api/v1/activities','POST',{'name':marker,'description':'isolated baseline fixture',
        'saleStartTime':iso(now-dt.timedelta(minutes=5)), 'saleEndTime':iso(now+dt.timedelta(hours=2)),
        'eventStartTime':iso(now+dt.timedelta(hours=3))})['data']['id']
    t=api(f'/api/v1/activities/{a}/ticket-types','POST',{'name':'baseline','priceCents':1000,'totalStock':stock})['data']['ticketTypeId']
    api(f'/api/v1/activities/{a}/status','PATCH',{'status':'ON_SALE'})
    return a,t,marker


def hikari(name):
    try:
        v=api('/actuator/metrics/'+name)['measurements']
        return {x['statistic']:x['value'] for x in v}
    except (OSError,ValueError,KeyError): return None


def global_locks():
    lines=sql("SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_row_lock_waits','Innodb_row_lock_time','Innodb_deadlocks');",root=True)
    return {a:int(b) for a,b in (line.split('\t') for line in lines)}


def sample(stop, samples):
    while not stop.is_set():
        item={'time':time.time(),'active':hikari('hikaricp.connections.active'),
              'idle':hikari('hikaricp.connections.idle'),'pending':hikari('hikaricp.connections.pending')}
        samples.append(item)
        stop.wait(.5)


def external_sample(out):
    try:
        p=subprocess.run([DOCKER,'stats','--no-stream','--format','{{.CPUPerc}}\t{{.MemUsage}}','pulsepass-mysql-1'],cwd=ROOT,capture_output=True,text=True,timeout=12)
        out['docker_stats']=p.stdout.strip() if p.returncode==0 else 'unavailable'
        out['lock_waits']=int(sql('SELECT COUNT(*) FROM performance_schema.data_lock_waits;',root=True)[0])
        out['sample_time']=time.time()
    except Exception as e: out['error']=str(e)


def run_one(folder, label, rate, mode, stock, duration):
    activity,ticket,marker=fixture(stock)
    out=folder/label; out.mkdir(parents=True,exist_ok=False)
    locks_before=global_locks(); timeout_before=hikari('hikaricp.connections.timeout'); acquire_before=hikari('hikaricp.connections.acquire')
    env=os.environ.copy(); env.update({'RATE':str(rate),'DURATION':f'{duration}s','USER_MODE':mode,'ACTIVITY_ID':str(activity),'TICKET_ID':str(ticket)})
    cmd=[str(K6),'run','--quiet','--summary-export',str(out/'summary.json'),'--out','json='+str(out/'raw.jsonl'),str(ROOT/'benchmarks/k6/baseline-arrival.js')]
    samples=[]; ext={}; stop=threading.Event(); observer=threading.Thread(target=sample,args=(stop,samples),daemon=True)
    with (out/'k6.log').open('w',encoding='utf-8') as log:
        started=time.time(); p=subprocess.Popen(cmd,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        observer.start(); external=threading.Thread(target=external_sample,args=(ext,),daemon=True); external.start()
        try: rc=p.wait(timeout=duration+30)
        except subprocess.TimeoutExpired:
            p.kill(); p.wait(); raise RuntimeError('k6 timed out')
        finally: stop.set(); observer.join(timeout=5); external.join(timeout=15)
    if rc: raise RuntimeError(f'k6 failed ({label}); inspect {out / "k6.log"}')
    locks_after=global_locks(); timeout_after=hikari('hikaricp.connections.timeout'); acquire_after=hikari('hikaricp.connections.acquire')
    summary=json.loads((out/'summary.json').read_text(encoding='utf-8-sig')); metrics=summary['metrics']
    metric=lambda n,k,default=0:metrics.get(n,{}).get(k,default)
    data=sql(f"SELECT total_stock,available_stock,(SELECT COUNT(*) FROM ticket_order WHERE activity_id={activity} AND status IN ('PENDING','CONFIRMED')),(SELECT COUNT(*) FROM (SELECT user_id FROM ticket_order WHERE activity_id={activity} AND status IN ('PENDING','CONFIRMED') GROUP BY user_id HAVING COUNT(*)>1) d) FROM inventory WHERE ticket_type_id={ticket};")
    initial,remaining,orders,duplicates=map(int,data[-1].split('\t'))
    db_ids=set(sql(f"SELECT id FROM ticket_order WHERE activity_id={activity} AND status IN ('PENDING','CONFIRMED');"))
    response_ids=[]
    with (out/'raw.jsonl').open(encoding='utf-8') as raw:
        for line in raw:
            obj=json.loads(line)
            if obj.get('type')=='Point' and obj.get('metric')=='accepted_orders': response_ids.append(str(obj['data']['tags']['order_id']))
    successes=int(metric('business_ok','count')); missing_ids=set(response_ids)-db_ids
    active=[x['active']['VALUE'] for x in samples if x['active'] is not None]
    idle=[x['idle']['VALUE'] for x in samples if x['idle'] is not None]
    pending=[x['pending']['VALUE'] for x in samples if x['pending'] is not None]
    result={'label':label,'rate':rate,'duration_s':duration,'mode':mode,'activity_id':activity,'ticket_id':ticket,'marker':marker,
      'initial':initial,'remaining':remaining,'orders':orders,'duplicates':duplicates,'stock_difference':initial-remaining-orders,
      'response_order_ids':len(response_ids),'response_unique_ids':len(set(response_ids)),'missing_response_ids':len(missing_ids),
      'requests':int(metric('http_reqs','count')),'qps':metric('http_reqs','rate'),'success':successes,
      'success_tps':successes/(time.time()-started),
      'out_of_stock':int(metric('business_out_of_stock','count')),'duplicate_reject':int(metric('business_duplicate_order','count')),
      'invalid':int(metric('business_invalid_argument','count')),'internal_error':int(metric('business_internal_error','count')),
      'http_timeout':int(metric('business_http_timeout','count')),'other':int(metric('business_other','count')),
      'dropped_iterations':int(metric('dropped_iterations','count')),'p50_ms':metric('http_req_duration','p(50)'),
      'p95_ms':metric('http_req_duration','p(95)'),'p99_ms':metric('http_req_duration','p(99)'),
      'success_p95_ms':metric('success_latency','p(95)',None),'hikari_active_max':max(active) if active else None,
      'hikari_idle_min':min(idle) if idle else None,'hikari_pending_max':max(pending) if pending else None,'sample_count':len(samples),
      'hikari_timeout_before':timeout_before,'hikari_timeout_after':timeout_after,'hikari_acquire_before':acquire_before,'hikari_acquire_after':acquire_after,
      'locks_before':locks_before,'locks_after':locks_after,'external_sample':ext,
      'http201':int(metric('status_201','count')),'http409':int(metric('status_409','count')),'http500':int(metric('status_500','count'))}
    # Use k6's measured run duration inferred from http_reqs rate, not setup/SQL time.
    result['success_tps']=successes * result['qps']/result['requests'] if result['requests'] else 0
    result['correct']=(remaining>=0 and remaining<=initial and duplicates==0 and initial-remaining==orders and successes==orders and len(response_ids)==successes and len(set(response_ids))==successes and not missing_ids)
    (out/'audit.json').write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf-8')
    (out/'samples.json').write_text(json.dumps(samples,indent=2),encoding='utf-8')
    print(json.dumps({k:result[k] for k in ('label','requests','qps','success','out_of_stock','duplicate_reject','dropped_iterations','p95_ms','orders','stock_difference','correct')},ensure_ascii=False),flush=True)
    if not result['correct']: raise RuntimeError('Database/response audit failed for '+label)
    return result


def main():
    p=argparse.ArgumentParser(); p.add_argument('--group',default='b0'); p.add_argument('--rates',default='10,25,50,100');
    p.add_argument('--modes',default='s1,s2-distinct,s2-same'); p.add_argument('--repeat',type=int,default=3); p.add_argument('--duration',type=int,default=5); p.add_argument('--warmup',type=int,default=3)
    args=p.parse_args(); rates=[int(x) for x in args.rates.split(',')]; modes=args.modes.split(',')
    if args.repeat<1 or args.duration<1 or args.warmup<1 or any(r not in (10,25,50,100) for r in rates) or any(m not in ('s1','s2-distinct','s2-same') for m in modes): raise ValueError('invalid workload')
    folder=RESULTS/('phase22-'+args.group+'-'+dt.datetime.now().strftime('%Y%m%d-%H%M%S'))
    folder.mkdir(parents=True,exist_ok=False)
    # Distinct fixture for warmup; never merge its measurements with the measured runs.
    run_one(folder,'warmup',10,'distinct',100000,args.warmup)
    for mode in modes:
        for rate in rates:
            for repeat in range(1,args.repeat+1):
                run_one(folder,f'{mode}-{rate}-r{repeat}',rate,'same' if mode.endswith('same') else 'distinct',100000 if mode=='s1' else 10,args.duration)
    print('RESULT_DIR='+str(folder),flush=True)

if __name__=='__main__': main()
