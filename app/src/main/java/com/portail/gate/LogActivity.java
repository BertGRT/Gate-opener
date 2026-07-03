package com.portail.gate;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class LogActivity extends Activity {

    private TextView tv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_log);
        tv = findViewById(R.id.logText);
        ((Button) findViewById(R.id.btnRefresh)).setOnClickListener(v -> refresh());
        ((Button) findViewById(R.id.btnClear)).setOnClickListener(v -> {
            Journal.clear(this);
            refresh();
        });
        refresh();
    }

    private void refresh() {
        tv.setText(Journal.read(this));
    }
}
