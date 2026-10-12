# Book-01

Libro contable Android de uso familiar para contadores: una interfaz de tabla inspirada en hojas de cálculo, con controles contables y trazabilidad.

## Alcance de este repositorio

Este repositorio corresponde a la **Parte 1**: núcleo del libro contable, backend contable y APK Android. La interfaz conversacional, Groq y el servidor MCP serán responsabilidad de otro mantenedor y no se implementan en esta parte.

La Parte 1 expondrá una API documentada y estable para que el chatbot consulte y proponga operaciones sin acceder directamente a la base de datos. Ver [`docs/contrato-integracion-chat.md`](docs/contrato-integracion-chat.md).

## Experiencia principal

- Hojas/tabs para Diario, Plan de cuentas, Mayor, Balance y Resultados.
- Filas y columnas legibles, búsqueda, filtros y totales.
- Uso cómodo en teléfono y presentación más amplia en tablet.
- Captura local y sincronización segura cuando haya conexión.
- Borradores editables; asientos contabilizados trazables y corregibles mediante reversas o ajustes.

## Stack base

- Android: Kotlin, Jetpack Compose.
- Datos locales: Room/SQLite; sincronización: WorkManager.
- Backend: API HTTPS documentada con OpenAPI y PostgreSQL.
- Valores monetarios exactos; no usar punto flotante para dinero.

## Estado actual

- Repositorio privado creado.
- Requisitos, arquitectura y contrato de integración documentados.
- Primera pantalla Android Compose creada con datos demostrativos y navegación visual.
- El prototipo aún no persiste datos ni se ha compilado en este entorno; no es todavía una APK funcional para llevar contabilidad real.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.
