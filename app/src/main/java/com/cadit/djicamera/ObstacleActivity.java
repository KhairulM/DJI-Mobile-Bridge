package com.cadit.djicamera;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cadit.djicamera.controller.DJISampleApplication;
import com.cadit.djicamera.utilities.ModuleVerificationUtil;

import dji.common.flightcontroller.ObstacleDetectionSector;
import dji.common.flightcontroller.VisionDetectionState;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;

public class ObstacleActivity extends Activity {
    private TextView textDescription;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);
        initUI();
        initUpdater();
    }

    private void initUpdater() {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController =
                    ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController();

            FlightAssistant intelligentFlightAssistant = flightController.getFlightAssistant();

            if (intelligentFlightAssistant != null) {

                intelligentFlightAssistant.setVisionDetectionStateUpdatedCallback(new VisionDetectionState.Callback() {
                    @Override
                    public void onUpdate(@NonNull VisionDetectionState visionDetectionState) {
                        StringBuilder stringBuilder = new StringBuilder();

                        ObstacleDetectionSector[] visionDetectionSectorArray =
                                visionDetectionState.getDetectionSectors();

                        for (ObstacleDetectionSector visionDetectionSector : visionDetectionSectorArray) {

                            visionDetectionSector.getObstacleDistanceInMeters();
                            visionDetectionSector.getWarningLevel();

                            stringBuilder.append("Obstacle distance: ")
                                    .append(visionDetectionSector.getObstacleDistanceInMeters())
                                    .append("\n");
                            stringBuilder.append("Distance warning: ")
                                    .append(visionDetectionSector.getWarningLevel())
                                    .append("\n");
                        }

                        stringBuilder.append("WarningLevel: ")
                                .append(visionDetectionState.getSystemWarning().name())
                                .append("\n");
                        stringBuilder.append("Sensor state: ")
                                .append(visionDetectionState.isSensorBeingUsed())
                                .append("\n");

//                        changeDescription(stringBuilder.toString());
                        textDescription.setText(stringBuilder.toString());

                    }
                });
            }
        } else {
            textDescription.setText("onAttachedToWindow FC NOT Available");
        }
    }

    private void initUI() {
        textDescription = (TextView) findViewById(R.id.textSensorRead);
    }


}
