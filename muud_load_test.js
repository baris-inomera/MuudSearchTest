import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const searchDuration = new Trend('search_duration_ms', true);

// Hedef: 50-60 TPS, 2 dakika sustain
export const options = {
  scenarios: {
    load: {
      executor: 'constant-arrival-rate',
      rate: 55,           // saniyede 55 istek (TPS)
      timeUnit: '1s',
      duration: '2m',     // 2 dakika
      preAllocatedVUs: 20,
      maxVUs: 50,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // %95'i 2 saniyenin altinda olmali
    errors: ['rate<0.01'],              // hata orani %1'den az olmali
  },
};

const BASE_URL = 'https://mirketgateway.apps.erdek.paas.turktelekom.intra/gateway/search/rest/v10/indices/49/search';

const QUERIES = ['tarkan', 'sezen aksu', 'no 1'];

export default function () {
  const query = QUERIES[Math.floor(Math.random() * QUERIES.length)];

  const payload = JSON.stringify({
    text: query,
    suggestion: false,
    correction: false,
    limit: 10,
    offset: 0,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
    timeout: '10s',
  };

  const start = Date.now();
  const res = http.post(BASE_URL, payload, params);
  const duration = Date.now() - start;

  searchDuration.add(duration, { query });

  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    'sonuc geldi': (r) => {
      try { return JSON.parse(r.body).content.length > 0; }
      catch { return false; }
    },
  });

  errorRate.add(!ok);
}
