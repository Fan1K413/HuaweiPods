package moe.chenxy.huaweipods.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.pods.HuaweiEqualizerCodec
import moe.chenxy.huaweipods.pods.SmartAudioFreeClip2BridgePolicy
import moe.chenxy.huaweipods.pods.encodeHuaweiDeviceRouteForBroadcast
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast

/** App-side one-shot client for the official EQ service already running in Smart Audio. */
internal object SmartAudioEqualizerClient {
    private const val RESULT_TIMEOUT_MS = 2_500L
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
        val nonce = UUID.randomUUID().toString()
        val timeout = Runnable { finish(nonce, false) }
        val resultReceiver = object : ResultReceiver(mainHandler) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                finish(nonce, resultCode == 1)
            }
        }
        pending[nonce] = PendingResult(complete, timeout)
        mainHandler.postDelayed(timeout, RESULT_TIMEOUT_MS)
        val relayThroughBluetooth = appContext.packageName ==
            SmartAudioFreeClip2BridgePolicy.SETTINGS_PACKAGE
        val sent = runCatching {
            appContext.sendIdentitySharingBroadcast(
                Intent(
                    if (relayThroughBluetooth) {
                        HuaweiPodsAction.ACTION_FREECLIP2_AUDIO_SET
                    } else {
                        HuaweiPodsAction.ACTION_SMART_AUDIO_FREECLIP2_EQ_SET
                    },
                ).apply {
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_NONCE, nonce)
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_ADDRESS, address)
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_PRESET_ID, presetId)
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_NAME, normalizedName)
                    putExtra(HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_EQ_GAINS, gains.toIntArray())
                    putExtra(
                        HuaweiPodsAction.EXTRA_FREECLIP2_BRIDGE_RESULT_RECEIVER,
                        resultReceiver,
                    )
                    if (relayThroughBluetooth) {
                        putExtra("address", address)
                        putExtra(
                            HuaweiPodsAction.EXTRA_DEVICE_ROUTE,
                            encodeHuaweiDeviceRouteForBroadcast(
                                HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                            ),
                        )
                        putExtra(
                            HuaweiPodsAction.EXTRA_FREECLIP2_AUDIO_KIND,
                            HuaweiPodsAction.FREECLIP2_AUDIO_KIND_EQUALIZER,
                        )
                    }
                    setPackage(
                        if (relayThroughBluetooth) {
                            SmartAudioFreeClip2BridgePolicy.BLUETOOTH_PACKAGE
                        } else {
                            SmartAudioFreeClip2BridgePolicy.SMART_AUDIO_PACKAGE
                        },
                    )
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
        }.isSuccess
        if (!sent) finish(nonce, false)
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
