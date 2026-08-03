package moe.chenxy.huaweipods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log as AndroidLog
import moe.chenxy.huaweipods.hook.Log
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.ExperimentalStdlibApi
import kotlin.text.HexFormat

@SuppressLint("MissingPermission")
object HuaweiL2capAncController {
    private const val TAG = "HuaweiPods-HuaweiAnc"
    private const val RFCOMM_CONNECT_TIMEOUT_MS = 3_000L
    private const val RFCOMM_CONNECT_RETRY_DELAY_MS = 350L
    private const val RFCOMM_CONNECT_ATTEMPTS = 2
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val executor = Executors.newSingleThreadExecutor()
    private var socket: BluetoothSocket? = null
    private var deviceAddress: String? = null
    private var socketLabel: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setAncEnabled(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val packet = HuaweiAncPackets.enabled(route, enabled) ?: run {
            notifyComplete(onComplete, false)
            return
        }
        enqueueWrite(context, device, route, packet, "enabled=$enabled", onComplete = onComplete)
    }

    fun setAncLevel(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        level: Int,
    ) {
        val packet = HuaweiAncPackets.level(route, level) ?: return
        val safeLevel = level.coerceIn(0, 8)
        enqueueWrite(context, device, route, packet, "level=$safeLevel")
    }

    fun requestBattery(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onBattery: (BatteryParams) -> Unit,
    ) {
        val packet = HuaweiAncPackets.batteryQuery(route) ?: return
        enqueueWrite(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "battery-query",
            responseWindowMs = 1_500L,
            onResponse = { response ->
                val battery = HuaweiRfcommResponseParser.parseBattery(
                    response,
                    includeCase = route.hasChargingCase,
                )
                logInfo(
                    context.applicationContext ?: context,
                    "Huawei battery response bytes=${response.size} parsed=${battery != null} device=${device.address}",
                )
                battery?.let(onBattery)
            },
        )
    }

    fun requestAncState(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        onResult: (Int?) -> Unit,
    ) {
        val packet = HuaweiAncPackets.currentStateQuery(route) ?: run {
            onResult(null)
            return
        }
        enqueueWrite(
            context = context,
            device = device,
            route = route,
            packet = packet,
            description = "anc-state-query",
            responseWindowMs = 1_500L,
            responseComplete = { response ->
                HuaweiRfcommResponseParser.parseAncStatus(response) != null
            },
            onComplete = { success ->
                if (!success) onResult(null)
            },
            onResponse = { response ->
                val status = HuaweiRfcommResponseParser.parseAncStatus(response)
                RfcommLog.d(
                    context.applicationContext ?: context,
                    "RFCOMM/RX",
                    "anc-state-query ${response.toHexString()}",
                )
                logInfo(
                    context.applicationContext ?: context,
                    "Huawei ANC state response bytes=${response.size} parsed=${status != null} device=${device.address}",
                )
                onResult(status)
            },
        )
    }

    fun sendRawPacket(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        packet: ByteArray,
        description: String,
        keepSocket: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        enqueueWrite(context, device, route, packet, "raw $description", keepSocket, onComplete)
    }

    fun sendRawPacketOnce(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        packet: ByteArray,
        description: String,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        sendRawPacket(context, device, route, packet, description, keepSocket = false, onComplete)
    }

