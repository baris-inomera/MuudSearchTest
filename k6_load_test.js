import http from 'k6/http';
import { check } from 'k6';

const queries = ['tarkan', 'sezen aksu', 'no 1'];

export const options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    constant_rate: {
      executor: 'constant-arrival-rate',
      rate: 55,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 60,
      maxVUs: 120,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const query = queries[__ITER % queries.length];

  const payload = JSON.stringify({
    text: query,
    suggestion: false,
    correction: false,
    limit: 10,
    offset: 0,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Basic V1BQZUhMeWc6NUt5Y09ESlp4aFFxQXZtNQ==',
      'X-SEARCH-APP-KEY': 'muud',
    },
    timeout: '30s',
  };

  const res = http.post(
    'https://mirketgateway.apps.erdek.paas.turktelekom.intra/gateway/search/rest/v10/indices/49/search',
    payload,
    params
  );

  check(res, {
    'status 200': (r) => r.status === 200,
    'has content': (r) => r.json('content') !== null,
  });
}
