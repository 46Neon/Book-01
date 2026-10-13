# Android app

Módulo Android de Book-01, construido con Kotlin + Jetpack Compose. La aplicación conserva sus cinco pestañas (Diario, Mayor, Cuentas, Balance y Más); desde «Más» se accede a configuración, trazabilidad, estados financieros y respaldo cifrado.

## Funciones del prototipo

- Diario local con asientos simples de ingreso/gasto, guardados como líneas de doble partida balanceadas.
- Plan de cuentas inicial y altas de cuentas. Nombre y estado se pueden editar; código y tipo son inmutables. Las cuentas inactivas no se ofrecen para asientos nuevos.
- Mayor por cuenta con movimientos cronológicos, debe/haber, saldo corrido y saldo inicial anterior al límite «Desde».
- Balance de comprobación por cuenta y periodo.
- Estados financieros informativos: estado de resultados con límites inclusivos opcionales Desde/Hasta y balance general a una fecha inclusiva. Los importes son centavos USD enteros (`Long`); solo se consideran líneas USD de asientos contabilizados. Los borradores y otras monedas se excluyen y se informan, sin conversión.
- En el balance general, el resultado acumulado se presenta separado de las cuentas de patrimonio solo para actividad que no esté cubierta por un cierre. Un cierre transfiere el neto de ingresos/gastos a patrimonio y las líneas técnicas de cierre no alteran la actividad que muestra el estado de resultados del periodo.
- Reversas como nuevos asientos con débitos/créditos exactamente opuestos, fecha y motivo, vínculo al asiento original y auditoría local append-only; no se permite reversar dos veces ni reversar una reversa. Bloqueos derivados inmutables y triggers SQLite impiden actualizar o borrar líneas contabilizadas, o insertar líneas adicionales después de contabilizar.
- Filtros de fecha estrictos `AAAA-MM-DD`, límites inclusivos y rechazo de fechas inválidas o rangos invertidos. Los movimientos de reversa afectan las cifras en su fecha efectiva de contabilización.
- Migración Room aditiva v1→v7, incluida la instantánea histórica de nombres y metadatos inmutables de cierres. v6→v7 crea una fila derivada e inmutable por asiento `POSTED` y protege sus líneas; el proceso de publicación inserta primero las líneas y luego el bloqueo en una misma transacción. Un rango sin líneas P&L se guarda como cierre de metadatos sin asiento ficticio; el cierre inclusivo bloquea fechas y es irreversible en este prototipo: no hay reapertura ni reversa ordinaria del asiento de cierre.
- Exportación cifrada offline y restauración v1 de una instantánea consistente de Room v7 mediante SAF. El archivo incluye configuración, cuentas, asientos, líneas con instantáneas de nombres, auditorías y cierres; conserva IDs, relaciones, datos nulos y centavos `Long`. Antes de restaurar, autentica/valida, muestra libro y conteos, y requiere confirmación separada. Solo restaura sobre una base vacía no inicializada o el libro predeterminado exactamente intacto; jamás reemplaza un libro modificado o con cualquier movimiento/auditoría/cierre. Toda validación final e inserción ocurre en una transacción Room, sin borrar filas, desactivar triggers ni generar auditorías artificiales. Los bloqueos derivados se reconstruyen para los asientos contabilizados después de insertar las líneas, y no forman parte del archivo.

## Límites y advertencia

Es un prototipo local, no un sistema listo para producción ni una herramienta de asesoría o declaración fiscal. El cierre no habilita reapertura y es una implementación informativa básica, pendiente de revisión profesional. No se afirma cumplimiento contable, fiscal o legal en Venezuela ni en otra jurisdicción. Faltan borradores, usuarios/roles, backend/API de producción, sincronización, hardening y revisión profesional. Importación/restauración v1 está limitada al libro predeterminado sin personalizaciones ni historial y no reemplaza libros existentes. Exportación/restauración aún no forman una política de respaldo de producción: seguridad criptográfica, gestión de claves, recuperación, retención, amenazas y tratamiento de datos deben revisarse antes de cualquier uso real. El flujo SAF requiere prueba en dispositivos reales. Las frases perdidas no se pueden recuperar; guarda la frase aparte del archivo. La aplicación no sube ni comparte el archivo automáticamente; la ubicación la elige la persona.

GitHub Actions [run 38324187651](https://github.com/46Neon/Book-01/actions/runs/38324187651) pasó pruebas unitarias, compilación Android-test, ensamblado debug y 19 pruebas instrumentadas en emulador, incluida la protección de líneas y migración Room v1→v7. El esquema Room v7 se generó como artefacto CI. Las pruebas cubren códec y repositorio, no el recorrido SAF completo en un teléfono físico. Antes de cualquier uso real se requiere prueba manual SAF y revisión independiente de seguridad criptográfica, manejo de frase/archivo y política de respaldo.

USD se conserva como centavos enteros y se maneja con aritmética exacta, sin coma flotante ni conversión de monedas. Use datos ficticios en desarrollo; no guarde libros reales ni secretos en el repositorio.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

La acción de GitHub genera un APK debug como artefacto cuando el job de compilación finaliza correctamente.
