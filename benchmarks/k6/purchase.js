import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const outcomes = {};
for (const name of ['OK','OUT_OF_STOCK','DUPLICATE_ORDER','INVALID_ARGUMENT','INTEGRITY_ERROR','DEADLOCK','LOCK_TIMEOUT','HTTP_TIMEOUT','SYSTEM_ERROR']) {
  outcomes[name] = new Counter(`outcome_${name.toLowerCase()}`);
}
const successfulLatency = new Trend('successful_latency_ms', true);
const statuses = {};
for (const status of ['0','200','201','400','404','409','500','503','other']) statuses[status] = new Counter(`http_status_${status}`);
export const options = { vus: Number(__ENV.VUS || 1), duration: __ENV.DURATION || '5s', discardResponseBodies: false, summaryTrendStats: ['avg','min','med','max','p(50)','p(95)','p(99)'] };
http.setResponseCallback(http.expectedStatuses(201, 409));

export default function () {
  // iterationInTest is unique across VUs, and starts at zero for each isolated run.
  const userId = __ENV.USER_MODE === 'same' ? 1 : exec.scenario.iterationInTest + 1;
  const body = JSON.stringify({runId: __ENV.RUN_ID, strategy: __ENV.STRATEGY, userId});
  const response = http.post('http://127.0.0.1:18081/benchmark/purchase', body,
    {headers:{'Content-Type':'application/json'}, timeout: '5s'});
  const status = String(response.status);
  (statuses[status] || statuses.other).add(1);
  let code = 'SYSTEM_ERROR';
  if (response.status === 0 && response.error_code === 1050) code = 'HTTP_TIMEOUT';
  else if (response.status !== 0) {
    try { const received = response.json('code'); if (outcomes[received]) code = received; } catch (_) { /* malformed response is a system error */ }
  }
  outcomes[code].add(1);
  if (code === 'OK') successfulLatency.add(response.timings.duration);
}
