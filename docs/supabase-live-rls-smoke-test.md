# Prueba en vivo de Supabase Auth y RLS

El test `backend/tests/live/test_supabase_rls_live.py` es un smoke test **opt-in y de solo lectura** para un proyecto Supabase de desarrollo. Comprueba que dos JWT reales de Auth son válidos y que dos usuarios solo pueden leer su propio libro y sus propias cuentas mediante PostgREST.

No crea, modifica ni elimina datos. No imprime tokens, claves ni cuerpos de respuesta. No prueba el servicio conversacional, Groq, el despliegue HTTPS ni la integración de Android.

## Preparación requerida

Usa un proyecto de **desarrollo**, nunca producción. Debe tener la migración de Book-01 aplicada, Auth habilitado y RLS activo. Provisiona:

- Usuario de prueba A y usuario de prueba B en Supabase Auth.
- Un libro diferente para cada usuario; cada uno debe tener únicamente la membresía prevista para la prueba.
- Al menos una cuenta en cada libro.
- Los JWT vigentes de ambos usuarios y los dos UUID de libros.

La web de Book-01 no ofrece autorregistro y la API actual no crea libros; el aprovisionamiento debe hacerse mediante un procedimiento administrativo controlado. No agregues usuarios o membresías desde el navegador para preparar este test.

## Variables necesarias

Configura estas variables en un entorno local seguro o un gestor de secretos. **No las guardes en Git, archivos compartidos, logs ni mensajes.** Los JWT son credenciales temporales y deben protegerse igual que secretos.

- `SUPABASE_LIVE_TESTS=1` — habilita explícitamente el test.
- `SUPABASE_URL` — origen HTTPS del proyecto, sin ruta ni query.
- `SUPABASE_PUBLISHABLE_KEY` — clave publicable del proyecto.
- `SUPABASE_TEST_USER_A_JWT` y `SUPABASE_TEST_USER_B_JWT` — JWT recientes emitidos por Auth para los dos usuarios.
- `SUPABASE_TEST_BOOK_A_ID` y `SUPABASE_TEST_BOOK_B_ID` — UUID distintos de los libros de prueba.

## Ejecución

Desde la raíz del repositorio, con las variables ya cargadas en el entorno seguro:

```sh
python -m pytest backend/tests/live/test_supabase_rls_live.py -q
```

Sin `SUPABASE_LIVE_TESTS=1`, el test queda omitido y no hace solicitudes. Para correr las pruebas unitarias normales no se necesitan claves ni proyecto Supabase:

```sh
python -m pytest backend/tests
```

## Criterio esperado

- Ambos JWT obtienen una identidad válida de Supabase Auth.
- A puede consultar su libro y al menos una cuenta del mismo.
- B puede consultar su libro y al menos una cuenta del mismo.
- A no ve el libro ni las cuentas de B; B tampoco ve los de A.

Una aprobación solo demuestra Auth y el aislamiento RLS de esos endpoints REST con esos usuarios de prueba. No demuestra que el backend, la web, el servicio de chat o Android estén desplegados o conectados correctamente. La contabilización sigue bloqueada en el esquema actual y no se debe crear un asiento `posted` para este test.
