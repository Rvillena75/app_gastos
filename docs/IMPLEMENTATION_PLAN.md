# Plan de estabilización y evolución de Gastos

Fecha de consolidación: 2026-10-27. Fuente: decisiones explícitas del usuario y revisión del repositorio. Este documento permite continuar sin el historial de la conversación.

## Estado de ejecución y límites

- Solo **F0.1** está autorizada para implementación en esta entrega.
- **F0.1 ejecutada el 2026-10-27:** 27 tests JVM aprobados, lint aprobado con 15 warnings previos y APK debug generado en copia aislada Windows, todo offline. Se preservaron los archivos productivos y esquemas. No se verificó ni respaldó el teléfono. Detalle y límites en el informe enlazado.
- F1–F7 son roadmap; guardar este documento no implica que estén implementadas ni autoriza avanzar automáticamente.
- Consultar [F0_BASELINE.md](F0_BASELINE.md) para entorno, protección, verificaciones y límites de F0.1.
- No hacer commits, descartar cambios, instalar/actualizar dependencias innecesariamente ni modificar el teléfono sin autorización.
- Publicación posterior: el usuario autorizó revisar y subir el estado actual a GitHub. Esa autorización permite el commit/push de esta entrega y la exclusión de entradas privadas/generadas, conservadas en disco; no autoriza implementar F1–F7 ni modificar el teléfono.
- Preparación pública autorizada posteriormente: conservar el repositorio original como archivo privado y publicar una historia nueva sin datos personales bajo la misma URL. Se sustituyen ejemplos de compras por fixtures inventados; no se implementan fases adicionales. Ver [PUBLICATION.md](PUBLICATION.md).
- El código local sin confirmar y los archivos sin seguimiento forman parte de la base; no reiniciar desde HEAD ni reconstruir el proyecto desde cero.

## A. Diagnóstico consolidado

La app tiene registro Wallet, edición manual, boletas y OCR local, categorías personalizables, tarjetas, análisis, presupuesto general y exportación XLSX. La evolución debe conservar ese núcleo. Las brechas principales son de integridad y recuperación:

- El matcher puede fusionar compras distintas, incluso fuera de una ventana temporal razonable.
- Actualizaciones completas pueden sobrescribir cambios concurrentes o reactivar gastos borrados.
- Persistir datos y programar su procesamiento son operaciones separadas.
- Gastos, correcciones, presupuestos y archivos utilizan mecanismos de almacenamiento diferentes.
- Faltan asociaciones reversibles, procedencia por campo y restauración completa.
- Cámara, ubicación y presentación de monedas tienen problemas concretos.

Puntos centrales de intervención, relativos a `app/src/main/java/com/villegol/appgastos/`: `data/ExpenseRepository.kt`, `domain/Inference.kt`, `data/Database.kt`, `source/wallet/`, `source/location/`, `ai/receipt/` y `ui/`.

La base revisada es `main`, commit `4dbaeae`, con cambios locales anteriores que incluyen Análisis, presupuesto, XLSX y esquema Room 6. Los 20 tests y QA de agosto son evidencia histórica hasta reproducirlos. No equivalen a validación actual ni a reconocimiento OCR real.

### Decisiones acordadas

| Tema | Decisión vigente |
|---|---|
| Coincidencias | Ventana máxima de **24 horas**; la cercanía temporal no demuestra identidad. |
| Ambigüedad | Conservar compras separadas; fusionar automáticamente solo con identidad fuerte del evento. |
| Enriquecimiento | OCR, historial confirmado y ubicación, exclusivamente con datos locales. |
| Fecha de boletas | Prevalece la fecha impresa; conservar captura y evidencia original por separado. |
| Categorías | Catálogo personalizado; no imponer una taxonomía fija. |
| Backup | Archivo **sin cifrado**, por elección del usuario. |
| Restauración | Reemplazo completo con recuperación del estado anterior; no mezclar conjuntos inicialmente. |
| Recurrencias | Recordar y confirmar; no registrar pagos previstos como gastos reales. |
| Prioridad posterior | Control financiero local antes de nuevas integraciones. |
| MVP bancario | Estado de cuenta nacional de tarjeta de crédito Banco de Chile, limitado al layout del PDF aportado. |
| Fuera del alcance inmediato | Backend, sincronización multidispositivo y multiusuario. |

Estas decisiones sustituyen las prohibiciones antiguas del brief sobre Análisis y uso contextual de ubicación. La ubicación por sí sola no identifica un comercio, no requiere tracking continuo ni consultas de mapas. F4 todavía debe implementar ese uso.

## B. Arquitectura objetivo

### B.1 Conservar la estructura

