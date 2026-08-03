package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HuaweiRfcommResponseParserTest {
    @Test
    fun `parses FreeBuds Pro 3 ANC main mode from captured state responses`() {
        assertEquals(1, HuaweiRfcommResponseParser.parseAncStatus(hex("5A0007002B2A010200001531")))
        assertEquals(2, HuaweiRfcommResponseParser.parseAncStatus(hex("5A0007002B2A010203015043")))
        assertEquals(1, HuaweiRfcommResponseParser.parseAncStatus(hex("5A0007002B2A010202025311")))
    }

    @Test
    fun `uses the latest valid ANC state frame from a combined RFCOMM read`() {
        val off = hex("5A0007002B2A010200001531")
        val unrelated = hex("5A0006002B040201003171")
        val noiseCancellation = hex("5A0007002B2A010203015043")

        assertEquals(2, HuaweiRfcommResponseParser.parseAncStatus(off + unrelated + noiseCancellation))
    }

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
    fun `uses the latest battery frame from a combined RFCOMM read`() {
        val older = hex("5A001B000127010164020364644C03030000000402140A0502010006010A69E6")
        val newer = hex("5A001B000108010156020300563503030000000402140A0502000006010AEEEC")

        val battery = HuaweiRfcommResponseParser.parseBattery(older + newer)

        assertEquals(0, battery?.left?.battery)
        assertEquals(86, battery?.right?.battery)
        assertEquals(53, battery?.case?.battery)
    }

    @Test
    fun `does not hide nonzero earbuds using unverified in-case side order`() {
        val leftInCase = HuaweiRfcommResponseParser.parseBattery(
            hex("5A001B000127010164020364644C03030000000402140A0502010006010A69E6"),
        )
        val rightInCase = HuaweiRfcommResponseParser.parseBattery(
            hex("5A001B000127010164020364644C03030000000402140A0502000106010AB503"),
        )

        assertEquals(true, leftInCase?.left?.isConnected)
        assertEquals(true, leftInCase?.right?.isConnected)
        assertEquals(true, rightInCase?.left?.isConnected)
        assertEquals(true, rightInCase?.right?.isConnected)
    }

    @Test
    fun `marks a zero percent earbud unavailable`() {
        val response = hex("5A001B000108010156020300563503030000000402140A0502000006010AEEEC")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)

        assertEquals(0, battery?.left?.battery)
        assertEquals(true, battery?.right?.isConnected)
        assertEquals(false, battery?.left?.isConnected)
        assertEquals(true, battery?.case?.isConnected)
    }

    @Test
    fun `keeps both earbuds visible for equal or missing in-case states`() {
        val bothInCase = HuaweiRfcommResponseParser.parseBattery(
            hex("5A001B000108010164020364644C03030000000402140A0502010106010AED15"),
        )
        val bothOutOfCase = HuaweiRfcommResponseParser.parseBattery(
            hex("5A001B000108010156020357563503030000000402140A0502000006010A25DA"),
        )
        val response = hex("5A0014000127010164020364645603030000000402140A075C")
        val withoutState = HuaweiRfcommResponseParser.parseBattery(response)

        assertEquals(true, bothInCase?.left?.isConnected)
        assertEquals(true, bothInCase?.right?.isConnected)
        assertEquals(true, bothOutOfCase?.left?.isConnected)
        assertEquals(true, bothOutOfCase?.right?.isConnected)
        assertEquals(true, withoutState?.left?.isConnected)
        assertEquals(true, withoutState?.right?.isConnected)
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
    fun `parses FreeBuds Pro 3 battery response captured from official app`() {
        val response = hex("5A00180001080101600203606413030301010004020A140502010126EC")

        val battery = HuaweiRfcommResponseParser.parseBattery(response)
        assertNotNull(battery)

        assertEquals(96, battery?.left?.battery)
        assertEquals(100, battery?.right?.battery)
        assertEquals(19, battery?.case?.battery)
        assertEquals(true, battery?.left?.isCharging)
        assertEquals(true, battery?.right?.isCharging)
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
