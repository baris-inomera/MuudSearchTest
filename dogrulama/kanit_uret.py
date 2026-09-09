# -*- coding: utf-8 -*-
"""
MUUD PROD Bulgular raporundaki B1 (ad alanlari bozuk) ve B2 (popularity olcegi)
bulgularini HAM RESPONSE'LARDAN yeniden uretir; her iddia icin kaynak dosyayi yazar.

KULLANIM (repo kokunden):
    python dogrulama/kanit_uret.py > dogrulama/KANIT_B1_B2.txt

GIRDI : reports/api-responses/{uat,prod}/*.json
        ResponseDumper'in topladigi, API'nin dondugu HAM cevaplar. Elle duzenlenmedi.
YONTEM: Hicbir sey tahmin edilmiyor; her sayi bu dosyalar uzerinde sayiliyor.
        Script tekrar kosuldugunda ayni sonucu verir.
"""
import json, glob, os, re, sys, unicodedata, collections, statistics as st

KOK = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                    "..", "reports", "api-responses"))
AD_ALANLARI = ("performerName", "songName", "albumName", "playlistName")
POP_ALANLARI = ("popularity", "prodPopularity", "popularity2", "performerPopularity")

def baslik(n, s):
    print("\n" + "=" * 100); print("%s  %s" % (n, s)); print("=" * 100)

if not os.path.isdir(KOK):
    print("HATA: %s yok." % KOK); sys.exit(1)

# ------------------------------------------------------------------ TEK GECIS
# kayit = (env, kind, id, alan, deger)  -> ad alanlari
# pop   = (env, kind, id, alan) -> deger
adlar   = collections.defaultdict(dict)              # env -> id -> performerName
kaynak  = collections.defaultdict(dict)              # env -> id -> dosya
ad_tum  = collections.defaultdict(list)              # env -> [(kind,alan,id,deger,dosya)]
pop_tum = collections.defaultdict(dict)              # (env,alan,kind) -> {id: deger}
dosya_sayisi = collections.Counter()

for env in ("uat", "prod"):
    for p in sorted(glob.glob(os.path.join(KOK, env, "*.json"))):
        dosya = os.path.basename(p)
        dosya_sayisi[env] += 1
        try:
            with open(p, encoding="utf-8") as f:
                r = json.load(f)
        except Exception as e:
            print("  ! okunamadi: %s (%s)" % (dosya, e)); continue
        for kind in ("songs", "performers", "albums", "playlists", "videos"):
            blok = r.get(kind) or {}
            for h in (blok.get("content") or []):
                d = h.get("data") or {}
                i = str(d.get("id"))
                for alan in AD_ALANLARI:
                    v = d.get(alan)
                    if isinstance(v, str) and v != "":
                        ad_tum[env].append((kind, alan, i, v, dosya))
                    elif isinstance(v, str):
                        ad_tum[env].append((kind, alan, i, v, dosya))   # bos ad da kaydedilir
                if kind == "performers" and d.get("performerName"):
                    adlar[env][i] = d["performerName"]
                    kaynak[env].setdefault(i, dosya)
                for alan in POP_ALANLARI:
                    v = d.get(alan)
                    if isinstance(v, (int, float)):
                        pop_tum[(env, alan, kind)][i] = v

print("MUUD — B1/B2 BULGU DOGRULAMA")
print("girdi klasoru: %s" % KOK)
for env in ("uat", "prod"):
    print("  %-5s %d response dosyasi, %d ad alani okundu"
          % (env, dosya_sayisi[env], len(ad_tum[env])))

# ============================================================ B1.1
baslik("B1.1", "AYNI ID, FARKLI AD — diakritik kaybi")
print("""Yontem: iki ortamdaki performers kayitlarinin id -> performerName eslemesi kurulur.
Ayni id'de ad FARKLI ve fark yalnizca diakritik/aksan ise listelenir.
'Mukerrer kayit degil' iddiasinin dayanagi: ID'ler AYNI.""")
def fold(s):
    t = unicodedata.normalize("NFD", s or "")
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    return unicodedata.normalize("NFC", t).replace("ı", "i").replace("İ", "i").lower()
def dia_say(s): return sum(1 for c in s if c in "çğıöşüÇĞİÖŞÜéèêáàâäñôûù")

