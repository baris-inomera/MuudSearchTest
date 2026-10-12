/**
 * MUUD SearchAPI — PROD Yük Testi
 * ─────────────────────────────────────────────────────────────────────────────
 * Hedef : https://searchapi.turktelekom.com.tr
 * Çıktı : Konsol + Grafana (InfluxDB veya Grafana Cloud)
 *
 * ÇALIŞTIRMA:
 *   Sadece konsol raporu:
 *     k6 run k6_load_test.js
 *
 *   Grafana (InfluxDB):
 *     k6 run --out influxdb=http://<INFLUX_HOST>:8086/k6 k6_load_test.js
 *
 *   Grafana Cloud k6:
 *     k6 cloud k6_load_test.js
 *
 *   Canlı web dashboard (k6 v0.49+):
 *     K6_WEB_DASHBOARD=true k6 run k6_load_test.js
 *
 * YÜK PROFİLİ (toplam ~12 dk):
 *   0→1 dk  : Isınma   0→10 VU
 *   1→6 dk  : Normal   10→30 VU  (gerçekçi kullanıcı yükü)
 *   6→9 dk  : Stres    30→60 VU  (pik yük senaryosu)
 *   9→12 dk : İniş     60→0  VU
 *
 * EŞİKLER (SLA bazlı):
 *   p95 < 1200ms  (DataCollector testinde ölçülen gerçek PROD p95 = ~310ms)
 *   p99 < 3000ms
 *   Hata oranı < %1
 * ─────────────────────────────────────────────────────────────────────────────
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ── Özel metrikler (Grafana'da ayrı panel olarak izlenebilir) ─────────────────
const correctionOffDuration = new Trend('muud_correction_off_ms', true);
const correctionOnDuration  = new Trend('muud_correction_on_ms',  true);
const errorCount            = new Counter('muud_errors');
const successRate           = new Rate('muud_success_rate');

// ── PROD bağlantı bilgileri ───────────────────────────────────────────────────
const BASE_URL = 'https://searchapi.turktelekom.com.tr';
const ENDPOINT = `${BASE_URL}/gateway/search/rest/v10/active-indices/search`;
const HEADERS  = {
  'Content-Type':    'application/json',
  'Accept':          '*/*',
  'X-SEARCH-APP-KEY':'muud',
  'Authorization':   'Basic QkRHVTc5a206ckpnNzBZaTd6VGxreVF2UQ==',
};

// ── Gerçek kullanıcı sorguları (real_keywords_top150.txt) ─────────────────────
const REAL_QUERIES = [
  'manifest','hadise','simge','blok3','ahmet kaya','sezen aksu',
  'müslüm gürses','tarkan','toz pembe','ferdi tayfur','semicenk',
  'gidişat','ta ki seni görene kadar','hileli','ibrahim tatlıses',
  'uzi','ceza','yalın','poizi','sagopa kajmer','yıldız tilbe',
  'ebru gündeş','eyletmen beni','murat göğebakan','tan taşçı',
  'ibrahim erkal','sibel can','barış manço','ara beni',
  'gel dedim geldin','tan','demet akalın','mabel matiz',
  'musa eroğlu','serdar ortaç','mustafa sandal','hande yener',
  'orhan gencebay','azer bülbül','kıvırcık ali','cem adrian',
  'buray','mebrure','gülşen','neşet ertaş','sefo','kayahan',
  'eypio','kıraç','duman','ezhel','motive','hadise ara beni',
  'ihtiyacım var','sıla','lvbel c5','manga','dua lipa',
  'nilüfer','güllü','ajda pekkan','selda bağcan','gazapizm',
  'ferhat göçer','beni al','gökhan türkmen','murat boz',
  'shakira','teoman','aleyna tilki','cem karaca',
  'geceye sözüm var','bergen','volkan konak','model',
  'elif buse doğan','ali kınık','edip akbayram','aura',
  'melike şahin','norm ender','derya uluğ','yalan',
  'funda arar','candan erçetin','seda sayan','cengiz kurtoğlu','edis',
];

