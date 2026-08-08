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
    fun `catalog lists only strictly validated two digit submodels`() {
        val config = """
            {
              "modelId":"000027",
              "defaultSubModelId":"02",
              "resourceMap":{
                "00":{
                  "resourceIndex":1,
                  "resourceKey":"00",
                  "resourceDesc":"White product resources",
                  "resourceRemark":"000027_00.zip",
                  "resourceFileSizeKb":2617
                },
                "01":{
                  "resourceIndex":0,
                  "resourceKey":"01",
                  "resourceDesc":"Black product resources",
                  "resourceRemark":"000027_01.zip",
                  "resourceFileSizeKb":4018
                },
                "0a":{
                  "resourceDesc":"lowercase key",
                  "resourceRemark":"000027_0a.zip",
                  "resourceFileSizeKb":1
                },
                "00_audioAccessoryManagerTV":{
                  "resourceDesc":"TV resources",
                  "resourceRemark":"000027_00_audioAccessoryManagerTV.zip",
                  "resourceFileSizeKb":1
                },
                "00_audioAccessoryManagerWatch":{
                  "resourceDesc":"Watch resources",
                  "resourceRemark":"000027_00_audioAccessoryManagerWatch.zip",
                  "resourceFileSizeKb":1
                },
                "02":{
                  "resourceDesc":"invalid archive",
                  "resourceRemark":"https://example.com/other.zip",
                  "resourceFileSizeKb":1
                },
                "03":{
                  "resourceKey":"04",
                  "resourceDesc":"mismatched key",
                  "resourceRemark":"000027_03.zip",
                  "resourceFileSizeKb":1
                },
                "04":{
                  "resourceDesc":"oversized archive",
                  "resourceRemark":"000027_04.zip",
                  "resourceFileSizeKb":65537
                },
                "05":{
                  "resourceDesc":"__LONG_DESCRIPTION__",
                  "resourceRemark":"000027_05.zip",
                  "resourceFileSizeKb":1
                },
                "06":{
                  "resourceDesc":"has__CONTROL__character",
                  "resourceRemark":"000027_06.zip",
                  "resourceFileSizeKb":1
                }
              }
            }
        """.trimIndent()
            .replace("__LONG_DESCRIPTION__", "x".repeat(81))
            .replace("__CONTROL__", "\\u0001")
        val options = OfficialSmartAudioResource.listOptions(
            configBytes = config.toByteArray(),
            expectedModelId = "000027",
        )

        assertEquals(listOf("01", "00"), options.map { it.subModelId })
        assertEquals(
            listOf("Black product resources", "White product resources"),
            options.map { it.resourceDesc },
        )
    }

    @Test
    fun `catalog never synthesizes an option from default submodel id`() {
        val options = OfficialSmartAudioResource.listOptions(
            configBytes = """
                {
                  "modelId":"000027",
                  "defaultSubModelId":"7F",
                  "resourceMap":{
                    "00":{
                      "resourceDesc":"White product resources",
                      "resourceRemark":"000027_00.zip",
                      "resourceFileSizeKb":2617
                    }
                  }
                }
            """.trimIndent().toByteArray(),
            expectedModelId = "000027",
        )

        assertEquals(listOf("00"), options.map { it.subModelId })
    }

    @Test
    fun `catalog rejects a mismatched model instead of using default submodel`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialSmartAudioResource.listOptions(
                configBytes = """
                    {
                      "modelId":"000141",
                      "defaultSubModelId":"00",
                      "resourceMap":{}
                    }
                """.trimIndent().toByteArray(),
                expectedModelId = "000027",
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
