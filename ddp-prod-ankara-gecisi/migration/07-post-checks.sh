#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FAZ 4 — Migration sonrası doğrulama (salt okunur)
#
# "Migration bitti" demek için buradaki tüm kontroller geçmeli.
#
#   ./07-post-checks.sh
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

preamble
REPORT="${ARTIFACT_DIR}/post-checks-$(date +%Y%m%d-%H%M%S).txt"
exec > >(tee "${REPORT}") 2>&1

FAILURES=0
check_fail() { fail "$*"; FAILURES=$((FAILURES + 1)); }

section "MIGRATION SONRASI DOĞRULAMA  ($(date '+%Y-%m-%d %H:%M:%S'))"

# --- 1. Sağlık -------------------------------------------------------------
section "1. Cluster sağlığı"
es GET '/_cluster/health' | jq '.'
ST="$(cluster_status)"
[[ "${ST}" == "green" ]] && ok "green" || check_fail "Cluster '${ST}' — green olmalı."

# --- 2. Topoloji -----------------------------------------------------------
section "2. Topoloji"
es GET '/_cat/nodes?v&h=name,ip,node.role,master,version,disk.used_percent&s=name'
echo

N="$(node_count)"
[[ "${N}" == "${TARGET_NODE_COUNT}" ]] \
  && ok "Node sayısı ${N} (hedef ${TARGET_NODE_COUNT})" \
  || check_fail "Node sayısı ${N}, hedef ${TARGET_NODE_COUNT}"

MASTERS="$(master_eligible_nodes)"
MASTER_N="$(wc -l <<<"${MASTERS}" | tr -d ' ')"
[[ "${MASTER_N}" == "${TARGET_MASTER_COUNT}" ]] \
  && ok "Master-eligible ${MASTER_N} (hedef ${TARGET_MASTER_COUNT}, quorum 2)" \
  || check_fail "Master-eligible ${MASTER_N}, hedef ${TARGET_MASTER_COUNT}"
printf '  %s\n' ${MASTERS}

if grep -qx "${IST_MASTER_3_NAME}" <<<"${MASTERS}"; then
  check_fail "${IST_MASTER_3_NAME} hâlâ cluster'da — decommission tamamlanmamış."
else
  ok "${IST_MASTER_3_NAME} cluster'dan çıkarılmış."
fi

# --- 3. Zone dağılımı ------------------------------------------------------
section "3. Zone (rack) dağılımı"
RACKS="$(node_rack_map)"
jq -r 'to_entries[] | "  \(.key)\t\(.value)"' <<<"${RACKS}" | sort

IST_N="$(jq -r --arg z "${ZONE_IST}" '[to_entries[]|select(.value==$z)]|length' <<<"${RACKS}")"
ANK_N="$(jq -r --arg z "${ZONE_ANK}" '[to_entries[]|select(.value==$z)]|length' <<<"${RACKS}")"
echo
log "${ZONE_IST}: ${IST_N} node    ${ZONE_ANK}: ${ANK_N} node"
UNKNOWN="$(jq -r --arg a "${ZONE_IST}" --arg b "${ZONE_ANK}" \
  '[to_entries[]|select(.value!=$a and .value!=$b)|.key]|join(", ")' <<<"${RACKS}")"
[[ -z "${UNKNOWN}" ]] || check_fail "Tanımsız rack değerine sahip node'lar: ${UNKNOWN}"

# --- 4. Awareness ayarları -------------------------------------------------
section "4. Awareness ayarları"
SETTINGS="$(es GET '/_cluster/settings?flat_settings=true')"
jq '.persistent | with_entries(select(.key | startswith("cluster.routing")))' <<<"${SETTINGS}"

A_ATTR="$(jq -r '.persistent["cluster.routing.allocation.awareness.attributes"] // ""' <<<"${SETTINGS}")"
A_FORCE="$(jq -r --arg k "cluster.routing.allocation.awareness.force.${AWARENESS_ATTR}.values" \
             '.persistent[$k] // ""' <<<"${SETTINGS}")"
[[ "${A_ATTR}" == "${AWARENESS_ATTR}" ]] \
  && ok "awareness.attributes=${A_ATTR}" || check_fail "awareness.attributes='${A_ATTR}', beklenen '${AWARENESS_ATTR}'"
[[ "${A_FORCE}" == "${ZONE_IST},${ZONE_ANK}" ]] \
  && ok "force.${AWARENESS_ATTR}.values=${A_FORCE}" || check_fail "force values='${A_FORCE}', beklenen '${ZONE_IST},${ZONE_ANK}'"

