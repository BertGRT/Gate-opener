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

import java.util.ArrayList;
import java.util.List;

public class GeofenceReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            Notif.show(ctx, "Portail", "Geofence erreur: " + (event != null ? event.getErrorCode() : "event null"));
            return;
        }
        if (event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) return;

        Notif.show(ctx, "Portail", "Geofence: ENTREE detectee");

        final PendingResult pending = goAsync();
        try {
            checkBtAndOpen(ctx, pending);
        } catch (Exception e) {
            Notif.show(ctx, "Portail", "Erreur BT: " + e.getMessage());
            pending.finish();
        }
    }

    private void checkBtAndOpen(final Context ctx, final PendingResult pending) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        if (adapter == null) {
            Notif.show(ctx, "Portail", "Bluetooth indisponible");
            pending.finish();
            return;
        }

        adapter.getProfileProxy(ctx, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                boolean allowed = false;
                List<String> found = new ArrayList<>();
                try {
                    SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
                    String[] names = p.getString("btNames", "").split(",");
                    for (BluetoothDevice d : proxy.getConnectedDevices()) {
                        String dn;
                        try {
                            dn = d.getName();
                        } catch (SecurityException se) {
                            dn = null;
                        }
                        if (dn == null) dn = d.getAddress();
                        found.add(dn);
                        for (String n : names) {
                            String nt = n.trim();
                            if (!nt.isEmpty() && dn.trim().equalsIgnoreCase(nt)) allowed = true;
                        }
                    }
                } catch (SecurityException se) {
                    Notif.show(ctx, "Portail", "Permission BLUETOOTH_CONNECT manquante");
                } finally {
                    adapter.closeProfileProxy(profile, proxy);
                }

                Notif.show(ctx, "Portail", "BT A2DP connectes: " + found + " -> autorise: " + (allowed ? "OUI" : "NON"));

                if (allowed) {
                    new Thread(() -> {
                        String r = SinricClient.open(ctx);
                        Notif.show(ctx, "Portail", "Ouverture envoyee: " + r);
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
