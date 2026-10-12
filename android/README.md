# Android app

Módulo Android de Book-01, construido con Kotlin + Jetpack Compose. La aplicación conserva sus cinco pestañas (Diario, Mayor, Cuentas, Balance y Más); desde «Más» se accede a configuración, trazabilidad, estados financieros y exportación cifrada.

## Funciones del prototipo

- Diario local con asientos simples de ingreso/gasto, guardados como líneas de doble partida balanceadas.
- Plan de cuentas inicial y altas de cuentas. Nombre y estado se pueden editar; código y tipo son inmutables. Las cuentas inactivas no se ofrecen para asientos nuevos.
- Mayor por cuenta con movimientos cronológicos, debe/haber, saldo corrido y saldo inicial anterior al límite «Desde».
- Balance de comprobación por cuenta y periodo.
- Estados financieros informativos: estado de resultados con límites inclusivos opcionales Desde/Hasta y balance general a una fecha inclusiva. Los importes son centavos USD enteros (`Long`); solo se consideran líneas USD de asientos contabilizados. Los borradores y otras monedas se excluyen y se informan, sin conversión.
- En el balance general, el resultado acumulado se presenta separado de las cuentas de patrimonio solo para actividad que no esté cubierta por un cierre. Un cierre transfiere el neto de ingresos/gastos a patrimonio y las líneas técnicas de cierre no alteran la actividad que muestra el estado de resultados del periodo.
- Reversas como nuevos asientos con débitos/créditos exactamente opuestos, fecha y motivo, vínculo al asiento original y auditoría local append-only; no se permite reversar dos veces ni reversar una reversa.
- Filtros de fecha estrictos `AAAA-MM-DD`, límites inclusivos y rechazo de fechas inválidas o rangos invertidos. Los movimientos de reversa afectan las cifras en su fecha efectiva de contabilización.
- Migración Room aditiva v1→v6, incluida la instantánea histórica de nombres y metadatos inmutables de cierres. Un rango sin líneas P&L se guarda como cierre de metadatos sin asiento ficticio; el cierre inclusivo bloquea fechas y es irreversible en este prototipo: no hay reapertura ni reversa ordinaria del asiento de cierre.
- Exportación offline de una instantánea consistente de Room v6 a un URI escogido por la persona mediante SAF/CreateDocument. El paquete incluye configuración, cuentas, asientos, líneas con instantáneas de nombres, auditorías y cierres; conserva IDs, relaciones y centavos `Long`. Usa un formato versionado con PBKDF2-HMAC-SHA-256 y AES-256-GCM, sal/nonce aleatorios nuevos por exportación, y frase introducida y confirmada en el momento.

## Límites y advertencia

Es un prototipo local, no un sistema listo para producción ni una herramienta de asesoría o declaración fiscal. El cierre no habilita reapertura y es una implementación informativa básica, pendiente de revisión profesional. No se afirma cumplimiento contable, fiscal o legal en Venezuela ni en otra jurisdicción. Faltan borradores, usuarios/roles, backend/API de producción, sincronización, **importación/restauración**, hardening y revisión profesional. El exportador añadido no constituye una política de respaldo de producción: se requiere una revisión independiente de criptografía, manejo de frase/archivo, recuperación, retención y amenazas antes de cualquier uso real. Las frases perdidas no se pueden recuperar; guarda la frase aparte del archivo. La aplicación no sube ni comparte el archivo automáticamente; la ubicación la elige la persona.

GitHub Actions [run 38319034733](https://github.com/46Neon/Book-01/actions/runs/38319034733) pasó las pruebas unitarias (incluidas las pruebas del códec de exportación cifrada), compilación de Android-test, ensamblado debug y la suite instrumentada en emulador. No hay todavía una prueba instrumentada que complete el flujo SAF de exportación en la UI. La exportación queda verificada por compilación, pruebas del formato y CI; antes de cualquier uso real requiere prueba manual del flujo en dispositivo y revisión independiente de seguridad criptográfica y respaldo.

USD se conserva como centavos enteros y se maneja con aritmética exacta, sin coma flotante ni conversión de monedas. Use datos ficticios en desarrollo; no guarde libros reales ni secretos en el repositorio.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

La acción de GitHub genera un APK debug como artefacto cuando el job de compilación finaliza correctamente.
