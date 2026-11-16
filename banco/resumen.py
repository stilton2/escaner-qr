# Tablas por formato a partir de una o dos salidas de banco.py (S-J6).
import re, sys, collections
grupos = [('QR', r'^qrcode'), ('Micro QR', r'^microqr'), ('rMQR', r'^rmqr'), ('Aztec', r'^aztec'), ('Data Matrix', r'^datamatrix'),
          ('PDF417', r'^pdf417'), ('Micro PDF417', r'^micropdf'), ('MaxiCode', r'^maxicode'), ('DataBar', r'^(rss|databar)'),
          ('EAN/UPC', r'^(ean|upc)'), ('Code 39/93/128', r'^code'), ('ITF', r'^itf'), ('Codabar', r'^codabar'), ('DX', r'^dx'),
          ('Telepen', r'^telepen'), ('Varios', r'^multi'), ('Falsos (sin código)', r'^falsepos')]
def leer(f):
    d = {}
    for l in open(f):
        m = re.match(r'(\S+)@(\S+) n=(\d+) (.*)', l)
        if not m: continue
        c, v, n, rest = m.groups()
        for p, b, mal in re.findall(r'(\w+) (\d+) mal (\d+)', rest): d[(c, v, p)] = (int(n), int(b), int(mal))
    return d
a = leer(sys.argv[1]); b = leer(sys.argv[2]); d = {**a, **b}
for v in ['orig', 'espejo', 'mitad', 'invertido']:
    print(f'\n### {v}\n| Formato | n | foto antes | foto ahora | cámara antes | cámara ahora |\n|---|---|---|---|---|---|')
    T = collections.Counter(); F = collections.Counter()
    for g, rx in grupos:
        cs = sorted({c for (c, vv, p) in d if vv == v and re.search(rx, c)})
        n = sum(d.get((c, v, 'img24'), (0, 0, 0))[0] for c in cs)
        def s(p, i=1): return sum(d.get((c, v, p), (0, 0, 0))[i] for c in cs)
        if g.startswith('Falsos'):
            print(f'| {g} | {n} | {s("img24",2)} falsos | {s("img",2)} | {s("cam24",2)} | {s("cam",2)} |')
        else:
            print(f'| {g} | {n} | {s("img24")} | {s("img")} | {s("cam24")} | {s("cam")} |')
            for p in ['img24', 'img', 'cam24', 'cam']: T[p] += s(p); F[p] += s(p, 2)
            T['n'] += n
    print(f'| **Total** | {T["n"]} | **{T["img24"]}** | **{T["img"]}** | **{T["cam24"]}** | **{T["cam"]}** |')
    print(f'| Leídos con texto distinto al esperado* | | {F["img24"]} | {F["img"]} | {F["cam24"]} | {F["cam"]} |')
