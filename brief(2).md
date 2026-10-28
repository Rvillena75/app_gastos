# Brief vigente — App Android personal para gestión automática de gastos

**Última actualización documental:** 27 de octubre de 2026

**Estado:** núcleo v1 implementado, con QA histórico en dispositivo y brechas de integridad pendientes; ejecución incremental del roadmap, limitada actualmente a F0.1.

## Autoridad y estado del roadmap

[docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) conserva el plan y las decisiones explícitas del usuario para futuras conversaciones. Sus decisiones posteriores sustituyen restricciones históricas contradictorias de este brief. [docs/F0_BASELINE.md](docs/F0_BASELINE.md) registra verificaciones actuales y sus límites. No interpretar las fases futuras como funcionalidades implementadas.

Decisiones acordadas todavía pendientes de implementación: deduplicación conservadora con ventana máxima de 24 horas; enriquecimiento con OCR, historial confirmado y ubicación solo locales; fecha impresa para boletas; categorías personalizables; backup sin cifrado y restauración por reemplazo recuperable; recurrencias por recordar/confirmar. Después de estabilizar, priorizar control financiero local y luego PDF Banco de Chile del layout aportado. Gmail, cuotas/reembolsos ampliados, sincronización y multiusuario no forman parte de F0.1.

## 1. Visión

Construir una aplicación Android nativa, de uso exclusivamente personal, que registre y clasifique gastos con la menor intervención manual posible.

En la primera versión la app debe registrar gastos pagados con tarjeta a partir de las notificaciones visibles de Google Wallet. También debe permitir adjuntar una foto de la boleta y procesarla con OCR. La integración con Gmail y la conciliación contra estados de cuenta bancarios quedan para etapas posteriores.

El objetivo no es que el usuario "anote gastos". El objetivo es que la aplicación registre los gastos automáticamente y que el usuario solo intervenga cuando exista información que el sistema no pueda inferir con suficiente confianza.

La unidad central del sistema es un **gasto real**. Google Wallet, GPS, boleta y correcciones manuales son **evidencias** asociadas al mismo gasto, no transacciones independientes. Gmail y los estados de cuenta quedan como posibles fuentes futuras.

## 1.1 Alcance aprobado de v1

- Fuente automática: notificaciones de compras con tarjeta visibles en Google Wallet.
- Fuentes complementarias: fotografía de boleta con OCR y edición manual.
- La revisión y asociación de evidencias debe ejecutarse automáticamente en segundo plano.
- Se registra el gasto completo de la compra. No existe soporte de cuotas en v1.
- No se modelan reembolsos ni gastos reembolsables. Si un gasto deja de ser válido, el usuario lo elimina.
- Actualmente la ubicación se guarda como evidencia del evento. F4 incorporará el uso contextual local de ubicaciones e historial confirmado; la ubicación sola no fijará un comercio ni requerirá tracking continuo o servicios de mapas.
- Gmail queda fuera de v1 y se implementará posteriormente.
- Se conserva el diseño extensible para incorporar estados de cuenta más adelante, pero no se implementa ese flujo en v1.
- La navegación productiva incluye `Resumen`, `Movimientos` y `Análisis`, además de una única acción global `Agregar`.
- `Análisis` opera sobre un mes explícito. No existen pantallas productivas de comparación de modelos.
- Un gasto se considera completo y no requiere revisión cuando tiene **comercio y monto**, aunque todavía no tenga categoría, nota, productos, tarjeta o boleta.
- Cada gasto puede asociarse opcionalmente a una tarjeta y a una lista de productos.
- Categorías y tarjetas se administran desde Configuración.

## 1.2 Plan vigente de procesamiento de boletas

El plan activo de la aplicación es mantener una sola ruta local y ligera para boletas:

```text
imagen
-> preprocessing existente
-> ML Kit Text Recognition latino bundled
-> OcrDocument espacial
-> parser determinista chileno
-> ReceiptValidator
-> categorización rápida
-> StructuredReceipt / JSON comparable
```

Decisiones vigentes:

- ML Kit es el único OCR/modelo especializado activo en el build.
- El OCR conserva bloques, líneas, elementos, orden, bounding boxes, coordenadas, dimensiones y confidence cuando la API lo entrega.
- El parser común trabaja sobre `OcrDocument`, no sobre un `String` OCR aplanado.
- Todo resultado pasa por validaciones deterministas antes de actualizar el gasto.
- Qwen, Gemma, PP-OCRv6 y Gemini Nano fueron retirados del build activo; no se descargan, empaquetan ni aparecen como capacidades `UNSUPPORTED`.
- Las correcciones manuales de comercio se guardan localmente como alias normalizados exactos. Las correcciones de categoría mantienen el mismo principio local.
- No se reentrena un modelo ni se envían datos financieros a la nube.
- Las herramientas visuales de comparación y prueba de modelos fueron retiradas de la app.
- La UI de producción no expone OCR crudo, JSON, confidence, tiempos ni nombres de pipelines.
- El dispositivo no debe conservar archivos GGUF, modelos de experimentación ni fotografías del laboratorio de OCR. Los fixtures del laboratorio pueden mantenerse únicamente en el PC, fuera del almacenamiento productivo de la app y acompañados por hashes de integridad.

## 1.3 Estado funcional implementado

El código revisado incluye las funcionalidades siguientes. La instalación y QA de agosto se documentan como evidencia histórica; no se ha vuelto a comprobar el teléfono en F0.1:

- registro automático desde notificaciones visibles de Google Wallet;
- alta manual y captura de boleta desde una sola acción `Agregar`;
- OCR local con ML Kit, parser chileno determinista y procesamiento en segundo plano;
- revisión excepcional basada en la presencia de comercio y monto;
- edición de comercio, monto, tarjeta, categoría, nota y productos;
- creación, edición y eliminación de categorías;
- creación, edición y eliminación de tarjetas, incluyendo nombre, últimos cuatro dígitos, tipo y color;
- resumen mensual separado por tarjeta mediante un carrusel apilado y deslizable;
- lista reciente sincronizada con la tarjeta seleccionada;
- búsqueda y filtros combinables por tarjeta, categoría y estado de revisión;
- detalle de gasto con boleta accesible bajo demanda en pantalla completa y zoom;
- borrado con confirmación y ventana para deshacer;
- análisis mensual con presupuesto general, gráficos por semana, categoría y tarjeta;
- exportación local `.xlsx` con resumen del periodo, todos los movimientos y catálogo de tarjetas;
- almacenamiento local con Room y archivos privados para las boletas.

No están implementados todavía: Gmail, estados de cuenta, conciliación bancaria, cuotas, reembolsos, presupuestos por categoría, recurrencias, backup/restauración ni exportación de boletas.

## 1.4 Validación operativa histórica — agosto de 2026

Los puntos de esta sección describen la validación registrada entonces; no sustituyen los resultados actuales de F0.1 ni autorizan modificar el teléfono.

