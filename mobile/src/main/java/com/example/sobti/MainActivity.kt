package com.example.sobti

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.sobti.aws.BedrockClient
import com.example.sobti.aws.AWSConfig
import com.example.sobti.aws.DynamoDBManager
import com.example.sobti.aws.DynamoDBManager.GetUserCallback
import com.example.sobti.aws.DynamoDBManager.UpdateCallback
import com.example.sobti.aws.DynamoDBManager.UserData
import com.example.sobti.aws.SNSManager
import com.google.android.gms.location.*
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

    private lateinit var tvHeartRate: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvStatus: TextView

    private lateinit var dbManager: DynamoDBManager
    private lateinit var prefs: SharedPreferences
    private var userEmail: String? = null
    private var emergencyNumber: String? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: String = "Loading..."

    private var currentHeartRate = 0
    private var previousHeartRate = 0
    private var heartRateTrendCount = 0

    // ✅ Bedrock Client
    private lateinit var bedrockClient: BedrockClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("SobtiPrefs", MODE_PRIVATE)
        userEmail = prefs.getString("user_email", "")

        AWSConfig.initialize(this)
        dbManager = DynamoDBManager(AWSConfig.getDDBClient())

        // ✅ Initialize Bedrock Titan Model
        bedrockClient = BedrockClient(
            "us-east-1",
            "amazon.titan-text-express-v1"
        )

        initViews()
        initPermissionsAndLocation()
        loadUserData()
        connectToWearable()

        val btnDummy = findViewById<Button>(R.id.btn_dummy_data)
        btnDummy.setOnClickListener {
            sendSNSAlert()
            sendSNSEmail()
            callBedrockForHealthSummary(currentHeartRate, 0, currentLocation)
        }

        val btnAiBot = findViewById<Button>(R.id.btn_ai_bot)
        btnAiBot.setOnClickListener {
            startActivity(Intent(this, AIBotActivity::class.java))
        }


    }

    private fun initViews() {
        tvUserName = findViewById(R.id.tvUserName)
        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvSteps = findViewById(R.id.tvSteps)
        tvLocation = findViewById(R.id.tvLocation)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun initPermissionsAndLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupLocationTracking()
        } else {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    private fun setupLocationTracking() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                30000L
            )
                .setMinUpdateIntervalMillis(15000L)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        val location = locationResult.lastLocation
                        location?.let {
                            currentLocation = "%.6f, %.6f".format(it.latitude, it.longitude)
                            updateLocationUI()
                        }
                    }
                },
                null
            )
        }
    }

    private fun loadUserData() {
        dbManager.getUserData(userEmail, object : GetUserCallback {
            override fun onSuccess(userData: UserData) {
                runOnUiThread {
                    tvUserName.text = "Welcome, ${userData.name}!"
                    emergencyNumber = userData.emergencyNumber

                    if (userData.lastHeartRate > 0) {
                        tvHeartRate.text = "${userData.lastHeartRate} bpm"
                    }
                    if (userData.lastSteps > 0) {
                        tvSteps.text = userData.lastSteps.toString()
                    }
                    userData.lastLocation?.let {
                        currentLocation = it
                        updateLocationUI()
                    }
                }
            }

            override fun onError(e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Error loading user data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun connectToWearable() {
        Wearable.getDataClient(this).addListener(this)
        tvStatus.text = "Status: Connected to Watch"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                if (event.dataItem.uri.path == WEAR_DATA_PATH) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val heartRate = dataMap.getInt("heartRate", 0)
                    val steps = dataMap.getInt("steps", 0)

                    runOnUiThread { updateHealthData(heartRate, steps) }
                }
            }
        }
    }

    private fun updateHealthData(heartRate: Int, steps: Int) {
        tvHeartRate.text = "$heartRate bpm"
        tvSteps.text = steps.toString()

        currentHeartRate = heartRate

        checkHeartRateTrend(heartRate)

        dbManager.updateHealthData(
            userEmail, heartRate, steps, currentLocation,
            object : UpdateCallback {
                override fun onSuccess() {}
                override fun onError(e: Exception?) {}
            }
        )

        // ✅ AI analysis from Bedrock
        callBedrockForHealthSummary(heartRate, steps, currentLocation)
    }

    private fun checkHeartRateTrend(heartRate: Int) {
        currentHeartRate = heartRate

        if (currentHeartRate > previousHeartRate && currentHeartRate > HIGH_HR_THRESHOLD) {
            heartRateTrendCount++
        } else if (currentHeartRate < previousHeartRate && currentHeartRate < LOW_HR_THRESHOLD) {
            heartRateTrendCount++
        } else {
            heartRateTrendCount = 0
        }

        if (heartRateTrendCount >= TREND_THRESHOLD) {
            triggerEmergency(heartRate)
            heartRateTrendCount = 0
        }

        previousHeartRate = currentHeartRate
    }

    private fun triggerEmergency(heartRate: Int) {
        if (emergencyNumber.isNullOrEmpty()) return

        val message =
            "SOBTI ALERT!\nAbnormal heart rate detected: $heartRate bpm\nLocation: $currentLocation\nImmediate assistance needed!"

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val smsManager = getSystemService(SmsManager::class.java)
                smsManager.sendTextMessage(emergencyNumber, null, message, null, null)

                runOnUiThread {
                    tvStatus.text = "Status: Emergency SMS Sent!"
                    tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    Toast.makeText(this, "Emergency alert sent!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ✅ SNS SMS Sending
    private fun sendSNSAlert() {

        if (emergencyNumber.isNullOrEmpty()) {
            Toast.makeText(this, "Emergency number not found!", Toast.LENGTH_SHORT).show()
            return
        }

        val message = "SOBTI ALERT (Dummy Trigger)\n" +
                "Heart Rate: $currentHeartRate bpm\n" +
                "Location: $currentLocation\n" +
                "Immediate attention required!"

        val snsManager = SNSManager()

        Thread {
            snsManager.sendSNSMessage(emergencyNumber!!, message, object : SNSManager.SNSCallback {
                override fun onSuccess(messageId: String?) {
                    runOnUiThread {
                        tvStatus.text = "Status: SNS alert sent ✅"
                        Toast.makeText(
                            this@MainActivity,
                            "SNS Message Sent Successfully!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onError(e: Exception) {
                    runOnUiThread {
                        tvStatus.text = "SNS Error ❌"
                        Toast.makeText(
                            this@MainActivity,
                            "SNS Error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        }.start()
    }

    // ✅ SNS EMAIL
    private fun sendSNSEmail() {

        val topicArn = ""

        val subject = "SOBTI Health Alert"

        val messageEmail = """
            SOBTI ALERT!
            Abnormal heart rate detected: $currentHeartRate bpm
            Location: $currentLocation
            Immediate attention required!
        """.trimIndent()

        val snsManager = SNSManager()

        Thread {
            snsManager.sendEmailToTopic(topicArn, subject, messageEmail, object : SNSManager.SNSCallback {
                override fun onSuccess(messageId: String?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Email Sent Successfully!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onError(e: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Error sending email: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        }.start()
    }

    // ✅ BEDROCK AI CALL
    private fun callBedrockForHealthSummary(heartRate: Int, steps: Int, location: String) {
        Thread {
            try {
                val prompt = """
                    You are an AI assistant providing health insights.
                    Analyze the following data and provide a short summary:
                    Heart Rate: $heartRate bpm
                    Steps: $steps
                    Location: $location
                """.trimIndent()

                val response = bedrockClient.invokeTitanText(prompt)

                runOnUiThread {
                    tvStatus.text = "AI: $response"
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Bedrock error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun updateLocationUI() {
        tvLocation.text = currentLocation
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val locationPermissionIndex = permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (locationPermissionIndex != -1 && grantResults[locationPermissionIndex] == PackageManager.PERMISSION_GRANTED) {
                setupLocationTracking()
            }

            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(
                    this,
                    "Some permissions denied. App may not work properly.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val WEAR_DATA_PATH = "/health_data"

        private const val TREND_THRESHOLD = 3
        private const val HIGH_HR_THRESHOLD = 120
        private const val LOW_HR_THRESHOLD = 50
    }
}
