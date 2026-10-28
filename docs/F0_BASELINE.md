# F0.1 — Base reproducible y protección del estado actual

Fecha: 2026-10-27. Alcance autorizado: **solo F0.1** de [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). No se implementaron F1–F7 ni cambios de comportamiento de producción.

> Registro de la ejecución F0 anterior a la preparación pública. Las rutas de usuario se anonimizaron y las referencias de datos personales se retiraron posteriormente. Los resultados y hashes de esta ejecución describen aquella base, no los fixtures públicos modificados después. Para la nueva verificación consultar [PUBLICATION.md](PUBLICATION.md).

## Protección e inventario previo

- Rama `main`; HEAD `4dbaeaeb8ec2b15205b7c34235a87dfde02bdb2c` (`Initial commit`). La referencia local `origin/main` coincide; no se hizo fetch.
- Estado inicial: **11 archivos modificados y 10 sin seguimiento**, sin cambios preparados para commit.
- La ampliación previa de Análisis, presupuesto, XLSX, tipos de tarjeta y esquema 6 se conserva. También se conservan la imagen de referencia, PDF bancario privado y su `Zone.Identifier`.
- Copia anterior a las ediciones F0: `<snapshots_privados>/20261027T205839.009349Z/`.
- Incluye `working-tree.tar.gz`, `manifest.json`, `unstaged.patch`, `staged.patch` y `SHA256SUMS`. Se verificó cada hash de los **82 archivos** versionados o sin seguimiento no ignorados, e incluye metadatos `.git`.
- Los directorios ignorados de build, caché y capturas no están en ese archivo; se conservaron en su lugar. Las compilaciones nuevas se ejecutan en una copia aislada.
- Esta es una **copia del repositorio**, no del almacenamiento privado de la app en el teléfono. No demuestra backup ni recuperación de gastos reales.
- No se ejecutaron comandos ADB, instalaciones, limpieza de datos, commits, cambios de rama, reset ni stash.

Los 11 archivos modificados previamente eran README, MainActivity, Database, ExpenseRepository, Model, Screens, Theme, colors.xml, themes.xml, brief y design-qa. Los 10 sin seguimiento eran la imagen, el PDF y su metadato, esquema 6, BudgetRepository, SpendingAnalytics, ExpenseXlsxExporter, AnalysisScreen y sus dos suites nuevas de tests. El inventario exacto y hashes quedan en la copia privada anterior.

## Entorno descubierto

| Elemento | Evidencia actual |
|---|---|
| Linux/WSL | No hay `java`, `javac`, `gradle` ni `adb` en PATH. `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` y `GRADLE_USER_HOME` estaban sin definir. |
| Java disponible | Android Studio de Windows: `/mnt/c/Program Files/Android/Android Studio/jbr`; OpenJDK/Javac **21.0.10**. No se encontró Java 17 independiente en las ubicaciones revisadas. |
| Objetivo del proyecto | Java/Kotlin JVM **17**, sin modificar. Runtime de Gradle y objetivo de bytecode son conceptos distintos. |
| SDK | `/mnt/c/Users/<usuario_windows>/AppData/Local/Android/Sdk`; plataforma Android 35 y Build Tools 36.0.0 presentes. |
| ADB | `platform-tools/adb.exe` localizado; **no ejecutado**, ni siquiera para enumerar dispositivos. |
| Gradle | Wrapper fijado en **8.11.1**; distribución existente en cachés Windows/Linux. AGP 8.9.2, Kotlin 2.1.10. |
| Caché escribible usada | `C:\Users\<usuario_windows>\.gradle`, ya existente. |
| Caché adicional | `.artifacts/f0/cache-seed/modules-2`, preparada a partir de dependencias Linux existentes. Artifacts inmutables por hard link; metadata copiada; sin locks/estado de instalación compartidos. |

No se añadieron ni actualizaron dependencias, JDK, SDK, plugins o herramientas. Las verificaciones Gradle usan `--offline`; se reutilizan artefactos existentes. La consulta de documentación Gradle para la caché de solo lectura no es una descarga de dependencias.

## Procedimiento reproducible

### Linux con herramientas nativas ya disponibles

Configurar una JVM compatible con Gradle 8.11.1/AGP y el SDK instalado. El proyecto conserva su objetivo JVM 17. Luego:

```bash
./gradlew --offline --no-daemon testDebugUnitTest
./gradlew --offline --no-daemon lintDebug
./gradlew --offline --no-daemon assembleDebug
```

