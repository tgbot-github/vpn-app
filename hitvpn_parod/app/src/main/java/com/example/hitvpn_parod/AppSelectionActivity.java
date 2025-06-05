package com.example.hitvpn_parod;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AppSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        TextView doneButton = findViewById(R.id.doneButton);
        doneButton.setOnClickListener(v -> finish());
    }
} 