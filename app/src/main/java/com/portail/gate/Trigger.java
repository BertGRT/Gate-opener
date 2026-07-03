package com.portail.gate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class Trigger {

    // Verifie les Bluetooth connectes sur les profils A2DP (audio) ET HEADSET (mains-libres)
    // puis ouvre si un appareil autorise est present.
    static void checkBtAndOpen(final Context ctx) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        if (adapter == null) {
            Notif.show(ctx, "Portail", "Bluetooth indisponible");
            return;
        }

        final Set<String> found = new HashSet<>();
        final int[] pending = {2};
        int[] profiles = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET};

        for (int prof : profiles) {
            boolean started = adapter.getProfileProxy(ctx, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    try {
                        for (BluetoothDevice d : proxy.getConnectedDevices()) {
                            String dn;
                            try {
                                dn = d.getName();
                            } catch (SecurityException se) {
                                dn = null;
                            }
                            if (dn == null) dn = d.getAddress();
                            found.add(dn);
                        }
                    } catch (SecurityException se) {
                        Notif.show(ctx, "Portail", "Permission BLUETOOTH_CONNECT manquante");
                    } finally {
                        adapter.closeProfileProxy(profile, proxy);
                    }
                    done(ctx, found, pending);
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    done(ctx, found, pending);
                }
            }, prof);

            if (!started) {
                done(ctx, found, pending);
            }
        }
    }

    private static void done(Context ctx, Set<String> found, int[] pending) {
        pending[0]--;
        if (pending[0] > 0) return; // on attend les 2 profils

        SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        String[] names = p.getString("btNames", "").split(",");
        boolean allowed = false;
        for (String dn : found) {
            for (String n : names) {
                String nt = n.trim();
                if (!nt.isEmpty() && dn.trim().equalsIgnoreCase(nt)) allowed = true;
            }
        }

        Notif.show(ctx, "Portail", "BT connectes: " + found + " -> autorise: " + (allowed ? "OUI" : "NON"));

        if (allowed) {
            new Thread(() -> {
                String r = SinricClient.open(ctx);
                Notif.show(ctx, "Portail", "Ouverture envoyee: " + r);
            }).start();
        }
    }
}
