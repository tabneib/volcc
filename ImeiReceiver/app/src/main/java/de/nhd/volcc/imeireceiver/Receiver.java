package de.nhd.volcc.imeireceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receiver for the intent sent by the sender that carries information about the sync time point.
 * Created by nhd on 21.07.15.
 */
public class Receiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        String appointment = intent.getStringExtra("de.nhd.volcc.Appointment");

        // Trigger the receiving process
        MainActivity.receiveSyncMes(Long.parseLong(appointment));
    }
}
