package com.example.sobti.aws;

import android.content.Context;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.sns.AmazonSNSClient; // ✅ Added

public class AWSConfig {

    // TODO: Replace with your AWS credentials
    private static final String COGNITO_POOL_ID = "";
    private static final Regions REGION = Regions.AP_SOUTH_1;

    private static CognitoCachingCredentialsProvider credentialsProvider;
    private static AmazonDynamoDBClient ddbClient;

    // ✅ Added SNS client variable
    private static AmazonSNSClient snsClient;

    public static void initialize(Context context) {
        if (credentialsProvider == null) {
            credentialsProvider = new CognitoCachingCredentialsProvider(
                    context.getApplicationContext(),
                    COGNITO_POOL_ID,
                    REGION
            );
        }

        if (ddbClient == null) {
            ddbClient = new AmazonDynamoDBClient(credentialsProvider);
            ddbClient.setRegion(com.amazonaws.regions.Region.getRegion(REGION));
        }

        // ✅ Initialize SNS Client
        if (snsClient == null) {
            snsClient = new AmazonSNSClient(credentialsProvider);
            snsClient.setRegion(com.amazonaws.regions.Region.getRegion(REGION));
        }
    }

    public static AmazonDynamoDBClient getDDBClient() {
        return ddbClient;
    }

    public static CognitoCachingCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    // ✅ SNS client getter
    public static AmazonSNSClient getSNSClient() {
        return snsClient;
    }
}
