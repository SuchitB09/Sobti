package com.example.sobti.aws;

import android.os.Handler;
import android.os.Looper;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DynamoDBManager {

    private static final String TABLE_NAME = "SobtiUsers";
    private final AmazonDynamoDBClient ddbClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public DynamoDBManager(AmazonDynamoDBClient client) {
        this.ddbClient = client;
    }

    // Check if user exists
    public void checkUserExists(String email, UserCheckCallback callback) {
        executor.execute(() -> {
            try {
                HashMap<String, AttributeValue> key = new HashMap<>();
                key.put("email", new AttributeValue().withS(email));

                GetItemRequest request = new GetItemRequest()
                        .withTableName(TABLE_NAME)
                        .withKey(key);

                GetItemResult result = ddbClient.getItem(request);

                UserData userData = null;
                if (result.getItem() != null && !result.getItem().isEmpty()) {
                    userData = parseUserData(result.getItem());
                }
                final UserData finalUserData = userData;
                mainThreadHandler.post(() -> callback.onResult(finalUserData != null, finalUserData));
            } catch (Exception e) {
                mainThreadHandler.post(() -> callback.onError(e));
            }
        });
    }

    // Save new user
    public void saveUser(UserData userData, SaveUserCallback callback) {
        executor.execute(() -> {
            try {
                Map<String, AttributeValue> item = new HashMap<>();
                item.put("email", new AttributeValue().withS(userData.email));
                item.put("name", new AttributeValue().withS(userData.name));
                item.put("age", new AttributeValue().withN(String.valueOf(userData.age)));
                item.put("height", new AttributeValue().withN(String.valueOf(userData.height)));
                item.put("weight", new AttributeValue().withN(String.valueOf(userData.weight)));
                item.put("emergencyNumber", new AttributeValue().withS(userData.emergencyNumber));
                item.put("createdAt", new AttributeValue().withN(String.valueOf(System.currentTimeMillis())));

                PutItemRequest request = new PutItemRequest()
                        .withTableName(TABLE_NAME)
                        .withItem(item);

                ddbClient.putItem(request);
                mainThreadHandler.post(callback::onSuccess);
            } catch (Exception e) {
                mainThreadHandler.post(() -> callback.onError(e));
            }
        });
    }

    // Update health data
    public void updateHealthData(String email, int heartRate, int steps, String location, UpdateCallback callback) {
        executor.execute(() -> {
            try {
                HashMap<String, AttributeValue> key = new HashMap<>();
                key.put("email", new AttributeValue().withS(email));

                HashMap<String, AttributeValueUpdate> updates = new HashMap<>();
                updates.put("lastHeartRate", new AttributeValueUpdate()
                        .withValue(new AttributeValue().withN(String.valueOf(heartRate)))
                        .withAction(AttributeAction.PUT));
                updates.put("lastSteps", new AttributeValueUpdate()
                        .withValue(new AttributeValue().withN(String.valueOf(steps)))
                        .withAction(AttributeAction.PUT));
                updates.put("lastLocation", new AttributeValueUpdate()
                        .withValue(new AttributeValue().withS(location))
                        .withAction(AttributeAction.PUT));
                updates.put("lastUpdated", new AttributeValueUpdate()
                        .withValue(new AttributeValue().withN(String.valueOf(System.currentTimeMillis())))
                        .withAction(AttributeAction.PUT));

                UpdateItemRequest request = new UpdateItemRequest()
                        .withTableName(TABLE_NAME)
                        .withKey(key)
                        .withAttributeUpdates(updates);

                ddbClient.updateItem(request);
                mainThreadHandler.post(callback::onSuccess);
            } catch (Exception e) {
                mainThreadHandler.post(() -> callback.onError(e));
            }
        });
    }

    // Get user data
    public void getUserData(String email, GetUserCallback callback) {
        executor.execute(() -> {
            try {
                HashMap<String, AttributeValue> key = new HashMap<>();
                key.put("email", new AttributeValue().withS(email));

                GetItemRequest request = new GetItemRequest()
                        .withTableName(TABLE_NAME)
                        .withKey(key);

                GetItemResult result = ddbClient.getItem(request);

                UserData userData = null;
                if (result.getItem() != null) {
                    userData = parseUserData(result.getItem());
                }
                final UserData finalUserData = userData;
                mainThreadHandler.post(() -> callback.onSuccess(finalUserData));
            } catch (Exception e) {
                mainThreadHandler.post(() -> callback.onError(e));
            }
        });
    }

    // Callbacks
    public interface UserCheckCallback {
        void onResult(boolean exists, UserData userData);
        void onError(Exception e);
    }

    public interface SaveUserCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public interface UpdateCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public interface GetUserCallback {
        void onSuccess(UserData userData);
        void onError(Exception e);
    }

    private UserData parseUserData(Map<String, AttributeValue> item) {
        UserData userData = new UserData();
        userData.email = item.get("email").getS();
        userData.name = item.get("name").getS();
        userData.age = Integer.parseInt(item.get("age").getN());
        userData.height = Integer.parseInt(item.get("height").getN());
        userData.weight = Integer.parseInt(item.get("weight").getN());
        userData.emergencyNumber = item.get("emergencyNumber").getS();

        if (item.containsKey("lastHeartRate")) {
            userData.lastHeartRate = Integer.parseInt(item.get("lastHeartRate").getN());
        }
        if (item.containsKey("lastSteps")) {
            userData.lastSteps = Integer.parseInt(item.get("lastSteps").getN());
        }
        if (item.containsKey("lastLocation")) {
            userData.lastLocation = item.get("lastLocation").getS();
        }

        return userData;
    }

    // User Data Model
    public static class UserData {
        public String email;
        public String name;
        public int age;
        public int height;
        public int weight;
        public String emergencyNumber;
        public int lastHeartRate;
        public int lastSteps;
        public String lastLocation;
    }
}