- Dispositivo: Samsung SM-S918B, Android 14+.
- Compilación: `testDebugUnitTest` y `assembleDebug` aprobados.
- Instalación: APK incremental, conservando movimientos, base Room y boletas.
- Navegación comprobada: inicio repetido, `Resumen`, las tres posiciones del carrusel, `Movimientos`, `Análisis`, `Agregar` y `Configuración` sin cierres.
- Análisis comprobado en el dispositivo con presupuesto persistente, gráficos, clasificación editable de tarjetas y creación real de un archivo `.xlsx` válido.
- Almacenamiento privado después de limpiar pruebas: aproximadamente 23,4 MB de datos, más el APK; sin archivos GGUF ni directorio de laboratorio en el teléfono.
- Laboratorio respaldado en el PC en `backups/receipt-lab-phone-20260811/`, con seis imágenes y `SHA256SUMS.txt` verificado.

---

## 2. Contexto y problema — contexto futuro

Los estados de cuenta bancarios contienen descriptores difíciles o imposibles de interpretar directamente, por ejemplo:

- `SB 648`
- `CV 886`
- `AHUM L358 CNO ALBA`
- `MERCADOPAGO*ROVI`
- `MERCADOPAGO*SAMSUNGELEC`
- `PAYU *UBER EATS`
- `CONSULTAS/PROCEDIMI`
- `JUMBO PILOTO EL ALB`

Algunos nombres permiten identificar el comercio, pero otros reflejan:

- razón social;
- procesador de pagos;
- número de sucursal;
- adquirente;
- comercio truncado;
- intermediarios como Mercado Pago o PayU;
- nombres internos del banco.

Por eso el sistema no debe depender del descriptor bancario como fuente principal para identificar una compra.

La estrategia de v1 será recopilar y revisar automáticamente las evidencias disponibles en el momento de la compra y al procesar una boleta. Gmail y la conciliación contra estados de cuenta bancarios se podrán incorporar posteriormente como fuentes adicionales.

---

## 3. Usuario objetivo

Un único usuario.

No diseñar para:

- publicación en Google Play;
- multiusuario;
- equipos empresariales;
- cumplimiento comercial;
- monetización;
- planes o suscripciones;
- soporte de múltiples cuentas de usuario;
- restricciones innecesarias de permisos pensadas para distribución pública.

Priorizar funcionalidad, automatización, privacidad local y facilidad de uso personal.

---

## 4. Principios de producto

### 4.1 Automatización por defecto

La mayoría de los gastos deben registrarse sin intervención del usuario.

### 4.2 Intervención manual como excepción

El usuario debe intervenir únicamente cuando:

- el comercio sea ambiguo;
- el monto esté ausente;
- quiera asignar o corregir una categoría;
- quiera agregar detalle;
- quiera adjuntar una boleta;
- quiera borrar un gasto;
- quiera corregir una inferencia incorrecta.

### 4.3 Nunca duplicar un gasto por tener múltiples fuentes

Por ejemplo:

- notificación de Wallet;
- foto de boleta;

pueden corresponder al mismo gasto. Gmail y los estados de cuenta no participan todavía en la deduplicación de v1.

El sistema debe fusionarlos como evidencias de una sola entidad `Expense`.

### 4.4 Las evidencias se combinan sin inventar datos

- Wallet, boleta y edición manual sirven para identificar y describir la compra.
- El monto y la moneda detectados deben conservar su fuente y confianza.
- La app no realiza conciliación contra estados de cuenta bancarios en v1 ni afirma que un gasto fue confirmado por el banco.
- La boleta sí pasa por reconciliaciones contables deterministas cuando hay datos suficientes: productos menos descuentos, cantidad por precio unitario, neto más IVA y subtotal/total.

### 4.5 Explicabilidad

La app debe poder mostrar por qué cree que un gasto corresponde a determinado comercio.

Ejemplo:

- monto exacto;
- mismo día;
- descriptor conocido;
- ubicación puntual del evento, conservando precisión y timestamp; su uso contextual local con historial confirmado se implementará en F4;
- boleta asociada;
- regla histórica.

### 4.6 Local-first

Los datos financieros principales deben vivir en el dispositivo.

La app debe funcionar sin depender de un backend remoto para registrar o consultar gastos.

---

# 5. Plataforma y stack base

## 5.1 Plataforma

- Android nativo.
- Kotlin.
- Jetpack Compose.
- Arquitectura modular y mantenible.

## 5.2 Componentes sugeridos

- UI: Jetpack Compose
- Navegación: Navigation Compose
- Base local: Room
- Procesamiento diferido: WorkManager
- Preferencias/configuración: DataStore cuando corresponda; SharedPreferences para alias locales pequeños de corrección
- Trigger de compras: `NotificationListenerService`
- Ubicación: Fused Location Provider / APIs Android equivalentes
- Cámara: `ActivityResultContracts.TakePicture` con la aplicación de cámara del sistema; CameraX figura como dependencia, pero no es el flujo activo
- OCR: ML Kit Text Recognition
- Serialización: contratos Kotlin serializados con `org.json` en la implementación actual
- DI: `AppContainer` explícito en la implementación actual; no introducir Hilt/Koin sin necesidad
- Logs: sistema estructurado de logging local

Codex puede cambiar librerías concretas si existe una alternativa claramente mejor, manteniendo la funcionalidad y arquitectura.

---

# 6. Modelo conceptual

El sistema debe separar explícitamente:

## 6.1 Expense

Representa el gasto real.

Ejemplo:

```text
Expense
├── id
├── amount / amountMinor
├── currency
├── occurredAt
├── merchant
├── category
├── description / nota
├── necessity
├── status
├── confidence
├── cardLast4
├── products[]
└── metadata
```

La regla productiva de revisión es deliberadamente simple:

```text
requiere revisión = comercio vacío OR monto ausente
```

Categoría, nota, tarjeta, productos y boleta enriquecen el gasto, pero su ausencia no lo deja pendiente.

## 6.1.1 ExpenseProduct

Producto opcional asociado a un gasto, creado manualmente o extraído desde una boleta.

```text
ExpenseProduct
├── id
├── expenseId
├── name
├── quantity
└── unitPriceMinor
```

La UI debe permitir agregar, editar y eliminar productos independientemente de comercio, monto, categoría y nota.

## 6.2 Evidence

Una observación que ayuda a identificar o confirmar un gasto.

Tipos iniciales:

```text
WALLET_NOTIFICATION
LOCATION
RECEIPT
RECEIPT_OCR
MANUAL_CORRECTION
MERCHANT_RULE
SYSTEM_INFERENCE
```

Cada evidencia debe poder existir independientemente y enlazarse posteriormente a un `Expense`.

## 6.3 Merchant

Entidad normalizada para comercios.

Ejemplo:

```text
Merchant
├── id
├── name
├── aliases
├── descriptors
├── categoryDefault
└── confidence/history
```

## 6.4 MerchantRule

Regla aprendida o definida para resolver comercios.

Ejemplos:

