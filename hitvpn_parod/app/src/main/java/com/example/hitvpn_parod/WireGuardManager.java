package com.example.hitvpn_parod;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.android.backend.Statistics;
import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;
import com.wireguard.crypto.Key;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class WireGuardManager {
    private static final String TAG = "WireGuardManager";
    private static final String PREFS_NAME = "WireGuardPrefs";
    private static final String KEY_PRIVATE_KEY = "private_key";
    private static final String KEY_PUBLIC_KEY = "public_key";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_DNS = "dns";
    private static final String KEY_LAST_CONNECTED = "last_connected";

    private final Context context;
    private final GoBackend backend;
    private HiVPNTunnel tunnel;
    private static final String TUNNEL_NAME = "hivpn";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int TIMEOUT_SECONDS = 30;
    private boolean isDisconnecting = false;
    private final SharedPreferences prefs;

    public interface ConnectionCallback {
        void onSuccess();
        void onError(String error);
    }

    private static class HiVPNTunnel implements Tunnel {
        private final String name;
        private Config config;
        private State state = State.DOWN;

        HiVPNTunnel(String name, Config config) {
            this.name = name;
            this.config = config;
        }

        public String getName() {
            return name;
        }

        public CompletionStage<Config> getConfig() {
            return CompletableFuture.completedFuture(config);
        }

        public CompletionStage<State> getState() {
            return CompletableFuture.completedFuture(state);
        }

        public CompletionStage<Statistics> getStatistics() {
            return CompletableFuture.completedFuture(null);
        }

        public void onStateChange(State newState) {
            Log.d(TAG, "Tunnel state changed to: " + newState);
            state = newState;
        }

        void setConfig(Config newConfig) {
            this.config = newConfig;
        }
    }

    public WireGuardManager(Context context) {
        this.context = context;
        this.backend = new GoBackend(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void saveConfiguration(String privateKey, String publicKey, String address, String endpoint, String dns) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PRIVATE_KEY, privateKey);
        editor.putString(KEY_PUBLIC_KEY, publicKey);
        editor.putString(KEY_ADDRESS, address);
        editor.putString(KEY_ENDPOINT, endpoint);
        editor.putString(KEY_DNS, dns);
        editor.apply();
    }

    private Config loadSavedConfig() {
        try {
            String privateKey = prefs.getString(KEY_PRIVATE_KEY, null);
            String publicKey = prefs.getString(KEY_PUBLIC_KEY, null);
            String address = prefs.getString(KEY_ADDRESS, null);
            String endpoint = prefs.getString(KEY_ENDPOINT, null);
            String dns = prefs.getString(KEY_DNS, null);

            if (privateKey == null || publicKey == null || address == null || endpoint == null || dns == null) {
                return null;
            }

            Interface.Builder interfaceBuilder = new Interface.Builder();
            interfaceBuilder.parsePrivateKey(privateKey);
            interfaceBuilder.addAddress(InetNetwork.parse(address));
            for (String dnsServer : dns.split(",")) {
                interfaceBuilder.addDnsServer(InetAddress.getByName(dnsServer.trim()));
            }

            Peer.Builder peerBuilder = new Peer.Builder();
            peerBuilder.parsePublicKey(publicKey);
            peerBuilder.parseEndpoint(endpoint);
            peerBuilder.parseAllowedIPs("0.0.0.0/0");
            peerBuilder.parseAllowedIPs("::/0");


            Config.Builder configBuilder = new Config.Builder();
            configBuilder.setInterface(interfaceBuilder.build());
            configBuilder.addPeer(peerBuilder.build());
            return configBuilder.build();
        } catch (Exception e) {
            Log.e(TAG, "Error loading saved config: " + e.getMessage());
            return null;
        }
    }

    public void connect(String privateKey, String publicKey, String address, String endpoint, String dns, ConnectionCallback callback) {
        executor.execute(() -> {
            try {
                if (isConnected()) {
                    mainHandler.post(() -> callback.onSuccess());
                    return;
                }

                Log.d(TAG, "Starting VPN connection...");

                saveConfiguration(privateKey, publicKey, address, endpoint, dns);

                Interface.Builder interfaceBuilder = new Interface.Builder();
                interfaceBuilder.parsePrivateKey(privateKey);
                interfaceBuilder.addAddress(InetNetwork.parse(address));

                for (String dnsServer : dns.split(",")) {
                    interfaceBuilder.addDnsServer(InetAddress.getByName(dnsServer.trim()));
                }

                Peer.Builder peerBuilder = new Peer.Builder();
                peerBuilder.parsePublicKey(publicKey);
                peerBuilder.parseEndpoint(endpoint);
                peerBuilder.parseAllowedIPs("0.0.0.0/0");
                peerBuilder.parseAllowedIPs("::/0");

                Config.Builder configBuilder = new Config.Builder();
                configBuilder.setInterface(interfaceBuilder.build());
                configBuilder.addPeer(peerBuilder.build());
                Config config = configBuilder.build();

                tunnel = new HiVPNTunnel(TUNNEL_NAME, config);

                CompletableFuture<Void> future = new CompletableFuture<>();
                try {
                    backend.setState(tunnel, Tunnel.State.UP, config);
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }

                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    prefs.edit().putLong(KEY_LAST_CONNECTED, System.currentTimeMillis()).apply();
                    mainHandler.post(() -> callback.onSuccess());
                } catch (Exception e) {
                    String error = "Connection failed: " + e.getMessage();
                    Log.e(TAG, error, e);
                    mainHandler.post(() -> callback.onError(error));
                }
            } catch (Exception e) {
                String error = "Connection setup failed: " + e.getMessage();
                Log.e(TAG, error, e);
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void disconnect(ConnectionCallback callback) {
        executor.execute(() -> {
            try {
                isDisconnecting = true;
                boolean disconnected = false;
                Exception lastError = null;

                for (int attempt = 1; attempt <= 3 && !disconnected; attempt++) {
                    try {
                        Log.d(TAG, "Disconnect attempt " + attempt);

                        HiVPNTunnel currentTunnel = null;
                        Config config = null;

                        switch (attempt) {
                            case 1:
                                currentTunnel = tunnel;
                                break;
                            case 2:
                                config = loadSavedConfig();
                                if (config != null) {
                                    currentTunnel = new HiVPNTunnel(TUNNEL_NAME, config);
                                }
                                break;
                            case 3:
                                currentTunnel = new HiVPNTunnel(TUNNEL_NAME, null);
                                break;
                        }

                        if (currentTunnel != null) {
                            backend.setState(currentTunnel, Tunnel.State.DOWN, config);
                            Thread.sleep(1000);

                            if (!isSystemVpnActive()) {
                                disconnected = true;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Disconnect attempt " + attempt + " failed: " + e.getMessage());
                        lastError = e;
                    }
                }

                if (disconnected || !isSystemVpnActive()) {
                    Log.d(TAG, "VPN successfully disconnected");
                    mainHandler.post(() -> callback.onSuccess());
                } else {
                    final String errorMessage = "Не удалось отключить VPN после нескольких попыток" +
                            (lastError != null ? ": " + lastError.getMessage() : "");
                    Log.e(TAG, errorMessage);
                    mainHandler.post(() -> callback.onError(errorMessage));
                }
            } catch (Exception e) {
                final String errorMessage = "Непредвиденная ошибка при отключении: " + e.getMessage();
                Log.e(TAG, errorMessage);
                mainHandler.post(() -> callback.onError(errorMessage));
            } finally {
                isDisconnecting = false;
                tunnel = null;
            }
        });
    }

    public boolean isConnected() {
        if (isDisconnecting) {
            return false;
        }

        boolean systemVpnActive = isSystemVpnActive();

        if (!systemVpnActive) {
            tunnel = null;
            return false;
        }

        if (tunnel == null) {
            Config config = loadSavedConfig();
            if (config != null) {
                tunnel = new HiVPNTunnel(TUNNEL_NAME, config);
            } else {
                Log.d(TAG, "VPN is active but unable to restore tunnel configuration");
                return true;
            }
        }

        return true;
    }

    private boolean isSystemVpnActive() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network activeNetwork = cm.getActiveNetwork();

            if (activeNetwork != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                boolean isVpnActive = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                Log.d(TAG, "System VPN state check: " + (isVpnActive ? "active" : "inactive"));
                return isVpnActive;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking system VPN state: " + e.getMessage());
        }
        return false;
    }

    public void cleanup() {
        if (isConnected() && !isDisconnecting) {
            disconnect(new ConnectionCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Successfully disconnected during cleanup");
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Failed to disconnect during cleanup: " + error);
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}