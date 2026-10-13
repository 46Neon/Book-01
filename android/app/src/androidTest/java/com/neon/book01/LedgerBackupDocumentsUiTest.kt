package com.neon.book01

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the Android system document picker UI; this complements, but does not replace, physical-device SAF testing. */
@RunWith(AndroidJUnit4::class)
class LedgerBackupDocumentsUiTest {
    @Test
    fun restoreOpensSystemDocumentsPickerAndCancelReturnsWithoutRequestingPassphrase() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val appPackage = instrumentation.targetContext.packageName
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            assertTrue("Book-01 did not reach the foreground", device.wait(Until.hasObject(By.pkg(appPackage)), TIMEOUT_MS))
            device.wait(Until.findObject(By.text("Más")), TIMEOUT_MS)
                ?.click() ?: throw AssertionError("The Más tab was not visible")
            device.wait(Until.findObject(By.text("Restaurar copia cifrada")), TIMEOUT_MS)
                ?.click() ?: throw AssertionError("The restore action was not visible")

            val pickerOpened = device.wait(Until.hasObject(By.pkg("com.android.documentsui")), TIMEOUT_MS) ||
                device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), TIMEOUT_MS)
            assertTrue("ACTION_OPEN_DOCUMENT did not display the system DocumentsUI picker", pickerOpened)

            device.pressBack()
            assertTrue(
                "Cancelling the picker did not return to the restore action",
                device.wait(Until.hasObject(By.text("Restaurar copia cifrada")), TIMEOUT_MS),
            )
            assertEquals(appPackage, device.getCurrentPackageName())
            assertFalse(
                "Cancelling document selection must not request a backup passphrase",
                device.wait(Until.hasObject(By.text("Frase de cifrado")), 1_000),
            )
        } finally {
            val foregroundPackage = device.getCurrentPackageName().orEmpty()
            if (foregroundPackage.contains("documentsui", ignoreCase = true)) device.pressBack()
            scenario.close()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