Un módulo Android; Kotlin, Compose, Room, WorkManager y ML Kit; `AppContainer` como DI manual; gasto separado de evidencia; reglas locales; gráficos Compose y exportador XLSX existentes. No introducir un framework DI, servidor, bus de eventos, event sourcing ni reorganización general de pantallas.

### B.2 Escrituras, concurrencia y procedencia

Mantener el repositorio como entrada de persistencia y sustituir las actualizaciones indiscriminadas del gasto completo por operaciones explícitas.

| Contrato interno previsto | Responsabilidad |
|---|---|
| `ExpensePatch` | Distinguir campo sin modificar, asignado y borrado explícitamente. |
| `MutationResult` | Actualizado, conflicto, borrado o inexistente. |
| `FieldOrigin` | Origen, evidencia y revisión que establecieron un campo. |
| `MatchDecision` | Evento repetido, asociación demostrada, candidatos ambiguos o compra nueva. |
| `PendingJob` | Procesamiento durable pendiente. |
| `PurchaseDate` | Fecha sin hora frente a instante conocido. |
| `BackupManifest` | Formato, compatibilidad, contenido y hashes del backup. |

Cada operación financiera actualizará en una transacción el gasto, productos, asociaciones, procedencias, auditoría y trabajos pendientes afectados. Añadir revisión numérica por gasto. Formularios comprobarán la revisión cargada; workers releerán el estado vigente dentro de la transacción y aplicarán solo campos permitidos.

Invariantes:

1. Ningún worker reactiva `DELETED`.
2. Una corrección manual no se sobrescribe automáticamente, aunque haya vaciado un campo opcional.
3. Repetir una operación no duplica productos, evidencias ni auditoría.
4. Revisión pendiente se calcula siempre con comercio+monto.
5. Datos heredados sin origen se conservan como `LEGACY`, no como confirmación humana.

### B.3 Persistencia financiera pequeña en Room

Trasladar presupuestos y alias de comercio/categoría desde DataStore/SharedPreferences a Room, manteniendo las interfaces de observación actuales. Esto permite guardar edición y aprendizaje juntos y evita coordinar tres almacenes financieros en el backup.

Importar las preferencias antes de habilitar escrituras normales, de forma idempotente y con marcador de finalización. No borrar los originales en la primera migración ni reimportarlos después de una restauración. No migrar configuración no financiera sin necesidad.

### B.4 Trabajo durable

Conservar eventos originales antes del parsing; generar trabajos pendientes en Room y ejecutarlos con WorkManager único por generación del conjunto, entidad y operación. Usar `KEEP` para solicitudes equivalentes y nueva generación para reprocesamiento explícito. La unicidad de WorkManager no sustituye la idempotencia transaccional.

Reconciliar pendientes al iniciar la app y conectar el listener. No prometer ejecución durante force-stop. Referencia: [trabajo único de Android](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work).

### B.5 Archivos y restauración

Centralizar originales inmutables, escritura temporal, renombrado, hashes y resolución de rutas. Backups deben contener referencias portables; mantener lectura de rutas privadas antiguas. Limpiar únicamente temporales no referenciados, nunca evidencia por antigüedad.

Restauración con exclusión de escrituras, registro durable de etapas y nueva generación para invalidar workers anteriores.

### B.6 Alternativas resueltas

| Problema | Elección y motivo |
|---|---|
| Duplicados | Identidad fuerte y sugerencias; evita ocultar compras reales. |
| Contexto geográfico | Historial local, sin servicio de mapas. |
| Backup | Exportación lógica consistente, no copiar SQLite abierto dependiendo de WAL. |
| Almacenes financieros | Consolidación limitada en Room, reutilizando interfaces. |
| Cámara | Mantener cámara del sistema mediante `TakePicture`; no implementar CameraX. |
| PDF | `PdfRenderer` y ML Kit por página, sin biblioteca general adicional inicialmente. |
| Historial | Auditoría transaccional sobre el modelo actual, no event sourcing. |

