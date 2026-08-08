package moe.chenxy.huaweipods.debugcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAudioResourceLocatorTest {
    @Test
    fun `parses strict China config and current submodel archive urls`() {
        val config = SmartAudioResourceLocator.parseObservedUrl(
            "https://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000141/000141.json",
        )
        assertEquals(SmartAudioResourceOrigin.CHINA, config?.origin)
        assertEquals("000141", config?.modelId)
        assertNull(config?.subModelId)
        assertEquals(SmartAudioObservedResourceKind.CONFIG, config?.kind)

        val archive = SmartAudioResourceLocator.parseObservedUrl(
            "https://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000141/000141_01.zip",
        )
        assertEquals("01", archive?.subModelId)
        assertEquals(SmartAudioObservedResourceKind.ARCHIVE, archive?.kind)
    }

    @Test
    fun `accepts only exact known regional prefixes`() {
        assertEquals(
            SmartAudioResourceOrigin.ASIA_AFRICA_LATIN,
            SmartAudioResourceLocator.parseObservedUrl(
                "https://contentcenter-dra.dbankcdn.cn/pub_1/HW-SmartHome_oem_900_9/41/v3/aaa/device/guide/000153/000153_02.zip",
            )?.origin,
        )
        assertEquals(
            SmartAudioResourceOrigin.EUROPE,
            SmartAudioResourceLocator.parseObservedUrl(
                "https://contentcenter-dre.dbankcdn.cn/pub_1/HW-SmartHome_oem_900_9/b0/v3/eu/device/guide/000153/000153.json",
            )?.origin,
        )
        assertNull(
            SmartAudioResourceLocator.parseObservedUrl(
                "https://contentcenter-dre.dbankcdn.cn/device/guide/AAM001/000153/000153.json",
            ),
        )
    }

    @Test
    fun `rejects auth query redirect-like paths and common resources`() {
        val invalid = listOf(
            "http://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000141/000141.json",
            "https://user@smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000141/000141.json",
            "https://smarthome-drcn.dbankcdn.cn:443/device/guide/AAM001/000141/000141.json",
            "https://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000141/000141.json?token=secret",
            "https://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000141/../000142/000142.json",
            "https://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/000141/000141_../../x.zip",
            "https://smarthome-drcn.dbankcdn.cn/device/guide/AAM001/00000A/00000A_00.zip",
            "https://example.com/device/guide/AAM001/000141/000141_01.zip",
        )
        invalid.forEach { assertNull(it, SmartAudioResourceLocator.parseObservedUrl(it)) }
    }

    @Test
    fun `selects only an explicitly observed submodel`() {
        val json = """
            {
              "modelId":"000141",
              "defaultSubModelId":"01",
              "resourceMap":{
                "01":{"resourceRemark":"000141_01.zip","resourceFileSizeKb":13972},
                "10":{"resourceRemark":"000141_10.zip","resourceFileSizeKb":15948}
              }
            }
        """.trimIndent().toByteArray()

        val observed = SmartAudioProductConfig.selectResource(json, "000141", "10")
        assertEquals("10", observed.subModelId)

        val missingSubmodel = runCatching {
            SmartAudioProductConfig.selectResource(json, "000141", null)
        }
        assertTrue(missingSubmodel.isFailure)
        assertTrue(missingSubmodel.exceptionOrNull()?.message.orEmpty().contains("不能自动选择"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects product config archive path injection`() {
        SmartAudioProductConfig.selectResource(
            """
                {
                  "modelId":"000141",
                  "defaultSubModelId":"01",
                  "resourceMap":{
                    "01":{"resourceRemark":"../000141_01.zip","resourceFileSizeKb":13972}
                  }
                }
            """.trimIndent().toByteArray(),
            "000141",
            "01",
        )
    }

    @Test
    fun `candidate policy merges route but rejects identity conflicts`() {
        val identity = SmartAudioResourceCandidate(
            origin = SmartAudioResourceOrigin.CHINA,
            modelId = "000153",
            subModelId = "02",
            observedAtEpochMs = 100L,
        )
        val regionalConfig = SmartAudioResourceCandidate(
            origin = SmartAudioResourceOrigin.EUROPE,
            modelId = "000153",
            subModelId = null,
            observedAtEpochMs = 200L,
        )
        val merged = SmartAudioResourceCandidatePolicy.mergeRouteWithKnownIdentity(
            identity,
            regionalConfig,
        )
        assertEquals(SmartAudioResourceOrigin.EUROPE, merged.origin)
        assertEquals("02", merged.subModelId)
        assertFalse(SmartAudioResourceCandidatePolicy.conflicts(identity, regionalConfig))
        assertTrue(
            SmartAudioResourceCandidatePolicy.conflicts(
                identity,
                identity.copy(subModelId = "03", observedAtEpochMs = 300L),
            ),
        )
        assertTrue(
            SmartAudioResourceCandidatePolicy.conflicts(
                identity,
                identity.copy(modelId = "000141", observedAtEpochMs = 300L),
            ),
        )
    }

    @Test
    fun `validates archive size against official config`() {
        assertTrue(SmartAudioProductConfig.isArchiveSizePlausible(14_306_897L, 13_972))
        assertFalse(SmartAudioProductConfig.isArchiveSizePlausible(1_024L, 13_972))
    }
}
