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

    private static long lastActionMs = 0;
    private static final long COOLDOWN_MS = 60000;

    // action = "ouverture" ou "fermeture" (meme impulsion envoyee au portail, juste pour le journal)
    static void checkBtAndOpen(final Context ctx, final String action) {
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
                    done(ctx, found, pending, finished, action);
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    done(ctx, found, pending, finished, action);
                }
            }, prof);

            if (!started) done(ctx, found, pending, finished, action);
        }
    }

    private static synchronized void done(Context ctx, Set<String> found, int[] pending, boolean[] finished, String action) {
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

        Notif.show(ctx, "Portail", "[" + action + "] BT connectes: " + found + " -> autorise: " + (allowed ? "OUI" : "NON"));

        if (!allowed) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastActionMs < COOLDOWN_MS) {
            Notif.show(ctx, "Portail", "[" + action + "] ignoree (anti-rebond < 60s)");
            return;
        }
        lastActionMs = now;

        new Thread(() -> {
            String r = SinricClient.open(ctx);
            Notif.show(ctx, "Portail", action + " envoyee: " + r);
        }).start();
    }
}