ortak  = set(adlar["uat"]) & set(adlar["prod"])
farkli = [(i, adlar["uat"][i], adlar["prod"][i]) for i in ortak if adlar["uat"][i] != adlar["prod"][i]]
sadece = [(i, u, p) for i, u, p in farkli if fold(u) == fold(p)]
kayip  = [(i, u, p) for i, u, p in sadece if dia_say(u) > dia_say(p)]
tersi  = [(i, u, p) for i, u, p in sadece if dia_say(u) < dia_say(p)]
print("\nSAYILAR")
print("  iki ortamda da gorulen performer id      : %d   <-- rapordaki 3244" % len(ortak))
print("  ayni id'de ad farkli                     : %d" % len(farkli))
print("  farki YALNIZCA diakritik olan            : %d" % len(sadece))
print("  UAT'ta diakritik VAR, PROD'da YOK        : %d   <-- rapordaki 12" % len(kayip))
print("  ters yon (PROD'da var, UAT'ta yok)       : %d" % len(tersi))
# Iki farkli durum var, karistirmamak icin ayriliyor:
#   (a) GERCEK diakritik kaybi: PROD'da harf duz yazilmis (Celik -> celik)
#   (b) SADECE NFD farki: gozle ayni, byte farkli (Kurtoglu NFC vs g+U+0306)
gercek_kayip, sadece_nfd = [], []
for i, u, p in kayip:
    if fold(u) == fold(p) and unicodedata.normalize("NFD", u) == unicodedata.normalize("NFD", p):
        sadece_nfd.append((i, u, p))     # NFD'ye indirgeyince ayni -> yazim ayni, form farkli
    else:
        gercek_kayip.append((i, u, p))
print("\n  bunlardan GERCEK diakritik kaybi          : %d" % len(gercek_kayip))
print("  bunlardan yalnizca NFC/NFD form farki    : %d  (gozle ayni gorunur, bkz. B1.2)"
      % len(sadece_nfd))
print("\nLISTE A — GERCEK diakritik kaybi (PROD'da harf duz yazilmis)")
for i, u, p in sorted(gercek_kayip, key=lambda x: x[1]):
    print("  id=%-9s UAT=%-30r PROD=%-30r  kaynak: prod/%s" % (i, u, p, kaynak["prod"].get(i, "-")))
print("\nLISTE B — yalnizca NFC/NFD form farki (yazim ayni)")
for i, u, p in sorted(sadece_nfd, key=lambda x: x[1]):
    print("  id=%-9s UAT uzunluk=%-3d PROD uzunluk=%-3d  %r  kaynak: prod/%s"
          % (i, len(u), len(p), u, kaynak["prod"].get(i, "-")))

# ============================================================ B1.2
baslik("B1.2", "UNICODE NORMALIZASYON FORMU — gozle ayni, byte farkli")
print("""Yontem: her ad alani NFC kontrolunden gecirilir. Metin NFD ise NFC'ye esit olmaz.
Ornek: 'g' + U+0306 (birlesik olmayan) yerine tek kod noktasi U+011F ('g') beklenir.""")
# IKI FARKLI SAYIM — karistirilmamali:
#  (1) VARLIK BAZLI: her index'in KENDI ad alani (songs.songName, performers.performerName,
#      albums.albumName). Bir katalog varligi bir kez sayilir. RAPORDAKI SAYI BUDUR.
#  (2) KAYIT BAZLI: ad alani nerede gorunuyorsa. performerName sarki/album kayitlarinda da
#      bulunur, o yuzden ayni sanatci onlarca kez sayilir. Daha buyuk cikar, ayni seyi olcmez.
KENDI_ALANI = {"songs": "songName", "performers": "performerName",
               "albums": "albumName", "playlists": "playlistName"}
nfd_varlik = collections.defaultdict(lambda: collections.defaultdict(dict))
nfd_kayit  = collections.defaultdict(lambda: collections.defaultdict(dict))
for env in ("uat", "prod"):
    for kind, alan, i, v, dosya in ad_tum[env]:
        if v and unicodedata.normalize("NFC", v) != v:
            nfd_kayit[env][alan][(kind, i)] = (v, dosya)
            if KENDI_ALANI.get(kind) == alan:
                nfd_varlik[env][alan][i] = (v, dosya)
nfd = nfd_varlik   # asagidaki ornek bloklari varlik bazli listeyi kullanir
print("\nSAYIM 1 — VARLIK BAZLI (index'in kendi ad alani) — RAPORDAKI SAYI")
for env in ("uat", "prod"):
    tot = 0
    for alan in AD_ALANLARI:
        n = len(nfd_varlik[env][alan])
        if n: print("  %-5s %-14s %d" % (env, alan, n))
        tot += n
    print("  %-5s %-14s %d" % (env, "TOPLAM", tot))
