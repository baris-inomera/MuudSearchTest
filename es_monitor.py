"""
ES Cluster Monitor — Coğrafi Yedeklilik Operasyonu
Kullanım: python es_monitor.py
Çıkmak için: Ctrl+C
"""

import urllib.request
import urllib.error
import json
import time
import base64
from datetime import datetime

# ── Ayarlar ─────────────────────────────────────────────────────────────────
ES_HOST   = "http://localhost:9200"   # netzilo port forward
USERNAME  = "ddpprod"
PASSWORD  = "Ddp_123Prod_456Mongo"
INTERVAL  = 30   # saniye (kaç saniyede bir kontrol)
LOG_FILE  = "es_monitor.log"
# ─────────────────────────────────────────────────────────────────────────────

AUTH = base64.b64encode(f"{USERNAME}:{PASSWORD}".encode()).decode()
HEADERS = {"Authorization": f"Basic {AUTH}", "Content-Type": "application/json"}

RENK = {"green": "\033[92m", "yellow": "\033[93m", "red": "\033[91m", "reset": "\033[0m", "bold": "\033[1m"}

def r(url):
    req = urllib.request.Request(f"{ES_HOST}{url}", headers=HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode())
    except Exception as e:
        return None

def log(msg):
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{ts}] {msg}"
    print(line)
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(line + "\n")

def check():
    ts = datetime.now().strftime("%H:%M:%S")

    # 1. Cluster Health
    health = r("/_cluster/health")
    if health is None:
        log("!! CLUSTER ERİŞİLEMİYOR — bağlantı yok veya cluster down !!")
        print("\a")  # bip sesi
        return

    status      = health.get("status", "?")
    nodes       = health.get("number_of_nodes", "?")
    data_nodes  = health.get("number_of_data_nodes", "?")
    unassigned  = health.get("unassigned_shards", 0)
    renk        = RENK.get(status, "")

    print(f"\n{'='*60}")
    print(f"[{ts}] Cluster: {RENK['bold']}{renk}{status.upper()}{RENK['reset']}  |  Nodes: {nodes}  |  Data: {data_nodes}  |  Unassigned: {unassigned}")

    # 2. Alert koşulları
    if status == "red":
        msg = f"!! RED ALERT !! Primary shardlar kayıp — veri erişilemiyor! Unassigned: {unassigned}"
        log(msg)
        print(f"{RENK['red']}{RENK['bold']}{msg}{RENK['reset']}")
        print("\a\a\a")

    elif status == "yellow":
        msg = f"UYARI: Cluster YELLOW — {unassigned} shard unassigned (replica atılamıyor)"
        log(msg)
        print(f"{RENK['yellow']}{msg}{RENK['reset']}")
        print("\a")

    else:
        print(f"{RENK['green']}OK — Tüm shardlar sağlıklı{RENK['reset']}")

    # 3. Node listesi (her zaman göster)
    nodes_data = r("/_cat/nodes?v&h=name,ip,node.role,master,heap.percent&format=json")
    if nodes_data:
        print(f"\n  {'İSİM':<20} {'IP':<15} {'ROL':<15} {'MASTER':<8} {'HEAP%'}")
        print(f"  {'-'*65}")
        for n in nodes_data:
            master_flag = " *" if n.get("master") == "*" else ""
            print(f"  {n.get('name','?'):<20} {n.get('ip','?'):<15} {n.get('node.role','?'):<15} {master_flag:<8} {n.get('heap.percent','?')}%")

    # 4. Unassigned shard detayı (sadece sorun varsa)
    if unassigned > 0:
        shards = r("/_cat/shards?v&h=index,shard,prirep,state,node&format=json&s=state")
        if shards:
            unassigned_list = [s for s in shards if s.get("state") == "UNASSIGNED"]
            print(f"\n  UNASSIGNED SHARDLAR ({len(unassigned_list)}):")
            for s in unassigned_list[:20]:  # max 20 satır
                print(f"    {s.get('index','?')} | shard:{s.get('shard','?')} | {s.get('prirep','?')}")

if __name__ == "__main__":
    print(f"ES Monitor başladı — {ES_HOST} — {INTERVAL}s aralıkla")
    print(f"Log: {LOG_FILE} | Çıkmak: Ctrl+C\n")
    while True:
        try:
            check()
            time.sleep(INTERVAL)
        except KeyboardInterrupt:
            print("\nDurduruldu.")
            break
        except Exception as e:
            log(f"Hata: {e}")
            time.sleep(INTERVAL)
