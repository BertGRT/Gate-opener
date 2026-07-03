package com.portail.gate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        // Relance la surveillance apres un redemarrage du telephone
        MainActivity.startMonitoring(ctx);
    }
}
