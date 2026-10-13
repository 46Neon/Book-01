# Prueba manual del flujo SAF (Android)

**Estado:** pendiente de ejecutar en un dispositivo físico. La suite de CI [run 38461086978](https://github.com/46Neon/Book-01/actions/runs/38461086978) pasó 29/29 pruebas; incluye una prueba instrumentada de round-trip cifrado por URI y otra que comprueba las acciones, nombre y tipos MIME de los intents de AndroidX. Estas pruebas no abren la interfaz del selector de documentos ni comprueban un proveedor de archivos real; por eso sigue haciendo falta el recorrido físico de abajo. Tampoco constituye una revisión de seguridad de producción.

## Seguridad y preparación

- Usa únicamente un dispositivo y un libro de prueba con datos ficticios. No uses libros reales ni frases habituales.
- Anota modelo del dispositivo, versión de Android, proveedor de documentos usado y commit del APK probado.
- Prepara un libro de destino vacío o exactamente intacto. La restauración está diseñada para rechazar libros con movimientos, auditoría, cierres o personalizaciones.
- Si el dispositivo contiene datos reales, **no borres los datos de la aplicación ni la desinstales para preparar la prueba**. Usa otro dispositivo o una instalación de prueba vacía.
- Usa una frase de cifrado de prueba de al menos 12 caracteres y guárdala separada del archivo durante la prueba. La app no puede recuperar una frase perdida.

## Recorrido

1. Instala el APK debug en la instalación de prueba. Crea y contabiliza un movimiento ficticio identificable, por ejemplo, un gasto con descripción `SAF-E2E-<fecha>` y monto USD 1,23. Confirma que aparece en Diario.
2. Abre **Más → Exportar copia cifrada**. En el selector del sistema, guarda el archivo como `book01-saf-e2e.b01` en Descargas. Completa y confirma la frase de prueba.
3. Comprueba que la app confirma la exportación y que el archivo aparece en el proveedor elegido.
4. En una instalación de prueba vacía, abre **Más → Restaurar copia cifrada**, selecciona el archivo y escribe la frase. Verifica en la vista previa el libro y los conteos esperados. Cancela y confirma que el libro de destino sigue sin cambios.
5. Repite la selección del archivo, revisa de nuevo la vista previa y confirma la restauración. Comprueba el mensaje de éxito y que Diario muestra la descripción y el monto exactos.
6. Intenta restaurar el mismo archivo otra vez sobre el libro ya poblado. Se debe rechazar sin reemplazar ni alterar sus datos.
7. Como comprobación adicional, intenta una vez con una frase incorrecta sobre un libro de prueba vacío: la autenticación debe fallar antes de la vista previa y el libro debe seguir vacío.

## Resultado que registrar

Marca cada paso como aprobado o fallido y anota cualquier mensaje mostrado, el modelo/versión del dispositivo, el proveedor SAF y el commit del APK. Al terminar, elimina solo los archivos y datos ficticios creados para la prueba; nunca elimines datos reales para limpiar el entorno.