REB="$(jq -r '.persistent["cluster.routing.rebalance.enable"] // "(default)"' <<<"${SETTINGS}")"
[[ "${REB}" == "(default)" || "${REB}" == "all" ]] \
  && ok "rebalance.enable=${REB}" \
  || check_fail "rebalance.enable='${REB}' — migration sonrası açık bırakılmalı (null/all)."

ALLOC="$(jq -r '.persistent["cluster.routing.allocation.enable"] // "(default)"' <<<"${SETTINGS}")"
[[ "${ALLOC}" == "(default)" || "${ALLOC}" == "all" ]] \
  && ok "allocation.enable=${ALLOC}" \
  || check_fail "allocation.enable='${ALLOC}' — rolling restart kilidi açık kalmış!"

EXCL="$(es GET '/_cluster/state/metadata?filter_path=metadata.cluster_coordination' \
  | jq -r '.metadata.cluster_coordination.voting_config_exclusions // [] | length')"
[[ "${EXCL}" == "0" ]] && ok "voting_config_exclusions boş" \
  || check_fail "voting_config_exclusions'da ${EXCL} kayıt kalmış — temizleyin: DELETE /_cluster/voting_config_exclusions"

# --- 5. ASIL KONTROL: her shard'ın iki zone'da kopyası var mı? ------------
section "5. Shard dağılımı — her shard'ın HEM İstanbul HEM Ankara'da kopyası"
SHARDS="$(es GET '/_cat/shards?format=json&h=index,shard,prirep,state,node&expand_wildcards=open')"

