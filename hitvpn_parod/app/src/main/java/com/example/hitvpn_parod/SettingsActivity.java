package com.example.hitvpn_parod;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView doneButton = findViewById(R.id.doneButton);
        doneButton.setOnClickListener(v -> finish());

        MaterialButton appSelectionButton = findViewById(R.id.appSelectionButton);
        MaterialButton manualSetupButton = findViewById(R.id.manualSetupButton);
        MaterialButton resetConfigButton = findViewById(R.id.resetConfigButton);
        MaterialButton supportButton = findViewById(R.id.supportButton);
        MaterialButton aboutButton = findViewById(R.id.aboutButton);

        appSelectionButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppSelectionActivity.class);
            startActivity(intent);
        });

        manualSetupButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManualSetupActivity.class);
            startActivity(intent);
        });

        resetConfigButton.setOnClickListener(v -> showResetConfirmationDialog());

        supportButton.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(""));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть Telegram", Toast.LENGTH_SHORT).show();
            }
        });

        aboutButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        });
    }

    private void showResetConfirmationDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
            .setTitle("Подтверждение")
            .setMessage("Вы уверены, что хотите сбросить конфигурацию?")
            .setPositiveButton("Да", (dialog, which) -> {
                SharedPreferences prefs = getSharedPreferences("VPNConfig", MODE_PRIVATE);
                prefs.edit().clear().apply();
                
                SharedPreferences serverPrefs = getSharedPreferences("ServerPrefs", MODE_PRIVATE);
                serverPrefs.edit()
                    .putString("selected_server", "Случайный, Автоматический выбор")
                    .apply();
                
                Intent intent = new Intent(SettingsActivity.this, VpnConfigActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("Нет", null)
            .setBackground(getResources().getDrawable(R.drawable.dialog_background));

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            TextView titleView = dialog.findViewById(android.R.id.title);
            if (titleView != null) {
                titleView.setTextColor(Color.WHITE);
            }
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setTextColor(Color.WHITE);
            }

            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(ContextCompat.getColor(this, R.color.blue));
            }
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(ContextCompat.getColor(this, R.color.blue));
            }
        });

        dialog.show();
    }
} 