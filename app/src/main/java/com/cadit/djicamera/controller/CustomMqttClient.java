package com.cadit.djicamera.controller;

import android.annotation.SuppressLint;
import android.util.Log;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import java.util.function.Consumer;

public class CustomMqttClient {
    private Mqtt3AsyncClient mClient;
    private String mTag;

    private CustomMqttClient () {}

    public CustomMqttClient(String tag, String brokerUri, Integer port) {
        mTag = tag;
        mClient = MqttClient.builder()
                .useMqttVersion3()
                .serverHost(brokerUri)
                .serverPort(port)
                .buildAsync();
    }

    public boolean isMqttConnected () {
        return mClient != null && mClient.getState().isConnected();
    }

    public synchronized boolean connect (String username, String password) {
        try {
            mClient.toBlocking()
                    .connectWith()
                    .simpleAuth()
                    .username(username)
                    .password(password.getBytes())
                    .applySimpleAuth()
                    .send();
            Log.v(mTag, "MQTT broker connected");
            return true;
        } catch (Exception e) {
            Log.e(mTag, e.toString());
            e.printStackTrace();
            return false;
        }
    }

    public synchronized boolean disconnect () {
        try {
            mClient.toBlocking().disconnect();
            Log.v(mTag, "MQTT broker disconnected");
            return true;
        } catch (Exception e) {
            Log.e(mTag, e.toString());
            e.printStackTrace();
            return false;
        }
    }

    public synchronized void publish (String topic, String payload, Boolean retain) {
        publish(topic, payload, MqttQos.AT_LEAST_ONCE, retain);
    }

    @SuppressLint("NewApi")
    public synchronized void publish (String topic, String payload, MqttQos qos, Boolean retain) {
        mClient.publishWith()
                .topic(topic)
                .payload(payload.getBytes())
                .qos(qos)
                .retain(retain)
                .send()
                .whenComplete((pubMsg, throwable) -> {
                    if (throwable != null) {
                        Log.e(mTag, "Failed to publish message: " + throwable.toString());
                    } else {
                        Log.d(mTag, "Publish MQTT " + topic + ": " + pubMsg.toString());
                    }
                });;
    }

    @SuppressLint("NewApi")
    public synchronized void subscribe (String topic, Consumer<Mqtt3Publish> callback) {
        mClient.subscribeWith()
                .topicFilter(topic)
                .callback(callback)
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        Log.e(mTag, "Failed to subscribe " + topic + " : " + throwable.toString());
                    } else {
                        Log.v(mTag, "Subscribe MQTT " + topic + ": " + subAck.toString());
                    }
                });
    }

    @SuppressLint("NewApi")
    public synchronized void unsubscribe (String topic) {
        mClient.unsubscribeWith()
                .topicFilter(topic)
                .send()
                .whenComplete((v, throwable) -> {
                    if (throwable != null) {
                        Log.e(mTag, "Failed to unsubscribe " + topic + " : " + throwable.toString());
                    }
                });
    }
}
