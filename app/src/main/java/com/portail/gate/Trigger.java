package com.portail.gate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.HashSet;
import java.util.Set;

public class Trigger {

    private static long lastOpenMs = 0;
    private static final long COOLDOWN_MS = 60000;

    // Verifie les Bluetooth connectes (profils A2DP audio ET HEADSET mains-libres)
    // puis ouvre si un appareil autorise est present. Anti-rebond de 60 s.
    static void checkBtAndOpen(final Context ctx) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        if (adapter == null) {
            Notif.show(ctx, "Portail", "Bluetooth indisponible");
            return;
        }

        final Set<String> found = new HashSet<>();
        final int[] pending = {2};
        final boolean[] finished = {false};
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
                    done(ctx, found, pending, finished);
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    done(ctx, found, pending, finished);
                }
            }, prof);

            if (!started) done(ctx, found, pending, finished);
        }
    }

    // Finalise UNE seule fois, quand les 2 profils ont repondu
    private static synchronized void done(Context ctx, Set<String> found, int[] pending, boolean[] finished) {
        pending[0]--;
        if (pending[0] > 0) return;
        if (finished[0]) return;
        finished[0] = true;

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

        if (!allowed) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastOpenMs < COOLDOWN_MS) {
            Notif.show(ctx, "Portail", "Ouverture ignoree (anti-rebond < 60s)");
            return;
        }
        lastOpenMs = now;

        new Thread(() -> {
            String r = SinricClient.open(ctx);
            Notif.show(ctx, "Portail", "Ouverture envoyee: " + r);
        }).start();
    }
}
