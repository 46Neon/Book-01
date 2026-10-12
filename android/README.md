# Android app

Módulo Android de Book-01, construido con Kotlin + Jetpack Compose. La primera pantalla imita la organización familiar de una hoja de cálculo: pestañas, cuadrícula, encabezados, columnas de importes, totales y filtros visuales.

## Estado

La pantalla actual es un prototipo navegable con datos ficticios. El botón de nuevo asiento y los filtros todavía son demostrativos. Room, dominio contable, sincronización, formularios reales y generación APK quedan para las siguientes iteraciones.

## Ejecutar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:assembleDebug
```

El APK de depuración, cuando la compilación esté configurada y validada, se genera bajo `android/app/build/outputs/apk/debug/`.
