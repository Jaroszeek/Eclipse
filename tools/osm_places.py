# Buduje liste nazwanych miejsc Krakowa do wyszukiwarki Dojazdu.
# Uruchamiane recznie przy aktualizacji danych; wynik ladowany do app/src/main/assets.
import gzip, json, sys, time, unicodedata, urllib.parse, urllib.request

Q = """
[out:json][timeout:300];
area["name"="Kraków"]["admin_level"="7"]->.a;
(
  nwr["name"]["amenity"](area.a);
  nwr["name"]["shop"](area.a);
  nwr["name"]["tourism"](area.a);
  nwr["name"]["leisure"](area.a);
  nwr["name"]["historic"](area.a);
  nwr["name"]["office"](area.a);
  nwr["name"]["place"](area.a);
  nwr["name"]["railway"~"^(station|halt)$"](area.a);
  way["name"]["highway"~"^(motorway|trunk|primary|secondary|tertiary|residential|living_street|pedestrian|unclassified)$"](area.a);
);
out center tags qt;
"""

ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]

raw = None
for url in ENDPOINTS:
    try:
        t0 = time.time()
        req = urllib.request.Request(url, data=urllib.parse.urlencode({"data": Q}).encode(),
                                     headers={"User-Agent": "Eclipse-dev-build/1.0"})
        raw = urllib.request.urlopen(req, timeout=400).read()
        print(f"{url}: {len(raw)/1e6:.1f} MB w {time.time()-t0:.0f}s")
        break
    except Exception as e:
        print(f"{url}: {e}")
if raw is None:
    sys.exit("zaden serwer nie odpowiedzial")


def norm(name):
    s = unicodedata.normalize("NFD", name.lower())
    return "".join(c for c in s if unicodedata.category(c) != "Mn").replace("\u0142", "l")


KINDS = ("amenity", "shop", "tourism", "leisure", "historic", "office", "place")
rows, seen = [], set()
for e in json.loads(raw)["elements"]:
    t = e.get("tags", {})
    name = (t.get("name") or "").strip()
    lat = e.get("lat") or (e.get("center") or {}).get("lat")
    lon = e.get("lon") or (e.get("center") or {}).get("lon")
    if not name or lat is None or lon is None:
        continue
    kind = "street" if t.get("highway") else next((t[k] for k in KINDS if k in t), "")
    # ten sam obiekt w promieniu ~100 m zapisujemy raz; dluga ulica zostaje jako wiele punktow
    key = (norm(name), round(lat, 3), round(lon, 3))
    if key in seen:
        continue
    seen.add(key)
    rows.append((name, kind, round(lat, 5), round(lon, 5)))

rows.sort()
text = "\n".join(f"{n}\t{k}\t{a}\t{o}" for n, k, a, o in rows)
print(f"punktow: {len(rows)}, nazw: {len({norm(r[0]) for r in rows})}")
print(f"tekst {len(text.encode())/1e6:.2f} MB")
open("places.tsv", "w", encoding="utf-8", newline="
").write(text)

idx = {}
for n, k, a, o in rows:
    idx.setdefault(norm(n), []).append(k)
for probe in ["dluga", "galeria krakowska", "wawel", "kazimierz", "nowa huta", "rynek glowny",
              "biblioteka", "basen", "szpital uniwersytecki", "karmelicka"]:
    hits = [n for n in idx if probe in n]
    print(f"  {probe!r}: {len(hits)} nazw -> {hits[:3]}")
