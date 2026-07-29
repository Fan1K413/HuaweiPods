package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HuaweiRfcommResponseParserTest {
    @Test
    fun `parses FreeBuds 5 battery response captured from official app`() {
        val response = hex("5A0014000127010164020364645603030000000402140A075C")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(86, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `finds battery frame inside a combined RFCOMM read`() {
        val prefix = hex("5A0006000A0E010100A26F")
        val batteryFrame = hex("5A0014000127010164020364645603030000000402140A075C")

        val battery = HuaweiRfcommResponseParser.parseBattery(prefix + batteryFrame)
        assertNotNull(battery)

        assertEquals(86, battery?.case?.battery)
    }

    @Test
    fun `parses FreeClip battery response captured from official app`() {
        val response = hex("5A0018000108010164020364642603030000000402140A05020101D504")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(38, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `parses FreeBuds 6i battery response captured from official app`() {
        val response = hex("5A001B000108010164020364646403030000000402140A0502010106010A5607")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(100, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `parses FreeBuds Pro 4 battery response captured from official app`() {
        val response = hex("5A001B00010801015C02035C5C4A030300000004020A140502000006010A6F4E")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(92, battery?.left?.battery)
        assertEquals(92, battery?.right?.battery)
        assertEquals(74, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `parses FreeBuds 7i battery response captured from official app`() {
        val response = hex("5A001B000108010164020364641D03030000000402140A0502000106010A31A3")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(100, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(29, battery?.case?.battery)
        assertEquals(false, battery?.left?.isCharging)
        assertEquals(false, battery?.right?.isCharging)
        assertEquals(false, battery?.case?.isCharging)
    }

    @Test
    fun `parses Eyewear temple batteries without exposing the placeholder case`() {
        val response = hex("5A0014000108010144020346440003030000000402140A392E")

        val battery = HuaweiRfcommResponseParser.parseBattery(response, includeCase = false)
        assertNotNull(battery)

        assertEquals(70, battery?.left?.battery)
        assertEquals(68, battery?.right?.battery)
        assertEquals(null, battery?.case)
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