Un fallo por artefactos no presentes en caché es una limitación de esa ejecución offline, no un resultado del análisis del código. No cambiar versiones ni ocultar el fallo para obtener un check verde.

### WSL con herramientas Windows existentes

El helper [verify_f0_windows.py](../scripts/verify_f0_windows.py) usa solo Python estándar, `cmd.exe`, `wslpath` y herramientas ya instaladas. Detecta un único SDK Windows o admite `--sdk`; exige la distribución wrapper 8.11.1 precargada para impedir su descarga automática. Copia scripts Gradle, fuentes, recursos, tests y esquemas a un directorio nuevo bajo `%LOCALAPPDATA%\Temp\app_gastos_f0`; no copia `.git`, fotos de raíz, PDF privado, APKs ni cachés de build del repositorio.

Comprobar requisitos sin copiar ni compilar:

```bash
python3 scripts/verify_f0_windows.py --check-environment
```

```bash
python3 scripts/verify_f0_windows.py
```

Si se necesita la caché adicional de esta sesión:

```bash
python3 scripts/verify_f0_windows.py \
  --readonly-cache-root .artifacts/f0/cache-seed
```

Para seleccionar solo los checks necesarios:

```bash
python3 scripts/verify_f0_windows.py \
  --readonly-cache-root .artifacts/f0/cache-seed \
  --tasks testDebugUnitTest
```

Cada ejecución genera un directorio único `.artifacts/f0/<UTC>/` con log, hashes de entrada, resumen, resultados JUnit y reporte lint si se produjo. Propaga el código de salida de Gradle y comprueba que sus fuentes originales no cambiaron. No omite tareas fallidas ni crea un lint baseline de supresión. `--continue` permite observar las tareas independientes aunque otra falle; no convierte el resultado global en éxito.

Preparación offline de la caché de solo lectura, si fuera necesario recrearla con la caché Linux ya existente:

```bash
python3 - <<'PY'
from pathlib import Path
import os
import shutil

origin = Path.home() / '.gradle/caches/modules-2'
target = Path('.artifacts/f0/cache-seed/modules-2')
if target.exists():
    print('El seed ya existe: reutilizarlo; no sobrescribir una copia en uso.')
else:
    for name in ('files-2.1', 'metadata-2.107'):
        source = origin / name
        if source.exists():
            shutil.copytree(
                source, target / name,
                copy_function=os.link if name == 'files-2.1' else shutil.copy2,
            )
PY
```

