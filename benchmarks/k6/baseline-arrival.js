import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const names = ['OK', 'OUT_OF_STOCK', 'DUPLICATE_ORDER', 'INVALID_ARGUMENT', 'INTERNAL_ERROR', 'HTTP_TIMEOUT', 'OTHER'];
const counts = {};
for (const name of names) counts[name] = new Counter(`business_${name.toLowerCase()}`);
const successLatency = new Trend('success_latency', true);
const acceptedOrders = new Counter('accepted_orders');
const statuses = {};
for (const status of ['0','201','400','404','409','500','503','other']) statuses[status] = new Counter(`status_${status}`);
const rate=Number(__ENV.RATE || 10);
export const options = {
  scenarios: { purchase: { executor:'constant-arrival-rate', rate, timeUnit:'1s',
    duration:__ENV.DURATION || '5s', preAllocatedVUs:50, maxVUs:200 } },
  summaryTrendStats:['avg','min','med','max','p(50)','p(95)','p(99)'],
};
http.setResponseCallback(http.expectedStatuses(201,409));
export default function () {
  // Exactly one HTTP request per iteration. Each run has its own activity.
  const userId=__ENV.USER_MODE === 'same' ? 987654321 : exec.scenario.iterationInTest + 1;
  const body=JSON.stringify({userId,activityId:Number(__ENV.ACTIVITY_ID),ticketTypeId:Number(__ENV.TICKET_ID)});
  const r=http.post('http://127.0.0.1:18081/api/v1/orders',body,{headers:{'Content-Type':'application/json'},timeout:'5s'});
  (statuses[String(r.status)] || statuses.other).add(1);
  let code='OTHER';
  if(r.status===0 && r.error_code===1050) code='HTTP_TIMEOUT';
  else if(r.status!==0) { try { const c=r.json('code'); if(counts[c]) code=c; } catch (_) {} }
  counts[code].add(1);
  if(code==='OK' && r.status===201) {
    successLatency.add(r.timings.duration);
    const id=r.json('data.id');
    if(id) acceptedOrders.add(1,{order_id:String(id)});
  }
}
