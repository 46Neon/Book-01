# Guía visual inicial

La maqueta aprobada por la persona usuaria guía la primera pantalla del APK. Es una referencia de estilo; no representa todavía una captura de una aplicación compilada.

## Principios

- Familiaridad de hoja de cálculo sin saturar la pantalla del teléfono.
- Fondo blanco/gris muy claro, texto azul oscuro y acento verde azulado.
- Pestañas de libro y filas/columnas con separadores sutiles.
- Importes alineados a la derecha; fechas, cuentas y descripción legibles.
- Resúmenes de saldo, ingresos y gastos sobre la tabla.
- Controles visibles para intervalo de fechas y filtros de texto, cuenta/categoría, tipo y estado; fechas estrictas `AAAA-MM-DD` y acción para limpiar.
- En móvil, desplazamiento horizontal explícito para columnas adicionales; en tablet, aprovechar más ancho.
- Cada fila se podrá abrir como detalle; no depender de gestos ocultos para acciones importantes.
- Estados de borrador, contabilizado, pendiente de sincronización y error deberán distinguirse claramente.

## Primera pantalla

- Cabecera con nombre del libro y selector.
- Resumen de cifras del periodo.
- Hoja Diario con selector de periodo, filtros y tabla. El selector abre los filtros por fechas inclusivas; búsqueda de descripción/cuenta/código; cuenta/categoría; ingreso/gasto; y borrador/contabilizado. Borradores y asientos contabilizados se filtran con los mismos criterios, sin separar las líneas del asiento. La contraparte opcional se muestra en el Diario y entra en la búsqueda de texto; no existe un filtro dedicado por contraparte.
- Acción principal “Nuevo asiento”.
- Navegación inferior: Diario, Mayor, Cuentas, Balance y Más.

Los valores visibles en el prototipo son exclusivamente demostrativos. En la aplicación real se obtendrán del libro y respetarán moneda, periodo y filtros.
