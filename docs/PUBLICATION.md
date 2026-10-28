# Preparación de la versión pública

El usuario autorizó publicar el proyecto para su portafolio y conservar la misma URL. Para que los commits antiguos con fotografías no se vuelvan públicos, se conserva el repositorio original como `app_gastos-private-archive` (privado) y se crea `app_gastos` con una historia nueva. No se borra ningún repositorio.

## Alcance de la limpieza

- Se conserva una copia privada verificable de la historia original antes de modificarla.
- No se publica la historia original ni se conecta el repositorio nuevo como fork.
- Fotografías, PDF bancario, respaldos y artefactos generados quedan fuera del árbol público.
- Se sustituyen ejemplos de compras en pruebas, simulador y documentación por datos ficticios, incluidos productos, importes, comercio, folio y tarjeta.
- El antiguo JSON de referencia se conserva en almacenamiento privado. Un JSON totalmente sintético pasa a `src/test/resources/receipt/` y una prueba comprueba su coherencia con el ejemplo canónico.
- Se retiran notas de QA con compras reales y rutas de máquina. El resumen público distingue evidencia histórica de verificaciones actuales.
- El nuevo commit usa la dirección `noreply` de GitHub para evitar publicar un correo personal.

No se implementan funcionalidades de F1–F7 ni se modifica la instalación del teléfono.

## Verificación

Nueva ejecución offline de tests, lint y build en una copia aislada el **2026-10-28 UTC**, posterior a la sustitución de fixtures:

| Comprobación | Resultado |
|---|---|
| `testDebugUnitTest` | **28 tests aprobados**, cero fallos, errores o saltos. |
| `lintDebug` | Aprobado, cero errores y 15 warnings preexistentes. |
| `assembleDebug` | Aprobado; no se instaló el APK ni se publicó un binario. |
| APK | Sin JSON de referencia/debug, corpus de test ni cadenas de los ejemplos personales retirados. |
| Fuentes | Hashes de los inputs originales sin cambios durante el build. |

El test adicional comprueba que el JSON sintético y el ejemplo canónico tienen los mismos campos, productos e importes. Se mantuvieron las pruebas de parsing y aritmética existentes, sustituyendo sus datos de entrada y resultados esperados coherentemente.

Los logs, reportes JUnit, lint y hashes de inputs se conservan localmente en `.artifacts/f0/20261028T020243.049151Z/`, excluidos de Git. No se instalaron ni actualizaron dependencias; se reutilizaron las cachés existentes.

## Controles de publicación

El nuevo repositorio debe tener un único commit raíz sin padres de la historia privada y no debe ser un fork. Antes de cambiar su visibilidad se comprueban los archivos alcanzables, las exclusiones y que su ID sea distinto del repositorio original. Después se verifica el acceso sin autenticar, la ausencia de los commits antiguos en el repositorio público y que el archivo original siga privado.

Esta revisión comprueba las entradas privadas conocidas y patrones comunes de credenciales; no sustituye una auditoría de seguridad completa del código. El estado funcional y las limitaciones permanecen descritos en el roadmap.