```text
"SB 648" -> Starbucks
"AHUM L358*" -> Farmacias Ahumada
"CV 886" -> Cruz Verde
"JUMBO PILOTO*" -> Jumbo
"PAYU *UBER EATS*" -> Uber Eats
```

Una regla puede considerar texto y señales del historial, por ejemplo:

- monto;
- hora;
- día;
- comercio observado anteriormente;
- fuente;
- tarjeta.

Las reglas activas todavía no usan ubicación. La decisión posterior para F4 permite reforzar coincidencias con ubicaciones e historial confirmado locales, sin crear una asociación global automática por una posición, sin mapas externos y sin tracking permanente.

## 6.5 PaymentCard

Representa una tarjeta identificada por sus últimos cuatro dígitos.

```text
PaymentCard
├── last4
├── name
├── colorHex
└── firstSeenAt
```

Puede aparecer automáticamente desde Wallet o crearse manualmente. El usuario puede editar nombre y color o eliminarla desde Configuración. Eliminar una tarjeta guardada no debe borrar gastos históricos.

## 6.6 BankTransaction — futuro

Movimiento que podría extraerse desde un estado de cuenta en una versión posterior. No forma parte del flujo de v1.

Debe conservar siempre el descriptor bancario original sin modificar.

## 6.7 Receipt

Boleta asociada a un gasto.

Debe almacenar:

- imagen original;
- fecha de captura;
- OCR bruto espacial (`OcrDocument`), conservando bloques, líneas, elementos, orden, bounding boxes y confidence;
- campos estructurados en el contrato común `StructuredReceipt`;
- comercio y razón social;
- RUT validado matemáticamente;
- número de boleta;
- fecha, hora, sucursal, dirección y caja;
- productos con descripción, cantidad, precio unitario, valor y líneas de origen;
- descuentos;
- subtotal, neto, exento, tasa IVA, IVA, total y moneda;
- medio de pago, cuando esté disponible;
- categoría con confidence y source;
- validaciones deterministas y confidence derivado.

La imagen y el OCR original deben mantenerse aunque el parser falle para permitir revisión y reprocesamiento.

## 6.8 EmailPurchaseEvidence — futuro

Información que se podrá extraer desde un email relevante en una versión posterior. No forma parte del flujo de v1.

Campos posibles:

- messageId;
- threadId;
- timestamp;
- sender;
- subject;
- merchant;
- amount;
- currency;
- orderId;
- products;
- receipt/document info;
- raw parsed data mínimo necesario.

No almacenar más contenido del email del necesario.

---

# 7. Flujo principal — compra presencial

## 7.1 Trigger

El usuario paga con Google Wallet.

Google Wallet genera una notificación.

`NotificationListenerService` detecta la notificación de Wallet.

La app crea inmediatamente un gasto provisional.

## 7.2 Datos capturados

Intentar extraer:

- monto;
- moneda;
- comercio mostrado por Wallet, si existe;
- tarjeta o medio de pago, si aparece;
- timestamp;
- título de notificación;
- texto;
- subtexto;
- package name;
- identificador de notificación.

Guardar siempre el texto crudo relevante para poder reparsearlo en el futuro.

## 7.3 Ubicación

Inmediatamente después del trigger:

- solicitar/obtener ubicación;
- guardar latitud;
- longitud;
- precisión;
- timestamp de la medición.

No es necesario tracking GPS permanente.

La ubicación se consulta asociada al evento de compra y conserva precisión y timestamp. No debe ser necesaria para registrar o identificar un gasto. F4 permitirá cruzarla localmente con compras confirmadas; por sí sola solo sugerirá un comercio. No hay tracking continuo ni consultas a mapas. Esa ampliación todavía no está implementada.

## 7.4 Inferencia inicial

La app intenta identificar:

1. comercio;
2. categoría;
3. lugar;
4. confianza.

Fuentes disponibles:

- texto Wallet;
- GPS como evidencia puntual, no como regla aprendida;
- reglas conocidas;
- historial;
- monto;
- horario.

## 7.5 Notificación interactiva propia

Después del pago, mostrar una notificación similar a:

```text
$11.000 · COMERCIO DEMO LOCAL 01
Banco Demo Crédito · ••1111 · 96%

[BOLETA] [EDITAR] [BORRAR]
```

Ejemplo sintético de formato de notificación:

```text
COMERCIO DEMO LOCAL 01       5:45 p. m.
CLP11,000 con Banco Demo Crédito ••1111
```

El parser debe aceptar el prefijo `CLP`, separadores de miles, el comercio con número de local y los últimos cuatro dígitos de la tarjeta. No debe interpretar el número del comercio como cuota.

Si no se identifica:

```text
$13.580 · Comercio desconocido
Las Condes

[BOLETA] [IDENTIFICAR] [BORRAR]
```

Ignorar la notificación no debe impedir que el gasto quede guardado.

---

# 8. Acción — Boleta

Al pulsar `BOLETA`:

1. abrir scanner/cámara dentro de la app;
2. capturar fotografía;
3. asociarla al gasto exacto que originó la notificación;
4. reutilizar el preprocessing desacoplado;
5. ejecutar ML Kit Text Recognition;
6. normalizar el resultado a `OcrDocument` espacial;
7. ejecutar el parser determinista chileno;
8. validar el `StructuredReceipt`;
9. categorizar con reglas/cache/keywords;
10. actualizar la inferencia del gasto;
11. conservar OCR bruto, JSON, validaciones y tiempos.

La fotografía debe mantener relación inequívoca con el `expenseId`.

Ejemplo conceptual:

```text
EXP-20260808-154231
└── receipt_01.jpg
```

## 8.1 OCR

La ruta activa es:

```text
imagen -> preprocessing -> ML Kit OCR
       -> OcrDocument -> parser determinista
       -> ReceiptValidator -> categorización -> Receipt
```

`OcrDocument` debe conservar, cuando estén disponibles:

- bloques;
- líneas;
- elementos;
- texto;
- bounding box;
- X/Y, ancho y alto;
- confidence;
- índice de bloque/línea/elemento;
- orden espacial.

El parser debe usar agrupación por líneas, posiciones X/Y, columnas, proximidad, encabezados, keywords, regex y patrones monetarios. No debe depender exclusivamente del orden del texto concatenado.

Intentar extraer desde la boleta:

- nombre del comercio;
- RUT;
- dirección;
- sucursal;
- fecha;
- hora;
- total;
- subtotal;
- propina, solo si se incorpora explícitamente al contrato de `StructuredReceipt` en una fase posterior;
- número de boleta;
- productos;
- cantidades;
- precio unitario;
- valor por línea;
- descuentos;
- neto, exento, IVA y total;
- medio de pago.

El OCR no debe inventar campos inexistentes ni sobrescribir silenciosamente una corrección manual. Un fallo del OCR no debe impedir guardar la fotografía.

## 8.2 Parser determinista chileno

El parser debe soportar específicamente:

