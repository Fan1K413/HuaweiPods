package moe.chenxy.huaweipods.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import moe.chenxy.huaweipods.pods.HuaweiEqualizerCodec
import moe.chenxy.huaweipods.pods.SmartAudioFreeClip2BridgePolicy
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast

/** App-side one-shot client for the official EQ service already running in Smart Audio. */
internal object SmartAudioEqualizerClient {
    private const val RESULT_TIMEOUT_MS = 2_500L
    private val registered = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, PendingResult>()

    fun setCustom(
        context: Context,
        address: String,
        presetId: Int,
        name: String,
        gains: List<Int>,
        complete: (Boolean) -> Unit,
    ) {
        val appContext = context.applicationContext ?: context
        val normalizedName = name.trim()
        if (
            !BluetoothAdapter.checkBluetoothAddress(address) ||
            presetId !in 0x64..0x66 ||
            normalizedName.isEmpty() ||
            normalizedName.toByteArray(StandardCharsets.UTF_8).size > 32 ||
            gains.size != HuaweiEqualizerCodec.BAND_COUNT ||
            gains.any { it !in HuaweiEqualizerCodec.GAIN_RANGE }
        ) {
            complete(false)
            return
        }
        ensureReceiver(appContext)
        val nonce = UUID.randomUUID().toString()
        val timeout = Runnable { finish(nonce, false) }
        pending[nonce] = PendingResult(complete, timeout)
        mainHandler.postDelayed(timeout, RESULT_TIMEOUT_MS)
        val sent = runCatching {
            appContext.sendIdentitySharingBroadcast(
                Intent(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_SET).apply {
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_NONCE, nonce)
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ADDRESS, address)
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_PRESET_ID, presetId)
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_NAME, normalizedName)
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_GAINS, gains.toIntArray())
                    setPackage(SmartAudioFreeClip2BridgePolicy.SMART_AUDIO_PACKAGE)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
        }.isSuccess
        if (!sent) finish(nonce, false)
    }

    private fun ensureReceiver(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        runCatching {
            context.registerReceiver(
                resultReceiver,
                IntentFilter(HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_RESULT),
                Context.RECEIVER_EXPORTED,
            )
        }.onFailure {
            registered.set(false)
        }
    }

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!SmartAudioFreeClip2BridgePolicy.isTrustedResultSender(sentFromPackage)) return
            val received = intent ?: return
            val nonce = SmartAudioFreeClip2BridgePolicy.normalizeNonce(
                received.getStringExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_NONCE),
            ) ?: return
            finish(
                nonce,
                received.getBooleanExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ACCEPTED, false),
            )
        }
    }

    private fun finish(nonce: String, success: Boolean) {
        val result = pending.remove(nonce) ?: return
        mainHandler.removeCallbacks(result.timeout)
        mainHandler.post { result.callback(success) }
    }

    private data class PendingResult(
        val callback: (Boolean) -> Unit,
        val timeout: Runnable,
    )
}
