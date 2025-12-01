package com.example.sobti;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sobti.aws.GeminiClient;

import java.util.concurrent.Executors;

public class AIBotActivity extends AppCompatActivity {

    private GeminiClient geminiClient;
    private EditText inputMessage;
    private TextView outputMessage;
    private Button btnSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.aibot);

        geminiClient = new GeminiClient("");

        inputMessage = findViewById(R.id.inputPrompt);
        outputMessage = findViewById(R.id.txtResponse);
        btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> sendToGemini());
    }

    private void sendToGemini() {
        String prompt = inputMessage.getText().toString().trim();

        if (prompt.isEmpty()) {
            outputMessage.setText("Please enter a message.");
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String response = geminiClient.generateText(prompt);

                runOnUiThread(() -> outputMessage.setText(response));

            } catch (Exception e) {
                Log.e("GEMINI_SEND_ERROR", e.getMessage(), e);
                runOnUiThread(() -> outputMessage.setText("Error: " + e.getMessage()));
            }
        });
    }
}
