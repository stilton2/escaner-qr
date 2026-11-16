# Banco de pruebas del lector EN EL PORTÁTIL (S-J6): mismo motor que la app (zxing-cpp 3.1.1 de PyPI) y las mismas pasadas que
# app/src/main/java/es/kalvo/escanerqr/nucleo/Camara.kt (Plan.camara / Plan.imagen). Si cambias el plan allí, cámbialo aquí.
# Uso:  python3 -m venv v && v/bin/pip install zxing-cpp==3.1.1 pillow numpy
#       git clone --depth 1 --branch v3.1.1 https://github.com/zxing-cpp/zxing-cpp zxc      (muestras, Apache 2.0; NO al repo)
#       v/bin/python -I banco.py zxc/test/samples orig,espejo,mitad,invertido [img24,img,cam24,cam] > salida.txt
#       v/bin/python -I resumen.py salida_antes.txt salida_ahora.txt      (tablas por formato)
# Solo imprime cuentas. img24/cam24 = v2.4; img/cam = ahora.
# Laptop bench (same zxing-cpp core 3.1.1 as the app, via PyPI). Prints only counts.
import sys, os, re, time, zxingcpp, numpy as np
from PIL import Image, ImageOps
root = sys.argv[1]; variantes = sys.argv[2].split(',')
B = zxingcpp.Binarizer
ESC = re.compile(r'<(NUL|SOH|STX|ETX|EOT|ENQ|ACK|BEL|BS|HT|LF|VT|FF|CR|SO|SI|DLE|DC1|DC2|DC3|DC4|NAK|SYN|ETB|CAN|EM|SUB|ESC|FS|GS|RS|US|DEL|U\+[0-9A-F]+)>')
def limpio(s): return ''.join(c for c in ESC.sub('', s) if ord(c) >= 32 and ord(c) != 127 and c not in '()�' and not c.isspace())
def read(a, maxs=255, **kw):
    try: return [r for r in zxingcpp.read_barcodes(a, **kw) if r.valid][:maxs]
    except Exception: return []
def reducir(a, f):
    h, w = a.shape[0]//f*f, a.shape[1]//f*f
    return a[:h,:w].reshape(h//f, f, w//f, f).mean(axis=(1,3)).astype(np.uint8)
def ampliar(a, f, suave):
    if not suave: return np.repeat(np.repeat(a, f, 0), f, 1)
    return np.array(Image.fromarray(a).resize((a.shape[1]*f, a.shape[0]*f), Image.BILINEAR))
def central(a, frac):
    h, w = a.shape; cw, ch = int(w*frac)//2*2, int(h*frac)//2*2
    x, y = (w-cw)//2//2*2, (h-ch)//2//2*2
    return a[y:y+ch, x:x+cw]
def pasada(a, frac=1, esc=1, suave=False, hist=False, espejo=False, inv=False, maxs=255):
    if frac < 1: a = central(a, frac)
    if a.shape[0] < 2 or a.shape[1] < 2: return []
    if esc > 1: a = ampliar(a, esc, suave)
    elif esc < -1: a = reducir(a, -esc)
    if espejo: a = np.ascontiguousarray(np.fliplr(a))
    if inv: a = 255 - a
    r = read(np.ascontiguousarray(a), maxs, try_downscale=esc <= 1, binarizer=B.GlobalHistogram if hist else B.LocalAverage)
    extra = esc > 1 or hist or espejo or inv
    return [x for x in r if not (extra and 'ITF' in str(x.format) and len(x.text) < 10)]
def plan_camara(w, h, n):
    g = min(w, h) > 1300
    l = [dict(esc=-2 if g else 1), dict(frac=0.5) if g else dict(frac=0.5, esc=2)]
    l.append([dict(esc=-2 if g else 1, hist=True), dict(frac=0.3 if g else 0.35, esc=3, suave=True), dict(esc=-2 if g else 1, espejo=True), dict(esc=-2 if g else 1, inv=True)][n % 4])
    return l
def plan_imagen(w, h):
    lado = max(w, h); l = [dict(hist=True), dict(espejo=True), dict(inv=True)]
    if lado <= 1600: l.append(dict(esc=2))
    if lado <= 1100: l.append(dict(esc=3, suave=True))
    if lado > 1600: l += [dict(frac=0.5, esc=2), dict(frac=0.34, esc=3, suave=True)]
    return l
def perfil(p, a):
    if p == 'img24': return read(a)
    if p == 'cam24': return read(a, 1)
    if p == 'img':
        r = read(a)
        if r and max(a.shape) > 1600: return r
        for q in plan_imagen(a.shape[1], a.shape[0]): r += pasada(a, **q)
        return r
    if p == 'cam':
        for n in range(4):
            for q in plan_camara(a.shape[1], a.shape[0], n):
                r = pasada(a, maxs=8, **q)
                if r: return r
        return []
def variante(im, v):
    if v == 'espejo': return ImageOps.mirror(im)
    if v == 'mitad': return im.resize((max(1, im.width//2), max(1, im.height//2)), Image.BILINEAR)
    if v == 'invertido': return ImageOps.invert(im)
    return im
perfiles = (sys.argv[3] if len(sys.argv) > 3 else 'img24,img,cam24,cam').split(',')
tot = {}
for c in sorted(os.listdir(root)):
    d = os.path.join(root, c)
    if not os.path.isdir(d): continue
    falsos = c.startswith('falsepositives'); sin = c.startswith('multi') or c in ('qrcode-7', 'pdf417-4')
    for v in variantes:
        bien = dict.fromkeys(perfiles, 0); mal = dict.fromkeys(perfiles, 0); ms = dict.fromkeys(perfiles, 0.0); n = 0
        for f in sorted(os.listdir(d)):
            if f.rsplit('.', 1)[-1].lower() not in ('png', 'jpg', 'webp'): continue
            base = os.path.join(d, f.rsplit('.', 1)[0])
            esp = None
            if os.path.exists(base + '.txt'): esp = ('t', open(base + '.txt', encoding='utf-8', errors='replace').read())
            elif os.path.exists(base + '.bin'): esp = ('b', open(base + '.bin', 'rb').read())
            if not falsos and not sin and esp is None: continue
            a = np.array(variante(Image.open(os.path.join(d, f)).convert('L'), v)); n += 1
            for p in perfiles:
                t0 = time.time(); r = perfil(p, a); ms[p] += time.time() - t0
                if falsos: mal[p] += bool(r); continue
                if sin: bien[p] += bool(r); continue
                def ok(x):
                    if esp[0] == 'b':
                        return bytes(x.bytes) == esp[1] or limpio(x.text) == limpio(esp[1].decode('latin-1'))
                    A, Bt = limpio(x.text), limpio(esp[1])
                    return A == Bt or '0' + A == Bt or A == '0' + Bt or ('extension' in c and A and Bt.startswith(A))
                if any(ok(x) for x in r): bien[p] += 1
                elif r: mal[p] += 1
        print(f'{c}@{v} n={n} ' + ' · '.join(f'{p} {bien[p]} mal {mal[p]}' for p in perfiles), flush=True)
        for p in perfiles:
            t = tot.setdefault((p, v), [0, 0, 0, 0.0])
            if not falsos: t[0] += bien[p]; t[2] += n
            t[1] += mal[p]; t[3] += ms[p]
for (p, v), t in sorted(tot.items(), key=lambda k: (k[0][1], k[0][0])):
    print(f'TOTAL {v} {p}: bien {t[0]}/{t[2]} mal {t[1]} {t[3]:.1f}s')
