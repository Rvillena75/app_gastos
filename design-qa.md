# QA de interfaz — resumen público

Este documento resume el QA histórico de agosto de 2026. Las capturas originales y las notas que contenían compras reales se conservan privadamente y no se distribuyen con el proyecto.

Se registraron recorridos sobre un Samsung SM-S918B para revisar navegación, carrusel de tarjetas, filtros, editor de tarjetas, visor de boleta con zoom, presupuesto, análisis mensual y exportación XLSX. Las instalaciones de entonces fueron incrementales y conservaron los datos existentes.

Decisiones de interfaz que se mantienen:

- Resumen, Movimientos y Análisis, con una única acción global Agregar.
- Carrusel centrado con proporción física de tarjeta 8,56 × 5,4 y colores personalizables.
- Carga de la fotografía de boleta solo cuando el usuario abre el visor.
- Presupuesto informativo y análisis con periodo explícito.
- Sin OCR crudo, JSON, scores ni comparación de modelos en los flujos productivos.

Este registro no es una prueba actual de dispositivo ni un benchmark reproducible. Los tests JVM no validan píxeles, permisos o latencia OCR.

Ver [F0_BASELINE.md](docs/F0_BASELINE.md) para la base reproducible, [PUBLICATION.md](docs/PUBLICATION.md) para las comprobaciones de la versión pública y [IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) para las limitaciones y fases pendientes.
