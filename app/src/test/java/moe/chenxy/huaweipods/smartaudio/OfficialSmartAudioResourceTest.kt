package moe.chenxy.huaweipods.smartaudio

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSmartAudioResourceTest {
    private val identity = SmartAudioResourceIdentity(
        address = "AA:BB:CC:DD:EE:FF",
        modelId = "000027",
        subModelId = "00",
    )

    @Test
    fun `selects only the confirmed submodel`() {
        val selection = OfficialSmartAudioResource.selectResource(
            configBytes = """
                {
                  "modelId":"000027",
                  "defaultSubModelId":"01",
                  "resourceMap":{
                    "00":{"resourceRemark":"000027_00.zip","resourceFileSizeKb":2617},
                    "01":{"resourceRemark":"000027_01.zip","resourceFileSizeKb":4018}
                  }
                }
            """.trimIndent().toByteArray(),
            identity = identity,
        )

        assertEquals("00", selection.subModelId)
        assertEquals("000027_00.zip", selection.archiveFileName)
        assertEquals(
            "https://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000027/000027_00.zip",
            OfficialSmartAudioResource.archiveUri(selection).toString(),
        )
    }

    @Test
    fun `never falls back to the config default color`() {
        assertThrows(IllegalStateException::class.java) {
            OfficialSmartAudioResource.selectResource(
                configBytes = """
                    {
                      "modelId":"000027",
                      "defaultSubModelId":"01",
                      "resourceMap":{
                        "01":{"resourceRemark":"000027_01.zip","resourceFileSizeKb":4018}
                      }
                    }
                """.trimIndent().toByteArray(),
                identity = identity,
            )
        }
    }

    @Test
    fun `allows only the fixed official host and path`() {
        assertTrue(OfficialSmartAudioResource.isAllowedUri(OfficialSmartAudioResource.configUri("000027")))
        assertFalse(
            OfficialSmartAudioResource.isAllowedUri(
                URI("https://example.com/device/guide/AAM001/000027/000027.json"),
            ),
        )
        assertFalse(
            OfficialSmartAudioResource.isAllowedUri(
                URI("https://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000027/000027.json?q=1"),
            ),
        )
    }
}
