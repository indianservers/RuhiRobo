package com.indianservers.ruhi

import android.content.Context
import com.indianservers.ruhi.hardware.RobotHardwareController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

class SmartHomeManager(
    context: Context,
    private val hardware: RobotHardwareController? = null
) {
    private val appContext = context.applicationContext
    private var client: MqttAndroidClient? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _commands = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val commands: SharedFlow<Pair<String, String>> = _commands

    fun connect(brokerHost: String, port: Int = 1883) {
        val brokerUri = "tcp://$brokerHost:$port"
        val mqtt = MqttAndroidClient(appContext, brokerUri, "ruhi-${System.currentTimeMillis()}")
        mqtt.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                subscribeDefaults()
                publishHomeAssistantDiscovery()
            }

            override fun connectionLost(cause: Throwable?) = Unit
            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit

            override fun messageArrived(topic: String, message: MqttMessage) {
                val payload = message.toString()
                _commands.tryEmit(topic to payload)
                reactToHomeAssistantEvent(topic, payload)
            }
        })
        client = mqtt
        mqtt.connect(MqttConnectOptions().apply { isAutomaticReconnect = true }, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) = Unit
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) = Unit
        })
    }

    fun disconnect() {
        runCatching { client?.disconnect() }
        client = null
    }

    fun publishEmotion(expression: RobotFaceView.Expression) = publish("ruhi/state/emotion", expression.name)
    fun publishBattery(level: Int) = publish("ruhi/state/battery", level.toString())
    fun publishMood(mood: MoodState) = publish("ruhi/state/mood", """{"happiness":${mood.happiness},"energy":${mood.energy},"curiosity":${mood.curiosity}}""")
    fun publishFaceDetected(nickname: String?) = publish("ruhi/event/face_detected", nickname ?: "unknown")
    fun publishObjectDetected(label: String, confidence: Float) = publish("ruhi/event/object_detected", """{"label":"$label","confidence":$confidence}""")
    fun turnOnLivingRoomLights() = publish("homeassistant/light/living_room/set", """{"state":"ON"}""")

    private fun subscribeDefaults() {
        listOf("ruhi/command/speak", "ruhi/command/emotion", "ruhi/command/drive", "homeassistant/events/+").forEach {
            runCatching { client?.subscribe(it, 0) }
        }
    }

    private fun publishHomeAssistantDiscovery() {
        publish(
            "homeassistant/sensor/ruhi_mood/config",
            """{"name":"Ruhi Mood","state_topic":"ruhi/state/mood","unique_id":"ruhi_mood"}"""
        )
    }

    private fun reactToHomeAssistantEvent(topic: String, payload: String) {
        scope.launch {
            when {
                payload.contains("lights_off", ignoreCase = true) -> hardware?.expressEmotion(RobotFaceView.Expression.SLEEP)
                payload.contains("alarm_triggered", ignoreCase = true) -> hardware?.expressEmotion(RobotFaceView.Expression.WORRIED)
                payload.contains("doorbell", ignoreCase = true) -> hardware?.expressEmotion(RobotFaceView.Expression.SURPRISED)
                payload.contains("temperature_high", ignoreCase = true) -> hardware?.expressEmotion(RobotFaceView.Expression.COLD_SWEAT)
                topic.endsWith("/drive") -> hardware?.driveForward(10f, 0.4f)
            }
        }
    }

    private fun publish(topic: String, payload: String) {
        runCatching { client?.publish(topic, MqttMessage(payload.toByteArray())) }
    }
}
