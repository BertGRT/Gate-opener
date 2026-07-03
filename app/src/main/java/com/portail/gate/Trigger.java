package com.portail.gate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class Trigger {

    // Verifie les Bluetooth connectes (A2DP) et ouvre si un appareil autorise est present
    static void checkBtAndOpen(final Context ctx) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        if (adapter == null) {
            Notif.show(ctx, "Portail", "Bluetooth indisponible");
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

                Notif.show(ctx, "Portail", "BT connectes: " + found + " -> autorise: " + (allowed ? "OUI" : "NON"));

                if (allowed) {
                    new Thread(() -> {
                        String r = SinricClient.open(ctx);
                        Notif.show(ctx, "Portail", "Ouverture envoyee: " + r);
                    }).start();
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
            }
        }, BluetoothProfile.A2DP);
    }
}
