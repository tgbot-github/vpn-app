package com.example.hitvpn_parod;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView doneButton = findViewById(R.id.doneButton);
        doneButton.setOnClickListener(v -> finish());

        MaterialButton privacyPolicyButton = findViewById(R.id.privacyPolicyButton);
        MaterialButton termsButton = findViewById(R.id.termsButton);

        privacyPolicyButton.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(""));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть Браузер", Toast.LENGTH_SHORT).show();
            }
        });

        termsButton.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(""));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть Браузер", Toast.LENGTH_SHORT).show();
            }
        });
    }
} 