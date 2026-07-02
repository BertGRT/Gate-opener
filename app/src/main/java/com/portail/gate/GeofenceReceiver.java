package com.portail.gate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

public class GeofenceReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) return;
        if (event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) return;

        final PendingResult pending = goAsync();
        try {
            checkBtAndOpen(ctx, pending);
        } catch (Exception e) {
            pending.finish();
        }
    }

    private void checkBtAndOpen(final Context ctx, final PendingResult pending) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        if (adapter == null) {
            pending.finish();
            return;
        }

        adapter.getProfileProxy(ctx, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                boolean allowed = false;
                try {
                    SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
                    String[] names = p.getString("btNames", "").split(",");
                    List<BluetoothDevice> devices = proxy.getConnectedDevices();
                    for (BluetoothDevice d : devices) {
                        String dn = d.getName();
                        if (dn == null) continue;
                        for (String n : names) {
                            String nt = n.trim();
                            if (!nt.isEmpty() && dn.trim().equalsIgnoreCase(nt)) {
                                allowed = true;
                            }
                        }
                    }
                } catch (SecurityException se) {
                    // Permission BLUETOOTH_CONNECT non accordee
                } finally {
                    adapter.closeProfileProxy(profile, proxy);
                }

                if (allowed) {
                    new Thread(() -> {
                        SinricClient.open(ctx);
                        pending.finish();
                    }).start();
                } else {
                    pending.finish();
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                pending.finish();
            }
        }, BluetoothProfile.A2DP);
    }
}