// ── DDP test sorguları (yazım hatası + etiket senaryoları) ────────────────────
// correction=false: n-gram, bitişik yazım, etiket
const DDP_CORRECTION_OFF = [
  'tarkan','tarkn','tarkanşımarık','blok3git','eyask','bunca yıl',
  'buncayıl','sana güvenmiyorum','sanagüvenmiyorum',
  'tarkan akustik','blok3 remix','tarkan remix',
  'kusura bakma','kusurabakma','ağlama ben ağlarım',
  'yapar mısın','yaparmısın','a canım','acanım',
];

// correction=true: klavye yazım hatası düzeltme (DDP-369)
const DDP_CORRECTION_ON = [
  'tarkn','tarksn','tarkna',
  'ezhel','ezel','ezheıl',
  'poizi','pozi','poizş',
  'göksel','goksel','gokswl',
  'mabel matiz','mabel matız','mabel mstiz',
  'blok3','blk3','blok4',
  'hadise','hadse','hadıse',
  'silarodrigo','olivia rdrigo',
  'sertab','sertb erener',
];

// ── Yük profili ───────────────────────────────────────────────────────────────
export const options = {
  insecureSkipTLSVerify: true,

  scenarios: {
    // Normal kullanıcı yükü — correction=false (n-gram arama, DDP-366 tipi)
    normal_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m',  target: 10 },   // ısınma
        { duration: '5m',  target: 30 },   // normal yük
        { duration: '3m',  target: 60 },   // stres
        { duration: '3m',  target: 0  },   // iniş
      ],
      tags: { scenario: 'normal_load' },
      exec: 'runCorrectionOff',
    },

    // Correction=true yükü — klavye düzeltme (DDP-369 tipi)
    // Daha az VU: correction daha CPU yoğun
    correction_load: {
      executor: 'ramping-vus',
      startTime: '1m',   // normal_load ile 1dk gecikmeli başla
      startVUs: 0,
      stages: [
        { duration: '1m',  target: 5  },
        { duration: '4m',  target: 15 },
        { duration: '3m',  target: 30 },
        { duration: '2m',  target: 0  },
      ],
      tags: { scenario: 'correction_load' },
      exec: 'runCorrectionOn',
    },
  },

  thresholds: {
    // Genel eşikler
    'http_req_duration':               ['p(95)<1200', 'p(99)<3000'],
    'http_req_failed':                 ['rate<0.01'],
    'muud_success_rate':               ['rate>0.99'],

    // Senaryo bazlı eşikler
    'muud_correction_off_ms':          ['p(95)<1000', 'p(99)<2000'],
    'muud_correction_on_ms':           ['p(95)<1500', 'p(99)<3000'],

    // Senaryo tag'li eşikler (Grafana'da ayrıştırmak için)
    'http_req_duration{scenario:normal_load}':     ['p(95)<1000'],
    'http_req_duration{scenario:correction_load}': ['p(95)<1500'],
  },
};

// ── Yardımcılar ───────────────────────────────────────────────────────────────
function pickRandom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function buildPayload(text, correction) {
  return JSON.stringify({
    text:       text,
    filters:    null,
    fields:     null,
    suggestion: true,
    correction: correction,
    limit:      10,
    offset:     0,
  });
}

function sendSearch(text, correction) {
  const payload = buildPayload(text, correction);
  const tag     = correction ? 'correction_on' : 'correction_off';

  const res = http.post(ENDPOINT, payload, {
    headers: HEADERS,
    timeout: '15s',
    tags:    { query_type: tag, term: text.substring(0, 30) },
  });

  const ok = check(res, {
    'HTTP 200':      (r) => r.status === 200,
    'content exists':(r) => {
      try { return r.json('content') !== null; } catch(e) { return false; }
    },
  });

  successRate.add(ok);
  if (!ok) {
    errorCount.add(1);
    console.error(`[HATA] ${text} | status=${res.status} | body=${res.body ? res.body.substring(0,200) : '(boş)'}`);
  }

  if (correction) {
    correctionOnDuration.add(res.timings.duration);
  } else {
    correctionOffDuration.add(res.timings.duration);
  }

  return res;
}

// ── Senaryo fonksiyonları ─────────────────────────────────────────────────────

