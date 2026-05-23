package com.indianservers.ruhi.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.indianservers.ruhi.RobotFaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.roundToInt

enum class BleConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED, READY }
typealias BleState = BleConnectionState

enum class RobotTouchZone { HEAD, CHIN, BACK, PAW }

data class RobotSensorState(
    val touchMask: Int = 0,
    val cliffDetected: Boolean = false,
    val distanceMm: Int = 0,
    val batteryPercent: Int = 0,
    val charging: Boolean = false,
    val leftEncoderTicks: Int = 0,
    val rightEncoderTicks: Int = 0,
    val errorFlags: Int = 0
)

data class FirmwareInfo(
    val version: String = "unknown",
    val capabilityFlags: Long = 0L
)

private data class BleCommand(
    val characteristicUuid: UUID,
    val value: ByteArray,
    val writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
)

class BleRobotManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandQueue = Channel<BleCommand>(capacity = 32)

    private var gatt: BluetoothGatt? = null
    private val characteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()
    private var reconnectDelayMs = 1_000L
    var preferredDeviceName: String = "RuhiRobo"

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _sensorState = MutableStateFlow(RobotSensorState())
    val sensorState: StateFlow<RobotSensorState> = _sensorState

    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    private val _onTouchEvent = MutableSharedFlow<RobotTouchZone>(extraBufferCapacity = 8)
    val onTouchEvent: SharedFlow<RobotTouchZone> = _onTouchEvent

    private val _onCliffDetected = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val onCliffDetected: SharedFlow<Unit> = _onCliffDetected

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name.orEmpty()
            if (name == preferredDeviceName || name == "RuhiBot" || name == "RuhiRobo") {
                adapter?.bluetoothLeScanner?.stopScan(this)
                _connectionState.value = BleConnectionState.CONNECTING
                gatt = result.device.connectGatt(context, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                this@BleRobotManager.gatt = gatt
                reconnectDelayMs = 1_000L
                _connectionState.value = BleConnectionState.CONNECTED
                gatt.discoverServices()
            } else {
                clearConnection()
                scheduleReconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(PRIMARY_SERVICE_UUID) ?: return
            listOf(
                EMOTION_OUT_UUID, HEAD_MOTOR_UUID, BODY_MOTOR_UUID, LED_RING_UUID,
                AUDIO_OUT_UUID, SENSOR_IN_UUID, FIRMWARE_INFO_UUID, OTA_UPDATE_UUID
            ).forEach { uuid -> service.getCharacteristic(uuid)?.let { characteristics[uuid] = it } }
            subscribeToSensorStream(gatt)
            _connectionState.value = BleConnectionState.READY
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == SENSOR_IN_UUID) parseSensorPacket(characteristic.value)
        }
    }

    init {
        scope.launch {
            for (command in commandQueue) {
                withTimeoutOrNull(3_000L) { writeNow(command) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(deviceName: String = preferredDeviceName) {
        preferredDeviceName = deviceName.ifBlank { "RuhiRobo" }
        if (!hasBlePermission()) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _connectionState.value = BleConnectionState.SCANNING
        adapter?.bluetoothLeScanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        gatt?.disconnect()
        gatt?.close()
        clearConnection()
    }

    fun sendEmotion(expr: RobotFaceView.Expression, intensity: Float = 1f, durationMs: Int = 2_000) {
        val payload = byteArrayOf(
            expr.ordinal.toByte(),
            (intensity.coerceIn(0f, 1f) * 255f).roundToInt().toByte(),
            ((durationMs.coerceIn(0, 65_535) ushr 8) and 0xFF).toByte(),
            (durationMs and 0xFF).toByte()
        )
        enqueue(BleCommand(EMOTION_OUT_UUID, payload))
    }

    fun sendHeadPosition(panAngle: Float, tiltAngle: Float, speed: Float = 0.5f) {
        val payload = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(panAngle.coerceIn(-45f, 45f))
            .putFloat(tiltAngle.coerceIn(-30f, 30f))
            .putFloat(speed.coerceIn(0.1f, 1f))
            .array()
        enqueue(BleCommand(HEAD_MOTOR_UUID, payload))
    }

    fun sendBodyMotor(leftSpeed: Float, rightSpeed: Float, durationMs: Int) {
        val payload = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(leftSpeed.coerceIn(-1f, 1f))
            .putFloat(rightSpeed.coerceIn(-1f, 1f))
            .putShort(durationMs.coerceIn(0, 65_535).toShort())
            .array()
        enqueue(BleCommand(BODY_MOTOR_UUID, payload))
    }

    fun sendLedColor(color: Int, effect: Int = 0, speed: Int = 128) {
        enqueue(
            BleCommand(
                LED_RING_UUID,
                byteArrayOf(effect.toByte(), Color.red(color).toByte(), Color.green(color).toByte(), Color.blue(color).toByte(), speed.coerceIn(0, 255).toByte())
            )
        )
    }

    fun playSound(soundId: Int, volume: Float = 1f) {
        enqueue(BleCommand(AUDIO_OUT_UUID, byteArrayOf(soundId.coerceIn(0, 255).toByte(), (volume.coerceIn(0f, 1f) * 255).roundToInt().toByte())))
    }

    fun sendOtaChunk(sequence: Int, crc16: Int, chunk: ByteArray) {
        val payload = ByteBuffer.allocate(4 + chunk.size).order(ByteOrder.BIG_ENDIAN)
            .putShort(sequence.toShort())
            .putShort(crc16.toShort())
            .put(chunk)
            .array()
        enqueue(BleCommand(OTA_UPDATE_UUID, payload))
    }

    @SuppressLint("MissingPermission")
    fun readFirmwareInfo(): FirmwareInfo {
        val characteristic = characteristics[FIRMWARE_INFO_UUID] ?: return FirmwareInfo()
        gatt?.readCharacteristic(characteristic)
        val bytes = characteristic.value ?: return FirmwareInfo()
        val version = bytes.take(8).map { it.toInt().toChar() }.joinToString("").trim { it <= ' ' || it.code == 0 }
        val flags = if (bytes.size >= 12) ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() else 0L
        return FirmwareInfo(version.ifBlank { "unknown" }, flags)
    }

    private fun enqueue(command: BleCommand) {
        commandQueue.trySend(command)
    }

    @SuppressLint("MissingPermission")
    private fun writeNow(command: BleCommand) {
        val target = characteristics[command.characteristicUuid] ?: return
        val connectedGatt = gatt ?: return
        target.writeType = command.writeType
        target.value = command.value
        connectedGatt.writeCharacteristic(target)
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToSensorStream(gatt: BluetoothGatt) {
        val sensorChar = characteristics[SENSOR_IN_UUID] ?: return
        gatt.setCharacteristicNotification(sensorChar, true)
        sensorChar.getDescriptor(CLIENT_CONFIG_UUID)?.let { descriptor ->
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun parseSensorPacket(bytes: ByteArray?) {
        if (bytes == null || bytes.size < 11) return
        val state = RobotSensorState(
            touchMask = bytes[0].toInt() and 0xFF,
            cliffDetected = bytes[1].toInt() != 0,
            distanceMm = u16(bytes[2], bytes[3]),
            batteryPercent = bytes[4].toInt() and 0xFF,
            charging = bytes[5].toInt() != 0,
            leftEncoderTicks = u16(bytes[6], bytes[7]),
            rightEncoderTicks = u16(bytes[8], bytes[9]),
            errorFlags = bytes[10].toInt() and 0xFF
        )
        val previousTouch = _sensorState.value.touchMask
        _sensorState.value = state
        _batteryLevel.value = state.batteryPercent
        emitNewTouches(previousTouch, state.touchMask)
        if (state.cliffDetected) _onCliffDetected.tryEmit(Unit)
    }

    private fun emitNewTouches(previous: Int, current: Int) {
        val changed = current and previous.inv()
        if (changed and 0x01 != 0) _onTouchEvent.tryEmit(RobotTouchZone.HEAD)
        if (changed and 0x02 != 0) _onTouchEvent.tryEmit(RobotTouchZone.CHIN)
        if (changed and 0x04 != 0) _onTouchEvent.tryEmit(RobotTouchZone.BACK)
        if (changed and 0x08 != 0) _onTouchEvent.tryEmit(RobotTouchZone.PAW)
    }

    private fun u16(high: Byte, low: Byte): Int = ((high.toInt() and 0xFF) shl 8) or (low.toInt() and 0xFF)

    private fun clearConnection() {
        gatt = null
        characteristics.clear()
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(60_000L)
            startScan(preferredDeviceName)
        }
    }

    private fun hasBlePermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        val PRIMARY_SERVICE_UUID: UUID = UUID.fromString("0000FE00-0000-1000-8000-00805F9B34FB")
        val EMOTION_OUT_UUID: UUID = UUID.fromString("0000FE01-0000-1000-8000-00805F9B34FB")
        val HEAD_MOTOR_UUID: UUID = UUID.fromString("0000FE02-0000-1000-8000-00805F9B34FB")
        val BODY_MOTOR_UUID: UUID = UUID.fromString("0000FE03-0000-1000-8000-00805F9B34FB")
        val LED_RING_UUID: UUID = UUID.fromString("0000FE04-0000-1000-8000-00805F9B34FB")
        val AUDIO_OUT_UUID: UUID = UUID.fromString("0000FE05-0000-1000-8000-00805F9B34FB")
        val SENSOR_IN_UUID: UUID = UUID.fromString("0000FE06-0000-1000-8000-00805F9B34FB")
        val FIRMWARE_INFO_UUID: UUID = UUID.fromString("0000FE07-0000-1000-8000-00805F9B34FB")
        val OTA_UPDATE_UUID: UUID = UUID.fromString("0000FE08-0000-1000-8000-00805F9B34FB")
        private val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