VIOLATIONS="$(jq -r --argjson racks "${RACKS}" --arg ist "${ZONE_IST}" --arg ank "${ZONE_ANK}" '
  [ .[] | select(.state == "STARTED") | {i: .index, s: .shard, r: ($racks[.node] // "?")} ]
  | group_by([.i, .s])
  | map({index: .[0].i, shard: .[0].s, racks: (map(.r) | unique)})
  | map(select((.racks | index($ist)) == null or (.racks | index($ank)) == null))
  | .[] | "  \(.index) shard=\(.shard) -> sadece [\(.racks | join(", "))]"
' <<<"${SHARDS}")"

if [[ -z "${VIOLATIONS}" ]]; then
  ok "Tüm shard'ların her iki lokasyonda kopyası var. Lokasyon kaybına dayanıklı."
else
  check_fail "Tek lokasyonda kalan shard'lar:"
  printf '%s\n' "${VIOLATIONS}"
  cat <<EOF

  En olası neden: index'in number_of_replicas değeri 0.
  Kontrol:  curl '${ES_URL}/_cat/indices?v&h=index,rep'
  Düzeltme: PUT /<index>/_settings {"index":{"number_of_replicas":1}}
EOF
fi

echo
log "Zone başına toplam shard:"
jq -r --argjson racks "${RACKS}" '
  [ .[] | select(.node != null and .node != "") | $racks[.node] // "?" ]
  | group_by(.) | map("  \(.[0])\t\(length)") | .[]' <<<"${SHARDS}"

echo
log "Primary'lerin zone dağılımı (bilgi amaçlı):"
jq -r --argjson racks "${RACKS}" '
  [ .[] | select(.prirep == "p" and .node != null) | $racks[.node] // "?" ]
  | group_by(.) | map("  \(.[0])\t\(length)") | .[]' <<<"${SHARDS}"
cat <<'EOF'
  NOT: Elasticsearch primary'yi bir zone'a sabitlemeye izin VERMEZ. Awareness
  sadece kopyaların dağılımını garanti eder. Ankara'ya düşen primary'lere yapılan
  yazmalar WAN gecikmesini yer — kalıcı bir dengesizlik görürseniz uygulama
  tarafında yazma latency'sini ölçün.
EOF

# --- 6. Veri bütünlüğü -----------------------------------------------------
section "6. Veri bütünlüğü"
DOC_NOW="$(es GET '/_all/_count?expand_wildcards=all' | jq -r '.count // 0')"
log "Şu anki doküman sayısı: ${DOC_NOW}"

PREV_FILE="$(ls -1t "${ARTIFACT_DIR}"/backup-*/doc-count.txt 2>/dev/null | head -1 || true)"
if [[ -n "${PREV_FILE}" ]]; then
  DOC_BEFORE="$(cat "${PREV_FILE}")"
  log "Migration öncesi   : ${DOC_BEFORE}   (${PREV_FILE})"
  if [[ "${DOC_NOW}" == "${DOC_BEFORE}" ]]; then
    ok "Doküman sayısı birebir aynı."
  else
    DIFF=$(( DOC_NOW - DOC_BEFORE ))
    warn "Fark: ${DIFF}. Cluster canlı yazma alıyorsa bu BEKLENEN bir durumdur."
    warn "Fark negatifse veya beklenenden büyükse durun ve inceleyin."
  fi
else
  warn "Karşılaştırma için migration öncesi doc-count bulunamadı."
fi

echo
es GET '/_cat/indices?v&h=index,health,status,pri,rep,docs.count,pri.store.size,store.size&s=index'

# --- 7. Yedek durumu -------------------------------------------------------
section "7. Yedek durumu"
if [[ -f "${ARTIFACT_DIR}/LAST_BACKUP" ]]; then
  # shellcheck source=/dev/null
  source "${ARTIFACT_DIR}/LAST_BACKUP"
  ok "Migration öncesi soğuk yedek kaydı mevcut (${timestamp:-?})"
  echo "    shard verisi  : ${data_node_ip:-?}:${data_tarball:-?}"
  echo "    cluster state : ${master_node_ip:-?}:${master_tarball:-?}"
  echo "    metadata      : ${dump_dir:-?}"
else
  check_fail "Yedek kaydı bulunamadı — 02-backup.sh çalıştırılmadan mı gelindi?"
fi

cat <<EOF

  YENİ TOPOLOJİDE YEDEKLEME — hâlâ açık madde:
  Migration'dan sonra data node sayısı 2'den 4'e çıktı (2 IST + 2 ANK) ve her
  shard'ın 2 kopyası iki lokasyona dağıldı. Artık TEK BİR data node'un klasörü
  tüm veriyi taşımıyor — soğuk kopya yöntemi bu topolojide ARTIK GEÇERLİ DEĞİL.

  Kalıcı çözüm: paylaşımlı disk (NFS) veya S3/MinIO + repository-s3 kurup
  gerçek ES snapshot'ına geçin. path.repo satırları config'lerde hazır duruyor.
  Bu, migration'ı bloklamaz ama açık bırakılmamalı.
EOF

# --- 8. İstemci tarafı -----------------------------------------------------
section "8. İstemci tarafı — MANUEL"
cat <<EOF
  ${IST_MASTER_3_IP}:9200 artık YOK. Şu repo'larda arayın ve node listelerini
  ${ANK_MASTER_1_IP} / Ankara data node'ları ile güncelleyin:

    grep -rn "${IST_MASTER_3_IP}" \\
      ../../../index-api ../../../search-api ../../../ui ../../../cms-catalog

  Kontrol listesi:
    [ ] index-api  ES bağlantı listesi güncellendi
    [ ] search-api ES bağlantı listesi güncellendi
    [ ] Kibana / monitoring hedefleri güncellendi
    [ ] Load balancer / haproxy backend'leri güncellendi
    [ ] Yedekleme cron'ları (varsa) yeni repo'yu kullanıyor
EOF

# --- Özet ------------------------------------------------------------------
section "ÖZET"
if (( FAILURES > 0 )); then
  fail "${FAILURES} kontrol BAŞARISIZ. Migration TAMAMLANMIŞ SAYILMAZ."
  echo "Rapor: ${REPORT}"
  exit 1
fi
ok "Tüm kontroller geçti. Migration tamamlandı."
cat <<EOF

  ŞİMDİ YAPILMAYACAKLAR (en az birkaç gün):
    * ${IST_MASTER_3_NAME} üzerindeki path.data'yı SİLMEYİN
    * ${SNAPSHOT_PATH_REPO} altındaki pre-migration snapshot'ını SİLMEYİN
    * Bunlar tek geri dönüş yolunuz.

  İZLENECEKLER:
    * Yazma latency'si (Ankara'ya düşen primary'ler nedeniyle artabilir)
    * IST<->ANK hat kullanımı
    * Cluster'ın green kalması

  BİLİNEN VE KABUL EDİLEN RİSK:
    3 master'ın 2'si İstanbul'da. İstanbul komple giderse Ankara'daki tek master
    quorum (2) sağlayamaz ve cluster ayağa kalkamaz. Bu manuel müdahale gerektirir
    — prosedür: ROLLBACK.md -> R7. Ankara kaybında İstanbul sorunsuz devam eder.

  Rapor: ${REPORT}
EOF
