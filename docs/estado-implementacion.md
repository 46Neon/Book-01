# Estado de implementación — Parte 1

## Hecho

- Repositorio privado y documentación de alcance, arquitectura, guía visual y contrato de integración.
- Primera pantalla Android Kotlin/Compose con estilo tipo hoja de cálculo.
- Base Room local versionada con cuentas, asientos y líneas contables.
- Catálogo inicial de demostración: Caja, Banco, Cuentas por pagar, Capital, Ingresos, Papelería, Servicios y Otros gastos.
- Hoja Plan de cuentas en formato de cuadrícula y alta de nuevas cuentas por código, nombre, tipo y uso opcional como Caja/Banco; no se permiten borrados ni cambios de código/tipo que alteren el histórico.
- Formulario local para ingreso o gasto simple: descripción, importe, fecha, categoría y cuenta de efectivo/banco.
- La capa de dominio genera las dos líneas contables, valida el tipo de cuenta y comprueba débito = crédito.
- La escritura del asiento, sus líneas y el evento inicial de auditoría se realiza en una transacción local.
- Ingresos y gastos resumidos desde líneas contables publicadas, de modo que una reversa reduce correctamente el saldo resumido; las otras monedas no se mezclan.
- Reversas seguras: crean un asiento contabilizado nuevo con las líneas del original invertidas exactamente, fecha/motivo obligatorios y vínculo a su original; primero se comprueba el equilibrio por moneda. La reversa, sus líneas y dos eventos de historial se guardan en una transacción Room. Se rechazan asientos no contabilizados, no balanceados, reversas y originales ya reversados; el original no se modifica. La base impone como máximo una reversa por original.
- Sección Trazabilidad accesible desde «Más» para revisar eventos de contabilización/reversa y abrir el flujo de reversa. No se ofrecen controles de edición o borrado de asientos contabilizados. Mayor y Balance reciben las líneas de reversa como asientos contabilizados normales.
- Los registros preexistentes migrados permanecen intactos, pero sin eventos retroactivos: la auditoría empieza con las nuevas contabilizaciones.
- Pruebas agregadas para inversión exacta de líneas, validación de elegibilidad y solicitud, conservación del original, duplicados/reversas de reversas y migración v1→v4. CI en el commit de reversas confirmó las pruebas unitarias, la compilación de Android-test y el APK debug. El runtime de las pruebas instrumentadas sigue sin verificarse: la ejecución manual falló antes de iniciar las pruebas porque el emulador no arrancó.
- Reglas contables y de altas de cuentas separadas en el dominio, con pruebas unitarias para asientos, códigos/tipos, balance, Mayor y precisión/formato USD.
- Moneda funcional del libro persistida como USD en Room, con migraciones aditivas 1→2→3→4 que conservan los asientos y cuentas existentes.
- Importes USD representados como centavos enteros (`Long`), analizados con `BigDecimal` exacto y sin coma flotante binaria; salida rotulada `US$` con dos decimales. Admite formas inequívocas estadounidense y española; rechaza más de dos decimales y entradas ambiguas en vez de adivinarlas.
- Migraciones Room 1→2→3→4 agregan configuración USD, indicador Caja/Banco, enlace de reversa e historial append-only sin borrar asientos ni líneas; las cuentas 1000/1010 existentes quedan marcadas para cobros/pagos. La prueba instrumentada está escrita para migrar una base v1 hasta v4 y verificar registros preservados, columnas y tabla de auditoría; su ejecución sigue pendiente, ya que la corrida manual no consiguió arrancar el emulador.
- La tabla muestra cada línea del asiento y calcula saldo neto, ingresos/gastos y totales desde los registros locales.
- Balance de comprobación local por cuenta: débitos/créditos exactos, naturaleza de saldo D/H y comprobación de que el total cuadra; rechaza mezclar monedas en el reporte USD.
- Mayor por cuenta con filas cronológicas, débitos/créditos, saldo corrido D/H y totales exactos; incluye saldo inicial anterior a «Desde», excluye borradores y separa otras monedas sin conversión.
- Filtros opcionales inclusivos Desde/Hasta para el Mayor y el Balance de comprobación, en formato estricto AAAA-MM-DD. El saldo inicial del Mayor incorpora los movimientos anteriores a «Desde»; los totales débito/crédito solo incluyen las filas del periodo.
- GitHub Actions ejecutó correctamente las pruebas unitarias, compiló Android-test y generó el APK de depuración en el commit de reversas; la ejecución instrumentada de migración sigue sin confirmarse porque el emulador no arrancó en la corrida manual.

## Limitaciones conocidas

- Moneda del prototipo fija en USD, dos decimales; sin conversión de cambio ni soporte multi-moneda.
- El plan permite agregar cuentas nuevas, pero aún no editar nombres/tipos ni desactivar cuentas; la plantilla sigue siendo genérica y no configurable por empresa.
- Los asientos sencillos se marcan contabilizados al guardar; aún faltan borradores, aprobación y controles de cierre de periodo. La reversa local y auditoría básica ya están implementadas; no hay actor/usuario autenticado, sincronización ni registro retroactivo para asientos previos a v4.
- Aún sin API/backend, usuarios, roles, sincronización, adjuntos, importación/exportación o restauración.
- El Mayor y el Balance de comprobación permiten seleccionar un rango de fechas local en USD; faltan controles de cierre de periodos y estados financieros completos.
- No se declara cumplimiento contable/fiscal local.

## Siguiente hito

Siguiente: repetir la migración y pruebas instrumentadas en un emulador funcional; luego controles de cierre/periodos y estados financieros más completos. Después siguen API/backend, sincronización y respaldo dentro de Parte 1. El chat conversacional continúa fuera de mi alcance; cualquier futuro soporte multi-moneda debe diseñarse expresamente antes de implementarse.
