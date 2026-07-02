package com.portail.gate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        // Re-enregistre la geofence apres un redemarrage du telephone
        MainActivity.registerGeofence(ctx);
    }
}
