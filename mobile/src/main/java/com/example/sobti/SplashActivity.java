package com.example.sobti;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkUserStatus();
            }
        }, SPLASH_DELAY);
    }

    private void checkUserStatus() {
        SharedPreferences prefs = getSharedPreferences("SobtiPrefs", MODE_PRIVATE);
        String userEmail = prefs.getString("user_email", "");

        Intent intent;
        if (userEmail.isEmpty()) {
            // New user - go to registration
            intent = new Intent(SplashActivity.this, RegistrationActivity.class);
        } else {
            // Existing user - go to main activity
            intent = new Intent(SplashActivity.this, MainActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