Este seed se encuentra en el mismo filesystem Linux que la caché original para permitir hard links; debe permanecer inmutable durante su uso. La metadata `2.107` corresponde a la caché encontrada, no a cualquier versión futura. No copiar locks ni usar una caché activa mutable como seed. Gradle espera que `GRADLE_RO_DEP_CACHE` apunte al directorio padre de `modules-2`: [documentación Gradle 8.11.1](https://docs.gradle.org/8.11.1/userguide/dependency_caching.html).

El aviso de CMD sobre directorio UNC se debe al inicio desde WSL; el batch cambia explícitamente al directorio Windows aislado antes de ejecutar Gradle. `--no-daemon` puede crear un proceso JVM de uso único por `org.gradle.jvmargs`; este termina con el build, no se instala un servicio.

## Resultados de esta ejecución

### Base anterior a los fixtures F0

1. Primer intento Windows offline: `testDebugUnitTest lintDebug assembleDebug --continue`.
   - **20 tests existentes aprobados**, cero fallos/errores.
   - APK debug generado en la copia aislada.
   - Resultado global **fallido**: lint no pudo resolver artefactos faltantes en la caché Windows, entre ellos `lint-gradle:31.9.2` y `ui-test-junit4:1.7.8`.
   - Log: `../.artifacts/f0/baseline-gradle.log`.
2. Reintento exclusivo de lint, usando seed de la caché Linux sin descargas:
   - **lintDebug aprobado**, cero errores y **15 warnings preexistentes**.
   - IDs: `OldTargetApi` (1), `ModifierParameter` (1), `ChromeOsAbiSupport` (1), `ObsoleteSdkInt` (1), `KaptUsageInsteadOfKsp` (1), `UseKtx` (10).
   - Log: `../.artifacts/f0/baseline-lint-cache-seed.log`.

La firma de esos avisos se usa para distinguir regresiones de F0.1; no son supresiones ni una promesa de corregirlos aquí.

### Código con fixtures y helper F0

Ejecución final actual: **2026-10-27**, `.artifacts/f0/20261027T210914.670509Z/`, con helper offline, copia aislada nueva y caché de solo lectura. Gradle terminó con **exit code 0**, `BUILD SUCCESSFUL`, 57 tareas ejecutadas en 2 min 30 s.

| Check | Resultado actual |
|---|---|
| `testDebugUnitTest` | **27 tests aprobados**: los 20 anteriores y 7 nuevos del corpus; cero fallos, errores o saltos. |
| `lintDebug` | **Aprobado**, cero errores y los mismos 15 warnings/IDs de la base. No se suprimieron avisos. |
| `assembleDebug` | **Aprobado**, APK de 80.918.573 bytes en la copia aislada. No instalado. |
| Objetivo JVM | Clase `SpendingAnalyzer.class` con major **61**, correspondiente a JVM 17, compilada usando runtime JBR 21. |
| Exclusión de fixtures | JSON de la base y clases `SyntheticBaselineFixtures` ausentes del APK/DEX. |
| Protección de fuentes | Los inputs de build mantienen sus hashes. Los 79 archivos originales no documentales, incluidos producción, tests anteriores, esquemas y entradas privadas, siguen idénticos a la copia previa. |
| Whitespace y formatos | JSON de fixtures y XML de resultados válidos; `git diff --check` sin incidencias tras corregir un espacio final introducido en documentación. |
| Helper y documentación | CLI/help, preflight válido y rechazo de SDK/caché/wrapper ausentes sin copiar ni descargar; sintaxis Python, enlaces locales nuevos y timestamp de ubicación sintética comprobados. |

Evidencia local ignorada por Git: `summary.json` (inputs, códigos, tests, APK), `gradle.log`, `verification.json` y `reports/` (JUnit y lint). Estos artefactos pueden no existir en otro checkout; los resultados anteriores se conservan aquí explícitamente como ejecución del 27 de octubre, no como garantía futura.

APK generado:

```text
C:\Users\<usuario_windows>\AppData\Local\Temp\app_gastos_f0\verify-0zq95cbo\project\app\build\outputs\apk\debug\app-debug.apk
SHA-256: 2822b8664baa66c381b5442e04b60319b1961b4d1132ac2f228c608443db41e9
```

El APK en `app/build/outputs/` del repositorio original no se regeneró: pertenece al historial local anterior. Usar el path de la ejecución aislada para identificar el artefacto nuevo, sin instalarlo automáticamente.

También persisten warnings de compilador sobre iconos/deprecaciones Compose, fallback kapt a lenguaje 1.9, opciones kapt de test no reconocidas y bibliotecas nativas que se empaquetan sin strip. No se ocultaron ni se corrigieron fuera de alcance. No impidieron estas pruebas, lint ni build.

## Fixtures y límites de cobertura

Ver [README de fixtures](../app/src/test/resources/baseline/README.md), `synthetic-expenses-v1.json`, `SyntheticBaselineFixtures.kt` y `SyntheticBaselineFixtureTest.kt`.

El corpus contiene nueve gastos inventados, dos tarjetas ficticias, una categoría personalizada, dos productos, tres evidencias, tres snapshots Wallet, una boleta OCR espacial sintética y candidatos temporales reservados para F1.3. Fija IDs/fechas/zona y evita `Instant.now()`/UUID aleatorios en el contenido cargado.

Los tests comprueban referencias, totales y filtros por moneda/mes, frontera UTC/Santiago, revisión, parsing Wallet, identidad del snapshot y aritmética/fecha de la boleta. No implementan ni declaran verificada la deduplicación futura.

## Documentación corregida

- README enlaza roadmap, entorno y corpus; añade lint y limita las afirmaciones JVM/dispositivo.
- Brief conserva sus requisitos e historial, pero sustituye prohibiciones contradictorias sobre Análisis y ubicación contextual local, aclara cámara del sistema y separa acuerdos futuros de capacidades actuales.
- Design QA queda expresamente identificado como histórico; se conserva la evidencia original.

## Pendiente y protección antes de futuras fases

- No hay backup nuevo de la base Room, boletas ni configuración del teléfono. La copia del repositorio no lo sustituye.
- No se ejecutaron pruebas instrumentadas, migraciones Room en Android, Wallet real, GPS, cámara, OCR ML Kit real, instalación ni pruebas de rendimiento físico.
- No se instaló el APK generado. Conservar la firma instalada y obtener/verificar recuperación antes de instalar futuras migraciones sobre los datos habituales.
- No se implementaron deduplicación 24 h, procedencia, nueva persistencia, workers, fecha impresa aplicada, asociaciones, backup del producto ni importador PDF.
- No avanzar automáticamente a F1.1; requiere una nueva tarea autorizada.
