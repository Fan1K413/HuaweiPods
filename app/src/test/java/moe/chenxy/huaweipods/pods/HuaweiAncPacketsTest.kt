package moe.chenxy.huaweipods.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiAncPacketsTest {
    @Test
    fun `FreeBuds 3 mode packets remain unchanged`() {
        assertArrayEquals(
            hex("5A0006002B040101006821"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS3, false),
        )
        assertArrayEquals(
            hex("5A0006002B040101017800"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS3, true),
        )
    }

    @Test
    fun `FreeBuds 5 mode packets match verified capture`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS5, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS5, true),
        )
    }

    @Test
    fun `FreeBuds 6i basic mode packets match verified capture`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, true),
        )
    }

    @Test
    fun `FreeBuds Pro 3 uses captured ANC on and protocol family off packets`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, true),
        )
    }

    @Test
    fun `FreeBuds Pro 4 basic mode packets match verified capture`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4, true),
        )
    }

    @Test
    fun `FreeBuds 7i basic mode packets match verified capture`() {
        assertArrayEquals(
            hex("5A0007002B0401020000D22D"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I, false),
        )
        assertArrayEquals(
            hex("5A0007002B04010201FFFFEC"),
            HuaweiAncPackets.enabled(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I, true),
        )
    }

    @Test
    fun `unverified FreeBuds 5 level command is unavailable`() {
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS5, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I, 0))
        assertNull(HuaweiAncPackets.level(HuaweiDeviceRoute.HUAWEI_EYEWEAR, 0))
    }

    @Test
    fun `newer Huawei models use the verified battery query`() {
        val query = hex("5A0009000108010002000300FBB9")

        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS5))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS6I))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO3))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS_PRO4))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS7I))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREECLIP))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREECLIP2))
        assertArrayEquals(query, HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_EYEWEAR))
        assertNull(HuaweiAncPackets.batteryQuery(HuaweiDeviceRoute.HUAWEI_FREEBUDS3))
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
