# Traza

**Traza: aplicación Android de seguimiento de gastos con captura desde Google Wallet y OCR de boletas en el dispositivo.**

Proyecto personal construido con **Kotlin, Jetpack Compose, Room, WorkManager y ML Kit**. Su objetivo es reducir el registro manual de compras y conservar la evidencia que permite revisar cada movimiento. La información financiera se almacena localmente; no requiere un backend propio.

## Funcionalidades implementadas

- Captura de compras a partir de notificaciones visibles de Google Wallet.
- Registro y edición manual de comercio, monto, categoría, tarjeta, nota y productos.
- Categorías y tarjetas personalizables, con color y tipo de tarjeta.
- Captura/importación de boletas y OCR latino local bundled con ML Kit.
- Parser chileno determinista: RUT, montos CLP, fechas, productos, descuentos y reconciliaciones contables.
- Aprendizaje local de alias exactos a partir de correcciones manuales.
- Resumen mensual por tarjeta, búsqueda y filtros.
- Análisis mensual por categoría, tarjeta y bloques semanales; presupuesto general informativo.
- Exportación local `.xlsx` con resumen del mes, todos los movimientos activos y tarjetas.
- Borrado lógico, deshacer y auditoría básica.

## Arquitectura

```text
Google Wallet -> NotificationListener -> ExpenseRepository -> Room -> Compose UI
                                            ^
Foto -> almacenamiento privado -> WorkManager -> ML Kit -> parser + validaciones

Presupuesto -> DataStore                 Datos financieros -> exportador XLSX
```

El modelo separa `Expense` de `Evidence`: notificaciones, fotografías y correcciones respaldan un mismo gasto. Room tiene migraciones explícitas y expone datos mediante Flow. `AppContainer` proporciona las dependencias sin un framework de DI.

El OCR conserva bloques, líneas, elementos y bounding boxes en `OcrDocument`. El parser combina esa estructura espacial con reglas deterministas y produce `StructuredReceipt`; las validaciones distinguen `PASS`, `FAIL` y `UNKNOWN`. No hay un LLM ni llamadas a modelos generativos en el flujo activo.

## Estado y limitaciones

Es un **MVP en desarrollo**. Hay código y evidencia histórica de uso en dispositivo, pero todavía existen problemas de deduplicación, concurrencia, permisos y totales que se describen en el roadmap. Las validaciones OCR actuales no son una garantía de aceptación correcta de todos los campos.

Backup/restauración completos, enriquecimiento con historial/ubicación, importación bancaria, Gmail, recurrencias, cuotas y reembolsos **no están implementados**. El PDF Banco de Chile mencionado en el plan es una referencia privada para una fase futura.

## Ejecutar y verificar

Requisitos: Android SDK 35, Build Tools 36.0.0 y una JVM compatible con Gradle 8.11.1/AGP 8.9.2. El objetivo de bytecode Java/Kotlin es 17. El dispositivo objetivo es **Android 14+ ARM64**.

Configurar el SDK mediante `ANDROID_HOME` o `local.properties`, y ejecutar:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

**Verificación local más reciente:** 28 tests aprobados, lint sin errores (15 warnings previos) y build debug aprobado. [Evidencia y límites](docs/PUBLICATION.md).

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Para WSL con Android Studio/SDK Windows ya instalados hay un [verificador offline aislado](scripts/verify_f0_windows.py), documentado en [F0_BASELINE.md](docs/F0_BASELINE.md).

Los tests JVM usan datos sintéticos para parsing, RUT, dinero, reconstrucción espacial, política de revisión, análisis y estructura XLSX. No ejecutan ML Kit sobre fotografías ni prueban permisos, GPS o migraciones Room en un teléfono. La evidencia de la última ejecución está en [PUBLICATION.md](docs/PUBLICATION.md).

## Privacidad de los ejemplos

Las fotografías personales, cartolas PDF, respaldos y credenciales no se distribuyen con el proyecto. Los ejemplos del parser, Wallet y exportación se sustituyeron por datos inventados antes de publicar. Los fixtures de prueba viven en `src/test/resources` y no se empaquetan en el APK.

El repositorio público comienza con una historia limpia. La historia original y los archivos privados se conservan fuera de esta publicación. No se deben incorporar datos reales en issues, capturas o nuevos fixtures.

## Documentación y desarrollo

- [Roadmap y decisiones](docs/IMPLEMENTATION_PLAN.md): fases, dependencias, aceptación y alcance pendiente.
- [Base reproducible F0](docs/F0_BASELINE.md): entorno y verificaciones iniciales.
- [Preparación pública](docs/PUBLICATION.md): privacidad y nueva validación.
- [Fixtures sintéticos](app/src/test/resources/baseline/README.md): corpus reutilizable.
- [Brief del producto](brief%282%29.md) y [resumen histórico de QA](design-qa.md).

La siguiente fase técnica es F1.1; la publicación no implica que las fases pendientes estén resueltas.
