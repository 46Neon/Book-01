# Documentación

## Decisiones provisionales de Parte 2A

El scaffold de API contable bajo `backend/` usa **FastAPI** provisionalmente, porque el servicio existente en `part2/service/` ya está implementado con ese framework. La selección es reversible y no implica aprobar un despliegue ni completar producción. El backend nuevo es solo lectura, no producción y no debe conectarse a un proyecto real hasta completar las revisiones de seguridad y validación pendientes. Ver [`../backend/README.md`](../backend/README.md) para límites y pruebas.

## Próximos hitos de Parte 2 (no implementados)

El contrato de seguridad, los bloqueos actuales y las entradas requeridas para 2B (borradores con confirmación) y 2C (búsqueda web) están documentados en [`parte-2b-borradores-y-2c-busqueda.md`](parte-2b-borradores-y-2c-busqueda.md). No implica que existan endpoints ni integraciones live.
