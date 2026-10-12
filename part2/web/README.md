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

- No hay llamadas `fetch`/XHR, endpoint configurable ni integración de red en este corte: el backend, el contrato final y el proveedor de autenticación aún no existen. No se deben introducir solicitudes contables hasta acordar esos componentes.
- El OpenAPI en `docs/parte-2a-openapi-propuesta.yaml` es una propuesta, no una API desplegada ni un contrato definitivo.
- No hay respuestas simuladas ni cifras contables de ejemplo; el área de resultados permanece vacía hasta recibir explícitamente datos válidos del renderer.
- No hay credenciales, claves de Groq ni secretos en el navegador. No se implementan escrituras, borradores, confirmaciones o contabilización.
- La interfaz no guarda el chat ni el contexto. Al limpiar o recargar, el contenido local desaparece.

## Requisitos antes de integrar un backend

1. Publicar y desplegar el contrato definitivo para consultas, con esquema de respuesta tipado que incluya moneda, periodo, filtros y referencias cuando correspondan; confirmar también el mapeo entre intención de chat y las cinco operaciones de consulta propuestas.
2. Definir autenticación/sesión de usuario y autorización por libro/tenant en el servidor. Nunca confiar en identificadores suministrados por el modelo ni incluir secretos de servicio en código cliente.
3. Acordar URL HTTPS por entorno, política CORS, límites, manejo estructurado de errores y límites de datos. Toda integración futura debe exigir configuración explícita del endpoint y fallar cerrada si está ausente o no es segura; no debe enviar preguntas ni contexto en modo desconectado.
4. Revisar el tratamiento de datos del prompt y resultados (retención, registros, privacidad) y probar que la API rechaza operaciones de escritura para este cliente.

## Comprobaciones

El shell es HTML/CSS/JavaScript plano. El código puede verificarse con `node --check part2/web/app.js`; no hay archivo YAML/JSON ni dependencias/configuración de paquetes en esta carpeta.