- RUT con puntos, espacios, guion y dígito verificador `K`;
- validación matemática del RUT;
- CLP con `$`, puntos, comas o números agrupados sin interpretar `11.000` como decimal;
- descuentos negativos como `-2.000`, `-$2.000` y `2.000-`;
- `IVA`, `IVA 19%`, `IVA (19%)`, `TOTAL IVA`, `NETO` y `TOTAL NETO`;
- fechas y horas chilenas;
- reconstrucción espacial de filas, columnas, descripciones multilínea y descuentos entre productos.

Cuando la cantidad y el valor de una línea son observables y el precio unitario asignado por OCR no cuadra, se permite derivar el precio únicamente si la división es exacta y la reconciliación aritmética lo demuestra. Esto no es un valor específico de un comercio.

## 8.3 Validación y confidence

Cada resultado debe incluir estados `PASS`, `FAIL` o `UNKNOWN` para:

- RUT;
- fecha;
- total positivo;
- cantidades razonables;
- cantidad por precio unitario;
- productos menos descuentos igual al total;
- neto más IVA igual al total;
- subtotal coherente;
- tasa de IVA coherente;
- categoría disponible.

El confidence general debe combinar campos detectados, confidence OCR si existe, total, fecha, RUT, productos, categoría y validaciones observables. No se acepta un score generado por un modelo como única fuente de confianza.

---

# 9. Acción — Editar / Identificar

Debe permitir resolver una compra en pocos segundos.

Campos:

- comercio;
- monto;
- tarjeta opcional;
- categoría;
- nota opcional;
- productos opcionales con nombre, cantidad y precio unitario;
- necesidad/prioridad;
- etiquetas opcionales;
- ubicación/lugar;

La versión productiva prioriza los campos ya implementados: comercio, monto, tarjeta, categoría, nota y productos. Necesidad, etiquetas y lugar pueden permanecer como extensiones futuras si no aparecen todavía en el editor.

Guardar con comercio y monto válidos debe sacar automáticamente el gasto de `Pendientes`. No exigir categoría, nota, productos ni boleta para marcarlo como revisado.

En la lista de movimientos, mantener una acción discreta de eliminación. La interacción principal será mantener apretado un gasto para abrir una ventana pequeña con `Editar` y `Borrar`; el detalle del gasto también debe ofrecer `Borrar`.

La interfaz debe sugerir:

- comercios aprendidos;
- coincidencias de texto e historial;
- categorías frecuentes.

Una corrección manual es evidencia de alta confianza y debe alimentar el aprendizaje.

En particular, cuando el usuario edita el comercio de un gasto existente, la app debe guardar localmente la asociación entre el texto observado en el `StructuredReceipt`/OCR y el nombre corregido. La asociación se aplica antes de categorizar y serializar futuras boletas.

El aprendizaje vigente es conservador: usa alias exactos después de normalizar mayúsculas, acentos, separadores y sufijos legales. No usa fuzzy matching global para no transformar un comercio distinto. Si el OCR produce otra variante, el usuario puede corregir esa variante también.

---

# 10. Acción — Borrar

En v1 no se modelan reembolsos ni gastos reembolsables. Si el usuario devuelve algo o detecta que una compra no debe registrarse, puede borrar el gasto desde la acción de mantener apretado o desde su detalle.

El borrado debe ser seguro para evitar toques accidentales: mostrar una confirmación breve y mantener la posibilidad de deshacer durante unos segundos. Para no romper evidencias ni referencias internas, se recomienda un borrado lógico local (`DELETED`) aunque la experiencia para el usuario sea simplemente `Borrar`.

---

# 11. Compras online — futuro

La app podrá detectar o importar evidencia desde emails de compras en una versión posterior. Gmail no forma parte de v1.

Buscar señales como:

- confirmación de compra;
- compra realizada;
- tu pedido;
- pedido confirmado;
- orden;
- gracias por tu compra;
- boleta electrónica;
- comprobante;
- pago aprobado.

No depender exclusivamente del asunto.

Debe aprender remitentes y formatos frecuentes.

## 11.1 Extracción

Intentar obtener:

- comercio;
- monto;
- moneda;
- fecha/hora;
- número de pedido;
- productos;
- cantidades;
- costos;
- dirección de entrega si fuera útil;
- documento/boleta asociado.

## 11.2 Asociación futura

Antes de crear un nuevo gasto, buscar coincidencia con:

- gasto Wallet;
- movimiento provisional;
- gasto ya existente;
- otra evidencia de boleta ya procesada.

Nunca crear un duplicado sin intentar matching primero.

---

# 12. Estados de cuenta bancarios — fuera de v1

La app no debe implementar todavía importación, parsing ni conciliación de estados de cuenta. Este apartado documenta una extensión futura, para no perder el contexto del diseño.

En una versión posterior la app podría permitir importar manualmente PDFs u otros formatos compatibles.

Primer banco relevante: Banco de Chile.

El parser futuro debería extraer al menos:

- lugar;
- fecha;
- código de referencia;
- descriptor original;
- monto de operación;
- monto total;
- número de cuota;
- valor cuota;
- tipo de movimiento;
- tarjeta, si es distinguible.

Debe reconocer:

- pagos;
- compras;
- compras en cuotas;
- reversos;
- abonos;
- intereses;
- impuestos;
- cargos;
- devoluciones.

Los descriptores originales jamás deben ser destruidos o sustituidos.

---

# 13. Conciliación — fuera de v1

La conciliación contra `BankTransaction` no se implementa en v1. En una versión posterior intentará vincular un movimiento bancario con un `Expense`.

## 13.1 Señales

En esa versión futura se podrían considerar:

- monto exacto;
- monto aproximado;
- fecha;
- diferencia de fecha contable;
- comercio;
- alias;
- descriptor;
- ubicación;
- email;
- boleta;
- tarjeta;
- historial;
- cuotas;
- reembolso;
- propina;
- reglas previas.

## 13.2 Score

El motor debe entregar una puntuación/confianza.

Ejemplo conceptual:

```text
monto exacto                  +50
fecha misma                   +25
fecha ±1 día                  +18
descriptor conocido           +25
merchant alias                +20
ubicación compatible          +20
email coincidente             +25
boleta coincidente            +35
regla histórica               +30
```

Los pesos no son definitivos.

Diseñar el motor para permitir ajustes sin modificar gran parte del código.

## 13.3 Umbrales

Conceptualmente:

- alta confianza -> unir automáticamente;
- media -> sugerir al usuario;
- baja -> mantener separado y enviar a pendientes.

Los umbrales deben ser configurables.

---

# 14. Relaciones no triviales — futuro

El sistema no debe asumir siempre matching 1:1 cuando se implemente la conciliación bancaria.

Debe soportar progresivamente:

- 1 gasto -> 1 movimiento bancario;
- 1 compra -> múltiples cargos;
- múltiples evidencias -> 1 gasto;
- reembolsos;
- anulaciones;
- reversos;
- propina separada;
- compras en cuotas;
- cargos repetidos;
- split payments;
- diferencia entre fecha real y fecha bancaria.

---

# 15. Aprendizaje

