# ADR — Supabase PostgreSQL y gestión de secretos

- **Estado:** decisión acordada para la arquitectura del proyecto; el despliegue y la configuración del entorno siguen pendientes.
- **Fecha:** 2026-10-12

## Decisiones

1. Book-01 usará PostgreSQL gestionado en Supabase para los datos contables compartidos.
2. La APK, la web y el chatbot no se conectarán directamente a tablas ni ejecutarán SQL. Toda operación contable pasa por la API/backend de Book-01, que valida permisos, reglas del dominio y transacciones.
3. Las políticas Row Level Security (RLS) de PostgreSQL/Supabase serán defensa en profundidad para aislar libros/empresas; no sustituyen la autorización de la API ni las validaciones contables.
4. La clave de Groq se configurará en el entorno de ejecución del servicio conversacional como `GROQ_API_KEY`. No se incluirá en JavaScript del navegador, la APK, el repositorio, prompts de cliente, registros ni artefactos.
5. Las credenciales privilegiadas de Supabase y la cadena de conexión de PostgreSQL, si el backend las necesita, quedarán solo en el servidor y en el gestor de secretos del despliegue. La clave `service_role` nunca se distribuye a clientes.
6. El servicio conversacional consulta el libro mediante la API contable autenticada. No necesita credenciales Supabase ni acceso directo a PostgreSQL.
7. Las migraciones del esquema se versionarán y revisarán; no se dependerá de cambios manuales no reproducibles en el panel de Supabase.

## Flujo objetivo

```text
APK / chat web → API contable autenticada → dominio y autorización
                                          → PostgreSQL en Supabase
Chat web → servicio conversacional → Groq (clave solo servidor)
                                 → API contable de solo lectura en 2A
```

En 2A, el servicio conversacional solo expone herramientas GET allowlisted. En etapas posteriores, los borradores y contabilizaciones deberán ejecutarse mediante operaciones explícitas del dominio, con idempotencia, transacciones y auditoría; nunca mediante SQL generado por el modelo.

## Pendiente antes de integración real

- Crear/configurar el proyecto Supabase y definir entornos de desarrollo, pruebas y producción.
- Elegir y configurar el proveedor de autenticación (Supabase Auth es una opción recomendada) y documentar issuer, audience, claims y membresía de libros.
- Implementar la API contable de Parte 1, con autorización por libro y RLS comprobable.
- Configurar secretos `GROQ_API_KEY` y credenciales DB/Supabase en el gestor de secretos del servicio desplegado; no compartir sus valores en chat ni subirlos a GitHub.
- Validar el contrato OpenAPI definitivo, migraciones, restauración, aislamiento entre tenants y pruebas de seguridad antes de manejar datos reales.
