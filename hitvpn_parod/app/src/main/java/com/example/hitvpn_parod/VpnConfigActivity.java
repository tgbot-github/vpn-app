package com.example.hitvpn_parod;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import android.graphics.Color;
import android.widget.Button;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class VpnConfigActivity extends AppCompatActivity {

    private TextInputEditText configInput;
    private MaterialButton loginButton;
    private TextView helpLink;
    private TextInputLayout configInputLayout;
    private OkHttpClient client;
    private static final String BASE_URL = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vpn_config);

        client = new OkHttpClient();

        configInput = findViewById(R.id.configInput);
        loginButton = findViewById(R.id.loginButton);
        helpLink = findViewById(R.id.helpLink);
        configInputLayout = findViewById(R.id.configInputLayout);

        configInputLayout.setBoxStrokeColor(getResources().getColor(R.color.white));

        configInputLayout.setBoxStrokeWidth(1);
        configInputLayout.setBoxStrokeColorStateList(getResources().getColorStateList(R.color.white));
        configInput.requestFocus();

        helpLink.setPaintFlags(helpLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        loginButton.setOnClickListener(v -> {
            String configUrl = configInput.getText().toString().trim();
            if (!configUrl.isEmpty()) {
                verifyConfigUrl(configUrl);
            } else {
                Toast.makeText(this, "Введите ссылку", Toast.LENGTH_SHORT).show();
            }
        });

        helpLink.setOnClickListener(v -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("Что такое ссылка?")
                .setMessage("Это уникальная ссылка для подключения к VPN. Вы можете получить её в личном кабинете на сайте после авторизации через Telegram бота @")
                .setPositiveButton("Открыть бота", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(""));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Не удалось открыть Telegram", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
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
        });
    }

    private void verifyConfigUrl(String configUrl) {
        loginButton.setEnabled(false);
        loginButton.setText("Проверка...");

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("config_url", configUrl);

            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/verify-config-url")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        requestBody.toString()
                    ))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(VpnConfigActivity.this, 
                            "Ошибка подключения к серверу", 
                            Toast.LENGTH_SHORT).show();
                        resetLoginButton();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> {
                        try {
                            String responseBody = response.body().string();
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            
                            if (response.isSuccessful() && jsonResponse.getBoolean("success")) {
                                String userId = jsonResponse.getString("user_id");
                                String accessToken = jsonResponse.getString("access_token");

                                saveUserData(userId, accessToken, configUrl);

                                Intent intent = new Intent(VpnConfigActivity.this, MainActivity.class);
                                startActivity(intent);
                                finish();
                            } else {
                                String errorMessage = jsonResponse.optString("message", "Неверная ссылка");
                                Toast.makeText(VpnConfigActivity.this, 
                                    errorMessage, 
                                    Toast.LENGTH_SHORT).show();
                                resetLoginButton();
                            }
                        } catch (Exception e) {
                            Toast.makeText(VpnConfigActivity.this, 
                                "Неверная ссылка", 
                                Toast.LENGTH_SHORT).show();
                            resetLoginButton();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, 
                "Неверная ссылка", 
                Toast.LENGTH_SHORT).show();
            resetLoginButton();
        }
    }

    private void resetLoginButton() {
        loginButton.setEnabled(true);
        loginButton.setText("Войти");
    }

    @Override
    protected void onResume() {
        super.onResume();
        configInput.requestFocus();
    }

    private void saveUserData(String userId, String accessToken, String configUrl) {
        SharedPreferences prefs = getSharedPreferences("VPNConfig", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("user_id", userId);
        editor.putString("access_token", accessToken);
        editor.putString("config_url", configUrl);
        editor.apply();
    }
}