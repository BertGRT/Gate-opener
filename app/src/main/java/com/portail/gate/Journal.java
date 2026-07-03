package com.portail.gate;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Journal {

    private static final String FILE = "journal.txt";

    static synchronized void add(Context ctx, String msg) {
        try {
            String ts = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            File f = new File(ctx.getFilesDir(), FILE);
            FileWriter w = new FileWriter(f, true);
            w.write("[" + ts + "] " + msg + "\n");
            w.close();
            if (f.length() > 60000) trim(ctx, f);
        } catch (Exception e) {
            // ignore
        }
    }

    private static void trim(Context ctx, File f) {
        try {
            String[] lines = read(ctx).split("\n");
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, lines.length - 200);
            for (int i = start; i < lines.length; i++) sb.append(lines[i]).append("\n");
            FileWriter w = new FileWriter(f, false);
            w.write(sb.toString());
            w.close();
        } catch (Exception e) {
            // ignore
        }
    }

    static String read(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return "(journal vide)";
            FileInputStream in = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            int n = in.read(data);
            in.close();
            String s = new String(data, 0, Math.max(0, n), "UTF-8");
            return s.isEmpty() ? "(journal vide)" : s;
        } catch (Exception e) {
            return "Erreur lecture journal: " + e.getMessage();
        }
    }

    static void clear(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (f.exists()) f.delete();
        } catch (Exception e) {
            // ignore
        }
    }
}
