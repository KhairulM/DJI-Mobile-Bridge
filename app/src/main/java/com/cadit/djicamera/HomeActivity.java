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
import com.cadit.djicamera.utilities.VideoFeedView;
import com.hivemq.client.mqtt.datatypes.MqttQos;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightMode;
import dji.common.flightcontroller.FlightOrientationMode;
import dji.common.flightcontroller.ObstacleDetectionSector;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
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
    private static final Integer DEFAULT_RTMP_PORT = 1935;

    // MQTT TOPICS
    private static final String TOPIC_OBSTACLE_DISTANCE = "dji/obstacle/distance";
    private static final String TOPIC_OBSTACLE_WARNING = "dji/obstacle/warning";
    private static final String TOPIC_CONTROL = "dji/control";
    private static final String TOPIC_CONTROL_TAKEOFF = "dji/control/takeoff";
    private static final String TOPIC_CONTROL_RTH = "dji/control/rth";
    private static final String TOPIC_CONTROL_LAND = "dji/control/land";
    private static final String TOPIC_CONTROL_TAKEOFF_RESULT = "dji/control/takeoff/result";
    private static final String TOPIC_CONTROL_RTH_RESULT = "dji/control/rth/result";
    private static final String TOPIC_CONTROL_LAND_RESULT = "dji/control/land/result";
    private static final String TOPIC_MODEL_NAME = "dji/model/name";
    private static final String TOPIC_STATUS_CONNECTION = "dji/status/connection";
    private static final String TOPIC_STATUS_FLIGHT_CONTROL = "dji/status/flight-control";
    private static final String TOPIC_STATUS_FLIGHT_MODE = "dji/status/flight-mode";
    private static final String TOPIC_STATUS_FLIGHT_TIME = "dji/status/flight-time";
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

    private VideoFeedView mPrimaryVideoFeedView;
    private TextView mTextProduct;
    private TextView mTextConnectionStatus;
    private TextView mTextFirmwareVersion;
    private TextView mTextSDKVersion;
    private EditText mEditIpAddress;
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
                                DJILog.d("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                Log.d(TAG, "onRegister: " + djiError.getDescription());
                                showToast("DJI SDK registration success");

                                DJISDKManager.getInstance().startConnectionToProduct();
                            } else {
                                Log.e(TAG, "onRegister: " + djiError.getDescription());
                                showToast( "DJI SDK registration fails, check if network is available");
                            }
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
                                        Log.d(TAG, "onConnectivityChanged: " + isConnected);
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

        mPrimaryVideoFeedView = (VideoFeedView) findViewById(R.id.video_feed_primary);

        mTextProduct = (TextView) findViewById(R.id.text_product_name);
        mTextConnectionStatus = (TextView) findViewById(R.id.text_product_status);
        mTextFirmwareVersion = (TextView) findViewById(R.id.text_firmware_version);

        mTextSDKVersion = (TextView) findViewById(R.id.text_sdk_version);
        mTextSDKVersion.setText("DJI SDK version: " + DJISDKManager.getInstance().getSDKVersion());

        mEditIpAddress = (EditText) findViewById(R.id.edit_text_ip_address);
        mEditMQTTPassword = (EditText) findViewById(R.id.edit_text_mqtt_password);
        mEditMQTTUsername = (EditText) findViewById(R.id.edit_text_mqtt_username);

        mBtnToggleConnect = (Button) findViewById(R.id.button_connect_disconnect);
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

        mEditIpAddress.addTextChangedListener(new CustomTextWatcher(mEditIpAddress) {
            @Override
            public void validate(TextView textView, String text) {
                mRtmpServerURI = String.format("rtmp://%s:%d/live/1234", text, DEFAULT_RTMP_PORT);
                mMqttBrokerURI= String.format("%s:%d", text, DEFAULT_MQTT_PORT);

                mIsRtmpURIValid = rtmpRegexp.matcher(mRtmpServerURI).matches();
                mIsMqttURIValid = mqttRegexp.matcher(mMqttBrokerURI).matches();

                refreshSDKRelativeUI();
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
        mBtnToggleConnect.setEnabled(
                isAircraftConnected() &&
                mIsRtmpURIValid &&
                mIsMqttURIValid);
    }

    private boolean isLiveStreamManagerOn() {
        if (DJISDKManager.getInstance().getLiveStreamManager() == null) {
            Log.e(TAG, "Livestream manager null");
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

    private boolean isFlightControlling() {
        return mBtnToggleControl.getText().toString() == getString(R.string.button_start_control);
    }

    private boolean isVirtualStickControlModeAvailable() {
        return isFlightControllerAvailable() &&
               mAircraft.getFlightController().isVirtualStickControlModeAvailable();
    }

    private boolean isLanding(){
        return isFlightControllerAvailable() &&
                mAircraft.getFlightController().getState().getFlightMode() == FlightMode.AUTO_LANDING;
    }

    private void onConnectionChange() {
        if (isAircraftConnected()) return;

        BaseProduct baseProduct = DJISDKManager.getInstance().getProduct();

        if (baseProduct != null && baseProduct.isConnected()) {
            try {
                Log.d(TAG, "onProductConnect");

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
                Log.d(TAG, "onProductDisconnect");
                gracefullyDisconnect();

                mAircraft = null;

                refreshSDKRelativeUI();
            } catch (Exception e) {
                showToast(e.toString());
                Log.e(TAG, e.toString());
            }
        }
    }

    private void refreshSDKRelativeUI() {
        if (isAircraftConnected()) {
            Log.d(TAG, "refreshSDK: True");

            setConnectButtonEnabled();
            mTextConnectionStatus.setText("Status: DJIAircraft connected");
            mTextConnectionStatus.setVisibility(View.VISIBLE);

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
                mPrimaryVideoFeedView.setVisibility(View.VISIBLE);
            } else {
                mBtnToggleConnect.setText(R.string.button_connect);

                mBtnToggleLivestream.setVisibility(View.GONE);
                mBtnToggleControl.setVisibility(View.GONE);
                mBtnCheckStatus.setVisibility(View.GONE);
                mPrimaryVideoFeedView.setVisibility(View.GONE);
            }
        } else {
            Log.d(TAG, "refreshSDK: False");
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
            mPrimaryVideoFeedView.setVisibility(View.GONE);
        }
    }

    private void gracefullyDisconnect() {
        publishStatusConnection(false);
        unregisterLivestreamListener();

        if (isLivestreaming()) {
            stopLivestream();
        }

        if (isFlightControlling()) {
            stopFlightControl();
        }

        if (!mMqttClient.disconnect()) {
            showToast("Failed to disconnect from MQTT broker");
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_connect_disconnect: {
                if (mBtnToggleConnect.getText().toString() == getResources().getString(R.string.button_connect)) {
                    Log.d(TAG, "onClickConnect");

                    if (mMqttClient != null) mMqttClient.disconnect();

                    mMqttClient = new CustomMqttClient(TAG, mMqttBrokerURI, mMqttPort);

                    if (!mMqttClient.connect(mMqttUsername, mMqttPassword)) {
                        showToast("Failed to connect to MQTT broker");
                        return;
                    }

                    registerLivestreamListener();
                    registerLiveVideoFeed();
                    setFlightControllerCallback();
                    setObstacleCallback();
                    setBatteryCallback();
                    publishModelName();
                    publishStatusConnection(true);

                    refreshSDKRelativeUI();
                } else {
                    Log.d(TAG, "onClickDisconnect");

                    gracefullyDisconnect();

                    refreshSDKRelativeUI();
                }
                break;
            }

            case R.id.button_start_livestream: {
                if (mBtnToggleLivestream.getText().toString() == getResources().getString(R.string.button_start_livestream)) {
                    Log.d(TAG, "onClickStartLivestream");

                    startLivestream(this);
                    mBtnToggleLivestream.setText(R.string.button_stop_livestream);
                } else {
                    Log.d(TAG, "onClickStopLivestream");

                    stopLivestream();
                    mBtnToggleLivestream.setText(R.string.button_start_livestream);
                }
                break;
            }

            case R.id.button_start_control: {
                if (mBtnToggleControl.getText().toString() == getResources().getString(R.string.button_start_control)) {
                    Log.d(TAG, "onClickStartFlightControl");

                    startFlightControl();
                    mBtnToggleControl.setText(R.string.button_stop_control);
                } else {
                    Log.d(TAG, "onClickStopFlightControl");

                    stopFlightControl();
                    mBtnToggleControl.setText(R.string.button_start_control);
                }
                break;
            }

            case R.id.button_check_status: {
                showToast("Is LiveStreamManager on: " + String.valueOf(isLiveStreamManagerOn()) + "\n" +
                "Is livestreaming: " + String.valueOf(isLivestreaming()) + "\n" +
                "Is MQTT broker connected: " + String.valueOf(isMqttConnected()) + "\n" +
                "Is flight controller available: " + String.valueOf(isFlightControllerAvailable()) + "\n" +
                "Is virtual stick control enabled: " + String.valueOf(mIsVirtualStickControlModeEnabled.get()) + "\n" +
                "Is virtual stick control available: " + String.valueOf(isVirtualStickControlModeAvailable()));
                break;
            }

            default:
                break;
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

    private void registerLiveVideoFeed() {
        if (VideoFeeder.getInstance() == null) return;

        mPrimaryVideoFeedView.registerLiveVideo(VideoFeeder.getInstance().getPrimaryVideoFeed(), true);
    }

    private void setVirtualStickControlModeEnabled(boolean enabled) {
        if (!isFlightControllerAvailable()) return;
        if (mIsVirtualStickControlModeEnabled.get() == enabled) return;

        mAircraft.getFlightController().setFlightOrientationMode(FlightOrientationMode.AIRCRAFT_HEADING, djiError -> {
            if (djiError != null) {
                Log.e(TAG, "setFlightOrientationMode: " + djiError.getDescription());
                showToast("Failed to set flight orientation mode: " + djiError.getDescription());
            }
        });

        // enabling virtual stick control mode
        mAircraft.getFlightController().setVirtualStickModeEnabled(enabled, djiError -> {
            if (djiError != null) {
                Log.e(TAG, "setVirtualStickControlModeEnabled: " + djiError.getDescription());
                showToast("Failed to set virtual stick control mode: " + djiError.getDescription());
            } else {
                mIsVirtualStickControlModeEnabled.compareAndSet(!enabled, enabled);
                publishStatusFlightControl(enabled);
            }
        });
    }

    private void setMotorsState(boolean state) {
        if (!isFlightControllerAvailable()) return;

        if (state) {
            mAircraft.getFlightController().turnOnMotors(djiError -> {
                if (djiError != null) {
                    Log.e(TAG, "setMotorsState: " + djiError.getDescription());
                    showToast("Failed to turn on motors: " + djiError.getDescription());
                }
            });
        } else {
            mAircraft.getFlightController().turnOffMotors(djiError -> {
                if (djiError != null) {
                    Log.e(TAG, "setMotorsState: " + djiError.getDescription());
                    showToast("Failed to turn off motors: " + djiError.getDescription());
                }
            });
        }
    }

    private void setFlightControllerCallback() {
        if (!isFlightControllerAvailable()) return;

        FlightController fc = mAircraft.getFlightController();
        fc.setStateCallback(flightControllerState -> {
            if (!isMqttConnected()) return;

            // check if landing confirmation needed
            if (isLanding() && flightControllerState.isLandingConfirmationNeeded()) {
                mAircraft.getFlightController().confirmLanding((djiError) -> {
                    if (djiError != null) {
                        showToast("Failed to confirm landing: " + djiError.getDescription());
                        Log.e(TAG, "setFlightControllerCallback: Failed to confirm landing: " + djiError.getDescription());
                    } else {
                        Log.d(TAG, "setFlightControllerCallback: Landing completed");
                        mMqttClient.publish(TOPIC_CONTROL_LAND_RESULT, "completed", MqttQos.EXACTLY_ONCE);
                        setMotorsState(false);
                    }
                });
            }

            // publish status topic
            mMqttClient.publish(TOPIC_STATUS_ALTITUDE, String.valueOf(flightControllerState.getAircraftLocation().getAltitude()), MqttQos.EXACTLY_ONCE);
            mMqttClient.publish(TOPIC_STATUS_VERTICAL_SPEED, String.valueOf(flightControllerState.getVelocityZ()), MqttQos.EXACTLY_ONCE);

            List<Float> horSpeed = Arrays.asList(flightControllerState.getVelocityX(), flightControllerState.getVelocityY());
            mMqttClient.publish(TOPIC_STATUS_HORIZONTAL_SPEED, horSpeed.toString(), MqttQos.EXACTLY_ONCE);

            // publish flight mode and flight time
            mMqttClient.publish(TOPIC_STATUS_FLIGHT_MODE, flightControllerState.getFlightModeString());
            mMqttClient.publish(TOPIC_STATUS_FLIGHT_TIME, String.valueOf(flightControllerState.getFlightTimeInSeconds()));
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
                obstacleWarnings.add(visionDetectionSector.getWarningLevel().toString());
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

    private void publishStatusFlightControl(boolean state) {
        if (!isMqttConnected()) return;

        mMqttClient.publish(TOPIC_STATUS_FLIGHT_CONTROL, String.valueOf(state));
    }

    private void publishModelName() {
        if (!isMqttConnected() || !isAircraftConnected()) return;

        mMqttClient.publish(TOPIC_MODEL_NAME, mAircraft.getModel().getDisplayName());
    }

    void startLivestream(Context context) {
        showToast("Starting livestream to " + mRtmpServerURI);

        if (!isLiveStreamManagerOn()) return;

        if (isLivestreaming()) {
            showToast("Livestream already started: " + mRtmpServerURI);
            return;
        }

        Log.d(TAG, "LIVESTREAM START");

        new Thread() {
            @Override
            public void run () {
                DJISDKManager.getInstance().getLiveStreamManager().setVideoSource(LiveStreamManager.LiveStreamVideoSource.Primary);
                DJISDKManager.getInstance().getLiveStreamManager().setAudioStreamingEnabled(false);
                DJISDKManager.getInstance().getLiveStreamManager().setVideoEncodingEnabled(false);
                DJISDKManager.getInstance().getLiveStreamManager().setLiveUrl(mRtmpServerURI);
                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoResolution(LiveVideoResolution.VIDEO_RESOLUTION_960_720);
                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoBitRateMode(LiveVideoBitRateMode.AUTO);
                int result = DJISDKManager.getInstance().getLiveStreamManager().startStream();
                DJISDKManager.getInstance().getLiveStreamManager().setStartTime();
                context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).edit().putString(URL_KEY, mRtmpServerURI).commit();
                showToast(DJISDKManager.getInstance().getLiveStreamManager().getLiveUrl() + ": " + result + "\n" +
                        "isVideoStreamSpeedConfigurable: " + DJISDKManager.getInstance().getLiveStreamManager().isVideoStreamSpeedConfigurable() + "\n" +
                        "isLiveAudioEnabled: " + DJISDKManager.getInstance().getLiveStreamManager().isLiveAudioEnabled());
            }
        }.start();
    }

    private void stopLivestream() {
        if (!isLiveStreamManagerOn()) return;

        showToast("Stopping livestream");
        DJISDKManager.getInstance().getLiveStreamManager().stopStream();

        Log.d(TAG, "LIVESTREAM STOPPED");
    }

    private void startFlightControl() {
        showToast("Starting flight control");

        setVirtualStickControlModeEnabled(true);

        // set control mode
        FlightController fc = mAircraft.getFlightController();
        fc.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
        fc.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        fc.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        fc.setVerticalControlMode(VerticalControlMode.POSITION);

        mMqttClient.subscribe(TOPIC_CONTROL, (message) -> {
            final String payload = new String(message.getPayloadAsBytes(), StandardCharsets.UTF_8);

            Log.d(TAG, "startFlightControl: Control received: " + payload);

            String[] strControls = payload.substring(1, payload.length()-1).split(",");

            FlightControlData newControl = new FlightControlData(
                    Float.parseFloat(strControls[0]),
                    Float.parseFloat(strControls[1]),
                    Float.parseFloat(strControls[2]),
                    Float.parseFloat(strControls[3])
            );

            mAircraft.getFlightController().sendVirtualStickFlightControlData(newControl, djiError -> {
                if (djiError != null) {
                    showToast("Failed to send virtual stick control data: " + djiError.getDescription());
                    Log.e(TAG, "startFlightControl: Failed to send virtual stick control data: " + djiError.getDescription());
                } else {
                    Log.d(TAG, "startFlightControl: Control sent: " + payload);
                }
            });
        });

        mMqttClient.subscribe(TOPIC_CONTROL_TAKEOFF, (message) -> {
            final String payload = new String(message.getPayloadAsBytes(), StandardCharsets.UTF_8).toLowerCase();

            Log.d(TAG, "startFlightControl: Takeoff command received: " + payload);

            if (payload.equals("true")) {
                mMqttClient.publish(TOPIC_CONTROL_TAKEOFF_RESULT, "started", MqttQos.EXACTLY_ONCE);

                mAircraft.getFlightController().startTakeoff(djiError -> {
                    if (djiError != null) {
                        mMqttClient.publish(TOPIC_CONTROL_TAKEOFF_RESULT, "failed", MqttQos.EXACTLY_ONCE);

                        Log.e(TAG, "startFlightControl: Failed to start takeoff: " + djiError.getDescription());
                        showToast("Failed to start takeoff: " + djiError.getDescription());
                    } else {
                        mMqttClient.publish(TOPIC_CONTROL_TAKEOFF_RESULT, "completed", MqttQos.EXACTLY_ONCE);

                        Log.d(TAG, "startFlightControl: Takeoff complete");
                    }
                });
            }
        });

        mMqttClient.subscribe(TOPIC_CONTROL_RTH, (message) -> {
            final String payload = new String(message.getPayloadAsBytes(), StandardCharsets.UTF_8).toLowerCase();

            Log.d(TAG, "startFlightControl: RTH command received: " + payload);

            if (payload.equals("true")) {
                mMqttClient.publish(TOPIC_CONTROL_RTH_RESULT, "started", MqttQos.EXACTLY_ONCE);

                mAircraft.getFlightController().startGoHome(djiError -> {
                    if (djiError != null) {
                        mMqttClient.publish(TOPIC_CONTROL_RTH_RESULT, "failed", MqttQos.EXACTLY_ONCE);

                        Log.e(TAG, "startFlightControl: Failed to start go home: " + djiError.getDescription());
                        showToast("Failed to start go home: " + djiError.getDescription());
                    } else {
                        mMqttClient.publish(TOPIC_CONTROL_RTH_RESULT, "completed", MqttQos.EXACTLY_ONCE);

                        Log.d(TAG, "startFlightControl: Starting to go home");
                    }
                });
            }
        });

        mMqttClient.subscribe(TOPIC_CONTROL_LAND, (message) -> {
            final String payload = new String(message.getPayloadAsBytes(), StandardCharsets.UTF_8).toLowerCase();

            Log.d(TAG, "startFlightControl: Land command received: " + payload);

            if (payload.equals("true")) {
                mMqttClient.publish(TOPIC_CONTROL_LAND_RESULT, "started", MqttQos.EXACTLY_ONCE);

                mAircraft.getFlightController().startLanding(djiError -> {
                    if (djiError != null) {
                        mMqttClient.publish(TOPIC_CONTROL_LAND_RESULT, "failed", MqttQos.EXACTLY_ONCE);

                        Log.e(TAG, "startFlightControl: Failed to start landing: " + djiError.getDescription());
                        showToast("Failed to start landing: " + djiError.getDescription());
                    } else {
                        Log.d(TAG, "startFlightControl: Starting to land");
                    }
                });
            }
        });
    }

    private void stopFlightControl() {
        showToast("Stopping flight control");

        setVirtualStickControlModeEnabled(false);

        mMqttClient.unsubscribe(TOPIC_CONTROL);
        mMqttClient.unsubscribe(TOPIC_CONTROL_TAKEOFF);
        mMqttClient.unsubscribe(TOPIC_CONTROL_RTH);
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
        gracefullyDisconnect();

        if (isMqttConnected()) mMqttClient.disconnect();

        super.onDestroy();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    private void showToast(final String toastMsg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });
    }
}