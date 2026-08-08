package moe.chenxy.huaweipods.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeBuds3BatteryAtPolicyTest {
    @Test
    fun `recognizes FreeBuds 3 battery capability query`() {
        listOf(
            "+HUAWEIBATTERY=?",
            "AT+HUAWEIBATTERY=?",
            "+huaweibattery:?",
        ).forEach { command ->
            assertEquals(
                FreeBuds3BatteryAtCommand.CAPABILITY_QUERY,
                classifyFreeBuds3BatteryAtCommand(command),
            )
        }
    }

    @Test
    fun `recognizes initial and update battery reports`() {
        listOf(
            "+HUAWEIBATTERY=6,2,100,3,1,4,100,5,1,6,55,7,0",
            "AT+UPDATEHUAWEIBATTERY=6,2,99,3,0,4,98,5,0,6,55,7,0",
        ).forEach { command ->
            assertEquals(
                FreeBuds3BatteryAtCommand.BATTERY_REPORT,
                classifyFreeBuds3BatteryAtCommand(command),
            )
        }
    }

    @Test
    fun `acknowledges close without treating it as battery data`() {
        assertEquals(
            FreeBuds3BatteryAtCommand.CLOSE,
            classifyFreeBuds3BatteryAtCommand("AT+CLOSEHUAWEIBATTERY=2"),
        )
    }

    @Test
    fun `rejects malformed and unrelated commands`() {
        listOf(
            "+HUAWEIBATTERY=6,2,100,3",
            "+HUAWEIBATTERY=OK",
            "+CLOSEHUAWEIBATTERY=unknown",
            "+ANDROID=?",
        ).forEach { command ->
            assertEquals(
                FreeBuds3BatteryAtCommand.OTHER,
                classifyFreeBuds3BatteryAtCommand(command),
            )
        }
    }
}
