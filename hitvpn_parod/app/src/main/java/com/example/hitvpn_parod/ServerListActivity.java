package com.example.hitvpn_parod;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ServerListActivity extends AppCompatActivity {
    private static final String PREF_NAME = "ServerPrefs";
    private static final String KEY_SELECTED_SERVER = "selected_server";
    private LinearLayout serverListLayout;
    private String selectedServer = null;
    private View randomServerView = null;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_list);

        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        selectedServer = preferences.getString(KEY_SELECTED_SERVER, "Случайный, Автоматический выбор");

        serverListLayout = findViewById(R.id.serverListLayout);

        randomServerView = addServer("Случайный", "Автоматический выбор", R.drawable.ic_random, true);

        addServer("Россия", "Москва", R.drawable.flag_ru, false);
        addServer("США", "Нью-Йорк", R.drawable.flag_us, false);
        addServer("Германия", "Берлин", R.drawable.flag_de, false);
        addServer("Япония", "Токио", R.drawable.flag_jp, false);

        String[] parts = selectedServer.split(", ");
        if (parts.length == 2) {
            View serverView = findServerView(parts[0], parts[1]);
            if (serverView != null) {
                selectServer(serverView, parts[0], parts[1], false);
            } else {
                selectServer(randomServerView, "Случайный", "Автоматический выбор", false);
            }
        } else {
            selectServer(randomServerView, "Случайный", "Автоматический выбор", false);
        }
    }

    private View findServerView(String country, String city) {
        for (int i = 0; i < serverListLayout.getChildCount(); i++) {
            View child = serverListLayout.getChildAt(i);
            TextView countryText = child.findViewById(R.id.countryText);
            TextView cityText = child.findViewById(R.id.cityText);
            if (countryText.getText().toString().equals(country) && 
                cityText.getText().toString().equals(city)) {
                return child;
            }
        }
        return null;
    }

    private View addServer(String country, String city, int flagResId, boolean isActive) {
        View serverView = LayoutInflater.from(this).inflate(R.layout.item_server, serverListLayout, false);
        
        ImageView flagImage = serverView.findViewById(R.id.flagImage);
        TextView countryText = serverView.findViewById(R.id.countryText);
        TextView cityText = serverView.findViewById(R.id.cityText);
        
        flagImage.setImageResource(flagResId);
        countryText.setText(country);
        cityText.setText(city);

        if (!isActive) {
            serverView.setEnabled(false);
            serverView.setAlpha(0.5f);
            serverView.setOnClickListener(null);
        } else {
            serverView.setOnClickListener(v -> selectServer(serverView, country, city, true));
        }
        
        serverListLayout.addView(serverView);
        return serverView;
    }

    private void selectServer(View serverView, String country, String city, boolean saveSelection) {
        try {
            for (int i = 0; i < serverListLayout.getChildCount(); i++) {
                View child = serverListLayout.getChildAt(i);
                ImageView indicator = child.findViewById(R.id.selectedIndicator);
                indicator.setVisibility(View.GONE);
            }

            ImageView indicator = serverView.findViewById(R.id.selectedIndicator);
            indicator.setVisibility(View.VISIBLE);
            selectedServer = country + ", " + city;

            if (saveSelection) {
                preferences.edit().putString(KEY_SELECTED_SERVER, selectedServer).apply();

                Intent resultIntent = new Intent();
                resultIntent.putExtra("selected_server", selectedServer);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
} 