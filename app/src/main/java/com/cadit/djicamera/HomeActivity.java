package com.cadit.djicamera;

import  androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.cadit.djicamera.controller.CustomMqttClient;
import com.cadit.djicamera.controller.CustomTextWatcher;
import com.hivemq.client.mqtt.datatypes.MqttQos;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import dji.common.battery.BatteryState;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.FlightOrientationMode;
import dji.common.flightcontroller.ObstacleDetectionSector;
import dji.common.flightcontroller.VisionDetectionState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.products.HandHeld;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;
import dji.sdk.sdkmanager.LiveVideoBitRateMode;
import dji.sdk.sdkmanager.LiveVideoResolution;

public class HomeActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = HomeActivity.class.getName();
    private static final String URL_KEY = "sp_stream_url";
    private static final Integer DEFAULT_MQTT_PORT = 1883;

    // MQTT TOPICS
    private static final String TOPIC_OBSTACLE_DISTANCE = "dji/obstacle/distance";
    private static final String TOPIC_OBSTACLE_WARNING = "dji/obstacle/warning";
    private static final String TOPIC_CONTROL = "dji/control/";
    private static final String TOPIC_MODEL_NAME = "dji/model/name";
    private static final String TOPIC_STATUS_CONNECTION = "dji/status/connection";
    private static final String TOPIC_STATUS_FLIGHT_MODE = "dji/status/flight-mode";
    private static final String TOPIC_STATUS_BATTERY = "dji/status/battery";
    private static final String TOPIC_STATUS_ALTITUDE = "dji/status/altitude";
    private static final String TOPIC_STATUS_VERTICAL_SPEED = "dji/status/vertical-speed";
    private static final String TOPIC_STATUS_HORIZONTAL_SPEED = "dji/status/horizontal-speed";

    private CustomMqttClient mMqttClient = null;

    private Aircraft mAircraft = null;
    private LiveStreamManager.OnLiveChangeListener mListener;
    private AtomicBoolean mIsVirtualStickControlModeEnabled = new AtomicBoolean(false);

    private String mRtmpServerURI = "";
    private String mMqttBrokerURI = "";
    private String mMqttUsername = "";
    private String mMqttPassword = "";
    private int mMqttPort = 1883;


    private Boolean mIsRtmpURIValid = false;
    private Boolean mIsMqttURIValid = false;

    private TextView mTextProduct;
    private TextView mTextConnectionStatus;
    private TextView mTextFirmwareVersion;
    private TextView mTextSDKVersion;
    private EditText mEditRTMPServerURI;
    private EditText mEditMQTTBrokerURI;
    private EditText mEditMQTTUsername;
    private EditText mEditMQTTPassword;
    private Button mBtnToggleConnect;
    private Button mBtnToggleLivestream;
    private Button mBtnToggleControl;
    private Button mBtnCheckStatus;


    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onConnectionChange();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_home);
        initUI(this);
        initListener();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(FPVDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast( "Registering DJI SDK");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                showToast("DJI SDK registration success");

                                DJISDKManager.getInstance().startConnectionToProduct();
                            } else {
                                showToast( "DJI SDK registration fails, check if network is available");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductDisconnect() {
                            onConnectionChange();
                        }
                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            onConnectionChange();
                        }

                        @Override
                        public void onProductChanged(BaseProduct baseProduct) {
                            onConnectionChange();
                        }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                                      BaseComponent newComponent) {

                            if (newComponent != null) {
                                newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                                    @Override
                                    public void onConnectivityChange(boolean isConnected) {
                                        Log.v(TAG, "onConnectivityChanged: " + isConnected);
                                    }
                                });
                            }
                            Log.d(TAG,
                                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                            componentKey,
                                            oldComponent,
                                            newComponent));

                        }
                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                        }

                        @Override
                        public void onDatabaseDownloadProgress(long l, long l1) {

                        }
                    });
                }
            });
        }
    }

    private void initUI(Context context) {
        mRtmpServerURI = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).getString(URL_KEY, mRtmpServerURI);
        mTextProduct = (TextView) findViewById(R.id.text_product_name);
        mTextConnectionStatus = (TextView) findViewById(R.id.text_product_status);
        mTextFirmwareVersion = (TextView) findViewById(R.id.text_firmware_version);

        mTextSDKVersion = (TextView) findViewById(R.id.text_sdk_version);
        mTextSDKVersion.setText("DJI SDK version: " + DJISDKManager.getInstance().getSDKVersion());

        mEditRTMPServerURI = (EditText) findViewById(R.id.edit_text_rtmp_server);
        mEditMQTTBrokerURI = (EditText) findViewById(R.id.edit_text_mqtt_broker);
        mEditMQTTPassword = (EditText) findViewById(R.id.edit_text_mqtt_password);
        mEditMQTTUsername = (EditText) findViewById(R.id.edit_text_mqtt_username);

        mBtnToggleConnect = (Button) findViewById(R.id.button_connect_disconnect);
