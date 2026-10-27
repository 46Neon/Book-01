package com.neon.book01

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the implemented book navigation without treating planned sheets as available features. */
@RunWith(AndroidJUnit4::class)
class ExistingBookNavigationUiTest {
    @Test
    fun bottomTabsOpenTheImplementedBookScreens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val appPackage = instrumentation.targetContext.packageName
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            assertTrue("The book did not reach the foreground", device.wait(Until.hasObject(By.pkg(appPackage)), TIMEOUT_MS))
            assertVisible(device, "Movimientos del libro")

            device.wait(Until.findObject(By.text("Mayor")), TIMEOUT_MS)
                ?.click() ?: throw AssertionError("The Mayor tab was not visible")
            assertVisible(device, "Movimientos y saldo corrido")

            device.wait(Until.findObject(By.text("Cuentas")), TIMEOUT_MS)
                ?.click() ?: throw AssertionError("The Cuentas tab was not visible")
            assertVisible(device, "Código, clasificación y uso en el libro")

            device.wait(Until.findObject(By.text("Balance")), TIMEOUT_MS)
                ?.click() ?: throw AssertionError("The Balance tab was not visible")
            assertVisible(device, "Balance de comprobación")

            device.wait(Until.findObject(By.text("Más")), TIMEOUT_MS)
                ?.click() ?: throw AssertionError("The Más tab was not visible")
            assertVisible(device, "Configuración del libro")
        } finally {
            scenario.close()
        }
    }

    private fun assertVisible(device: UiDevice, text: String) {
        assertTrue("Expected implemented screen content '$text'", device.wait(Until.hasObject(By.textContains(text)), TIMEOUT_MS))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
