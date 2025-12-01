package com.example.sobti.aws;

public class AwsCredentialsProvider {

    public static String getAccessKeyId() {
        return "";
    }

    public static String getSecretAccessKey() {
        return "";
    }

    public static String getSessionToken() {
        return null; // only if using Cognito or STS tokens
    }
}
