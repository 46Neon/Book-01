package com.neon.book01

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Regression guard: sales and purchases are the first, separate auxiliary books. */
@RunWith(AndroidJUnit4::class)
class AuxiliaryBooksNavigationUiTest {
    @Test
    fun salesAndPurchasesOpenTheirOwnBooksAndInvoiceActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            assertVisible(device, "Libro auxiliar · Ventas")
            assertVisible(device, "Nueva factura de venta")
            assertVisible(device, "Resumen del período · 0 facturas")
            assertVisible(device, "Saldos por cliente")
            assertVisible(device, "Orden: mayor saldo vencido")
            assertVisible(device, "Sin saldos pendientes.")
            assertVisible(device, "Vencidas")
            assertVisible(device, "Pagadas")
            device.wait(Until.findObject(By.text("Compras")), TIMEOUT_MS)
                ?.click() ?: throw AssertionError("Compras book tab was not visible")
            assertVisible(device, "Libro auxiliar · Compras")
            assertVisible(device, "Nueva factura de compra")
            assertVisible(device, "Saldos por proveedor")
            assertVisible(device, "Orden: mayor saldo vencido")
            assertVisible(device, "Sin saldos pendientes.")
            device.wait(Until.findObject(By.text("Ventas")), TIMEOUT_MS)
                ?.click() ?: throw AssertionError("Ventas book tab was not visible")
            assertVisible(device, "Libro auxiliar · Ventas")
        } finally {
            scenario.close()
        }
    }

    private fun assertVisible(device: UiDevice, text: String) {
        assertTrue("Expected book content '$text'", device.wait(Until.hasObject(By.textContains(text)), TIMEOUT_MS))
    }

    private companion object { const val TIMEOUT_MS = 10_000L }
}

