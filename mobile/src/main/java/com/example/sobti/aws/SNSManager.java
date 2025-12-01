package com.example.sobti.aws;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

public class SNSManager {

    private AmazonSNSClient snsClient;

    public SNSManager() {
        snsClient = AWSConfig.getSNSClient();
    }

    // ✅ Existing SMS Sending Method
    public void sendSNSMessage(String phoneNumber, String message, SNSCallback callback) {
        try {

            // Basic null check
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                if (callback != null) {
                    callback.onError(new Exception("Phone number is null or empty"));
                }
                return;
            }

            PublishRequest publishRequest = new PublishRequest()
                    .withPhoneNumber(phoneNumber)
                    .withMessage(message);

            PublishResult result = snsClient.publish(publishRequest);

            if (callback != null) {
                callback.onSuccess(result.getMessageId());
            }

        } catch (Exception e) {
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    // ✅ Added: SNS Email Sending Method using Topic ARN
    public void sendEmailToTopic(String topicArn, String subject, String message, SNSCallback callback) {
        try {

            // Basic null check
            if (topicArn == null || topicArn.trim().isEmpty()) {
                if (callback != null) {
                    callback.onError(new Exception("Topic ARN is null or empty"));
                }
                return;
            }

            PublishRequest publishRequest = new PublishRequest()
                    .withTopicArn(topicArn)
                    .withSubject(subject)
                    .withMessage(message);

            PublishResult result = snsClient.publish(publishRequest);

            if (callback != null) {
                callback.onSuccess(result.getMessageId());
            }

        } catch (Exception e) {
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    public interface SNSCallback {
        void onSuccess(String messageId);
        void onError(Exception e);
    }
}
