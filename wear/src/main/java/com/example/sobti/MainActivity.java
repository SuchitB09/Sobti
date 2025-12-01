package com.example.sobti;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.*;
import java.util.Random;

public class MainActivity extends FragmentActivity implements SensorEventListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String DATA_PATH = "/health_data";

    private TextView tvHeartRate, tvSteps, tvStatus;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private Sensor stepCounterSensor;

    private int currentHeartRate = 0;
    private int currentSteps = 0;

    private DataClient dataClient;

    // Test/Fake heart rate simulation
    private boolean useTestMode = true; // Set to false for real sensor data
    private Random random = new Random();
    private int testHeartRate = 75;
    private int testTrendCounter = 0;
    private boolean testIncreasing = false;
    private boolean testDecreasing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        requestPermissions();
        setupSensors();

        dataClient = Wearable.getDataClient(this);

        // Start test mode if enabled
        if (useTestMode) {
            startTestMode();
        }
    }

    private void initViews() {
        tvHeartRate = findViewById(R.id.tvHeartRate);
        tvSteps = findViewById(R.id.tvSteps);
        tvStatus = findViewById(R.id.tvStatus);
    }

    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION
        };

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        if (sensorManager != null) {
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

            if (heartRateSensor != null && !useTestMode) {
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
                tvStatus.setText("Heart Rate: Active");
            } else {
                tvStatus.setText("Test Mode: Active");
            }

            if (stepCounterSensor != null) {
                sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    private void startTestMode() {
        // Simulate heart rate readings every 3 seconds
        new Thread(() -> {
        while (useTestMode) {
            try {
                Thread.sleep(3000);

                // Randomly decide to simulate abnormal patterns
                if (random.nextInt(20) == 0 && !testIncreasing && !testDecreasing) {
                    // Start increasing trend
                    testIncreasing = true;
                    testTrendCounter = 0;
                } else if (random.nextInt(20) == 1 && !testIncreasing && !testDecreasing) {
                    // Start decreasing trend
                    testDecreasing = true;
                    testTrendCounter = 0;
                }

                // Generate heart rate based on current trend
                if (testIncreasing) {
                    testHeartRate += random.nextInt(8) + 5; // Increase by 5-12
                    testTrendCounter++;

                    if (testTrendCounter >= 5 || testHeartRate > 140) {
                        testIncreasing = false;
                        testTrendCounter = 0;
                    }
                } else if (testDecreasing) {
                    testHeartRate -= random.nextInt(8) + 5; // Decrease by 5-12
                    testTrendCounter++;

                    if (testTrendCounter >= 5 || testHeartRate < 40) {
                        testDecreasing = false;
                        testTrendCounter = 0;
                    }
                } else {
                    // Normal fluctuation
                    int change = random.nextInt(11) - 5; // -5 to +5
                    testHeartRate += change;

                    // Keep in reasonable range
                    if (testHeartRate < 60) testHeartRate = 60 + random.nextInt(10);
                    if (testHeartRate > 100) testHeartRate = 90 + random.nextInt(10);
                }

                currentHeartRate = testHeartRate;
                currentSteps += random.nextInt(5); // Simulate steps

                runOnUiThread(() -> {
                    updateUI();
                    sendDataToPhone();
                });

            } catch (InterruptedException e) {
                break;
            }
        }
    }).start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            currentHeartRate = (int) event.values[0];
            updateUI();
            sendDataToPhone();
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            currentSteps = (int) event.values[0];
            updateUI();
            sendDataToPhone();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this implementation
    }

    private void updateUI() {
        tvHeartRate.setText(currentHeartRate + " bpm");
        tvSteps.setText(String.valueOf(currentSteps));

        // Update status color based on heart rate
        if (currentHeartRate > 120 || currentHeartRate < 50) {
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            if (useTestMode) {
                tvStatus.setText("Test: ALERT!");
            } else {
                tvStatus.setText("Status: ALERT!");
            }
        } else {
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            if (useTestMode) {
                tvStatus.setText("Test Mode: Normal");
            } else {
                tvStatus.setText("Status: Normal");
            }
        }
    }

    private void sendDataToPhone() {
        PutDataMapRequest dataMap = PutDataMapRequest.create(DATA_PATH);
        dataMap.getDataMap().putInt("heartRate", currentHeartRate);
        dataMap.getDataMap().putInt("steps", currentSteps);
        dataMap.getDataMap().putLong("timestamp", System.currentTimeMillis());

        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();

        Task<DataItem> putDataTask = dataClient.putDataItem(request);
        putDataTask.addOnSuccessListener(dataItem -> {
        // Data sent successfully
    });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && !useTestMode) {
            if (heartRateSensor != null) {
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (stepCounterSensor != null) {
                sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
        @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                setupSensors();
            }
        }
    }
}