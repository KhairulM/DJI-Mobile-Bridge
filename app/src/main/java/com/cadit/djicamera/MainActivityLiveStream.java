package com.cadit.djicamera;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.cadit.djicamera.utilities.VideoFeedView;
import com.cadit.djicamera.view.PresentableView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;
import dji.sdk.sdkmanager.LiveVideoBitRateMode;
import dji.sdk.sdkmanager.LiveVideoResolution;

public class MainActivityLiveStream extends AppCompatActivity implements View.OnClickListener, PresentableView {

    private String liveShowUrl = "rtmp://192.168.18.19:1935/live/streamkey1ABC";

    private VideoFeedView primaryVideoFeedView;
    private EditText showUrlInputEdit;

    private Button startLiveShowBtn;
    private Button stopLiveShowBtn;

    private Button soundOnBtn;
    private Button soundOffBtn;

    private Button isLiveShowOnBtn;
    private Button showInfoBtn;
    private Button showLiveStartTimeBtn;

    private LiveStreamManager.OnLiveChangeListener listener;
    private static final String URL_KEY = "sp_stream_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_live_stream);
        initUI(this);
        initListener();
    }

    private void initUI(Context context) {
        liveShowUrl = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).getString(URL_KEY, liveShowUrl);

        primaryVideoFeedView = (VideoFeedView) findViewById(R.id.video_view_primary_video_feed);
        primaryVideoFeedView.registerLiveVideo(VideoFeeder.getInstance().getPrimaryVideoFeed(), true);

        showUrlInputEdit = (EditText) findViewById(R.id.edit_live_show_url_input);
        showUrlInputEdit.setText(liveShowUrl);

        startLiveShowBtn = (Button) findViewById(R.id.btn_start_live_show);
        startLiveShowBtn.setOnClickListener(this);

        stopLiveShowBtn = (Button) findViewById(R.id.btn_stop_live_show);
        stopLiveShowBtn.setOnClickListener(this);

        soundOnBtn = (Button) findViewById(R.id.btn_sound_on);
        soundOnBtn.setOnClickListener(this);

        soundOffBtn = (Button) findViewById(R.id.btn_sound_off);
        soundOffBtn.setOnClickListener(this);

        isLiveShowOnBtn = (Button) findViewById(R.id.btn_is_live_show_on);
        isLiveShowOnBtn.setOnClickListener(this);

        showInfoBtn = (Button) findViewById(R.id.btn_show_info);
        showInfoBtn.setOnClickListener(this);

        showLiveStartTimeBtn = (Button) findViewById(R.id.btn_show_live_start_time);
        showLiveStartTimeBtn.setOnClickListener(this);
    }

    private void initListener() {
        showUrlInputEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                liveShowUrl = s.toString();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        listener = new LiveStreamManager.OnLiveChangeListener() {
            @Override
            public void onStatusChanged(int i) {
                Log.d("LIVESTREAM", "status changed : " + i);
            }
        };
    }

    private boolean isLiveStreamManagerOn() {
        if (DJISDKManager.getInstance().getLiveStreamManager() == null) {
            Toast.makeText(this, "LIVE STREAM NULL", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        BaseProduct product = com.cadit.djicamera.controller.DJISampleApplication.getProductInstance();
        if (product == null || !product.isConnected()) {
            Toast.makeText(this, "DISCONNECT", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isLiveStreamManagerOn()){
            DJISDKManager.getInstance().getLiveStreamManager().registerListener(listener);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isLiveStreamManagerOn()){
            DJISDKManager.getInstance().getLiveStreamManager().unregisterListener(listener);
        }
    }

    @Override
    public int getDescription() {
        return R.string.component_listview_live_stream;
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    void startLiveShow(Context context) {
        Toast.makeText(this, "Start Live Show" + liveShowUrl, Toast.LENGTH_SHORT).show();
        if (!isLiveStreamManagerOn()) {
            return;
        }
        if (DJISDKManager.getInstance().getLiveStreamManager().isStreaming()) {
            Log.d("START LIVE SHOW", liveShowUrl);
            return;
        }
        new Thread() {
            @Override
            public void run() {
                DJISDKManager.getInstance().getLiveStreamManager().setLiveUrl(liveShowUrl);
                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoResolution(LiveVideoResolution.VIDEO_RESOLUTION_960_720);
                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoBitRateMode(LiveVideoBitRateMode.AUTO);
                int result = DJISDKManager.getInstance().getLiveStreamManager().startStream();
                DJISDKManager.getInstance().getLiveStreamManager().setStartTime();
                context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).edit().putString(URL_KEY, liveShowUrl).commit();
                Log.d("START LIVE SHOW", "startLive:" + result +
                        "\n isVideoStreamSpeedConfigurable:" + DJISDKManager.getInstance().getLiveStreamManager().isVideoStreamSpeedConfigurable() +
                        "\n isLiveAudioEnabled:" + DJISDKManager.getInstance().getLiveStreamManager().isLiveAudioEnabled());
            }
        }.start();
    }

    private void stopLiveShow() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().stopStream();
        Toast.makeText(this, "STOP LIVE SHOW", Toast.LENGTH_SHORT).show();
    }

    private void soundOn() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().setAudioMuted(false);
        Toast.makeText(this, "SOUND ON", Toast.LENGTH_SHORT).show();
    }

    private void soundOff() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().setAudioMuted(true);
        Toast.makeText(this, "SOUND OFF", Toast.LENGTH_SHORT).show();
    }

    private void isLiveShowOn() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        Toast.makeText(this, "Is Live Show On:" + DJISDKManager.getInstance().getLiveStreamManager().isStreaming(), Toast.LENGTH_SHORT).show();
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

        Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
    }

    private void showLiveStartTime() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        if (!DJISDKManager.getInstance().getLiveStreamManager().isStreaming()){
            Toast.makeText(this, "Please Start Live First", Toast.LENGTH_LONG).show();
            return;
        }
        long startTime = DJISDKManager.getInstance().getLiveStreamManager().getStartTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String sd = sdf.format(new Date(Long.parseLong(String.valueOf(startTime))));

        Toast.makeText(this, "Live Start Time: " + sd, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start_live_show:
                startLiveShow(this);
                break;
            case R.id.btn_stop_live_show:
                stopLiveShow();
                break;
            case R.id.btn_sound_on:
                soundOn();
                break;
            case R.id.btn_sound_off:
                soundOff();
                break;
            case R.id.btn_is_live_show_on:
                isLiveShowOn();
                break;
            case R.id.btn_show_info:
                showInfo();
                break;
            case R.id.btn_show_live_start_time:
                showLiveStartTime();
                break;

            default:
                break;
        }
    }
}