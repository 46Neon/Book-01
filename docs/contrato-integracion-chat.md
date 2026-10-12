# Contrato de integración para el mantenedor de Parte 2

Este documento define el límite previsto entre el libro contable de Parte 1 y el chatbot de Parte 2. **No describe una implementación del chatbot ni autoriza acceso directo a la base de datos.** La versión definitiva se publicará como OpenAPI junto con el backend.

## Principios

- El chat usa la API HTTPS autenticada del libro; no abre conexión SQL.
- API y dominio contable son la autoridad sobre saldos, reportes, validaciones y permisos.
- Las respuestas deben incluir moneda, periodo, filtros y referencias de filas cuando aplique.
- Las escrituras empiezan como borradores. El cliente presenta una vista previa; una confirmación explícita permite solicitar contabilización.
- El usuario y el libro se identifican mediante la sesión validada. No confiar en `user_id`/`tenant_id` suministrados por el modelo.
- Usar clave de idempotencia para operaciones que creen o contabilicen registros.

## Recursos y acciones previstos

- Consultar cuentas y su estado.
- Listar asientos y líneas por intervalo de fechas, cuenta, contraparte y estado.
- Obtener saldos y reportes de balance/resultados para un periodo.
- Crear un borrador de asiento/ingreso/gasto con sus líneas.
- Validar un borrador y devolver errores explicados.
- Contabilizar un borrador ya revisado y autorizado.

Nombres, rutas y esquemas son preliminares hasta publicar OpenAPI. El mantenedor de Parte 2 debe usar los esquemas oficiales generados, no codificar rutas tentativas.

## Forma de datos

- Los importes se transmiten como texto decimal exacto (por ejemplo `"125.40"`) junto con código de moneda; nunca como float JSON.
- Fechas contables usan fecha ISO `YYYY-MM-DD`; timestamp de auditoría usa RFC 3339.
- Un asiento incluye líneas separadas, débitos y créditos, y referencias a cuenta/documento cuando existan.
- Las respuestas usan identificadores estables, estado (`draft`, `posted`, `reversed`, etc.) y errores estructurados.

## Flujo de escritura previsto

1. El chatbot reúne datos faltantes; no inventa cuentas, fecha, moneda ni contraparte.
2. Llama la acción de crear borrador con una clave de idempotencia.
3. La API valida esquema y reglas semánticas y devuelve el borrador normalizado.
4. La interfaz muestra vista previa: fecha, descripción, cuentas, importes, moneda y efecto.
5. Solo ante aprobación explícita de la persona, solicita contabilización.
6. El servidor vuelve a validar permisos, versión, periodo y equilibrio; registra auditoría.
7. La API devuelve referencia del asiento para abrirlo en la hoja Diario.

## Errores que el backend debe comunicar

Validación de partida doble, periodo cerrado, cuenta inactiva, moneda/precisión inválida, duplicado, falta de autorización, conflicto de versión, documento requerido y servicio temporalmente no disponible. El chatbot puede traducirlos a lenguaje sencillo, pero no debe ocultar ni superar el bloqueo.
