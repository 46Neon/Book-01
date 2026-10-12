# Alcance — Parte 1 de Book-01

## Propósito

Crear el libro contable y su APK Android antes de integrar la experiencia de chatbot. La APK debe ser utilizable por una persona contadora que ya conoce hojas de cálculo: organización en tablas, encabezados, filtros, filas, totales y pestañas; sin copiar ciegamente la libertad de edición de Excel cuando comprometa la integridad contable.

## Personas y necesidad

- Contador/a o responsable que necesita registrar y revisar movimientos con rapidez.
- Dueño/a de negocio que necesita consultar cifras y movimientos comprensibles.
- En una etapa posterior, un mantenedor del chatbot que consultará o solicitará borradores mediante la API publicada.

## Hojas y vistas del MVP

1. **Diario:** fecha, número, descripción, cuenta, débito, crédito, moneda, contraparte, estado y soporte.
2. **Plan de cuentas:** código, nombre, tipo, cuenta padre, estado y clasificación para reportes.
3. **Mayor:** movimientos por cuenta, débitos, créditos y saldo acumulado.
4. **Balance de comprobación:** saldo deudor y acreedor por cuenta y periodo.
5. **Resultados:** ingresos, costos y gastos para un rango de fechas.

## Experiencia tipo hoja de cálculo

- Pestañas con nombres claros; cuadrícula con encabezados y filas identificables.
- Filtro por fecha, cuenta, categoría, contraparte y estado; búsqueda textual.
- Encabezados y columnas clave persistentes al desplazarse.
- Totales y periodo visibles; selección de fila para abrir el detalle.
- En móvil, mostrar columnas prioritarias y abrir el resto en un detalle; en tablet, tabla más ancha.
- Formularios de captura rápidos que conservan el vocabulario y el orden de columnas del Diario.
- Importación/exportación por etapas; cualquier importación se valida en una vista previa antes de confirmarse.

## Reglas funcionales

- Doble partida en la contabilización; cada asiento debe cuadrar.
- Guardar borradores editables separados de asientos contabilizados.
- Un asiento contabilizado no se borra ni se sobrescribe; corrección mediante reversa/ajuste con vínculo al original.
- Todo saldo y reporte se calcula con reglas deterministas del dominio, no con fórmulas del modelo de IA.
- Guardar fechas, moneda y cantidades con precisión explícita; evitar `float`/`double` para importes.
- Periodos, zonas horarias, precisión monetaria y localización deben configurarse, no codificarse como una regla fiscal universal.

## Fuera del alcance de Parte 1

- Chat conversacional, prompts, Groq, búsqueda web, MCP y herramientas de IA.
- Declarar cumplimiento fiscal/legal en Venezuela u otra jurisdicción sin análisis y aprobación profesional.
- Nómina, inventario avanzado, e-invoicing local y presentación automática de declaraciones en el MVP.

## Criterios de aceptación del MVP

- Registrar borrador y convertirlo en asiento contabilizado únicamente tras validación.
- Mostrar Diario, Plan de cuentas, Mayor, Balance de comprobación y Resultados.
- Los asientos desbalanceados, los accesos no autorizados y las operaciones duplicadas deben rechazarse.
- Las correcciones dejan historia consultable.
- La interfaz permite filtrar y revisar filas con claridad en teléfono y tablet.
- La API documentada permite consultar y crear borradores con los mismos controles.
