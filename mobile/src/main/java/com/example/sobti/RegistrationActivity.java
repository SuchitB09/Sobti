package com.example.sobti;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.sobti.aws.AWSConfig;
import com.example.sobti.aws.DynamoDBManager;
import com.example.sobti.aws.DynamoDBManager.UserData;

public class RegistrationActivity extends AppCompatActivity {

    private EditText etName, etEmail, etAge, etHeight, etWeight, etEmergencyNumber;
    private Button btnSubmit;
    private ProgressBar progressBar;

    private DynamoDBManager dbManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        // Initialize AWS
        AWSConfig.initialize(this);
        dbManager = new DynamoDBManager(AWSConfig.getDDBClient());

        prefs = getSharedPreferences("SobtiPrefs", MODE_PRIVATE);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etAge = findViewById(R.id.etAge);
        etHeight = findViewById(R.id.etHeight);
        etWeight = findViewById(R.id.etWeight);
        etEmergencyNumber = findViewById(R.id.etEmergencyNumber);
        btnSubmit = findViewById(R.id.btnSubmit);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndSubmit();
            }
        });
    }

    private void validateAndSubmit() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();
        String emergencyNumber = etEmergencyNumber.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Valid email is required");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(ageStr)) {
            etAge.setError("Age is required");
            etAge.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(heightStr)) {
            etHeight.setError("Height is required");
            etHeight.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(weightStr)) {
            etWeight.setError("Weight is required");
            etWeight.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(emergencyNumber) || emergencyNumber.length() < 10) {
            etEmergencyNumber.setError("Valid emergency number is required");
            etEmergencyNumber.requestFocus();
            return;
        }

        int age = Integer.parseInt(ageStr);
        int height = Integer.parseInt(heightStr);
        int weight = Integer.parseInt(weightStr);

        // Check if user already exists
        checkAndRegisterUser(email, name, age, height, weight, emergencyNumber);
    }

    private void checkAndRegisterUser(final String email, final String name, final int age,
                                      final int height, final int weight, final String emergencyNumber) {
        showLoading(true);

        dbManager.checkUserExists(email, new DynamoDBManager.UserCheckCallback() {
            @Override
            public void onResult(boolean exists, UserData userData) {
                if (exists) {
                    showLoading(false);
                    Toast.makeText(RegistrationActivity.this,
                            "Account already exists with this email. Syncing data...",
                            Toast.LENGTH_LONG).show();

                    // Save email to preferences and go to main activity
                    prefs.edit().putString("user_email", email).apply();
                    navigateToMain();
                } else {
                    // Register new user
                    registerNewUser(email, name, age, height, weight, emergencyNumber);
                }
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(RegistrationActivity.this,
                        "Error checking user: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void registerNewUser(String email, String name, int age, int height, int weight, String emergencyNumber) {
        UserData userData = new UserData();
        userData.email = email;
        userData.name = name;
        userData.age = age;
        userData.height = height;
        userData.weight = weight;
        userData.emergencyNumber = emergencyNumber;

        dbManager.saveUser(userData, new DynamoDBManager.SaveUserCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(RegistrationActivity.this,
                        "Registration successful!",
                        Toast.LENGTH_SHORT).show();

                // Save email to preferences
                prefs.edit().putString("user_email", email).apply();
                navigateToMain();
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(RegistrationActivity.this,
                        "Registration failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(RegistrationActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!show);
    }
}