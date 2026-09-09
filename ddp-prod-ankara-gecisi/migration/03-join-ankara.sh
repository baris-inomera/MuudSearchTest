#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FAZ 1 — Ankara node'larını cluster'a kat
#
# NEDEN bu sırayla:
#   * Awareness HENÜZ AÇILMIYOR. force.rack.values ayarı ANK node'ları yokken
#     açılırsa ES tüm replica'ları "ankara zone'u yok" diye unassigned bırakır
#     ve cluster anında yellow olur.
#   * rebalance.enable=none veriyoruz: ES boş node görünce kendi kafasına göre
#     shard taşımasın. Awareness açıldığında taşıma TEK SEFERDE ve doğru hedefe
#     yapılsın. Aksi halde 25GB WAN üzerinden iki kez gider.
#   * ank-master-1 önce, data node'lar sonra: master sayısı 3 -> 4 olur
#     (quorum 3, IST'te 3 master var, tolerans korunuyor).
#
#   ./03-join-ankara.sh
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

preamble
require_ank_ips
section "FAZ 1 — ANKARA NODE'LARINI KAT"

# --- Yedek zorunluluğu -----------------------------------------------------
[[ -f "${ARTIFACT_DIR}/LAST_BACKUP" ]] \
  || die "Yedek kaydı bulunamadı (${ARTIFACT_DIR}/LAST_BACKUP). Önce ./02-backup.sh çalıştırın."
# shellcheck source=/dev/null
source "${ARTIFACT_DIR}/LAST_BACKUP"
[[ -n "${data_tarball:-}" && -n "${master_tarball:-}" ]] \
  || die "Yedek kaydı eksik — hem shard verisi hem cluster state kopyası gerekli.
     ./02-backup.sh'yi baştan çalıştırın."
ok "Yedek kaydı mevcut (${timestamp}): ${doc_count} doküman, ${shards} shard"
log "  shard verisi  : ${data_node_ip}:${data_tarball}"
log "  cluster state : ${master_node_ip}:${master_tarball}"
warn "Bu dosyaların GERÇEKTEN yerinde ve makul boyutta olduğunu doğruladınız mı?"
confirm "Yedek dosyaları yerinde, devam edilsin mi?"

[[ "$(cluster_status)" == "green" ]] || die "Cluster green değil."
snapshot_cluster_settings "before-join" >/dev/null

# --- 1. Rebalance kilidi ---------------------------------------------------
section "1. Rebalance kilidi"
es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.rebalance.enable":"none"}}' >/dev/null
ok "cluster.routing.rebalance.enable=none — yeni node'lar boş kalacak."
echo "   (Awareness kaynaklı zorunlu taşımalar bu ayardan ETKİLENMEZ; onlar ayrı"
echo "    bir decider yolundan gider. Yani Faz 2 planlandığı gibi çalışır.)"

# --- 2. Recovery throttle --------------------------------------------------
section "2. WAN recovery throttle"
es PUT /_cluster/settings -d "$(jq -nc \
    --arg bps "${RECOVERY_MAX_BYTES_PER_SEC}" \
    --arg conc "${NODE_CONCURRENT_RECOVERIES}" \
    '{persistent:{
        "indices.recovery.max_bytes_per_sec": $bps,
        "cluster.routing.allocation.node_concurrent_recoveries": $conc
     }}')" >/dev/null
ok "indices.recovery.max_bytes_per_sec=${RECOVERY_MAX_BYTES_PER_SEC}, node_concurrent_recoveries=${NODE_CONCURRENT_RECOVERIES}"