La extracción nativa de texto `PdfRenderer.Page.getTextContents()` se añadió en API 35 y no cubre uniformemente Android 14. La primera ruta bancaria será renderizado+OCR local. `pdftotext` sirve de herramienta de desarrollo, no como dependencia del teléfono. [Referencia Android](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page#getTextContents()).

## C. Roadmap general

| Fase | Resultado utilizable | Prioridad | Dependencias | Complejidad |
|---|---|---|---|---|
| F0 | Base reproducible y estado protegido | Indispensable | Ninguna | Media |
| F1 | Escrituras, estados, trabajos y deduplicación consistentes | Indispensable | F0 | Alta |
| F2 | Cámara, monedas, ubicación y formularios correctos | Indispensable | F1 | Media |
| F3 | Backup y restauración recuperable | Recuperación indispensable | F1–F2 | Alta |
| F4 | Boletas, asociaciones reversibles y enriquecimiento local | Cierre de próxima versión estable | F1–F3 | Alta |
| F5 | Presupuestos por categoría, alertas, recurrencias y exportaciones | Complementaria; siguiente prioridad | F4 | Media–alta |
| F6 | PDF Banco de Chile y conciliación | Complementaria | F4–F5 | Alta |
| F7 | Especificar cuotas/reembolsos ampliados y Gmail | Backlog posterior | F6 para banca | Alta |

Hitos: núcleo corregido después de F2; recuperación completa después de F3; siguiente versión estable después de F4. F5/F6 se entregan separadas y no retrasan la estabilización.

## D. Plan detallado por fase

### F0 — Base reproducible y protección

#### F0.1 — Reproducir la base y preparar datos de prueba

- **Objetivo:** distinguir el funcionamiento actual de resultados históricos y proteger el trabajo existente.
- **Estado actual:** 20 tests JVM y QA guardado; herramientas no disponibles inicialmente en PATH WSL; cambios previos confirmados y sin seguimiento deben conservarse.
- **Implementación:** localizar Java/SDK; documentar Java/JVM 17, SDK 35 y Build Tools 36.0.0; reproducir tests, lint y build sin actualizar dependencias; registrar la JVM efectiva si es otra compatible; inventariar y proteger cambios; corregir contradicciones documentales; preparar fixtures sintéticos de gastos y evidencias.
- **Componentes:** documentación, configuración de desarrollo reproducible, recursos y utilidades exclusivamente de test; no cambiar comportamiento productivo.
- **Dependencias:** ninguna.
- **Aceptación:** build reproducible o limitación específica registrada; resultado de cada check identificable; datos sintéticos separados de información personal; trabajo previo preservado. Antes de instalar futuras migraciones sobre el teléfono habitual, obtener copia técnica consistente y demostrar recuperación. Si no es posible, usar primero instalación aislada y no actualizar el conjunto principal.
- **Validación:** `testDebugUnitTest`, `lintDebug`, `assembleDebug`; checks del inventario y fixtures. Solo declarar resultados realmente ejecutados.
- **Riesgos:** confundir evidencia antigua con actual, omitir fuentes nuevas o llamar backup del teléfono a una copia del repositorio.
- **Complejidad:** media.

### F1 — Integridad y procesamiento

#### F1.1 — Operaciones transaccionales, revisiones y procedencia

- **Objetivo:** impedir sobrescrituras y estados inconsistentes.
- **Estado actual:** edición, productos, aprendizaje y auditoría pueden guardarse por separado.
- **Implementación:** añadir revisión, procedencia por campo y auditoría JSON versionada; operaciones explícitas de edición, borrado, restauración y aplicación de evidencia; pasar cambios explícitos desde formularios. Consolidar presupuestos y alias en Room con importación idempotente. Mantener auditoría antigua sin intentar interpretar `toString()` como JSON.
- **Componentes:** `ExpenseRepository`, entidades/DAO/migraciones, repositorios pequeños y formularios.
- **Dependencias:** F0.1.
- **Aceptación:** edición, productos, aprendizaje y auditoría atómicos; conflicto sin actualización parcial; restauración recalcula revisión pendiente; valores heredados intactos.
- **Validación:** Room con rollback, ediciones concurrentes, borrado/restauración e importación de preferencias interrumpida/repetida.
- **Riesgos:** pérdida de asociaciones heredadas o modificación de datos existentes.
- **Complejidad:** alta.

#### F1.2 — Ingestión durable y workers seguros

- **Objetivo:** eliminar la ventana entre guardar y encolar y evitar efectos repetidos.
- **Estado actual:** workers sin protección suficiente de borrado y cancelación.
- **Implementación:** persistir evento original y trabajo antes del parsing; tabla pequeña de trabajos; aplicar resultados y finalización en una transacción. Hasta tres intentos automáticos para errores transitorios, después reintento manual; errores definitivos no se repiten automáticamente. Propagar `CancellationException` y cancelar tareas Google. Recuperar boletas `PROCESSING` y gastos con enriquecimiento pendiente. Solicitudes antiguas sin generación no escriben: el reconciliador crea equivalentes seguros.
- **Componentes:** listener, workers, repositorio y DAO de trabajos.
- **Dependencias:** F1.1.
- **Aceptación:** interrupción tras guardar no pierde procesamiento; reejecución sin duplicados; OCR no reactiva borrados.
- **Validación:** fallos antes/después de commits y encolado, simultaneidad, cancelación y relanzamiento.
- **Riesgos:** trabajos bloqueados o reintentos indefinidos.
- **Complejidad:** alta.

#### F1.3 — Deduplicación conservadora de 24 horas

- **Objetivo:** evitar fusiones falsas.
- **Estado actual:** score sin límite temporal obligatorio y selección del primer candidato.
- **Implementación:** funciones puras para decisión; candidatos indexados por fecha/moneda/monto. Evento exacto repetido es idempotente; monto/comercio/tarjeta semejantes solo sugieren. `notificationKey` solo no demuestra identidad. Fuera de 24 h no hay sugerencia por similitud; empate conserva separados. Sugerencias independientes de `PENDING_REVIEW`; rechazo persistente para el mismo par. Clasificar avisos de compra, administrativos inequívocos y no clasificados; conservar estos últimos sin crear gastos silenciosamente.
- **Componentes:** `DuplicateMatcher`, parser/listener, consultas del repositorio y sugerencias.
- **Dependencias:** F1.1–F1.2.
- **Aceptación:** dos pagos iguales reales separados; evento exacto repetido crea un solo gasto; límite de 24 h exacto.
- **Validación:** límites temporales, distintas monedas/tarjetas, actualización de contenido, claves reutilizadas y múltiples candidatos.
- **Riesgos:** más sugerencias; preferible a ocultar compras.
- **Complejidad:** media–alta.

### F2 — Android y coherencia de uso

#### F2.1 — Cámara, archivos y borradores

- **Objetivo:** evitar fallos por permisos y pérdida de capturas/ediciones.
- **Estado actual:** `TakePicture` con permiso CAMERA declarado, sin solicitud previa y estado en `remember`.
- **Implementación:** conservar cámara externa y retirar CAMERA al no utilizar CameraX directamente; manejar cámara ausente y errores de lanzamiento. Centralizar archivos. `SavedStateHandle` en captura/edición para URI, ID, revisión y campos. OCR tardío no reinicializa borradores. Galería usa temporal, valida imagen y publica antes del procesamiento. Cancelación no crea gasto.
- **Componentes:** captura/editor/revisión, manifiesto y servicio de archivos.
- **Dependencias:** F1.
- **Aceptación:** instalación nueva, cancelación y recreación conservan comportamiento correcto.
- **Validación:** permisos previos ausentes, cámara inexistente, cancelación, imagen inválida y recreación.
- **Riesgos:** archivos huérfanos o callbacks duplicados.
- **Complejidad:** media.

#### F2.2 — Monedas y totales

- **Objetivo:** corregir Resumen y edición no CLP.
- **Estado actual:** Resumen suma `amountMinor` de distintas monedas como CLP.
- **Implementación:** reutilizar `SpendingAnalyzer`; tipo monetario entero+moneda; CLP/USD/EUR explícitos. Resumen/Análisis seleccionan moneda, CLP por defecto; sin conversión cambiaria. Presupuesto general CLP en esta versión. Editor respeta decimales y par importe/moneda atómico. XLSX conserva centavos. Sin Float/Double financieros, salvo proporciones visuales.
- **Componentes:** dinero, parser, editor, resumen, analizador y XLSX.
- **Dependencias:** F1.1.
- **Aceptación:** mismos datos/moneda producen totales coincidentes; USD/EUR nunca se suman como pesos.
- **Validación:** monedas, desconocidos, redondeo/separadores y overflow.
- **Riesgos:** reinterpretar centavos o cambiar el alcance global de Movimientos XLSX.
- **Complejidad:** media.

#### F2.3 — Ubicación puntual verificable

- **Objetivo:** evitar ubicaciones antiguas o tardías atribuidas a compras.
- **Estado actual:** fallback a `lastLocation` sin límite y permisos de fondo incompletos.
- **Implementación:** distinguir acceso foreground/background. Solicitud máxima 10 s; antigüedad/diferencia con evento hasta 2 min. Trabajo tardío registra ausencia. Guardar precisión; solo posiciones de hasta 100 m participan en inferencia automática. Falta de permiso/GPS/resultado no bloquea gasto; sin tracking ni servicio permanente. Cancelación cooperativa y límites explícitos de antigüedad.
- **Componentes:** configuración de permisos, `LocationCaptureWorker` y evidencia.
- **Dependencias:** F1.2.
- **Aceptación:** no presentar posiciones fuera de ventana como lugar de compra.
- **Validación:** permisos completos/aproximados/denegados, pantalla apagada, demora, caché antigua y cancelación.
- **Riesgos:** Android puede no entregar posición; expresar ausencia sin inventarla.
- **Complejidad:** media.

### F3 — Backup y restauración

#### F3.1 — Backup lógico portable

- **Objetivo:** copia financiera completa.
- **Estado actual:** XLSX y fotos del laboratorio no son backup de la app.
- **Implementación:** `BackupService`, DTO versionados y SAF en Configuración. ZIP sin cifrado con manifiesto/compatibilidad, todas las entidades financieras activas/borradas, evidencias, enlaces, productos, tarjetas, categorías, presupuestos, correcciones, auditoría, trabajos pendientes y originales con SHA-256. Snapshot lógico bajo exclusión breve; copiar originales inmutables después. Excluir cachés, DB interna WorkManager, permisos y credenciales.
- **Componentes:** servicio de backup, DAO de snapshot, archivos y Configuración.
- **Dependencias:** F1–F2.
- **Aceptación:** punto consistente; detecta faltantes; conserva IDs/relaciones.
- **Validación:** round-trip, hashes, borrados y exportación interrumpida.
- **Riesgos:** espacio insuficiente y contenido legible según elección sin cifrado.
- **Complejidad:** media–alta.

#### F3.2 — Reemplazo con recuperación

- **Objetivo:** restaurar sin mezclar DB y archivos incompatibles.
- **Estado actual:** no hay restauración.
- **Implementación:** validar staging completo: versiones, hashes, relaciones, duplicados, rutas y espacio; rechazar futuro/incompleto. Recuperación interna del estado anterior; bloquear escrituras; cambiar generación; journal durable. Restaurar DB con operaciones parametrizadas transaccionales y publicar archivos por renombrado. Interrupción previa a finalización recupera antes de UI/workers. Bandeja durable separada para notificaciones recibidas durante restore. Reconstruir trabajo desde pendientes, sin restaurar WorkManager interno.
- **Componentes:** backup/restore, coordinación de almacenamiento, listener y arranque.
- **Dependencias:** F3.1.
- **Aceptación:** éxito o interrupción deja conjunto íntegro; workers viejos no escriben.
- **Validación:** fallos por etapa, ZIP truncado/hash/rutas/versiones/espacio y evento concurrente.
- **Riesgos:** consumo temporal y coordinación filesystem/Room.
- **Complejidad:** alta.

### F4 — Boletas y enriquecimiento

#### F4.1 — Campos OCR y fecha impresa

- **Objetivo:** aprovechar OCR sin ignorar contradicciones o perder fecha real.
- **Estado actual:** fecha extraída no se aplica; validación no condiciona campos.
- **Implementación:** fecha local y precisión DATE/DATE_TIME; nunca presentar hora inventada. Fecha impresa válida prevalece sobre captura salvo corrección manual. Conservar Wallet original. Aplicar campos independientes; UNKNOWN no equivale a fallo. Fallo de montos deja propuesta salvo corroboración independiente de la misma compra; RUT inválido no descarta otros campos. No estimar precios desde compras parecidas. Persistir validaciones/orígenes. Conservar fechas legacy sin origen fiable y ofrecer revisión de discrepancias, sin migración silenciosa masiva.
- **Componentes:** aplicación de boletas, fecha, validación, workers y UI.
- **Dependencias:** F1, F3.
- **Aceptación:** boleta antigua corresponde al periodo impreso; campos independientes se completan; manual intacto.
- **Validación:** sin hora, fecha inválida/futura, cambio de mes/DST, totales contradictorios y reintentos.
- **Riesgos:** cambio de periodo o confusión entre dato derivado y observado.
- **Complejidad:** alta.

#### F4.2 — Asociar y revertir evidencias

- **Objetivo:** escaneo independiente sin duplicación y asociaciones corregibles.
- **Estado actual:** escanear desde Agregar siempre crea gasto nuevo.
- **Implementación:** permitir boleta sin `expenseId` mientras se procesa. Destino explícito conserva asociación y muestra contradicciones; sin candidatos crea gasto/enlace atómicos; con candidatos ambiguos ofrece asociar o crear otro. Imagen idéntica o identidad documental completa y válida no crea otra compra automáticamente. Auditar enlaces/fusiones. Reversión restaura enlaces y solo campos automáticos atribuibles; preserva cambios manuales posteriores y muestra conflictos. Fusión manual borra lógicamente origen con motivo y datos recuperables.
- **Componentes:** Receipt/DAO/migración, asociación, auditoría, captura/detalle.
- **Dependencias:** F1.3, F4.1.
- **Aceptación:** coincidencias revisables y reversión sin pérdida de productos/ediciones.
- **Validación:** misma foto/documento, múltiples candidatos, rechazo persistente, merge/undo y concurrencia.
- **Riesgos:** referencias o productos movidos incorrectamente.
- **Complejidad:** alta.

#### F4.3 — Historial y contexto local

- **Objetivo:** completar con evidencia local trazable.
- **Estado actual:** alias y reglas separados entre Wallet y OCR.
- **Implementación:** servicio determinista con prioridad manual > evidencia directa/asociación confirmada > alias exacto > historial confirmado > sugerencias. Para comercio automático desde historial exigir al menos dos antecedentes confirmados concordantes del descriptor y sin rivales. Ubicación refuerza; sola solo sugiere. Resolver categoría en catálogo vigente sin crear taxonomía. No inferir monto/productos/fecha de compras anteriores. Permitir corregir/rechazar/eliminar aprendizaje; sin mapas ni nube.
- **Componentes:** inferencia, correcciones, consultas Room y explicación UI.
- **Dependencias:** F2.3, F4.1–F4.2.
- **Aceptación:** descriptor corregido reutilizable en Wallet y OCR; ubicación ambigua no fija comercio.
- **Validación:** comercios cercanos, centros comerciales, historial contradictorio, categorías renombradas/eliminadas y precisión insuficiente.
- **Riesgos:** propagar error o tratar inferencias anteriores como confirmaciones humanas.
- **Complejidad:** alta.

### F5 — Control financiero local

#### F5.1 — Categorías, presupuestos y alertas

- **Objetivo:** ampliar presupuesto sin bloquear gastos.
- **Estado actual:** presupuesto mensual general.
- **Implementación:** IDs para presupuestos por categoría; conservar nombres históricos y archivar categorías utilizadas. Presupuestos CLP mensuales; umbrales configurables 80/100 % iniciales; notificaciones optativas y emisión persistida por versión/mes. No reproducir avisos antiguos tras restore. General y categorías independientes; sin suma ni copia mensual automática.
- **Componentes:** categorías, presupuestos, análisis y notificaciones.
- **Dependencias:** F4.
- **Aceptación:** rename mantiene presupuesto; aviso no repetido; compras nunca bloqueadas.
- **Validación:** cruces, corrección/borrado, permiso denegado, archivo de categoría y restore.
- **Riesgos:** alertas repetidas o pérdida de vinculación histórica.
- **Complejidad:** media.

#### F5.2 — Pagos previstos y suscripciones

- **Objetivo:** recordar sin inventar gastos.
- **Estado actual:** no hay recurrencias.
- **Implementación:** plantillas manuales mensuales/anuales y ocurrencias fuera de Expense; comercio/moneda, monto/tarjeta opcionales y fecha. Confirmar con gasto existente, registrar o saltar. Wallet compatible marca una única ocurrencia sin crear gasto adicional; ambiguo requiere selección. Días inexistentes al último del mes. Recordatorios aproximados WorkManager, sin alarmas exactas; detección automática fuera de primera entrega.
- **Componentes:** modelos/DAO, recordatorios, UI y asociaciones.
- **Dependencias:** F4.
- **Aceptación:** previsto no afecta totales; confirmación idempotente; sin duplicado por plantilla.
- **Validación:** meses cortos/bisiestos, atraso, concurrencia y candidatos múltiples.
- **Riesgos:** confundir previsión y pago real.
- **Complejidad:** media–alta.

#### F5.3 — CSV, JSON y fotografías

- **Objetivo:** ampliar portabilidad.
- **Estado actual:** XLSX.
- **Implementación:** snapshots+SAF; CSV UTF-8 BOM con `;`, fechas ISO e importe entero/moneda; JSON versionado con enteros; ZIP de boletas con índice y hashes. CSV/JSON abarcan activos globales como Movimientos XLSX. Backup sigue siendo la única restauración completa.
- **Componentes:** exportadores, archivos y UI.
- **Dependencias:** F3, F4.
- **Aceptación:** contenido equivalente por movimiento/moneda y sin modificar DB.
- **Validación:** comillas/saltos/Unicode, centavos, faltantes y cancelación.
- **Riesgos:** confundir exportación con backup.
- **Complejidad:** baja–media.

### F6 — Banco de Chile, layout aportado

La referencia es un PDF privado aportado localmente por el usuario, excluido del repositorio público. Es un estado nacional de tarjeta de crédito de cuatro páginas, con tres columnas monetarias, cuotas, pagos y cargos. No publicar su contenido ni incluirlo en assets. La identificación del layout no demuestra precisión del futuro OCR; el importador todavía no está implementado.

#### F6.1 — Extracción y validación PDF

- **Objetivo:** borrador bancario verificable.
- **Estado actual:** no hay importador ni fixture bancario sintético.
- **Implementación:** parser específico sobre documentos espaciales por página, reutilizando ML Kit y utilidades, no parser de boletas. Render secuencial; página/coordenadas/filas originales; cabeceras/continuaciones/secciones; referencia, fecha, descriptor, monto operación, total a pagar, número/total cuotas y valor facturado. Validar subtotales y fórmula total impresa. Layout desconocido/protegido/inconsistente no se importa silenciosamente. Fixtures sintéticos coherentes; muestra real solo validación privada.
- **Componentes:** fuente bancaria, render, parser, contrato de borrador y pruebas.
- **Dependencias:** F4–F5.
- **Aceptación:** todas las filas extraídas o señaladas para revisar; totales cuadran o desacuerdo visible.
- **Validación:** multipágina, filas partidas, signos, referencias repetidas, cuotas y dígitos OCR; comparación con transcripción revisada, no solo otro parser.
- **Riesgos:** columnas mal asociadas o filtración de datos en fixtures.
- **Complejidad:** alta.

#### F6.2 — Persistencia y vista previa bancaria

- **Objetivo:** conservar cartola sin convertir cada renglón en gasto.
- **Estado actual:** sin entidades bancarias.
- **Implementación:** BankStatement/BankEntry separados de Expense, original+hash. Tipos compra/cuota/pago/devolución/comisión/interés/impuesto/desconocido; dudosos requieren revisión. Vista previa de periodo/tarjeta/secciones/errores. Guardar registro bancario; crear gastos solo con selección confirmada. Mismo PDF idempotente; mismo periodo con otro contenido es revisión en conflicto, sin reemplazo automático.
- **Componentes:** entidades/DAO/migración, importación y UI.
- **Dependencias:** F6.1.
- **Aceptación:** pagos/saldos no aumentan gasto; signos y montos originales conservados.
- **Validación:** reimportación, regenerado, abonos, desconocidos y cancelación.
- **Riesgos:** duplicar compras o tratar abonos como compras negativas.
- **Complejidad:** alta.

#### F6.3 — Conciliación revisable

- **Objetivo:** relacionar movimientos y descubrir faltantes.
- **Estado actual:** no hay conciliación bancaria.
- **Implementación:** enlaces auditables y reversibles; candidatos por moneda/importe/fecha original/tarjeta/descriptor. Ventana 24 h sobre compra, no emisión de cartola. Similitud solo sugiere. Varias cuotas enlazan una compra; gasto completo una vez y cargos mensuales separados. No crear gasto por cuota ni por reimportar operación.
- **Componentes:** matcher bancario, enlaces, auditoría y UI.
- **Dependencias:** F4.2, F6.2.
- **Aceptación:** Wallet+boleta+cartola cuentan una compra; asociación reversible.
- **Validación:** repetidas, cuotas entre periodos, antiguas, descriptor truncado y faltantes confirmados.
- **Riesgos:** coincidencia falsa o suma de compra y cuotas.
- **Complejidad:** alta.

### F7 — Especificación previa de backlog

Las tareas siguientes no autorizan implementar todavía esos motores/integraciones: producen una especificación posterior y fixtures, con decisiones de producto explícitas.

#### F7.1 — Cuotas y reembolsos completos

- **Objetivo:** ampliar deuda y devoluciones.
- **Estado actual:** F6 conservará registros, no un motor completo.
- **Implementación:** especificar calendario/cargos y devolución parcial; recomendar compra original inmutable, cuotas asociadas, devoluciones vinculadas y bruto/neto separados.
- **Componentes:** contratos financieros, agregaciones y fixtures posteriores.
- **Dependencias:** F6.
- **Aceptación:** reglas acordadas para devolución parcial/total, fuera de periodo y otra moneda, sin doble conteo.
- **Validación:** ejemplos contables sintéticos antes de programar.
- **Riesgos:** cambiar el significado de presupuesto/totales.
- **Complejidad:** alta para implementación posterior.

#### F7.2 — Viabilidad Gmail

- **Objetivo:** compras online.
- **Estado actual:** sin OAuth ni parsers de correo.
- **Implementación:** prueba separada de autorización Android con cuenta personal y Gmail solo lectura; importación iniciada por usuario; parsing local; identidad cuenta+mensaje. Sin envío de correos, backend ni modelos externos. Verificar cliente OAuth/firma/revocación y formatos limitados antes de importación habitual. No asumir resuelta viabilidad de `gmail.readonly`, alcance restringido.
- **Componentes:** adaptador de origen, autorización y parsers futuros.
- **Dependencias:** F4 y alcance nuevo.
- **Aceptación:** autorización/revocación demostradas y formatos soportados definidos.
- **Validación:** revocación, repetidos, reenvíos y recibo/confirmación del mismo pedido.
- **Riesgos:** OAuth y duplicación de un pedido por múltiples emails.
- **Complejidad:** alta para implementación posterior.

Referencia: [scopes Gmail](https://developers.google.com/workspace/gmail/api/auth/scopes).

## E. Estrategia de validación

| Área | Casos obligatorios | Nivel |
|---|---|---|
| Duplicados | Evento exacto, clave reutilizada, pagos iguales, 24 h y exceso, monedas y empate | JVM + Room |
| Concurrencia | Editar durante OCR, borrar durante worker, doble procesador | Integración |
| Estados | Borrar/restaurar completo/incompleto; confirmar sin datos; reintentar | JVM + Room |
| Migraciones | Versiones 1–6 a nuevos esquemas, preferencias y legacy | Room Android |
| Dinero | CLP/USD/EUR, centavos, separadores, UI/export y overflow | JVM + dispositivo |
| Boletas | Descuentos/IVA/columnas/foto girada/incompletos/fecha/conflictos | JVM + ML Kit real |
| Ubicación | Permisos, precisión, antigüedad, demora y fondo | Dispositivo físico |
| Cámara | Nueva instalación, cancelación, recreación y ausencia | Instrumentación + físico |
| Backup | IDs/relaciones/borrados/auditoría/originales/pendientes | Round-trip |
| Restore | Interrupciones por etapa, worker antiguo, evento nuevo y corrupción | Integración con fallos |
| Recurrencias | Previsto sin gasto, doble confirmación y Wallet | JVM + Room |
| Banco | Cabeceras, cuotas/cargos/abonos, subtotales y repetición | Fixture + PDF privado |

Para cerrar una fase: tests específicos y regresión aprobados, migraciones comprobadas antes de teléfono habitual, lint sin errores nuevos relacionados, validación física de rutas Android afectadas y preservación de identidades/totales salvo cambio explícito auditado. Actualizar backup/restore con cada cambio de esquema. Registrar código/dispositivo/evidencia sin datos completos en logs.

Añadir solo dependencias de prueba justificadas, como Room Testing y WorkManager Testing alineadas con producción. JVM no demuestra ejecución de ML Kit ni restricciones Android. Rendimiento histórico no demuestra rendimiento nuevo.

## F. Riesgos y decisiones pendientes

1. Legacy sin procedencia: preservar, no atribuir confirmación humana.
2. Migración/restauración: recuperación demostrada antes de datos habituales.
3. Ubicación no garantizada: ausencia no bloquea gastos.
4. Un PDF: soportar layout aportado y rechazar otros claramente.
5. OCR imperfecto: originales, subtotales y revisión evitan importación silenciosamente incompleta.
6. Backup legible: elección sin cifrado; informar sin añadir contraseñas.
7. Firma: conservar firma instalada para futuras actualizaciones; no generar otra clave suponiendo compatibilidad.

Pendiente solo para F7: momento de impacto del reembolso en presupuesto/neto; información adicional de cuotas; remitentes Gmail; cliente y viabilidad OAuth. No bloquea F0–F6.

Revisión crítica del plan: backup adelantado al enriquecimiento amplio; identidad separada de similitud; previsiones fuera de gastos; invalidación de workers tras restore; cuotas bancarias conservadas sin motor completo; consolidación limitada; sin mapas, biblioteca PDF general ni reescritura arquitectónica.

## G. Alcance recomendado

- **Próxima estable F0–F4:** base verificable; deduplicación; escrituras/borrado/workers; cámara/borradores; monedas; ubicación limitada; backup/restore; fecha impresa; asociación/reversión; enriquecimiento local; documentación y pruebas.
- **Posterior F5:** presupuestos por categoría, alertas, recurrencias confirmables y exportaciones.
- **MVP bancario F6:** PDF Banco de Chile aportado, importación revisable y conciliación. No cubre cuenta corriente, otros layouts/bancos ni cambio de monedas.
- **Backlog F7:** Gmail y contabilidad ampliada de cuotas/reembolsos. Sincronización y multiusuario fuera.

## H. Secuencia de ejecución

1. F0.1: reproducir/proteger.
2. F1.1: transacciones/revisiones/procedencia.
3. F1.2: ingestión/trabajos.
4. F1.3: deduplicación.
5. F2.1: cámara/archivos/borradores.
6. F2.2: dinero/agregaciones.
7. F2.3: ubicación/permisos.
8. F3.1–F3.2: backup y recuperación demostrada.
9. F4.1: campos OCR/fecha.
10. F4.2: asociaciones/reversión.
11. F4.3: historial/contexto.
12. Cerrar estable con regresión/migración/recuperación/dispositivo.
13. F5: control local.
14. F6: Banco de Chile.
15. F7: especificar backlog.

Cada fase termina utilizable y con evidencia de aceptación. Solo ejecutar la tarea autorizada; no avanzar automáticamente ni modificar el teléfono sin autorización y recuperación previamente comprobada.
