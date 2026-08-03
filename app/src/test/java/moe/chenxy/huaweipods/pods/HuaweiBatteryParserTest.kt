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
}
