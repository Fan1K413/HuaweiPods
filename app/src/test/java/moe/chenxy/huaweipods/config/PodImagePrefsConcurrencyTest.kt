package moe.chenxy.huaweipods.config

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodImagePrefsConcurrencyTest {
    @Test
    fun `concurrent connected-device updates do not lose entries`() {
        val prefs = InMemoryPreferences().preferences
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(40)
        repeat(40) { index ->
            executor.execute {
                start.await()
                PodImagePrefs.upsertConnected(
                    prefs = prefs,
                    service = null,
                    address = String.format(Locale.US, "AA:BB:CC:DD:%02X:%02X", index / 16, index % 16),
                    name = "Headset $index",
                )
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertEquals(40, PodImagePrefs.load(prefs).size)
    }

    @Test
    fun `cloud and connection mutations preserve each others fields`() {
        val prefs = InMemoryPreferences().preferences
        val address = "AA:BB:CC:DD:EE:FF"
        PodImagePrefs.upsertConnected(prefs, null, address, "FreeBuds 3")
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        executor.execute {
            start.await()
            PodImagePrefs.upsertConnected(prefs, null, address, "My FreeBuds 3")
            done.countDown()
        }
        executor.execute {
            start.await()
            PodImagePrefs.saveCloudImages(
                prefs = prefs,
                address = address,
                modelId = "000027",
                subModelId = "00",
                imagePaths = mapOf(PodImageResource.BOX to "/images/official.png"),
            )
            done.countDown()
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()

        val saved = requireNotNull(PodImagePrefs.find(prefs, address))
        assertEquals("My FreeBuds 3", saved.name)
        assertEquals("000027", saved.cloudModelId)
        assertEquals("00", saved.cloudSubModelId)
        assertEquals("/images/official.png", saved.cloudBoxImagePath)
    }

    @Test
    fun `cold service bind copies the latest local snapshot to remote preferences`() {
        val local = InMemoryPreferences().preferences
        val remote = InMemoryPreferences().preferences
        PodImagePrefs.upsertConnected(local, null, "AA:BB:CC:DD:EE:FF", "FreeBuds 3")
        PodImagePrefs.saveCloudImages(
            prefs = local,
            address = "AA:BB:CC:DD:EE:FF",
            modelId = "000027",
            subModelId = "00",
            imagePaths = mapOf(PodImageResource.BOX to "/images/cloud.png"),
        )

        PodImagePrefs.syncSnapshotToRemote(local, remote)

        val copied = requireNotNull(PodImagePrefs.find(remote, "AA:BB:CC:DD:EE:FF"))
        assertEquals("FreeBuds 3", copied.name)
        assertEquals("/images/cloud.png", copied.cloudBoxImagePath)
    }

    @Test
    fun `old identity finishing after new identity cannot overwrite cloud images`() {
        val prefs = InMemoryPreferences().preferences
        val address = "AA:BB:CC:DD:EE:FF"
        PodImagePrefs.recordLatestCloudIdentity(prefs, address, "000027", "00")
        PodImagePrefs.recordLatestCloudIdentity(prefs, address, "000027", "01")

        assertTrue(
            PodImagePrefs.saveCloudImagesIfLatest(
                prefs = prefs,
                address = address,
                modelId = "000027",
                subModelId = "01",
                imagePaths = mapOf(PodImageResource.BOX to "/images/new.png"),
            ),
        )
        assertFalse(
            PodImagePrefs.saveCloudImagesIfLatest(
                prefs = prefs,
                address = address,
                modelId = "000027",
                subModelId = "00",
                imagePaths = mapOf(PodImageResource.BOX to "/images/old.png"),
            ),
        )

        val saved = requireNotNull(PodImagePrefs.find(prefs, address))
        assertEquals("01", saved.cloudSubModelId)
        assertEquals("/images/new.png", saved.cloudBoxImagePath)
    }

    private class InMemoryPreferences {
        private val raw = Collections.synchronizedMap(mutableMapOf<String, String>())

        val preferences: SharedPreferences = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> {
                    Thread.sleep(1)
                    raw[args[0] as String] ?: args.getOrNull(1)
                }
                "edit" -> editor()
                "toString" -> "InMemoryPreferences"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, String?>()
            lateinit var editor: SharedPreferences.Editor
            editor = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "putString" -> {
                        pending[args[0] as String] = args.getOrNull(1) as? String
                        editor
                    }
                    "apply" -> {
                        Thread.sleep(1)
                        applyPending(pending)
                        null
                    }
                    "commit" -> {
                        applyPending(pending)
                        true
                    }
                    "toString" -> "InMemoryEditor"
                    "hashCode" -> System.identityHashCode(editor)
                    "equals" -> false
                    else -> throw UnsupportedOperationException("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
            return editor
        }

        private fun applyPending(pending: Map<String, String?>) {
            synchronized(raw) {
                pending.forEach { (key, value) ->
                    if (value == null) raw.remove(key) else raw[key] = value
                }
            }
        }
    }
}
