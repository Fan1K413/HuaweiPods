package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Test

class HuaweiBatteryParserTest {
    @Test
    fun `zero percent earbud is unavailable on HFP path`() {
        val result = HuaweiBatteryParser.parse(
            "+HUAWEIBATTERY:6,2,0,3,1,4,100,5,0,6,50,7,0",
        )

        assertEquals(false, result?.battery?.left?.isConnected)
        assertEquals(true, result?.battery?.right?.isConnected)
        assertEquals(true, result?.battery?.case?.isConnected)
    }

    @Test
    fun `zero percent right earbud is unavailable on HFP path`() {
        val result = HuaweiBatteryParser.parse(
            "+HUAWEIBATTERY:6,2,100,3,0,4,0,5,0,6,50,7,0",
        )

        assertEquals(true, result?.battery?.left?.isConnected)
        assertEquals(false, result?.battery?.right?.isConnected)
    }

    @Test
    fun `zero percent charging case remains available on HFP path`() {
        val result = HuaweiBatteryParser.parse(
            "+HUAWEIBATTERY:6,2,0,3,1,4,80,5,0,6,0,7,1",
        )

        assertEquals(false, result?.battery?.left?.isConnected)
        assertEquals(true, result?.battery?.left?.isCharging)
        assertEquals(true, result?.battery?.case?.isConnected)
    }
}
