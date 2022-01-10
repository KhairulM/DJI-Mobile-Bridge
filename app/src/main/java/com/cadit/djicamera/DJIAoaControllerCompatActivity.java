package com.cadit.djicamera;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;

import dji.sdk.sdkmanager.DJISDKManager;

public class DJIAoaControllerCompatActivity extends Activity {

    /**
     * Constructor.
     */
    public DJIAoaControllerCompatActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new View(this));

        Intent aoaIntent = getIntent();
        if (aoaIntent!=null) {
            String action = aoaIntent.getAction();
            if (action== UsbManager.ACTION_USB_ACCESSORY_ATTACHED) {
                Intent attachedIntent=new Intent();
                attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
                sendBroadcast(attachedIntent);
            }
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}