La app debe mejorar con correcciones locales sin reentrenar modelos. El roadmap F4 ampliará el enriquecimiento con historial confirmado y ubicación contextual local; no se debe confundir esa decisión futura con la implementación actual de alias exactos.

## 15.1 Corrección local activa de comercio y categoría

Cuando el usuario edita y guarda el comercio de un gasto existente, guardar un alias local entre el valor observado en OCR/`StructuredReceipt` y el valor corregido. Cuando corrige una categoría, guardar también esa asociación local.

Ejemplo activo:

```text
TIEMDA DEMO SPA
+ corrección manual
= COMERCIO DEMOSTRACION SPA
```

El alias se normaliza y se aplica en futuras ejecuciones equivalentes del parser. No se usa fuzzy matching global.

## 15.2 Aprendizaje contextual futuro

La ampliación F4 ya acordada usará fuente, tarjeta, monto, hora, texto, correcciones manuales y ubicaciones puntuales del historial confirmado. Todo será local. Una posición aislada no fijará el comercio; los casos ambiguos conservarán una sugerencia revisable.

Ejemplo:

```text
SB 648 + confirmación manual = Starbucks
```

No asumir que un texto ambiguo tiene una única interpretación global ni resolverlo únicamente por ubicación. Preservar procedencia y confirmaciones según F4.

## 15.3 Correcciones

Si el usuario corrige una inferencia:

- conservar inferencia anterior como historial;
- marcar corrección manual;
- aprender localmente el alias observado -> valor corregido;
- reutilizarlo en futuras ejecuciones del parser;
- guardar también la asociación de categoría cuando el usuario la corrija.

Ejemplo:

```text
OCR: TIEMDA DEMO SPA
Corrección manual: COMERCIO DEMOSTRACION SPA
Siguiente OCR equivalente: COMERCIO DEMOSTRACION SPA
```

Esto es memoria local de correcciones, no entrenamiento ni descarga de un modelo.

---

# 16. Pendientes

Pantalla dedicada exclusivamente a casos que requieren intervención.

Tipos:

- comercio desconocido;
- monto ausente.

Categoría desconocida, tarjeta ausente, falta de nota, falta de productos u OCR incompleto no son por sí solos razones para dejar un gasto pendiente. Los posibles duplicados deben resolverse mediante deduplicación idempotente; solo requieren intervención si en el futuro se incorpora explícitamente un flujo de conflicto.

En la vista de movimientos, los selectores `Todos` y `Por revisar` muestran sus cantidades dentro del propio recuadro. El contador de `Por revisar` debe representar exactamente los gastos que todavía incumplen la regla comercio+monto.

La experiencia ideal es que el usuario pueda dejar esta pantalla en `0 pendientes`.

---

# 17. Pantalla principal

Debe priorizar información útil, no métricas decorativas. El título es dinámico: `Mis gastos de {mes}`.

El resumen mensual se presenta mediante un carrusel de tarjetas inspirado en Google Wallet:

- una página por tarjeta conocida;
- una página `Sin tarjeta asociada` cuando existan gastos sin tarjeta;
- tarjeta activa centrada con las vecinas visibles detrás;
- gesto horizontal con snapping central y profundidad visual;
- ancho visual vigente aproximadamente 8% mayor que la primera versión del carrusel, usando padding lateral simétrico y sin desplazar los extremos;
- proporción física de tarjeta `8,56 × 5,4` manteniendo el ancho definido por el carrusel;
- color configurable por tarjeta;
- contenido interno reducido a nombre, últimos cuatro dígitos cuando existan, icono superior derecho y monto total mensual centrado;
- sin mes, categorías, conteos, comparaciones ni movimientos dentro de la tarjeta.

Debajo del carrusel se muestran únicamente los movimientos correspondientes a la tarjeta seleccionada. Cambiar de tarjeta debe actualizar tanto el total como esa lista.

Accesos principales:

- Resumen
- Agregar, como acción global única
- Movimientos
- Configuración

Pendientes es una vista contextual accesible desde el resumen cuando requiere atención; no ocupa un destino permanente.

`Agregar` se ubica como destino más a la derecha. Al pulsarlo, abre una hoja con dos tarjetas de acción redondeadas y sin divisor:

- `Ingresar datos`;
- `Escanear una boleta`.

La transición entre `Resumen` y `Movimientos` es lateral y coherente con la posición de ambos destinos. Las navegaciones que no forman parte de esa relación no deben producir un flash de pantalla.

---

# 18. Pantalla de movimientos

Lista cronológica con:

- comercio;
- monto;
- categoría;
- fecha/hora;
- estado;
- búsqueda;
- filtros combinables.

Filtros implementados:

- búsqueda textual;
- tarjeta, incluyendo `Sin tarjeta asociada`;
- categoría, incluyendo `Sin categoría`;
- `Todos` o `Por revisar`, ambos con su contador dentro del recuadro.

Tarjeta y categoría pueden aplicarse simultáneamente. Los filtros futuros de fecha, monto, boleta, necesidad, fuente o eliminados solo deben incorporarse si aportan una acción clara sin sobrecargar la pantalla.

---

# 19. Detalle de gasto

Debe presentar la información útil del gasto con jerarquía de producción, sin exponer diagnósticos técnicos.

Ejemplo:

```text
STARBUCKS
$28.872

30 noviembre 2026
18:37

Alimentación
Capricho

Ubicación
Mallplaza Los Dominicos

Boleta
[Ver boleta]

Productos
...

Estado
✓ Identificado
```

Debe ser posible:

- editar;
- borrar;
- deshacer borrado;
- cambiar comercio;
- cambiar monto;
- cambiar tarjeta;
- cambiar categoría;
- editar nota y productos;
- abrir la boleta en pantalla completa;
- ampliar la boleta entre 100% y 500% mediante gesto de zoom/pan y controles accesibles.

No mostrar una previsualización inline de la boleta dentro del detalle. Debe existir solo el botón `Ver boleta`, que carga la imagen bajo demanda para evitar trabajo innecesario y abre el visor de pantalla completa.

---

# 20. Categorías

Categorías iniciales:

- Alimentación
- Supermercado
- Restaurantes
- Transporte
- Estacionamiento
- Salud
- Ropa
- Tecnología
- Entretenimiento
- Universidad
- Suscripciones
- Servicios
- Hogar
- Viajes
- Regalos
- Otros

Deben poder crearse, editarse y eliminarse desde Configuración.

La sección funciona como menú desplegable: comienza contraída, muestra el número de categorías y permite agregar una nueva sin expandir toda la lista. Al expandirse, cada fila conserva acciones explícitas para editar y eliminar. Esto evita obligar al usuario a recorrer todas las categorías para llegar a la sección de tarjetas.

---

# 21. Clasificación de necesidad — futuro

Cada gasto puede clasificarse como:

- Necesario
- Útil
- Capricho

Debe ser opcional.

Permitir reglas por comercio/categoría.

Ejemplo:

```text
YouTube Premium -> Útil
Farmacia -> Necesario
```

El usuario puede sobrescribir cualquier valor.

---