//        TODO: UNCOMMENT THIS LINE BEFORE TESTING WITH DJI DRONE
        mBtnToggleConnect.setEnabled(false);
        mBtnToggleConnect.setOnClickListener(this);

        mBtnToggleLivestream = (Button) findViewById(R.id.button_start_livestream);
        mBtnToggleLivestream.setOnClickListener(this);

        mBtnToggleControl = (Button) findViewById(R.id.button_start_control);
        mBtnToggleControl.setOnClickListener(this);

        mBtnCheckStatus = (Button) findViewById(R.id.button_check_status);
        mBtnCheckStatus.setOnClickListener(this);
    }

    private void initListener() {
        Pattern rtmpRegexp = Pattern.compile("^(rtmp:\\/\\/)((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!$)|:[0-9]+\\/)){4}(live(\\/|$))[a-zA-Z0-9]*?$");
        Pattern mqttRegexp = Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!$)|:[0-9]+$|$)){4}$");

        mEditRTMPServerURI.addTextChangedListener(new CustomTextWatcher(mEditRTMPServerURI) {
            @Override
            public void validate(TextView textView, String text) {
                mRtmpServerURI = text;
                mIsRtmpURIValid = rtmpRegexp.matcher(text).matches();
                setConnectButtonEnabled();
            }
        });

        mEditMQTTBrokerURI.addTextChangedListener(new CustomTextWatcher(mEditMQTTBrokerURI) {
            @Override
            public void validate(TextView textView, String text) {
                mIsMqttURIValid = mqttRegexp.matcher(text).matches();

                String[] split = text.split(":");
                mMqttBrokerURI = split[0];
                if (split.length > 1) mMqttPort = Integer.valueOf(split[1]);
                else mMqttPort = DEFAULT_MQTT_PORT;

                setConnectButtonEnabled();
            }
        });

        mEditMQTTUsername.addTextChangedListener(new CustomTextWatcher(mEditMQTTUsername) {
            @Override
            public void validate(TextView textView, String text) {
                mMqttUsername = text;
            }
        });

        mEditMQTTPassword.addTextChangedListener(new CustomTextWatcher(mEditMQTTPassword) {
            @Override
            public void validate(TextView textView, String text) {
                mMqttPassword = text;
            }
        });

        mListener = new LiveStreamManager.OnLiveChangeListener() {
            @Override
            public void onStatusChanged(int i) {
                Log.d("LIVESTREAM", "status changed : " + i);
            }
        };
    }

    private void setConnectButtonEnabled() {
        if (isAircraftConnected() &&
            mIsRtmpURIValid &&
            mIsMqttURIValid)
        {
            mBtnToggleConnect.setEnabled(true);
        } else {
//            mBtnToggleConnect.setEnabled(true);
//            TODO: UNCOMMENT THIS LINE BEFORE TESTING WITH DJI DRONE
            mBtnToggleConnect.setEnabled(false);
        }
    }

    private void showToast(final String toastMsg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isLiveStreamManagerOn() {
        if (DJISDKManager.getInstance().getLiveStreamManager() == null) {
            Log.v(TAG, "Livestream manager null");
            return false;
        }
        return true;
    }

    private boolean isAircraftConnected() {
        return mAircraft != null && mAircraft.isConnected();
    }

    private boolean isMqttConnected() {return mMqttClient != null && mMqttClient.isMqttConnected();}

    private boolean isFlightControllerAvailable() {
        return isAircraftConnected() && mAircraft.getFlightController() != null;
    }

    private boolean isLivestreaming() {
        return isLiveStreamManagerOn() && DJISDKManager.getInstance().getLiveStreamManager().isStreaming();
    }

    private boolean isVirtualStickControlModeAvailable() {
        return isFlightControllerAvailable() &&
               mAircraft.getFlightController().isVirtualStickControlModeAvailable();
    }

    private void onConnectionChange() {
        if (isAircraftConnected()) return;

        BaseProduct baseProduct = DJISDKManager.getInstance().getProduct();

        if (baseProduct != null && baseProduct.isConnected()) {
            try {
                Log.v(TAG, "onProductConnect");

                if (baseProduct instanceof Aircraft) {
                    showToast("DJI aircraft connected");
                    publishStatusConnection(true);

                    mAircraft = (Aircraft) baseProduct;

                    refreshSDKRelativeUI();
                } else if (baseProduct instanceof HandHeld) {
                    showToast("Remote controller connected");
                } else {
                    showToast("Connected product is not a DJI product");
                    Log.e(TAG, "Connected product is not a DJI product");
                }
            } catch (Exception e) {
                showToast(e.toString());
                Log.e(TAG, e.toString());
            }
        } else {
            try {
                Log.v(TAG, "onProductDisconnect");
                gracefullyDisconnect();

                mAircraft = null;
            } catch (Exception e) {
                showToast(e.toString());
                Log.e(TAG, e.toString());
            }
        }
    }

    private void refreshSDKRelativeUI() {
//        TODO: REMOVE "!" FROM THIS LINE BEFORE TESTING WITH DJI DRONE
        if (isAircraftConnected()) {
            Log.v(TAG, "refreshSDK: True");

            setConnectButtonEnabled();
            mTextConnectionStatus.setText("Status: DJIAircraft connected");
            mTextConnectionStatus.setVisibility(View.VISIBLE);
//            TODO: UNCOMMENT THIS LINES BEFORE TESTING WITH DJI DRONE
            if (mAircraft.getFirmwarePackageVersion() != null) {
                mTextFirmwareVersion.setText("Firmware version: " + mAircraft.getFirmwarePackageVersion());
            }

            if (mAircraft.getModel() != null) {
                mTextProduct.setText("" + mAircraft.getModel().getDisplayName());
            } else {
                mTextProduct.setText(R.string.product_name_unknown);
            }

            if (isMqttConnected()) {
                mBtnToggleConnect.setText(R.string.button_disconnect);

                mBtnToggleLivestream.setVisibility(View.VISIBLE);
                mBtnToggleControl.setVisibility(View.VISIBLE);
                mBtnCheckStatus.setVisibility(View.VISIBLE);
            } else {
                mBtnToggleConnect.setText(R.string.button_connect);

                mBtnToggleLivestream.setVisibility(View.GONE);
                mBtnToggleControl.setVisibility(View.GONE);
                mBtnCheckStatus.setVisibility(View.GONE);
            }
        } else {
            Log.v(TAG, "refreshSDK: False");
            mTextConnectionStatus.setText(R.string.product_status_default);
            mTextConnectionStatus.setVisibility(View.GONE);

            mTextProduct.setText(R.string.product_name_default);
            mTextFirmwareVersion.setText(R.string.firmware_default_text);

            mBtnToggleConnect.setText(R.string.button_connect);
            mBtnToggleConnect.setEnabled(false);

            mBtnToggleLivestream.setText(R.string.button_start_livestream);
            mBtnToggleLivestream.setVisibility(View.GONE);

            mBtnToggleControl.setText(R.string.button_start_control);
            mBtnToggleControl.setVisibility(View.GONE);

            mBtnCheckStatus.setVisibility(View.GONE);
        }
    }

    private void gracefullyDisconnect() {
        publishStatusConnection(false);

        if (!mMqttClient.disconnect()) {
            showToast("Failed to disconnect from MQTT broker");
            return;
        }

        unregisterLivestreamListener();
        setVirtualStickControlModeEnabled(false);

        if (isLivestreaming()) {
            stopLivestream();
        }

        if (mIsVirtualStickControlModeEnabled.get()) {
            stopFlightControl();
        }

        refreshSDKRelativeUI();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_connect_disconnect: {
                if (mBtnToggleConnect.getText().toString() == getResources().getString(R.string.button_connect)) {
                    if (mMqttClient != null) mMqttClient.disconnect();

                    mMqttClient = new CustomMqttClient(TAG, mMqttBrokerURI, mMqttPort);
                    if (!mMqttClient.connect(mMqttUsername, mMqttPassword)) {
                        showToast("Failed to connect to MQTT broker");
                    } else {
                        registerLivestreamListener();
                        setFlightControllerCallback();
                        setObstacleCallback();
                        setBatteryCallback();
                        publishModelName();
                        publishStatusConnection(true);

                        refreshSDKRelativeUI();
                    }
                } else {
                    gracefullyDisconnect();
                }
                break;
            }

            case R.id.button_start_livestream: {
                if (mBtnToggleLivestream.getText().toString() == getResources().getString(R.string.button_start_livestream)) {
                    startLivestream(this);
                    mBtnToggleLivestream.setText(R.string.button_stop_livestream);
                } else {
                    stopLivestream();
                    mBtnToggleLivestream.setText(R.string.button_start_livestream);
                }
                break;
            }

            case R.id.button_start_control: {
                if (mBtnToggleControl.getText().toString() == getResources().getString(R.string.button_start_control)) {
                    startFlightControl();
                    mBtnToggleControl.setText(R.string.button_stop_control);
                } else {
                    stopFlightControl();
                    mBtnToggleControl.setText(R.string.button_start_control);
                }
                break;
            }

            case R.id.button_check_status: {
                showToast("isLivestreamManagerOn: " + String.valueOf(isLiveStreamManagerOn()) + "\n" +
                "isLivestreaming: " + String.valueOf(isLivestreaming()) + "\n" +
                "isMqttConnected: " + String.valueOf(isMqttConnected()) + "\n" +
                "isFlightControllerAvailable: " + String.valueOf(isFlightControllerAvailable()) + "\n" +
                "isVirtualStickControlModeAvailable: " + String.valueOf(isVirtualStickControlModeAvailable()));
                break;
            }
        }
    }

    private void registerLivestreamListener() {
        if (!isLiveStreamManagerOn()) return;

        DJISDKManager.getInstance().getLiveStreamManager().registerListener(mListener);
    }

    private void unregisterLivestreamListener() {
        if (!isLiveStreamManagerOn()) return;

        DJISDKManager.getInstance().getLiveStreamManager().unregisterListener(mListener);
    }

    private void setVirtualStickControlModeEnabled(boolean enabled) {
        if (!isVirtualStickControlModeAvailable()) return;

        mAircraft.getFlightController().setFlightOrientationMode(FlightOrientationMode.AIRCRAFT_HEADING, djiError -> {
            if (djiError != null) {
                DJILog.e("Flight orientation mode", djiError.getDescription());
                showToast("Failed to set flight orientation mode");
            }
        });

        // enabling virtual stick control mode
        mAircraft.getFlightController().setVirtualStickModeEnabled(enabled, djiError -> {
            if (djiError != null) {
                DJILog.e("Virtual stick enable", djiError.getDescription());
                showToast("Failed to set virtual stick control mode");
            } else {
                mIsVirtualStickControlModeEnabled.compareAndSet(!enabled, enabled);
            }
        });
    }

    private void setFlightControllerCallback() {
        if (!isFlightControllerAvailable()) return;

        FlightController fc = mAircraft.getFlightController();
        fc.setStateCallback(flightControllerState -> {
//            mTextConnectionStatus.setText(getString(R.string.flight_mode_format, flightControllerState.getFlightModeString()));

            if (!isMqttConnected()) return;

            // publish status topic
            mMqttClient.publish(TOPIC_STATUS_ALTITUDE, String.valueOf(flightControllerState.getAircraftLocation().getAltitude()), MqttQos.EXACTLY_ONCE);
            mMqttClient.publish(TOPIC_STATUS_VERTICAL_SPEED, String.valueOf(flightControllerState.getVelocityZ()), MqttQos.EXACTLY_ONCE);

            List<Float> horSpeed = Arrays.asList(flightControllerState.getVelocityX(), flightControllerState.getVelocityY());
            mMqttClient.publish(TOPIC_STATUS_HORIZONTAL_SPEED, horSpeed.toString(), MqttQos.EXACTLY_ONCE);

            // publish flight mode
            mMqttClient.publish(TOPIC_STATUS_FLIGHT_MODE, flightControllerState.getFlightModeString());
        });
    }

    private void setBatteryCallback () {
        if (!isAircraftConnected()) return;

        mAircraft.getBattery().setStateCallback(batteryState -> {
            if (!isMqttConnected()) return;

            mMqttClient.publish(TOPIC_STATUS_BATTERY, Integer.toString(batteryState.getChargeRemainingInPercent()));
        });
    }

    private void setObstacleCallback() {
        if (!isFlightControllerAvailable()) return;

        FlightAssistant intelligentFlightAssistant = mAircraft.getFlightController().getFlightAssistant();

        if (intelligentFlightAssistant == null) return;

        intelligentFlightAssistant.setVisionDetectionStateUpdatedCallback(visionDetectionState -> {
            ObstacleDetectionSector[] visionDetectionSectorArray =
                    visionDetectionState.getDetectionSectors();

            List<Float> obstacleDistances = new ArrayList<Float>();
            List<String> obstacleWarnings = new ArrayList<String>();

            // FOR WARNING LEVEL, REFER TO https://developer.dji.com/api-reference/android-api/Components/VisionDetectionState/DJIVisionDetectionState_DJIVisionDetectionSector.html#djivisiondetectionstate_visionsectorwarning_inline
            for (ObstacleDetectionSector visionDetectionSector : visionDetectionSectorArray) {
                obstacleDistances.add(visionDetectionSector.getObstacleDistanceInMeters());

                switch (visionDetectionSector.getWarningLevel()){
                    case INVALID:
                        obstacleWarnings.add("INVALID");
                        break;
                    case LEVEL_1:
                        obstacleWarnings.add("LEVEL_1");
                        break;
                    case LEVEL_2:
                        obstacleWarnings.add("LEVEL_2");
                        break;
                    case LEVEL_3:
                        obstacleWarnings.add("LEVEL_3");
                        break;
                    case LEVEL_4:
                        obstacleWarnings.add("LEVEL_4");
                        break;
                    case LEVEL_5:
                        obstacleWarnings.add("LEVEL_5");
                        break;
                    case LEVEL_6:
                        obstacleWarnings.add("LEVEL_6");
                        break;
                    case UNKNOWN:
                        obstacleWarnings.add("UNKNOWN");
                        break;
                }
            }

            if (!isMqttConnected()) return;

            mMqttClient.publish(TOPIC_OBSTACLE_DISTANCE, obstacleDistances.toString());
            mMqttClient.publish(TOPIC_OBSTACLE_WARNING, obstacleWarnings.toString());
        });
    }

    private void publishStatusConnection(boolean state) {
        if (!isMqttConnected()) return;

        mMqttClient.publish(TOPIC_STATUS_CONNECTION, String.valueOf(state));
    }

    private void publishModelName() {
        if (!isMqttConnected() || !isAircraftConnected()) return;

        mMqttClient.publish(TOPIC_MODEL_NAME, mAircraft.getModel().getDisplayName());
    }

    private synchronized void startLivestream(Context context) {
        showToast("Starting livestream to " + mRtmpServerURI);
        if (!isLiveStreamManagerOn()) return;

        if (isLivestreaming()) {
            Log.d("LIVESTREAM STARTED", mRtmpServerURI);
            return;
        }
        new Thread() {
            @Override
            public void run() {
                DJISDKManager.getInstance().getLiveStreamManager().setLiveUrl(mRtmpServerURI);
                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoResolution(LiveVideoResolution.VIDEO_RESOLUTION_960_720);
                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoBitRateMode(LiveVideoBitRateMode.AUTO);
                DJISDKManager.getInstance().getLiveStreamManager().setAudioStreamingEnabled(false);
                int result = DJISDKManager.getInstance().getLiveStreamManager().startStream();
                context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).edit().putString(URL_KEY, mRtmpServerURI).commit();
                Log.d("START LIVESTREAM", "startLive:" + result +
                        "\n isVideoStreamSpeedConfigurable:" + DJISDKManager.getInstance().getLiveStreamManager().isVideoStreamSpeedConfigurable() +
                        "\n isLiveAudioEnabled:" + DJISDKManager.getInstance().getLiveStreamManager().isLiveAudioEnabled());
            }
        }.start();
    }

    private synchronized void stopLivestream() {
        if (!isLiveStreamManagerOn()) return;

        showToast("Stopping Livestream");
        DJISDKManager.getInstance().getLiveStreamManager().stopStream();
        showToast("LIVESTREAM STOPPED");
    }

    private synchronized void startFlightControl() {
        if (mIsVirtualStickControlModeEnabled.get()) {
            showToast("Starting flight control");

            // set control mode
            FlightController fc = mAircraft.getFlightController();
            fc.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            fc.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            fc.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            fc.setVerticalControlMode(VerticalControlMode.POSITION);

            mMqttClient.subscribe(TOPIC_CONTROL, (message) -> {
                final String payload = new String(message.getPayloadAsBytes(), StandardCharsets.UTF_8);

                Log.v(TAG, "Control received: " + payload);

                String[] strControls = payload.substring(1, payload.length()-1).split(",");

                FlightControlData newControl = new FlightControlData(
                        Float.parseFloat(strControls[0]),
                        Float.parseFloat(strControls[1]),
                        Float.parseFloat(strControls[2]),
                        Float.parseFloat(strControls[3])
                );

                mAircraft.getFlightController().sendVirtualStickFlightControlData(newControl, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            Log.e(TAG, "Failed to send virtual stick control data");
                        } else {
                            Log.v(TAG, "Control sent: " + payload);
                        }
                    }
                });
            });
        } else {
            showToast("Virtual stick control mode disable");
            setVirtualStickControlModeEnabled(true);
        }
    }

    private synchronized void stopFlightControl() {
        showToast("Stopping flight control");
        setVirtualStickControlModeEnabled(false);

        mMqttClient.unsubscribe(TOPIC_CONTROL);
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        super.onDestroy();

        if (isLivestreaming()) stopLivestream();
        if (mIsVirtualStickControlModeEnabled.get()) stopFlightControl();

        if (isMqttConnected()) mMqttClient.disconnect();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }
}