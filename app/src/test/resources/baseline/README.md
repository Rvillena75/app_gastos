# Base sintética F0.1

`synthetic-expenses-v1.json` se escribió con datos inventados para pruebas JVM. No deriva de fotografías, notificaciones, ubicaciones, tarjetas ni cartolas del usuario. Vive en `src/test/resources`: no se empaqueta como asset de producción/debug ni se carga en Room o en el teléfono.

El corpus fija IDs, instantes y zona `America/Santiago`. Incluye:

- Gastos CLP, USD y EUR, un borrado lógico y un periodo anterior.
- Comercio ausente, monto ausente, categoría personalizada y movimientos sin tarjeta.
- Un instante UTC de septiembre que todavía corresponde a agosto en Santiago.
- Wallet con monto/tarjeta, otro evento USD y una compra sin monto.
- Evidencias Wallet, ubicación ficticia y corrección manual con referencias verificables.
- Una boleta OCR **sintética**, sin imagen: productos 8.000 + 4.500, descuento 2.500, neto 8.403, IVA 1.597 y total 10.000 CLP. RUT de ejemplo 11.111.111-1, fecha sin hora y comercio ficticio.
- Candidatos a 24 h y 24 h + 1 s para la futura F1.3. Son entradas, no afirmaciones de que el matcher actual cumpla el contrato.

`SyntheticBaselineFixtures` carga estos datos para reutilizarlos en tests posteriores. `SyntheticBaselineFixtureTest` comprueba referencias, análisis por moneda/zona, política de revisión, parsing Wallet, identidad exacta del snapshot y parsing/aritmética de la boleta sintética.

No prueba Room, workers, deduplicación futura, GPS, Android, ML Kit real, backup/restauración ni parsing bancario. La muestra PDF personal permanece fuera de este corpus. Durante la preparación pública, los ejemplos históricos del parser se sustituyeron también por datos inventados; la publicación no requiere fotografías privadas.
