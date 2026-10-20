package com.stepvolt.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.stepvolt.app.R;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing()) return;
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, SPLASH_DELAY_MS);
    }
}
