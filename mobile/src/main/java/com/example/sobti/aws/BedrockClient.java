package com.example.sobti.aws;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BedrockClient {

    private final String region;
    private final String modelId;
    private final OkHttpClient http;
    private final Gson gson;

    public BedrockClient(String region, String modelId) {
        this.region = region;
        this.modelId = modelId;
        this.http = new OkHttpClient();
        this.gson = new Gson();
    }

    public String invokeTitanText(String prompt) throws Exception {

        // ------- Build Titan Request JSON -------
        JsonObject payload = new JsonObject();
        payload.addProperty("inputText", prompt);

        JsonObject cfg = new JsonObject();
        cfg.addProperty("maxTokenCount", 256);
        cfg.addProperty("temperature", 0.7);
        cfg.addProperty("topP", 1);

        JsonArray stopSequences = new JsonArray();
        cfg.add("stopSequences", stopSequences);

        payload.add("textGenerationConfig", cfg);

        String requestBody = gson.toJson(payload).trim();

        // ------- Endpoint -------
        String host = "bedrock-runtime." + region + ".amazonaws.com";
        String url = "https://" + host + "/model/" + modelId + "/invoke";

        // ------- Timestamp -------
        String amzDate = getAmzDate();                 // YYYYMMDD'T'HHMMSS'Z'
        String dateStamp = amzDate.substring(0, 8);    // YYYYMMDD

        // ------- Payload Hash -------
        String payloadHash = sha256Hex(requestBody);

        // ------- Canonical Request -------
        String canonicalRequest =
                "POST\n" +
                        "/model/" + modelId + "/invoke\n" +
                        "\n" +
                        "content-type:application/json\n" +
                        "host:" + host + "\n" +
                        "x-amz-date:" + amzDate + "\n" +
                        "\n" +
                        "content-type;host;x-amz-date\n" +
                        payloadHash;

        // Debug (Optional)
        System.out.println("CANONICAL REQUEST:\n" + canonicalRequest);

        // ------- String to Sign -------
        String service = "bedrock";
        String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";

        String stringToSign =
                "AWS4-HMAC-SHA256\n" +
                        amzDate + "\n" +
                        credentialScope + "\n" +
                        sha256Hex(canonicalRequest);

        System.out.println("STRING TO SIGN:\n" + stringToSign);

        // ------- Signing Key -------
        byte[] signingKey = getSignatureKey(
                AwsCredentialsProvider.getSecretAccessKey(),
                dateStamp,
                region,
                service
        );

        // ------- Signature -------
        String signature = hmacHex(signingKey, stringToSign);

        // ------- Authorization Header -------
        String authorizationHeader =
                "AWS4-HMAC-SHA256 " +
                        "Credential=" + AwsCredentialsProvider.getAccessKeyId() + "/" + credentialScope +
                        ", SignedHeaders=content-type;host;x-amz-date" +
                        ", Signature=" + signature;

        // ------- HTTP Request -------
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Host", host)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Amz-Date", amzDate)
                .addHeader("Authorization", authorizationHeader)
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        // ------- Execute -------
        try (Response response = http.newCall(request).execute()) {
            String body = response.body().string();

            if (!response.isSuccessful()) {
                throw new IOException("HTTP error " + response.code() + ": " + body);
            }

            JsonObject root = gson.fromJson(body, JsonObject.class);
            JsonArray results = root.getAsJsonArray("results");
            return results.get(0).getAsJsonObject().get("outputText").getAsString();
        }
    }

    // ==========================
    // Helpers
    // ==========================

    private static String getAmzDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private static String sha256Hex(String data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private static byte[] HmacSHA256(byte[] key, String data)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(byte[] key, String msg) throws Exception {
        return bytesToHex(HmacSHA256(key, msg));
    }

    private static byte[] getSignatureKey(String secretKey, String date, String region, String service)
            throws Exception {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = HmacSHA256(kSecret, date);
        byte[] kRegion = HmacSHA256(kDate, region);
        byte[] kService = HmacSHA256(kRegion, service);
        return HmacSHA256(kService, "aws4_request");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