# 22. Suscripciones y recurrencia — futuro

Detectar automáticamente patrones recurrentes.

Ejemplo:

```text
YouTube
~día 15
~$3.450
mensual
```

Permitir marcar como:

- suscripción;
- servicio recurrente;
- gasto periódico.

Mostrar próximas recurrencias de forma informativa, sin inventar cargos futuros como gastos reales.

---

# 23. Resumen y análisis financiero

`Resumen` mantiene el vistazo operacional y sincronizado con la tarjeta activa. Debe mostrar:

- mes actual en el encabezado global;
- gasto total del mes para la tarjeta activa;
- movimientos recientes de esa misma tarjeta;
- pendientes que requieran una acción.

La sección separada `Análisis` concentra información financiera que requiere más espacio y siempre opera sobre un mes explícito. Incluye:

- presupuesto general del mes;
- total, conteo y promedio por movimiento;
- ritmo semanal;
- distribución por categoría;
- distribución por tarjeta;
- exportación del libro `.xlsx`.

No repetir en la tarjeta información que ya aparece inmediatamente debajo: movimientos, categorías, mes, conteos ni comparaciones. No mostrar métricas históricas o técnicas sin periodo, contexto o una acción clara. La UI debe conservar una jerarquía simple y dibujar los gráficos con Compose para evitar dependencias y trabajo innecesario.

---

# 24. Presupuestos

Implementado:

- presupuesto mensual general;
- progreso del mes.

No bloquear gastos por superar presupuesto.

Futuro:

- presupuesto por categoría;
- alertas configurables.

---

# 25. Búsqueda

La búsqueda implementada cubre:

- comercio;
- nota/descripción;
- categoría;
- nombre o últimos cuatro dígitos de la tarjeta.

Puede combinarse con los filtros de tarjeta, categoría y revisión descritos en la sección 18.

Extensiones futuras posibles:

- texto original de la notificación;
- producto leído desde boleta;
- producto leído desde OCR;
- monto;
- categoría;
- fecha;
- RUT;
- número de boleta;
- pedido;
- etiquetas.

Ejemplos:

```text
"samsung"
"zapatillas"
"uber eats"
"COMERCIO DEMO LOCAL 01"
"$28.872"
```

---

# 26. Privacidad y almacenamiento

Por ser una app personal:

- priorizar almacenamiento local;
- no subir datos financieros automáticamente;
- proteger información sensible;
- no registrar en logs datos completos innecesarios;
- permitir backups manuales;
- separar archivos de boletas de datos estructurados;
- mantener el dispositivo libre de modelos experimentales y fixtures de laboratorio;
- considerar cifrado local cuando sea razonable.

No construir infraestructura de producción innecesaria.

Estado operativo actual:

- la base Room y las boletas viven en almacenamiento privado de la app;
- Qwen y Gemma no forman parte del APK ni deben persistir como archivos descargados;
- el laboratorio de OCR se conserva en el PC de desarrollo, no en el teléfono;
- limpiar material de laboratorio no debe borrar movimientos, base de datos ni boletas productivas.

---

# 27. Backup y exportación

Implementado:

- exportación `.xlsx` compatible con Excel/Sheets;
- hoja `Resumen` para el mes elegido, incluyendo presupuesto;
- hoja `Movimientos` con todos los gastos activos;
- hoja `Tarjetas` con nombre, últimos cuatro dígitos, tipo y color;
- creación mediante el selector de archivos de Android, sin permiso amplio de almacenamiento.

Implementar eventualmente:

- exportación CSV;
- exportación JSON;
- backup completo;
- restauración;
- exportación separada de boletas.

Idealmente el formato de backup debe preservar IDs y relaciones.

---

# 28. Motor de inferencia

Separar el motor de inferencia del código de UI.

Debe ser testeable independientemente.

Entrada conceptual:

```text
ExpenseCandidate
+ wallet evidence
+ merchant rules
+ receipts
+ history
```

Salida:

```text
merchantCandidate
categoryCandidate
matchCandidate
confidence
reasons[]
```

Ejemplo:

```json
{
  "merchant": "Starbucks",
  "confidence": 0.96,
  "reasons": [
    "merchant text recognized in Wallet notification",
    "same amount and time as the Wallet event",
    "manual correction previously confirmed"
  ]
}
```

La UI debe poder mostrar estas razones.

## 28.1 Contrato común de boletas

El sistema debe mantener una sola abstracción de pipeline:

```text
ReceiptPipeline
├── capability()
└── process(ReceiptPipelineInput)
       └── ReceiptPipelineRun
```

La implementación activa es `MlKitReceiptPipeline`. Su salida es siempre el mismo `StructuredReceipt`, acompañado internamente por `OcrDocument`, `ReceiptValidation`, confidence, categoría, timing y errores.

Las mediciones deben usar reloj monotónico y separar:

- preprocessing;
- model load, si existe;
- OCR;
- inference, si existe;
- parsing;
- validation;
- categorization;
- total/end-to-end;
- cold start frente a warm inference.

---

# 29. IA / LLM

No existe actualmente un pipeline LLM/generativo activo para boletas.

Qwen, Gemma, PP-OCRv6 y Gemini Nano fueron retirados para mantener la app ligera y evitar una segunda arquitectura de inferencia. No deben reintroducirse como dependencias, capacidades ficticias o botones `UNSUPPORTED` sin una decisión explícita posterior.

Tampoco deben quedar archivos residuales de esos modelos en `files/local-models` ni pantallas/laboratorios de comparación en el dispositivo. Los datos de prueba visuales pueden archivarse en el PC, separados del producto.

La ruta actual utiliza:

1. reglas deterministas;
2. ML Kit OCR local bundled;
3. parser determinista;
4. validaciones contables;
5. categorización por comercio conocido/cache, reglas y keywords;
6. correcciones manuales aprendidas localmente.

Una futura alternativa de IA solo podrá evaluarse en una fase separada si demuestra una ventaja clara de precisión o cobertura sin romper el contrato `StructuredReceipt` ni aumentar innecesariamente el tamaño de la app.

---

# 30. Estados principales de Expense

Diseñar una máquina de estados clara.

Ejemplo:

```text
DETECTED
ENRICHING
PENDING_REVIEW
IDENTIFIED
DELETED
```

No necesariamente usar exactamente estos nombres, pero mantener el concepto explícito.

---

# 31. Manejo de duplicados

Antes de crear un nuevo Expense desde cualquier fuente:

1. buscar candidatos existentes;
2. calcular similitud;
3. decidir si:
   - asociar evidencia;
   - crear nuevo gasto;
   - pedir revisión.

No confiar solo en monto.

Usar combinación de:

- timestamp;
- monto;
- fuente;
- comercio;
- tarjeta;
- referencia;
- pedido.

---

# 32. Historial y auditoría

Mantener historial de cambios importantes:

- comercio anterior/nuevo;
- categoría anterior/nueva;
- asociaciones de evidencias;
- desasociaciones de evidencias;
- reglas aprendidas;
- correcciones manuales.

