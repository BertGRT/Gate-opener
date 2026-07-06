package com.portail.gate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class BtScan {

    interface Callback {
        void onResult(Set<String> names);
    }

    // Renvoie (via callback) les noms des appareils BT connectes (profils A2DP + HEADSET)
    static void connectedNames(final Context ctx, final Callback cb) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        if (adapter == null) {
            cb.onResult(new HashSet<>());
            return;
        }
        final Set<String> found = new HashSet<>();
        final int[] pending = {2};
        final boolean[] done = {false};
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
                        // ignore
                    } finally {
                        adapter.closeProfileProxy(profile, proxy);
                    }
                    one(found, pending, done, cb);
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    one(found, pending, done, cb);
                }
            }, prof);
            if (!started) one(found, pending, done, cb);
        }
    }

    private static synchronized void one(Set<String> found, int[] pending, boolean[] done, Callback cb) {
        pending[0]--;
        if (pending[0] > 0) return;
        if (done[0]) return;
        done[0] = true;
        cb.onResult(found);
    }

    // Un des appareils connectes est-il dans btNames OU btNamesGarage ?
    static boolean anyAuthorized(Context ctx, Set<String> found) {
        SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        Set<String> allowed = new HashSet<>();
        String csv = p.getString("btNames", "") + "," + p.getString("btNamesGarage", "");
        for (String s : csv.split(",")) {
            String t = s.trim().toLowerCase();
            if (!t.isEmpty()) allowed.add(t);
        }
        for (String f : found) {
            if (allowed.contains(f.trim().toLowerCase())) return true;
        }
        return false;
    }
}
