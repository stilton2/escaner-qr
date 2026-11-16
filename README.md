# Escáner QR

Lector de códigos QR y de barras para Android, **sin anuncios, sin rastreadores y sin internet**.
Pensado para instaladores (alarmas, cámaras, redes): reconoce etiquetas de equipos y copia lo que hace falta sin teclear.
Con [Shizuku](https://shizuku.rikka.app/) añade una pestaña para gestionar las wifis guardadas del móvil.

*English below.*

## Qué hace

- **Lee** QR, Code 128, Data Matrix, Code 39, EAN/UPC y más, con la cámara (linterna y zoom), desde una imagen de la galería o desde «Compartir» de otra app.
- **Resultado según lo leído**: abrir enlace, llamar, correo, SMS, mapa, contacto, evento, texto…
- **Etiquetas de equipos**:
  - Etiqueta JSON con modelo, MAC y nº de serie → MAC en grande, ya copiada.
  - MAC con o sin separadores → se copia siempre como `AA:BB:CC:DD:EE:FF` (y botón para copiarla sin «:»).
  - Número de SIM (ICCID, 19–20 cifras que empiezan por 89) → se enseña de 4 en 4 y se copia entero.
  - Código de artículo con modelo, nº de serie y fecha → una fila por dato, cada una con su botón de copiar.
- **Wifi**: botón **Conectar**. Con Shizuku la guarda como red normal (se puede cambiar u olvidar luego en Ajustes) y se conecta al momento; sin Shizuku usa la ventana de Android «¿Guardar esta red?». Copiar contraseña y ver la red como QR.
- **Historial** por días, con buscador, favoritos y notas; distingue lo escaneado de lo importado.
- **Importar historial de otras apps**: QR Bot, Binary Eye (CSV y JSON), QR & Barcode Scanner (wewewe718), Barcode Scanner (ZXing) y casi cualquier CSV/JSON/TXT: reconoce las columnas por su nombre y, si no, te deja elegirlas. Importar dos veces no duplica.
- **Exportar** el historial en CSV o JSON.
- **Ajustes**: copiar al portapapeles al leer, copiar directamente la MAC, vibrar, pitido, abrir enlaces solos, lectura continua, repetidos, buscador, tema.

### Pestaña Crear
- Hacer códigos: texto, enlace, wifi, teléfono, SMS, correo, contacto, ubicación, evento, portapapeles y códigos de barras (EAN-13, EAN-8, UPC-A, Code 128, Code 39, ITF, Data Matrix, Aztec, PDF 417, con aviso si el dato no vale para ese formato).
- Compartir la imagen, guardarla en la galería o copiar el texto. Lo creado queda en el Historial como «Creado».

### Pestaña Wifis
Dos listas: **En el móvil** (necesita Shizuku) y **Leídas** (todas las wifis escaneadas o importadas, con marca si ya están en el móvil o con otra contraseña; no necesita Shizuku).

- Lista de las redes guardadas con buscador, ordenar (nombre, última conexión, guardada por primera vez, última vez escaneada) y filtros (con ubicación, escaneadas, por fechas, de otras apps).
- Ficha de cada red: QR grande para compartir, conectar, cambiar contraseña, olvidar, quién la guardó, ajustes de la red y lecturas de esa red en tu historial.
- En móviles **Samsung**: dónde se usa cada red (mapa), cuándo se guardó por primera vez y la última conexión.
- Redes que otra app dejó como «sugerencia» (no se pueden editar en Ajustes): se señalan y se pueden volver a guardar como red normal.
- Con Shizuku, la ficha enseña la **contraseña** de cada red guardada (oculta hasta pulsar el ojo; solo en memoria, nunca se guarda ni sale del móvil). Las de «sugerencias» de otras apps Android no las da.
- **Pasar de golpe** las redes que otra app dejó como sugerencia (p. ej. QR Bot) a redes normales, con la contraseña de tu historial importado, para poder desinstalar esa app sin perderlas.
- Al leer el QR de una wifi que ya tienes, avisa si la contraseña es la misma u otra.

Sin Shizuku todo lo demás funciona igual.

## Privacidad

- Permisos: **cámara**, **vibración** y Shizuku (opcional). **Sin permiso de internet**: nada sale del móvil.
- Sin anuncios, sin analíticas, sin servicios de Google (ni ML Kit ni Firebase).
- El historial vive solo en la app. Exportarlo es cosa tuya.

## Instalar

Descarga la APK de [Releases](../../releases) (cuando la haya) o compílala:

```sh
./gradlew assembleDebug        # necesita JDK 17+ y el SDK de Android (compileSdk 36)
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requiere Android 11 (API 30) o superior. La APK incluida es solo para arm64.

## Librerías

| Librería | Para qué | Licencia |
|---|---|---|
| [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) | Leer códigos | Apache-2.0 |
| [ZXing core](https://github.com/zxing/zxing) | Dibujar QR | Apache-2.0 |
| [Shizuku API](https://github.com/RikkaApps/Shizuku-API) | Órdenes de wifi como usuario shell | MIT |
| [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) | Servicios internos de wifi | Apache-2.0 |
| AndroidX, CameraX, Material Components | Interfaz y cámara | Apache-2.0 |

## Licencia

[GPL-3.0](LICENSE): puedes usarla, estudiarla, cambiarla y repartirla gratis; si repartes una versión cambiada, su código también tiene que ser libre.

---

## English

**Escáner QR** is an Android QR/barcode scanner with **no ads, no trackers and no internet permission**, aimed at installers: it recognises device labels (JSON with model/MAC/serial, bare MACs, SIM ICCIDs, part numbers) and copies the useful bit automatically (MACs always as `AA:BB:CC:DD:EE:FF`).
Wifi QR codes get a **Connect** button (saved as a normal network via [Shizuku](https://shizuku.rikka.app/), or through Android's "Save this network?" dialog without it).
History by day with search, favourites and notes; **import** from QR Bot, Binary Eye and generic CSV/JSON/TXT (column mapping when unknown); export to CSV or JSON.
A **Create** tab makes QR codes (text, link, wifi, phone, SMS, e-mail, contact, location, event) and barcodes (EAN, UPC, Code 128/39, ITF, Data Matrix, Aztec, PDF 417).
With Shizuku, a **Wifis** tab lists saved networks (with their passwords, shown on demand, memory only) with sort/filters, share-as-QR, connect, change password, forget, bulk-convert other apps' suggestions into normal networks, network settings and, on Samsung phones, location and first/last connection. A second list shows every wifi code you scanned or imported.
UI in Spanish. Android 11+. License: GPL-3.0.
