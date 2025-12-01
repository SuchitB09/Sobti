package com.example.sobti.aws;

import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;

public class GeminiClient {

    private final GenerativeModelFutures modelFutures;

    public GeminiClient(String apiKey) {

        GenerativeModel model = new GenerativeModel(
                "gemini-2.5-flash",
                apiKey
        );

        modelFutures = GenerativeModelFutures.from(model);
    }

    public String generateText(String prompt) {
        try {

            // ✅ Add medical context WITHOUT changing logic
            String medicalPrompt =
                    "You are a helpful medical assistant. Provide general information only, " +
                            "do not diagnose or prescribe medication. Recommend consulting a doctor " +
                            "for serious issues.\nUser query: " + prompt;

            Content content = new Content.Builder()
                    .addText(medicalPrompt)   // ✅ replaced prompt with medicalPrompt
                    .build();

            GenerateContentResponse response =
                    modelFutures.generateContent(content).get();

            // bold start + end
            // bullets instead of *

            return response.getText()
                    .replace("**", "")           // bold start + end
                    .replace("*", "• ");

        } catch (Exception e) {
            Log.e("GEMINI_ERROR", "Error generating text", e);
            return "Error: " + e.getMessage();
        }
    }
}
