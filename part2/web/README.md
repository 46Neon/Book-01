# Book-01 · shell web de consulta

Interfaz estática, adaptable y en español para explorar el futuro chat de consultas contables de **solo lectura**. Este corte es únicamente una maqueta funcional del cliente: no implementa el chatbot ni el servicio contable y no significa que Parte 2 esté completa.

## Ejecutar localmente

Desde la raíz del repositorio, sirve esta carpeta por HTTP:

```sh
python3 -m http.server 8000 --directory part2/web
```

Abre <http://localhost:8000>. También puede abrirse `index.html` directamente, aunque servirla por HTTP se parece más a un despliegue estático real. No requiere instalación de paquetes ni proceso de compilación.

## Qué incluye

- Diseño responsive para móvil y escritorio; estructura semántica, etiquetas de formulario, estados accesibles y foco visible.
- Compositor y conversación locales. En el estado actual, al enviar una pregunta la interfaz indica claramente que no se envió y que no se consultaron datos. El texto no se persiste en almacenamiento local ni se transmite.
- Contexto explícito para periodo, moneda y filtros. Se puede validar el orden de las fechas; como no hay servicio conectado, el contexto tampoco se transmite.
- Aviso persistente de solo lectura y estado honesto de backend pendiente.
- `window.Book01Web.renderQueryResult(container, result)`, renderer de tablas accesible para una futura respuesta con forma `{ caption?, columns: [{ key, label }], rows: [...] }`. Usa nodos DOM y `textContent` para tratar los valores recibidos como texto, no como HTML. Valida la forma y limita columnas/filas.

## Límites actuales y seguridad

- No hay llamadas `fetch`/XHR, endpoint configurable ni integración de red en este corte. Existe un FastAPI de lectura provisional y testeable con transportes sintéticos, pero no hay instancia desplegada, proyecto Supabase, emisor/sesión de autenticación configurados ni contrato final.
- El OpenAPI en `docs/parte-2a-openapi-propuesta.yaml` es una propuesta revisada para reflejar el esquema SQL actual, no una API desplegada ni un contrato aprobado.
- No hay respuestas simuladas ni cifras contables de ejemplo; el área de resultados permanece vacía hasta recibir explícitamente datos válidos del renderer.
- No hay credenciales, claves de Groq ni secretos en el navegador. No se implementan escrituras, borradores, confirmaciones o contabilización.
- La interfaz no guarda el chat ni el contexto. Al limpiar o recargar, el contenido local desaparece.

## Requisitos antes de integrar un backend

1. Elegir el proyecto Supabase real; configurar su URL, Auth issuer/audience/proveedores, exposición/grants REST, RLS, membresías y usuarios de prueba. Aplicar migrations y probar book isolation con JWT reales antes de usarlo.
2. Desplegar la API contable y el servicio Part 2 por HTTPS; configurar sus bases URL y claves de runtime mediante settings/secrets del hosting. No usar service-role. La clave Groq solo se configura después de aprobar tratamiento de prompts/resultados.
3. Acordar login web que adquiera un JWT de usuario y mantenga el token solo en memoria. La interfaz no debe guardar tokens en localStorage/sessionStorage ni hacer llamadas directas a Supabase; solo enviará al servicio Part 2 por HTTPS.
4. Definir same-origin routing o CORS restringido, límites, manejo de errores, expiración/renovación de sesión y auditoría de privacidad. Validar integración real de extremo a extremo antes de cambiar el aviso de «Backend pendiente».

## Comprobaciones

El shell es HTML/CSS/JavaScript plano. El código puede verificarse con `node --check part2/web/app.js`; no hay archivo YAML/JSON ni dependencias/configuración de paquetes en esta carpeta.