/** correction=false — gerçek kullanıcı sorguları + DDP-366 senaryoları */
export function runCorrectionOff() {
  // %70 gerçek kullanıcı, %30 DDP yazım senaryosu
  const text = Math.random() < 0.7
    ? pickRandom(REAL_QUERIES)
    : pickRandom(DDP_CORRECTION_OFF);

  sendSearch(text, false);
  sleep(Math.random() * 0.5 + 0.2);  // 0.2–0.7s düşünme süresi
}

/** correction=true — klavye yazım hatası düzeltme (DDP-369) */
export function runCorrectionOn() {
  const text = pickRandom(DDP_CORRECTION_ON);
  sendSearch(text, true);
  sleep(Math.random() * 0.8 + 0.3);  // 0.3–1.1s düşünme süresi
}

// ── Özet (test bitince otomatik yazdırılır) ───────────────────────────────────
export function handleSummary(data) {
  const d = data.metrics;

  function fmt(metric, stat) {
    try {
      const v = d[metric] && d[metric].values;
      if (!v) return '-';
      const val = v[stat] !== undefined ? v[stat] : v['p(' + stat.replace('p(','').replace(')','') + ')'];
      return val !== undefined ? Math.round(val) + 'ms' : '-';
    } catch(e) { return '-'; }
  }
  function fmtRate(metric) {
    try { return d[metric] ? (d[metric].values.rate * 100).toFixed(2) + '%' : '-'; } catch(e) { return '-'; }
  }
  function fmtCount(metric) {
    try { return d[metric] ? d[metric].values.count : '-'; } catch(e) { return '-'; }
  }

  function thresholdPassed(metric) {
    try {
      const t = d[metric] && d[metric].thresholds;
      if (!t) return 'VERİ YOK';
      const allOk = Object.values(t).every(v => v.ok);
      return allOk ? 'GEÇER ✓' : 'BAŞARISIZ ✗';
    } catch(e) { return 'KONTROL ET'; }
  }

  const summary = `
╔══════════════════════════════════════════════════════════════════╗
║         MUUD SearchAPI — PROD Yük Testi Özeti                   ║
╠══════════════════════════════════════════════════════════════════╣
║  Hedef : ${BASE_URL.padEnd(55)}║
╠══════════════════════════════════════════════════════════════════╣
║  GENEL                                                           ║
║    Toplam istek : ${String(fmtCount('http_reqs')).padEnd(47)}║
║    Hata oranı   : ${fmtRate('http_req_failed').padEnd(47)}║
║    Başarı oranı : ${fmtRate('muud_success_rate').padEnd(47)}║
╠══════════════════════════════════════════════════════════════════╣
║  YANIT SÜRELERİ — http_req_duration (tüm istekler)              ║
║    p50  : ${fmt('http_req_duration','p(50)').padEnd(55)}║
║    p90  : ${fmt('http_req_duration','p(90)').padEnd(55)}║
║    p95  : ${fmt('http_req_duration','p(95)').padEnd(55)}║
║    p99  : ${fmt('http_req_duration','p(99)').padEnd(55)}║
╠══════════════════════════════════════════════════════════════════╣
║  correction=false (n-gram / DDP-366)                            ║
║    p95  : ${fmt('muud_correction_off_ms','p(95)').padEnd(55)}║
║    p99  : ${fmt('muud_correction_off_ms','p(99)').padEnd(55)}║
╠══════════════════════════════════════════════════════════════════╣
║  correction=true (klavye düzeltme / DDP-369)                    ║
║    p95  : ${fmt('muud_correction_on_ms','p(95)').padEnd(55)}║
║    p99  : ${fmt('muud_correction_on_ms','p(99)').padEnd(55)}║
╠══════════════════════════════════════════════════════════════════╣
║  EŞİK SONUÇLARI                                                  ║
║    p95 < 1200ms (genel)   : ${thresholdPassed('http_req_duration').padEnd(37)}║
║    Hata < %1              : ${thresholdPassed('http_req_failed').padEnd(37)}║
╚══════════════════════════════════════════════════════════════════╝
`;

  console.log(summary);

  return {
    'stdout': summary,
  };
}