Debe ser posible entender cómo terminó un gasto en su estado actual.

---

# 33. Configuración

La pantalla actual de Configuración debe sentirse como un único flujo vertical corto.

Secciones implementadas:

## Permisos
- acceso a notificaciones;
- ubicación;
- cámara;
- archivos.

## Fuentes
- Google Wallet;
- OCR de boletas.

## Categorías
- crear;
- editar;
- eliminar;
- contraer/expandir la lista.

## Tarjetas
- listar como recuadros siempre abiertos;
- agregar;
- editar nombre y últimos cuatro dígitos;
- eliminar sin borrar movimientos históricos;
- elegir color mediante un selector propio inspirado en Samsung Notes, sin cuentagotas;
- ofrecer pestañas `Muestras` y `Espectro`, matriz de colores, Hex/RGB y colores recientes.

Secciones futuras, no presentes todavía en la UI productiva:

## Reglas
- revisar reglas aprendidas;
- editar;
- eliminar;
- desactivar.

Las correcciones de comercio provenientes de boletas deben poder revisarse y eliminarse como alias locales. No se deben almacenar modelos generativos ni datos de entrenamiento remoto.

## Matching
- umbrales;
- comportamiento automático.

## Datos
- exportar;
- backup;
- restaurar;
- limpiar caché;
- logs de diagnóstico.

Configuración no debe volver a incluir controles de modelos, pipelines ni laboratorios.

---

# 34. Flujo global

```text
                     ┌─────────────────────┐
                     │ GOOGLE WALLET       │
                     │ compra presencial   │
                     └──────────┬──────────┘
                                │
                                ▼
                     ┌─────────────────────┐
                     │ Expense provisional │
                     └──────────┬──────────┘
                                │
               ┌────────────────┼────────────────┐
               ▼                ▼                ▼
          Ubicación          Boleta           Manual
               │                │                │
               └────────────────┼────────────────┘
                                ▼
                      Motor de inferencia
                                │
               ┌────────────────┴────────────────┐
               │                                 │
               ▼                                 ▼
        Alta confianza                     Baja confianza
               │                                 │
               ▼                                 ▼
        Identificar solo                      Pendientes
               │                                 │
               └────────────────┬────────────────┘
                                ▼
                         Expense enriquecido
                                ▲
                                │
                  ┌─────────────┴─────────────┐
                  │                           │
                Boleta                  Edición manual
                  │                           │
                  └─────────────┬─────────────┘
                                ▼
                  Historial + Dashboard
```

---

# 35. Flujo ideal de uso diario

## Compra presencial

```text
Pagar con Wallet
-> app detecta
-> toma ubicación
-> identifica automáticamente
-> notificación de confirmación
-> usuario la ignora
```

Tiempo requerido por el usuario: 0 segundos.

## Compra presencial ambigua

```text
Pagar con Wallet
-> app detecta
-> no sabe comercio
-> notificación
-> usuario pulsa Identificar
-> selecciona comercio
-> guardar
```

Tiempo esperado: pocos segundos.

## Compra con boleta importante

```text
Pagar
-> notificación
-> Boleta
-> foto
-> OCR
-> guardar
```

## Revisión automática

```text
Notificaciones de Wallet
-> parser automático en segundo plano
-> deduplicación y enriquecimiento
-> revisar solo excepciones
```

---

# 36. Requisitos UX

La app debe sentirse rápida.

Prioridades:

- máximo 1-2 acciones para resolver un pendiente común;
- formularios cortos;
- sugerencias automáticas;
- no pedir información que ya pueda inferirse;
- evitar confirmaciones innecesarias;
- permitir corregir después;
- no interrumpir el flujo de pago.
- evitar información duplicada entre tarjetas y listas;
- mantener geometría, radios, superficies y espaciado consistentes entre hojas, formularios y tarjetas;
- preservar la posición visual durante transiciones y carruseles, incluidos el primer y último elemento;
- no producir flashes de fondo al cambiar entre destinos principales;
- cargar imágenes grandes solamente cuando el usuario decida abrirlas.

La notificación posterior a una compra debe ser útil, pero no molesta.

Para boletas, la experiencia debe limitarse a:

- adjuntar o tomar una foto;
- indicar si se está leyendo, si quedó guardada o si necesita reintento;
- revisar comercio, monto, tarjeta, categoría, nota y productos;
- abrir la fotografía asociada en un visor dedicado con zoom.

No mostrar OCR RAW, JSON, scores, tiempos, nombres de pipeline ni terminología de modelos al usuario.

---

# 37. Requisitos de confiabilidad

- Ninguna evidencia debe perderse por un fallo del parser.
- Conservar datos crudos necesarios para reprocesar.
- No sobrescribir silenciosamente correcciones manuales.
- La asociación automática de evidencias debe poder deshacerse.
- Una notificación Wallet repetida no debe crear gastos duplicados.
- Una revisión automática debe ser idempotente y segura de repetir.
- OCR fallido no debe bloquear el guardado de la foto.
- Si no hay GPS disponible, registrar gasto igualmente.
- Si no hay internet, flujo presencial debe seguir funcionando.

---

# 38. Requisitos de rendimiento

La detección de Wallet debe sentirse inmediata.

Evitar trabajo pesado dentro del listener.

Flujo sugerido:

```text
NotificationListenerService
-> persistir evento mínimo
-> enqueue background work
-> ubicación / matching / enriquecimiento
```

La UI debe leer desde Room mediante flujos reactivos.

---

# 39. Datos derivados vs datos originales

Separar siempre:

## Originales
- texto de notificación;
- OCR bruto;
- coordenadas;
- tarjeta o medio de pago detectado;
- monto detectado.

## Derivados
- comercio normalizado;
- categoría;
- contexto de lugar, si está disponible;
- score;
- explicación;
- necesidad;
- etiquetas.

En una boleta también son derivados el `StructuredReceipt`, el resultado del parser, la categoría, las validaciones y el confidence. El `OcrDocument` y el OCR bruto permanecen como evidencia original/reprocesable.

Nunca destruir los originales al normalizar.

---

# 40. Casos límite

Considerar desde el diseño:

- misma compra notificada dos veces;
- Wallet sin monto;
- Wallet sin comercio;
- compra offline;
- ubicación imprecisa;
- local dentro de mall;
- monto idéntico en compras distintas;
- cargo internacional;
- moneda extranjera;
- borrado accidental con opción de deshacer;
- transferencia no considerada gasto;
- cargo recurrente;
- suscripción;
- compra sin Wallet;
- gasto ingresado manualmente;
- boleta borrosa;
- OCR incorrecto;
- tarjeta no identificada;
- ubicación no disponible, sin bloquear el registro.

---

# 41. Fuera de alcance inicial

No priorizar:

- iOS;
- web app completa;
- cuentas multiusuario;
- compartir gastos con amigos;
- funcionalidades sociales;
- conexión con open banking;
- pagos desde la app;
- inversiones;
- impuestos;
- contabilidad empresarial;
- facturación;
- publicación en stores;
- analítica cloud;
- dashboards web externos;
- arquitectura distribuida innecesaria.