    private fun enqueueWrite(
        context: Context,
        device: BluetoothDevice,
        route: HuaweiDeviceRoute,
        packet: ByteArray,
        description: String,
        keepSocket: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null,
        responseWindowMs: Long = 0L,
        responseComplete: ((ByteArray) -> Boolean)? = null,
        onResponse: ((ByteArray) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext ?: context
        if (!isHuaweiDeviceRouteEnabled(route)) {
            logInfo(
                appContext,
                "Huawei write rejected: disabled route=$route address=${device.address}",
            )
            notifyComplete(onComplete, false)
            return
        }
        logInfo(appContext, "Huawei ANC enqueue $description keepSocket=$keepSocket device=${device.address}")
        runCatching {
            executor.execute {
                runCatching {
                    logInfo(appContext, "Huawei ANC worker started $description keepSocket=$keepSocket device=${device.address}")
                    val currentSocket = ensureSocket(device)
                    currentSocket.outputStream.write(packet)
                    currentSocket.outputStream.flush()
                    val hex = packet.toHexString()
                    RfcommLog.d(appContext, "RFCOMM/TX", "$description $hex")
                    if (responseWindowMs > 0L && onResponse != null) {
                        val response = collectSocketResponse(
                            currentSocket,
                            responseWindowMs,
                            responseComplete,
                        )
                        mainHandler.post { onResponse(response) }
                    }
                    logInfo(
                        appContext,
                        "Huawei ANC RFCOMM write finished $description keepSocket=$keepSocket socket=$socketLabel packet=$hex device=${device.address}"
                    )
                }.onFailure {
                    closeSocket()
                    logError(appContext, "Huawei ANC send failed $description device=${device.address}", it)
                    notifyComplete(onComplete, false)
                }.onSuccess {
                    if (!keepSocket) closeSocket()
                    notifyComplete(onComplete, true)
                }
            }
        }.onFailure {
            logError(appContext, "Huawei ANC enqueue failed $description device=${device.address}", it)
            notifyComplete(onComplete, false)
        }
    }

    fun disconnect(device: BluetoothDevice? = null) {
        if (device != null && deviceAddress != device.address) return
        executor.execute { closeSocket() }
    }

    private fun ensureSocket(device: BluetoothDevice): BluetoothSocket {
        val currentSocket = socket
        if (currentSocket != null && deviceAddress == device.address) {
            Log.w(TAG, "Huawei ANC reusing RFCOMM socket label=$socketLabel device=${device.address}")
            return currentSocket
        }

        closeSocket()
        var lastFailure: Throwable? = null
        for (candidate in socketCandidates(device)) {
            repeat(RFCOMM_CONNECT_ATTEMPTS) { attempt ->
                Log.w(
                    TAG,
                    "Huawei ANC connecting RFCOMM label=${candidate.label} attempt=${attempt + 1} device=${device.address}"
                )
                runCatching {
                    val newSocket = connectSocketWithTimeout(candidate.create(), candidate.label)
                    socket = newSocket
                    deviceAddress = device.address
                    socketLabel = candidate.label
                    Log.w(TAG, "Huawei ANC RFCOMM connected label=${candidate.label} device=${device.address}")
                    return newSocket
                }.onFailure {
                    lastFailure = it
                    Log.w(
                        TAG,
                        "Huawei ANC RFCOMM candidate failed label=${candidate.label} attempt=${attempt + 1} device=${device.address}",
                        it
                    )
                    if (attempt + 1 < RFCOMM_CONNECT_ATTEMPTS) {
                        Thread.sleep(RFCOMM_CONNECT_RETRY_DELAY_MS)
                    }
                }
            }
        }
        throw lastFailure ?: IOException("No Huawei ANC RFCOMM candidate succeeded")
    }

    private fun socketCandidates(device: BluetoothDevice): List<SocketCandidate> {
        val candidates = mutableListOf<SocketCandidate>()
        fun add(label: String, create: () -> BluetoothSocket) {
            if (candidates.none { it.label == label }) candidates += SocketCandidate(label, create)
        }

        add("secure-spp-$SPP_UUID") { device.createRfcommSocketToServiceRecord(SPP_UUID) }
        add("insecure-spp-$SPP_UUID") { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) }

        device.uuids.orEmpty()
            .mapNotNull { it?.uuid }
            .filter { it != SPP_UUID }
            .forEach { uuid ->
                add("secure-sdp-$uuid") { device.createRfcommSocketToServiceRecord(uuid) }
                add("insecure-sdp-$uuid") { device.createInsecureRfcommSocketToServiceRecord(uuid) }
            }

        (1..8).forEach { channel ->
            add("secure-channel-$channel") { hiddenChannelSocket(device, channel, secure = true) }
            add("insecure-channel-$channel") { hiddenChannelSocket(device, channel, secure = false) }
        }
        return candidates
    }

    private fun hiddenChannelSocket(device: BluetoothDevice, channel: Int, secure: Boolean): BluetoothSocket {
        val methodName = if (secure) "createRfcommSocket" else "createInsecureRfcommSocket"
        val method = device.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
        return method.invoke(device, channel) as BluetoothSocket
    }

    private fun connectSocketWithTimeout(socket: BluetoothSocket, label: String): BluetoothSocket {
        val connected = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        val thread = Thread({
            runCatching {
                socket.connect()
                connected.set(true)
            }.onFailure { failure.set(it) }
        }, "HuaweiAnc-rfcomm-connect")
        thread.start()
        thread.join(RFCOMM_CONNECT_TIMEOUT_MS)

        if (connected.get()) return socket
        failure.get()?.let {
            runCatching { socket.close() }
                .onFailure { closeError -> Log.w(TAG, "Huawei ANC RFCOMM failure close failed label=$label", closeError) }
            throw it
        }
        runCatching { socket.close() }
            .onFailure { Log.w(TAG, "Huawei ANC RFCOMM timeout close failed label=$label", it) }
        throw SocketTimeoutException("Huawei ANC RFCOMM connect timed out after ${RFCOMM_CONNECT_TIMEOUT_MS}ms label=$label")
    }

    private fun closeSocket() {
        val oldSocket = socket
        socket = null
        deviceAddress = null
        socketLabel = null
        runCatching { oldSocket?.close() }
            .onFailure { Log.w(TAG, "Huawei ANC socket close failed", it) }
    }

    private fun collectSocketResponse(
        socket: BluetoothSocket,
        timeoutMs: Long,
        responseComplete: ((ByteArray) -> Boolean)?,
    ): ByteArray {
        val input = socket.inputStream
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        var lastReadAt = 0L
        while (System.nanoTime() < deadline) {
            val available = runCatching { input.available() }.getOrDefault(0)
            if (available > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, available))
                if (read > 0) {
                    output.write(buffer, 0, read)
                    lastReadAt = System.nanoTime()
                    if (responseComplete?.invoke(output.toByteArray()) == true) break
                }
                continue
            }
            if (
                responseComplete == null &&
                lastReadAt > 0L &&
                System.nanoTime() - lastReadAt >= 200_000_000L
            ) break
            Thread.sleep(20L)
        }
        return output.toByteArray()
    }

    private data class SocketCandidate(
        val label: String,
        val create: () -> BluetoothSocket,
    )

    @OptIn(ExperimentalStdlibApi::class)
    private fun ByteArray.toHexString(): String = toHexString(HexFormat.UpperCase)

    private fun logInfo(context: Context, message: String) {
        Log.w(TAG, message)
        AndroidLog.i(TAG, message)
        RfcommLog.i(context, TAG, message)
    }

    private fun logError(context: Context, message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
        AndroidLog.e(TAG, message, throwable)
        RfcommLog.e(context, TAG, "$message: ${throwable.message.orEmpty()}")
    }

    private fun notifyComplete(callback: ((Boolean) -> Unit)?, success: Boolean) {
        callback ?: return
        mainHandler.post { callback(success) }
    }
}
