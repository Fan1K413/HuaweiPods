package moe.chenxy.huaweipods.pods

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HuaweiDeviceRouteResolverTest {
    @Test
    fun `automatic recognition never writes injected read only preferences`() {
        val editCalled = AtomicBoolean(false)
        val readOnlyPrefs = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getString" -> null
                "edit" -> {
                    editCalled.set(true)
                    throw UnsupportedOperationException("Read only implementation")
                }
                "toString" -> "ReadOnlySharedPreferences"
                "hashCode" -> 0
                "equals" -> false
                else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        } as SharedPreferences

        HuaweiDeviceRouteResolver.init(readOnlyPrefs)

        assertEquals(
            HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
            HuaweiDeviceRouteResolver.resolve(
                address = "AA:BB:CC:DD:EE:FF",
                deviceName = "HUAWEI FreeBuds 3",
            ),
        )
        assertFalse(editCalled.get())
    }
}
