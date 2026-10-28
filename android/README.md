# Android app

Módulo Android de Ariño, construido con Kotlin + Jetpack Compose. La navegación prioriza los libros auxiliares de Ventas y Compras; Diario, Mayor, Cuentas, Balance y Más mantienen el acceso al trabajo contable general, informes, auditoría y respaldo cifrado.

## Funciones del prototipo

- Diario local con asientos simples de ingreso/gasto, guardados como líneas de doble partida balanceadas.
- Libros auxiliares de ventas y compras con resumen derivado por período: cantidad de facturas y suma exacta de sus totales brutos (no equivale a cobros/pagos ni al saldo pendiente); se usan centavos `Long` y la suma rechaza desbordamientos.
- Plan de cuentas inicial y altas de cuentas. Nombre y estado se pueden editar; código y tipo son inmutables. Las cuentas inactivas no se ofrecen para asientos nuevos.
- Mayor por cuenta con movimientos cronológicos, debe/haber, saldo corrido y saldo inicial anterior al límite «Desde».
- Balance de comprobación por cuenta y periodo.
- Estados financieros informativos: estado de resultados con límites inclusivos opcionales Desde/Hasta y balance general a una fecha inclusiva. Los importes son centavos USD enteros (`Long`); solo se consideran líneas USD de asientos contabilizados. Los borradores y otras monedas se excluyen y se informan, sin conversión.
- En el balance general, el resultado acumulado se presenta separado de las cuentas de patrimonio solo para actividad que no esté cubierta por un cierre. Un cierre transfiere el neto de ingresos/gastos a patrimonio y las líneas técnicas de cierre no alteran la actividad que muestra el estado de resultados del periodo.
- Reversas como nuevos asientos con débitos/créditos exactamente opuestos, fecha y motivo, vínculo al asiento original y auditoría local append-only; no se permite reversar dos veces ni reversar una reversa. Bloqueos derivados inmutables y triggers SQLite impiden actualizar o borrar líneas contabilizadas, o insertar líneas adicionales después de contabilizar.
- Filtros de fecha estrictos `AAAA-MM-DD`, límites inclusivos y rechazo de fechas inválidas o rangos invertidos. Los movimientos de reversa afectan las cifras en su fecha efectiva de contabilización.
- Migración Room aditiva v1→v9, incluida la instantánea histórica de nombres, metadatos inmutables de cierres y contraparte opcional del asiento (v8→v9). v6→v7 crea bloqueos derivados para todos los asientos `POSTED`; v7→v8 añade borradores locales editables y guardas SQL para validar su publicación, sin permitir cambios posteriores a asientos contabilizados. El proceso de publicación inserta primero las líneas y activa el bloqueo dentro de la misma transacción. Un rango sin líneas P&L se guarda como cierre de metadatos sin asiento ficticio; el cierre inclusivo bloquea fechas y es irreversible en este prototipo: no hay reapertura ni reversa ordinaria del asiento de cierre.
- Exportación cifrada offline y restauración v1 de una instantánea consistente de Room v9 mediante SAF. El archivo incluye configuración, cuentas, borradores, asientos, líneas con instantáneas de nombres, auditorías y cierres; conserva IDs, relaciones, datos nulos y centavos `Long`. Antes de restaurar, autentica/valida, muestra libro y conteos, y requiere confirmación separada. Solo restaura sobre una base vacía no inicializada o el libro predeterminado exactamente intacto; jamás reemplaza un libro modificado o con cualquier movimiento/auditoría/cierre. Toda validación final e inserción ocurre en una transacción Room, sin borrar filas, desactivar triggers ni generar auditorías artificiales. Los bloqueos derivados se reconstruyen para los asientos contabilizados después de insertar las líneas, y no forman parte del archivo.

## Límites y advertencia

Los libros de ventas y compras están disponibles en esta rama, sujetos a integración en main y validación de build antes de distribuir APK. Es un prototipo local, no un sistema listo para producción ni una herramienta de asesoría o declaración fiscal. El cierre no habilita reapertura y es una implementación informativa básica, pendiente de revisión profesional. No se afirma cumplimiento contable, fiscal o legal en Venezuela ni en otra jurisdicción. Faltan backend/API de producción, sincronización, usuarios/roles, aprobaciones, hardening y revisión profesional. Importación/restauración v1 está limitada al libro predeterminado sin personalizaciones ni historial y no reemplaza libros existentes. Exportación/restauración aún no forman una política de respaldo de producción: seguridad criptográfica, gestión de claves, recuperación, retención, amenazas y tratamiento de datos deben revisarse antes de cualquier uso real. El selector SAF debe probarse manualmente en dispositivos reales; sigue pendiente la [lista de comprobación](../docs/prueba-manual-saf.md). Las frases perdidas no se pueden recuperar; guarda la frase aparte del archivo. La aplicación no sube ni comparte el archivo automáticamente; la ubicación la elige la persona.

GitHub Actions [run 38327256848](https://github.com/46Neon/Book-01/actions/runs/38327256848) pasó pruebas unitarias, compilación Android-test y ensamblado debug; [run instrumentado 38327263369](https://github.com/46Neon/Book-01/actions/runs/38327263369), sobre Room v8, pasó 23/23 pruebas en emulador, incluidas migración v1→v8, borradores y restauración. [Run de build 38333738927](https://github.com/46Neon/Book-01/actions/runs/38333738927) pasó pruebas unitarias, compilación Android-test y ensamblado debug; [run instrumentado 38333742905](https://github.com/46Neon/Book-01/actions/runs/38333742905) ejecutó `:app:connectedDebugAndroidTest` en emulador y pasó **24/24 pruebas** (0 fallos, 0 errores, 0 omitidas), incluida la migración v1→v9 y la validación de contraparte opcional. Consulta el [artefacto con los resultados XML](https://github.com/46Neon/Book-01/actions/runs/38333742905/artifacts/11783798652). Las pruebas cubren códec, repositorio y transacciones, no el recorrido SAF completo en un teléfono físico. Usa la [lista de comprobación SAF](../docs/prueba-manual-saf.md); antes de cualquier uso real también se requiere revisión independiente de seguridad criptográfica, manejo de frase/archivo y política de respaldo.

USD se conserva como centavos enteros y se maneja con aritmética exacta, sin coma flotante ni conversión de monedas. Use datos ficticios en desarrollo; no guarde libros reales ni secretos en el repositorio.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

La acción de GitHub genera un APK debug como artefacto cuando el job de compilación finaliza correctamente.

