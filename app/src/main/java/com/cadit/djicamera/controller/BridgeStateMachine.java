import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.cadit.djicamera.utilities.ModuleVerificationUtil;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

import java.util.HashMap;
import java.util.Map;

import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightOrientationMode;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;

public class BridgeStateMachine {
    private Aircraft mAircraft;

    private com.cadit.djicamera.controller.CustomMqttClient mMqttClient;

    private boolean mInputValid = false;
    private String mRtmpServerUri = "";
    private String mMqttBrokerUri = "";
    private String mMqttUsername = "";
    private String mMqttPassword = "";
    private Integer mMqttPort = 1883;

    private Context mContext;
    private String mTag;

    // MQTT TOPICS
    private static final String TOPIC_OBSTACLE_DISTANCE = "dji/obstacle/distance";
    private static final String TOPIC_OBSTACLE_WARNING = "dji/obstacle/warning";
    private static final String TOPIC_CONTROL_ROLL = "dji/control/roll";
    private static final String TOPIC_CONTROL_PITCH = "dji/control/pitch";
    private static final String TOPIC_CONTROL_YAW = "dji/control/yaw";
    private static final String TOPIC_CONTROL_VERTICAL_THROTTLE = "dji/control/vertical-throttle";
    private static final String TOPIC_MODEL_NAME = "dji/model/name";
    private static final String TOPIC_STATUS_CONNECTION = "dji/status/connection";
    private static final String TOPIC_STATUS_FLIGHT_MODE = "dji/status/flight-mode";
    private static final String TOPIC_STATUS_BATTERY = "dji/status/battery";
    private static final String TOPIC_STATUS_ALTITUDE = "dji/status/altitude";
    private static final String TOPIC_STATUS_VERTICAL_SPEED = "dji/status/vertical-speed";
    private static final String TOPIC_STATUS_HORIZONTAL_SPEED = "dji/status/horizontal-speed";

    public BridgeStateMachine (Context context, String tag) {
        mContext = context;
        mTag = tag;
    }

    public void setMqttPort (int mqttPort) {
        mMqttPort = mqttPort;
    }

    public int getMqttPort () {
        return mMqttPort;
    }

    public synchronized void onAircraftConnected (Aircraft aircraft) {
        mAircraft = aircraft;

        if (mInputValid && mAircraft.isConnected()) {
            initBridge();
        }
    }

    public synchronized void onAircraftDisconnected () {
        mAircraft = null;
    }

    public synchronized void onInputValid (boolean valid,
                              String rtmpServerUri,
                              String mqttBrokerUri,
                              String mqttUsername,
                              String mqttPassword) {
        mInputValid = valid;
        mRtmpServerUri = rtmpServerUri;
        mMqttBrokerUri = mqttBrokerUri;
        mMqttUsername = mqttUsername;
        mMqttPassword = mqttPassword;

        if (isAircraftConnected()) {
            initBridge();
        }
    }

    private boolean isAircraftConnected () {
        return mAircraft != null && mAircraft.isConnected();
    }

    private boolean isVirtualStickModeAvailable () {
        if (!isAircraftConnected()) return false;
        else return mAircraft.getFlightController().isVirtualStickControlModeAvailable();
    }

    private boolean isLiveStreamManagerOn() {
        if (DJISDKManager.getInstance().getLiveStreamManager() == null) {
            showToast("LIVE STREAM NULL");
            return false;
        }
        return true;
    }

    @SuppressLint("NewApi")
    private synchronized void initBridge () {
        showToast("Initializing bridge");

        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            // setting flight orientation mode to aircraft heading
            mAircraft.getFlightController().setFlightOrientationMode(FlightOrientationMode.AIRCRAFT_HEADING, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        DJILog.e("Flight orientation mode", djiError.getDescription());
                        showToast("Failed to set flight orientation mode");
                    }
                }
            });

            // enabling virtual stick control mode
            mAircraft.getFlightController().setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        DJILog.e("Virtual stick enable", djiError.getDescription());
                        showToast("Failed to enable virtual stick control mode");
                    }
                }
            });
        }

        // init mqtt client
        mMqttClient = new com.cadit.djicamera.controller.CustomMqttClient(mTag, mMqttBrokerUri, mMqttPort);

        // connect mqtt client
        if (!mMqttClient.connect(mMqttUsername, mMqttPassword)) {
            showToast("Failed to connect to MQTT broker");
        } else {
            // set publishers
            setPublishers();
        }
    }

    private void setPublishers() {

    }

    private void showToast(final String toastMsg) {
        Toast.makeText(mContext, toastMsg, Toast.LENGTH_SHORT).show();
    }
}
