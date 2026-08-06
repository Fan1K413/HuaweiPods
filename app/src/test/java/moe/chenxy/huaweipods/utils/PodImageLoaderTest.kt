package moe.chenxy.huaweipods.utils

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.config.PodImageResource
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodImageLoaderTest {
    @Test
    fun `image preference is read only from the requested address`() {
        val prefs = sharedPreferencesWithEarphones(
            """
            [
              {
                "address": "AA:BB:CC:DD:EE:01",
                "name": "HUAWEI FreeBuds 6i",
                "boxImagePath": "/images/current_box.img",
                "lastConnectedAt": 1
              },
              {
                "address": "AA:BB:CC:DD:EE:02",
                "name": "HUAWEI FreeClip 2",
                "boxImagePath": "/images/latest_box.img",
                "lastConnectedAt": 999
              }
            ]
            """.trimIndent(),
        )

        assertEquals(
            "AA:BB:CC:DD:EE:01",
            PodImageLoader.currentImagePreference(prefs, "aa:bb:cc:dd:ee:01")?.address,
        )
        assertNull(PodImageLoader.currentImagePreference(prefs, "AA:BB:CC:DD:EE:03"))
    }

    @Test
    fun `FreeBuds 6i uses its dedicated fallback images`() {
        assertEquals(
            R.drawable.img_freebuds6i_box,
            PodImageLoader.modelFallbackResId(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                PodImageResource.BOX,
                R.drawable.img_box,
            ),
        )
        assertEquals(
            R.drawable.img_freebuds6i_left,
            PodImageLoader.modelFallbackResId(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                PodImageResource.LEFT,
                R.drawable.img_left,
            ),
        )
        assertEquals(
            R.drawable.img_freebuds6i_right,
            PodImageLoader.modelFallbackResId(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS6I,
                PodImageResource.RIGHT,
                R.drawable.img_right,
            ),
        )
    }

    @Test
    fun `FreeClip 2 uses its dedicated fallback images`() {
        assertEquals(
            R.drawable.img_freeclip2_box,
            PodImageLoader.modelFallbackResId(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                PodImageResource.BOX,
                R.drawable.img_box,
            ),
        )
        assertEquals(
            R.drawable.img_freeclip2_left,
            PodImageLoader.modelFallbackResId(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                PodImageResource.LEFT,
                R.drawable.img_left,
            ),
        )
        assertEquals(
            R.drawable.img_freeclip2_right,
            PodImageLoader.modelFallbackResId(
                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                PodImageResource.RIGHT,
                R.drawable.img_right,
            ),
        )
    }

    @Test
    fun `models without dedicated images keep the global fallback`() {
        val globalFallback = R.drawable.img_box

        assertEquals(
            globalFallback,
            PodImageLoader.modelFallbackResId(
                HuaweiDeviceRoute.HUAWEI_FREEBUDS3,
                PodImageResource.BOX,
                globalFallback,
            ),
        )
    }

    private fun sharedPreferencesWithEarphones(json: String): SharedPreferences {
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getString" -> json
                "toString" -> "PodImageLoaderTestPreferences"
                "hashCode" -> 0
                "equals" -> false
                else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        } as SharedPreferences
    }
}