# --- 3. Ankara node'larını başlat -----------------------------------------
wait_for_node() {
  local name="$1" timeout="${2:-600}" waited=0
  log "'${name}' bekleniyor…"
  while (( waited < timeout )); do
    if es GET '/_cat/nodes?format=json&h=name' 2>/dev/null \
       | jq -e --arg n "${name}" 'map(.name) | index($n) != null' >/dev/null 2>&1; then
      ok "'${name}' cluster'a katıldı (${waited}s)"; return 0
    fi
    printf '%s  … bekleniyor (%ss)%s\n' "$_C_DIM" "${waited}" "$_C_OFF"
    sleep 10; waited=$(( waited + 10 ))
  done
  fail "'${name}' ${timeout}s içinde katılmadı."
  cat <<EOF

  TEŞHİS SIRASI:
    1. Node ayakta mı?            systemctl status elasticsearch
    2. Loglar:                    tail -200 /app/elasticsearch-*/logs/${CLUSTER_NAME}.log
    3. En sık 3 neden:
       a) yaml'da cluster.initial_master_nodes BIRAKILDI
          -> node kendi ayrı cluster'ını bootstrap etti.
          ÇÖZÜM: servisi durdur, yaml'dan satırı sil, path.data'yı TAMAMEN sil
                 (bu node'da veri yok, güvenli), servisi başlat.
       b) Sertifika uyumsuz -> logda "failed to establish trust" / "handshake"
          ÇÖZÜM: elastic-certificates.p12'yi IST node'undan tekrar kopyala,
                 keystore parolalarını ekle.
       c) 9300 firewall kapalı -> logda "connect_timeout" / "no route to host"
          ÇÖZÜM: nc -z <ist-ip> 9300 ile iki yönlü doğrula.
    4. cluster.name yazım hatası (beklenen: ${CLUSTER_NAME})

  Rollback: ROLLBACK.md -> R1
EOF
  die "Devam edilmiyor."
}

section "3. ${ANK_MASTER_1_NAME} başlatılıyor"
echo "  ŞİMDİ ${ANK_MASTER_1_NAME} (${ANK_MASTER_1_IP}) üzerinde ES servisini başlatın."
echo "  yaml kontrolü: cluster.initial_master_nodes SATIRI OLMAMALI."
confirm "${ANK_MASTER_1_NAME} başlatıldı mı?"
wait_for_node "${ANK_MASTER_1_NAME}"

MASTER_N="$(master_eligible_nodes | wc -l | tr -d ' ')"
log "Master-eligible node sayısı: ${MASTER_N} (beklenen 4 — quorum 3, IST'te 3 master var)"
(( MASTER_N == 4 )) || warn "Beklenen 4, bulunan ${MASTER_N}. Devam etmeden nedenini anlayın."

section "4. Ankara data node'ları başlatılıyor"
echo "  ŞİMDİ ${ANK_DATA_1_NAME} (${ANK_DATA_1_IP}) ve ${ANK_DATA_2_NAME} (${ANK_DATA_2_IP})"
echo "  üzerinde ES servislerini başlatın."
confirm "Ankara data node'ları başlatıldı mı?"
wait_for_node "${ANK_DATA_1_NAME}"
wait_for_node "${ANK_DATA_2_NAME}"

# --- 5. Doğrulama ----------------------------------------------------------
section "5. Doğrulama"
es GET '/_cat/nodes?v&h=name,ip,node.role,master,version&s=name'
echo

N="$(node_count)"
log "Toplam node: ${N} (beklenen 8 = 5 IST + 3 ANK; .28 henüz ayakta)"
(( N == 8 )) || warn "Beklenen 8, bulunan ${N}."

log "rack attribute dağılımı:"
node_rack_map | jq -r 'to_entries[] | "  \(.key)\t\(.value)"' | sort
BAD_RACK="$(node_rack_map | jq -r --arg z "${ZONE_ANK}" \
  '[to_entries[] | select(.key | test("-ank-")) | select(.value != $z) | .key] | join(", ")')"
if [[ -z "${BAD_RACK}" ]]; then
  ok "Ankara node'larının hepsinde node.attr.${AWARENESS_ATTR}=${ZONE_ANK}"
else
  die "Yanlış rack değeri taşıyan ANK node'ları: ${BAD_RACK}
     Awareness bu değere göre çalışır — yanlışsa Faz 2 hatalı dağıtım yapar.
     yaml'ı düzeltip node'u restart edin."
fi

log "Ankara node'larındaki shard sayısı (rebalance=none nedeniyle 0 olmalı):"
es GET '/_cat/allocation?v&h=node,shards,disk.indices,disk.avail&s=node'

wait_for_green 600
ok "FAZ 1 tamam. Sıradaki adım: ./04-enable-awareness.sh"