print("\n  Beklenen: PROD 49 (songName 27 + albumName 19 + performerName 3), UAT 6 (albumName 6)")
print("\nSAYIM 2 — KAYIT BAZLI (ad alani nerede gorunuyorsa)")
print("  Not: performerName sarki/album kayitlarinda da bulunur; ayni sanatci")
print("       birden fazla kez sayilir. Rapordaki sayi bu DEGIL.")
for env in ("uat", "prod"):
    tot = 0
    for alan in AD_ALANLARI:
        n = len(nfd_kayit[env][alan])
        if n: print("  %-5s %-14s %d" % (env, alan, n))
        tot += n
    print("  %-5s %-14s %d" % (env, "TOPLAM", tot))
print("\nORNEK — ayni id, iki ortamda kod noktasi karsilastirmasi")
secili = None
for i, u, p in sadece:
    if unicodedata.normalize("NFC", p) != p or unicodedata.normalize("NFC", u) != u:
        secili = i; break
if secili:
    for env in ("uat", "prod"):
        n = adlar[env][secili]
        print("  %-5s id=%-9s %r" % (env, secili, n))
        print("        uzunluk=%d  kod noktalari=%s" % (len(n), ["U+%04X" % ord(c) for c in n]))
        print("        kaynak: %s/%s" % (env, kaynak[env].get(secili, "-")))
print("\nDIGER NFD ORNEKLERI (prod) — ilk 6")
c = 0
for alan in AD_ALANLARI:
    for i, (v, dosya) in list(nfd["prod"][alan].items()):
        if c >= 6: break
        print("  %-14s id=%-9s %r" % (alan, i, v))
        print("        var olan  =%s" % (["U+%04X" % ord(x) for x in v][:14]))
        print("        NFC hali  =%s" % (["U+%04X" % ord(x) for x in unicodedata.normalize("NFC", v)][:14]))
        print("        kaynak: prod/%s" % dosya); c += 1

# ============================================================ B1.3
baslik("B1.3", "KARAKTER KAYBI (?) ve GORUNMEZ KARAKTERLER")
print("Yontem: ad alanlarinda regex taramasi; her bulgu kaynak dosyasiyla listelenir.")
DESEN = [
    ("kelime icinde '?' — kodlanamayan harf", re.compile(r"\w\?\w")),
    ("sonda TAB",                             re.compile(r"\t$")),
    ("sonda NBSP (U+00A0)",                   re.compile(" $")),
    ("bas veya sonda bosluk",                 re.compile(r"^\s|\s$")),
    ("cift bosluk",                           re.compile(r"  +")),
    ("cift-encode izi (Ã/Ä/Å)",               re.compile(r"[ÃÄÅ][-¿­]")),
]
for etiket, rx in DESEN:
    print("\n  --- %s ---" % etiket)
    for env in ("uat", "prod"):
        bul = {}
        for kind, alan, i, v, dosya in ad_tum[env]:
            if v and rx.search(v):
                bul.setdefault(v, (alan, i, dosya))
        print("    %-5s %d farkli ad" % (env, len(bul)))
        for v, (alan, i, dosya) in sorted(bul.items())[:6]:
            print("       %-14s id=%-9s %r   (%s/%s)" % (alan, i, v, env, dosya))

# ============================================================ B1.4
baslik("B1.4", "BOS AD")
for env in ("uat", "prod"):
    bos = [(alan, i, dosya) for kind, alan, i, v, dosya in ad_tum[env] if v.strip() == ""]
    tekil = {(a, i) for a, i, _ in bos}
    print("  %-5s %d kayit (tekil %d)  ornek: %s" % (env, len(bos), len(tekil), bos[:3]))

# ============================================================ B2
baslik("B2", "popularity ALANI INDEX'LER ARASINDA AYNI OLCEKTE DEGIL")
print("""Yontem: her (ortam, alan, index) icin tekil id bazinda deger toplanir; medyan ve
maksimum hesaplanir. 'Normalize' iddiasinin dayanagi maksimumun tam 100 olmasi;
'ham sayac' iddiasinin dayanagi maksimumun medyanin yuzlerce kati olmasi.""")
for env in ("prod", "uat"):
    print("\n  --- %s ---" % env.upper())
    for alan in POP_ALANLARI:
        for kind in ("songs", "videos", "performers", "albums"):
            d = pop_tum.get((env, alan, kind))
            if not d: continue
            v = sorted(d.values()); m = st.median(v)
            print("    %-19s %-11s n=%-6d medyan=%-9.2f maks=%-10.2f maks/medyan=%s"
                  % (alan, kind, len(v), m, v[-1], ("%.0fx" % (v[-1]/m)) if m else "-"))

baslik("SON", "OZET")
print("""Yukaridaki her sayi %s altindaki JSON dosyalarindan
sayilarak uretildi; hicbiri elle girilmedi. Script tekrar kosuldugunda ayni sonucu verir.
Bir satiri kendiniz dogrulamak icin yaninda yazan kaynak dosyayi acip ilgili id'yi aratin.""" % KOK)
