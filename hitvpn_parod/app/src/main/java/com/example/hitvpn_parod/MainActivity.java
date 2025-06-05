package com.example.hitvpn_parod;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.net.VpnService;
import androidx.core.content.ContextCompat;
import android.app.AlertDialog;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREF_NAME = "ServerPrefs";
    private static final String KEY_SELECTED_SERVER = "selected_server";
    private MaterialButton vpnButton;
    private MaterialButton serversButton;
    private TextView vpnStatusText;
    private TextView connectionStatusText;
    private TextView downloadedText;
    private TextView uploadedText;
    private boolean isVpnConnected = false;
    private String selectedServer = null;
    private long downloadedBytes = 0;
    private long uploadedBytes = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable trafficUpdateRunnable;
    private ValueAnimator colorAnimator;
    private SharedPreferences preferences;
    private TextView userIdText;
    private WireGuardManager wireGuardManager;

    private static final String VPN_ADDRESS = "";
    private static final String VPN_DNS = "";
    private static final String VPN_PRIVATE_KEY = "";
    private static final String VPN_PUBLIC_KEY = "";
    private static final String VPN_ENDPOINT = "";

    private static final int VPN_REQUEST_CODE = 1;
    private static final int SERVER_SELECTION_REQUEST_CODE = 2;
    private static final int ANIMATION_DURATION = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isVpnConfigured()) {
            startActivity(new Intent(this, VpnConfigActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        selectedServer = preferences.getString(KEY_SELECTED_SERVER, "Случайный, Автоматический выбор");

        vpnButton = findViewById(R.id.vpnButton);
        serversButton = findViewById(R.id.serversButton);
        vpnStatusText = findViewById(R.id.vpnStatusText);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        downloadedText = findViewById(R.id.downloadedText);
        uploadedText = findViewById(R.id.uploadedText);
        userIdText = findViewById(R.id.userIdText);

        vpnStatusText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        vpnStatusText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        vpnStatusText.setText("Нажмите на кнопку,\nчтобы включить VPN");
        connectionStatusText.setText("VPN отключен");
        downloadedText.setText("0 B");
        uploadedText.setText("0 B");

        wireGuardManager = new WireGuardManager(this);

        checkVpnState();

        vpnButton.setOnClickListener(v -> toggleVpn());

        findViewById(R.id.settingsButton).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.serversButton).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(this, ServerListActivity.class);
                startActivityForResult(intent, SERVER_SELECTION_REQUEST_CODE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        trafficUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isVpnConnected) {
                    downloadedBytes += (long) (Math.random() * 1024);
                    uploadedBytes += (long) (Math.random() * 512);
                    updateTrafficStats();
                }
                handler.postDelayed(this, 1000);
            }
        };

        loadUserData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Handler().postDelayed(() -> {
            Log.d(TAG, "Checking VPN state after resume");
            checkVpnState();
        }, 500);
    }

    private void checkVpnState() {
        Log.d(TAG, "Checking VPN state");
        boolean newState = wireGuardManager.isConnected();
        Log.d(TAG, "VPN state check result: " + (newState ? "connected" : "disconnected"));

        if (newState != isVpnConnected) {
            isVpnConnected = newState;
            updateVpnStatus();
        }
    }

    private void toggleVpn() {
        Log.d(TAG, "toggleVpn called, current state: " + (isVpnConnected ? "connected" : "disconnected"));

        if (!isVpnConnected) {
            Intent vpnIntent = VpnService.prepare(this);
            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
            } else {
                onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
            }
        } else {
            Log.d(TAG, "Initiating VPN disconnect");
            vpnButton.setEnabled(false);
            disconnectVpn();
        }
    }

    private void connectVpn() {
        try {
            wireGuardManager.connect(
                    VPN_PRIVATE_KEY,
                    VPN_PUBLIC_KEY,
                    VPN_ADDRESS,
                    VPN_ENDPOINT,
                    VPN_DNS,
                    new WireGuardManager.ConnectionCallback() {
                        @Override
                        public void onSuccess() {
                            isVpnConnected = true;
                            updateVpnStatus();
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Failed to connect to VPN", new Exception(error));
                            Toast.makeText(MainActivity.this, "Не удалось подключиться к VPN: " + error, Toast.LENGTH_SHORT).show();
                            isVpnConnected = false;
                            updateVpnStatus();
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to VPN", e);
            Toast.makeText(this, "Не удалось подключиться к VPN: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isVpnConnected = false;
            updateVpnStatus();
        }
    }

    private void disconnectVpn() {
        Log.d(TAG, "disconnectVpn called");

        wireGuardManager.disconnect(new WireGuardManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "VPN disconnect success callback received");
                runOnUiThread(() -> {
                    isVpnConnected = false;
                    updateVpnStatus();
                    vpnButton.setEnabled(true);

                    new Handler().postDelayed(() -> {
                        checkVpnState();
                    }, 1000);
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "VPN disconnect error callback received: " + error);
                runOnUiThread(() -> {
                    vpnButton.setEnabled(true);
                    new Handler().postDelayed(() -> {
                        checkVpnState();
                    }, 1000);
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            connectVpn();
        } else if (requestCode == SERVER_SELECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                String server = data.getStringExtra("selected_server");
                if (server != null) {
                    selectedServer = server;
                    preferences.edit().putString(KEY_SELECTED_SERVER, selectedServer).apply();
                    updateConnectionStatus();
                }
            }
        }
    }

    private void updateConnectionStatus() {
        if (isVpnConnected) {
            connectionStatusText.setText("VPN подключен");
        } else if (selectedServer != null && !selectedServer.startsWith("Случайный")) {
            connectionStatusText.setText("Выбран сервер: " + selectedServer);
        } else {
            connectionStatusText.setText("VPN отключен");
        }
    }

    private void updateVpnStatus() {
        if (isVpnConnected) {
            vpnStatusText.setText("Нажмите на кнопку,\nчтобы отключить VPN");
            connectionStatusText.setText("VPN подключен");
            animateButtonColor(true);
            downloadedBytes = 0;
            uploadedBytes = 0;
            updateTrafficStats();
            handler.post(trafficUpdateRunnable);
        } else {
            vpnStatusText.setText("Нажмите на кнопку,\nчтобы включить VPN");
            connectionStatusText.setText("VPN отключен");
            animateButtonColor(false);
            handler.removeCallbacks(trafficUpdateRunnable);
            downloadedBytes = 0;
            uploadedBytes = 0;
            updateTrafficStats();
        }
    }

    private void animateButtonColor(boolean toGreen) {
        if (colorAnimator != null) {
            colorAnimator.cancel();
        }

        int startColor = toGreen ? Color.WHITE : Color.parseColor("#FF00C853");
        int endColor = toGreen ? Color.parseColor("#FF00C853") : Color.WHITE;

        colorAnimator = ValueAnimator.ofArgb(startColor, endColor);
        colorAnimator.setDuration(ANIMATION_DURATION);
        colorAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        colorAnimator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            vpnButton.setBackgroundTintList(ColorStateList.valueOf(color));
            connectionStatusText.setTextColor(color);
        });

        colorAnimator.start();
    }

    private void updateTrafficStats() {
        String downloaded = formatBytes(downloadedBytes);
        String uploaded = formatBytes(uploadedBytes);
        downloadedText.setText(downloaded);
        uploadedText.setText(uploaded);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(trafficUpdateRunnable);
        if (colorAnimator != null) {
            colorAnimator.cancel();
        }
        if (wireGuardManager != null) {
            wireGuardManager.cleanup();
        }
    }

    private boolean isVpnConfigured() {
        SharedPreferences prefs = getSharedPreferences("VPNConfig", MODE_PRIVATE);
        return prefs.contains("config_url");
    }

    private void loadUserData() {
        SharedPreferences prefs = getSharedPreferences("VPNConfig", MODE_PRIVATE);
        String userId = prefs.getString("user_id", "");
        if (!userId.isEmpty()) {
            userIdText.setText("ID: " + userId);
        }
    }

    private void logout() {
    }
}