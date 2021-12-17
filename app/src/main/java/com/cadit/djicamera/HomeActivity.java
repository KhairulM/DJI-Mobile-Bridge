package com.cadit.djicamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.cadit.djicamera.controller.DJISampleApplication;
import com.cadit.djicamera.utilities.ModuleVerificationUtil;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.ObstacleDetectionSector;
import dji.common.flightcontroller.VisionDetectionState;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;
import dji.sdk.sdkmanager.LiveVideoBitRateMode;
import dji.sdk.sdkmanager.LiveVideoResolution;

public class HomeActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = HomeActivity.class.getName();
    private static final String URL_KEY = "sp_stream_url";
    private static final String TOPIC_OBSTACLE_DISTANCE = "dji/obstacle/distance";
    private static final String TOPIC_OBSTACLE_WARNING = "dji/obstacle/warning";
    private static final int MQTT_PORT = 1883;

    private Mqtt3AsyncClient mMqttClient = null;

    private BaseProduct mProduct = null;
    private LiveStreamManager.OnLiveChangeListener mListener;

    private String mRtmpServerURI = "";
    private String mMqttBrokerURI = "192.168.100.5";
    private String mMqttUsername = "";
    private String mMqttPassword = "";

    private TextView mTextProduct;
    private TextView mTextConnectionStatus;
    private TextView mTextFirmwareVersion;
    private TextView mTextSDKVersion;
    private EditText mEditRTMPServerURI;
    private EditText mEditMQTTBrokerURI;
    private EditText mEditMQTTUsername;
    private EditText mEditMQTTPassword;
    private Button mBtnToggleStartStop;
    private Button mBtnCheckLivestream;
    private Button mBtnShowInfo;

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
    private static final int REQUEST_PERMISSION_CODE = 96385;

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshSDKRelativeUI();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_home);
        initUI(this);
        initListener();
        initMQTTClient();

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
                                DJISDKManager.getInstance().startConnectionToProduct();
                                showToast("DJI SDK registration success");
                            } else {
                                showToast( "DJI SDK registration fails, check if network is available");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            stopLivestream();
                            unregisterLSListener();
                            mProduct = null;

                            refreshSDKRelativeUI();
                            showToast("Product disconnected");

                        }
                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                            mProduct = DJISDKManager.getInstance().getProduct();
                            registerLSListener();

                            refreshSDKRelativeUI();
                            showToast("Product connected");

                        }

                        @Override
                        public void onProductChanged(BaseProduct baseProduct) {
                            stopLivestream();
                            mProduct = DJISDKManager.getInstance().getProduct();
                            registerLSListener();

                            refreshSDKRelativeUI();
                            showToast("Product changed");
                        }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                                      BaseComponent newComponent) {

                            if (newComponent != null) {
                                newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                                    @Override
                                    public void onConnectivityChange(boolean isConnected) {
                                        Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
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
        mTextConnectionStatus = (TextView) findViewById(R.id.text_connection_status);
        mTextFirmwareVersion = (TextView) findViewById(R.id.text_firmware_version);

        mTextSDKVersion = (TextView) findViewById(R.id.text_sdk_version);
        mTextSDKVersion.setText("DJI SDK version: " + DJISDKManager.getInstance().getSDKVersion());

        mEditRTMPServerURI = (EditText) findViewById(R.id.edit_text_rtmp_server);
        mEditMQTTBrokerURI = (EditText) findViewById(R.id.edit_text_mqtt_broker);
        mEditMQTTPassword = (EditText) findViewById(R.id.edit_text_mqtt_password);
        mEditMQTTUsername = (EditText) findViewById(R.id.edit_text_mqtt_username);

        mBtnToggleStartStop = (Button) findViewById(R.id.button_start);
        mBtnToggleStartStop.setOnClickListener(this);
        mBtnToggleStartStop.setEnabled(false);

        mBtnCheckLivestream = (Button) findViewById(R.id.button_check_livestream);
        mBtnCheckLivestream.setOnClickListener(this);

        mBtnShowInfo = (Button) findViewById(R.id.button_show_info);
        mBtnShowInfo.setOnClickListener(this);
    }

    private void initListener() {
        mEditRTMPServerURI.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mRtmpServerURI = charSequence.toString();
                setStartButtonState();
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        mEditMQTTBrokerURI.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mMqttBrokerURI = charSequence.toString();
                setStartButtonState();
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        mEditMQTTUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mMqttUsername = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        mEditMQTTPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mMqttPassword = charSequence.toString();
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        mListener = new LiveStreamManager.OnLiveChangeListener() {
            @Override
            public void onStatusChanged(int i) {
                Log.d("LIVESTREAM", "status changed : " + i);
            }
        };
    }

    private void setStartButtonState() {
        if (isProductConnected() &&
            mRtmpServerURI.length() > 0 &&
            mMqttBrokerURI.length() > 0) {
            mBtnToggleStartStop.setEnabled(true);
        } else {
            mBtnToggleStartStop.setEnabled(false);
        }
    }

    private void initMQTTClient() {
        mMqttClient = MqttClient.builder()
                .useMqttVersion3()
                .serverHost(mMqttBrokerURI)
                .serverPort(MQTT_PORT)
                .buildAsync();
    }

    private void showToast(final String toastMsg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void registerLSListener() {
        if (!isLiveStreamManagerOn()) return;

        DJISDKManager.getInstance().getLiveStreamManager().registerListener(mListener);
    }

    private void unregisterLSListener() {
        if (!isLiveStreamManagerOn()) return;

        DJISDKManager.getInstance().getLiveStreamManager().unregisterListener(mListener);
    }

    private boolean isLiveStreamManagerOn() {
        if (DJISDKManager.getInstance().getLiveStreamManager() == null) {
            showToast("LIVE STREAM NULL");
            return false;
        }
        return true;
    }

    private boolean isProductConnected() {
        return mProduct != null && mProduct.isConnected();
    }

    private void refreshSDKRelativeUI() {
        if (isProductConnected()) {
            Log.v(TAG, "refreshSDK: True");

            String str = mProduct instanceof Aircraft ? "DJIAircraft":"DJIHandHeld";
            mTextConnectionStatus.setText("Status: " + str + " connected");
            mTextConnectionStatus.setVisibility(View.VISIBLE);

            if (mProduct.getFirmwarePackageVersion() != null) {
                mTextFirmwareVersion.setText("Firmware version: " + mProduct.getFirmwarePackageVersion());
            }

            if (mProduct.getModel() != null) {
                mTextProduct.setText("" + mProduct.getModel().getDisplayName());
            } else {
                mTextProduct.setText("Unnamed DJI Product");
            }

            // if product connected after rtmp server is filled
            setStartButtonState();
        } else {
            Log.v(TAG, "refreshSDK: False");
            mTextConnectionStatus.setText(R.string.product_default_status);
            mTextConnectionStatus.setVisibility(View.GONE);
            mTextProduct.setText(R.string.product_default_name);
            mTextFirmwareVersion.setText(R.string.firmware_default_text);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_start: {
                toggleStartButton();
                break;
            }
            
            case R.id.button_check_livestream: {
                showLiveStreamStatus();
                break;
            }

            case R.id.button_show_info: {
                showInfo();
                break;
            }
        }
    }

    private void toggleStartButton() {
        if (mBtnToggleStartStop.getText().toString() == getResources().getString(R.string.button_start)) {
            Log.v(TAG, "STARTING MOBILE BRIDGE");
            mEditRTMPServerURI.setEnabled(false);

            connectMQTTClient();
            startLivestream(this);
            startObstacleDetection();

            mBtnToggleStartStop.setText(R.string.button_stop);
            mBtnCheckLivestream.setVisibility(View.VISIBLE);
            mBtnShowInfo.setVisibility(View.VISIBLE);
        } else {
            Log.v(TAG, "STOPPING MOBILE BRIDGE");
            mEditRTMPServerURI.setEnabled(true);

            stopLivestream();
            disconnectMQTTClient();

            mBtnToggleStartStop.setText(R.string.button_start);
            mBtnCheckLivestream.setVisibility(View.GONE);
            mBtnShowInfo.setVisibility(View.GONE);
        }
    }

    @SuppressLint("NewApi")
    private void connectMQTTClient() {
        if (mMqttClient != null) {
            showToast("Connecting to MQTT server");
            Log.v(TAG, "Connecting to MQTT server");

            mMqttClient.connectWith()
                    .simpleAuth()
                    .username(mMqttUsername)
                    .password(mMqttPassword.getBytes())
                    .applySimpleAuth()
                    .send()
                    .whenComplete((mqtt3ConnAck, throwable) -> {
                        if (throwable != null) {
                            showToast("Failed to connect to MQTT server: " + throwable.toString());
                            Log.e(TAG, throwable.toString());
                        } else {
                            showToast("MQTT server connected");
                            Log.v(TAG, "MQTT server connected");
                        }
                    });
        } else {
            Log.e(TAG, "MQTT client have not been initialized");
        }
    }

    @SuppressLint("NewApi")
    private void disconnectMQTTClient() {
        if (mMqttClient != null) {
            showToast("Disconnecting from MQTT server");
            Log.v(TAG, "Disconnecting from MQTT server");

            mMqttClient.disconnect()
                    .whenComplete((ack, throwable) -> {
                        if (throwable != null) {
                            showToast("Failed to disconnect from MQTT server: " + throwable.toString());
                            Log.e(TAG, throwable.toString());
                        } else {
                            showToast("MQTT server disconnected");
                            Log.v(TAG, "MQTT server disconnected");
                        }
                    });
        } else {
            Log.e(TAG, "MQTT client have not been initialized");
        }
    }

    private void startLivestream(Context context) {
        showToast("Starting livestream to " + mRtmpServerURI);
        if (!isLiveStreamManagerOn()) {
            return;
        }
        if (DJISDKManager.getInstance().getLiveStreamManager().isStreaming()) {
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

    private void stopLivestream() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().stopStream();
        showToast("STOPPING LIVESTREAM");
    }

    private void startObstacleDetection() {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController =
                    ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController();

            FlightAssistant intelligentFlightAssistant = flightController.getFlightAssistant();

            if (intelligentFlightAssistant != null) {
                new Thread() {
                    @Override
                    public void run() {
                        intelligentFlightAssistant.setVisionDetectionStateUpdatedCallback(new VisionDetectionState.Callback() {
                            @Override
                            public void onUpdate(@NonNull VisionDetectionState visionDetectionState) {
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

                                mqttPublish(TOPIC_OBSTACLE_DISTANCE, obstacleDistances.toString());
                                mqttPublish(TOPIC_OBSTACLE_WARNING, obstacleWarnings.toString());
                            }
                        });
                    }
                }.start();
            }
        }
    }

    @SuppressLint("NewApi")
    private void mqttPublish(String topic, String payload) {
        mMqttClient.publishWith()
                .topic(topic)
                .payload(payload.getBytes())
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .whenComplete((pubMsg, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, throwable.toString());
                    } else {
                        Log.v(TAG, pubMsg.toString());
                    }
                });
    }

    private void showLiveStreamStatus() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        showToast("Is LiveStream On:" + DJISDKManager.getInstance().getLiveStreamManager().isStreaming());
    }

    private void showInfo() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Video BitRate:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoBitRate()).append(" kpbs\n");
        sb.append("Audio BitRate:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveAudioBitRate()).append(" kpbs\n");
        sb.append("Video FPS:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoFps()).append("\n");
        sb.append("Video Cache size:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoCacheSize()).append(" frame");
        sb.append("Video Resolution:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoResolution());

        showToast(sb.toString());
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
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }
}