---

# 42. Orden lógico de desarrollo

No es un MVP rígido; es una secuencia recomendada para reducir complejidad.

## Etapa A — núcleo

- proyecto Android;
- arquitectura;
- Room;
- Expense/Evidence/Merchant;
- navegación;
- CRUD;
- timeline de gastos.

## Etapa B — Wallet

- NotificationListenerService;
- parser;
- creación provisional;
- deduplicación;
- notificación interactiva.

## Etapa C — ubicación

- captura;
- asociación;
- visualización como evidencia puntual;
- la implementación actual no aprende lugares; F4 incorporará contexto local del historial confirmado sin tracking continuo.

## Etapa D — boletas

- cámara del sistema mediante `TakePicture` e importación de imágenes;
- almacenamiento;
- preprocessing desacoplado;
- ML Kit Text Recognition local bundled;
- `OcrDocument` espacial;
- parser determinista chileno;
- `ReceiptValidator` y confidence observable;
- categorización rápida;
- asociación al `Expense`;
- revisión user-facing de los datos detectados.

Estado actual: implementada en la build vigente. No agregar Qwen, Gemma, PP-OCRv6 ni Gemini Nano a esta etapa.

## Etapa E — inferencia

- merchant rules;
- matching;
- scores;
- explicabilidad;
- aprendizaje de correcciones de categoría;
- alias locales de comercio provenientes de correcciones manuales.

Estado actual: reglas/categorías y alias locales están activos. No hay entrenamiento de modelos ni enriquecimiento basado en ubicación implementado. Ese uso contextual local está acordado para F4.

## Etapa futura — Gmail

- integración;
- detección;
- parsers;
- asociación.

## Etapa G — resumen y consulta

- resumen mensual por tarjeta: implementado;
- carrusel apilado, colores y sincronización de movimientos: implementado;
- búsqueda y filtros por tarjeta/categoría/revisión: implementado;
- presupuesto mensual general y progreso: implementado;
- presupuesto por categoría y alertas: futuro;
- recurrencia: futuro;
- análisis mensual con gráficos semanales, por categoría y por tarjeta: implementado;
- análisis históricos adicionales: solo si tienen periodo, contexto y acción clara.

## Etapa H — robustez

- backup;
- export `.xlsx`: implementado;
- export CSV/JSON y backup/restauración: futuro;
- logging;
- recuperación;
- pruebas.

## Etapa futura — estados de cuenta

- importación PDF;
- parser Banco de Chile;
- normalización;
- idempotencia.

## Etapa futura — conciliación

- matcher;
- UI de resolución;
- reversos;
- cuotas;
- refunds.

---

# 43. Estructura de módulos sugerida

Codex puede ajustarla.

```text
app/
core/
  database/
  model/
  ui/
  utils/
feature/
  home/
  expenses/
  pending/
  analytics/
  settings/
source/
  wallet/
  location/
  receipt/
  email/          # futuro
  bank/          # futuro
engine/
  matching/
  merchant/
  reconciliation/ # futuro
  inference/
  deduplication/
```

Mantener las fuentes externas desacopladas del dominio.

---

# 44. Criterios de éxito

La app será exitosa si:

1. detecta automáticamente una compra de Wallet;
2. crea un único Expense;
3. captura contexto de ubicación;
4. permite adjuntar boleta desde la notificación;
5. permite identificar/corregir en segundos;
6. aprende del usuario;
7. puede leer una boleta con OCR sin bloquear el guardado;
8. borra un gasto en pocos segundos y permite deshacer;
9. deja solo las excepciones reales en Pendientes;
10. permite entender por qué identificó un comercio;
11. genera un historial financiero útil;
12. funciona en el día a día sin que el usuario tenga que mantener manualmente una planilla.
13. procesa la misma boleta con un contrato `StructuredReceipt` estable;
14. conserva OCR espacial y permite auditar por qué un campo fue extraído;
15. valida contablemente los campos observables y distingue `PASS`, `FAIL` y `UNKNOWN`;
16. mantiene OCR, JSON, confidence y tiempos fuera de la experiencia de usuario;
17. mejora los errores repetidos de comercio mediante correcciones manuales locales sin inventar datos.
18. no exige revisión cuando comercio y monto ya están presentes;
19. permite administrar categorías y tarjetas sin navegar por listas innecesariamente largas;
20. separa el resumen por tarjeta y mantiene sincronizada la lista de movimientos;
21. permite filtrar movimientos por tarjeta y categoría al mismo tiempo;
22. mantiene la boleta fuera del detalle hasta que el usuario decide abrirla, con zoom funcional;
23. no conserva modelos experimentales ni material de laboratorio en el teléfono.

---

# 45. North Star

La experiencia ideal es:

```text
Pagar -> olvidar.
```

La app se encarga de registrar, enriquecer, aprender y revisar automáticamente.

El usuario solo aparece en el flujo cuando su conocimiento personal es realmente necesario.

---

# 46. Instrucciones para Codex

Antes de implementar:

1. leer este brief completo;
2. no simplificar los conceptos `Expense` y `Evidence`; dejar `Reconciliation` preparado solo para una fase futura;
3. definir una arquitectura clara antes de escribir grandes cantidades de código;
4. conservar datos crudos y derivados por separado;
5. diseñar deduplicación e idempotencia desde el inicio;
6. evitar acoplar UI con parsers o matching;
7. usar Android nativo y prácticas actuales;
8. priorizar local-first;
9. evitar infraestructura de producción innecesaria;
10. documentar decisiones importantes;
11. implementar por vertical slices verificables;
12. crear tests especialmente para parsing determinista, RUT, CLP, descuentos, fechas, IVA, reconstrucción espacial, validaciones, serialización, deduplicación, matching, OCR y borrado/deshacer;
13. no inventar APIs de Google Wallet inexistentes: el trigger de compras presenciales debe basarse en las notificaciones visibles al sistema;
14. tratar Google Wallet y OCR como fuentes de evidencia, no como dueños del modelo de datos;
15. permitir que cualquier asociación automática pueda revisarse o deshacerse;
16. mantener solo ML Kit + parser como pipeline activo de boletas hasta que exista una decisión explícita de ampliar el alcance;
17. no sustituir una validación observable por un confidence de modelo.
18. tratar este brief como contrato vigente: distinguir siempre funcionalidad implementada de ideas futuras;
19. mantener la regla de revisión basada exclusivamente en comercio+monto salvo decisión explícita posterior;
20. conservar `Análisis` mensual como destino productivo; no introducir pruebas de modelos ni información técnica en la UI productiva;
21. preservar la proporción `8,56 × 5,4`, centrado y contenido mínimo del carrusel de tarjetas;
22. no almacenar modelos GGUF ni fixtures de laboratorio dentro de los datos privados del teléfono.

Cuando exista una decisión técnica relevante no definida aquí, Codex debe elegir la alternativa más simple que preserve robustez, mantenibilidad y automatización.
