package com.realitycompanion;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    private static ApiClient instance;
    private final OkHttpClient client;
    private final Gson gson;
    private String baseUrl;
    private String sessionToken;
    private final SharedPreferences prefs;

    public interface ApiCallback {
        void onSuccess(Object result);
        void onError(String error);
    }

    private ApiClient(Context ctx) {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        gson = new Gson();
        prefs = ctx.getSharedPreferences("device", Context.MODE_PRIVATE);
        loadConfig();
    }

    public static synchronized ApiClient getInstance(Context ctx) {
        if (instance == null) {
            instance = new ApiClient(ctx.getApplicationContext());
        }
        return instance;
    }

    private void loadConfig() {
        baseUrl = prefs.getString("server_url", "");
        sessionToken = prefs.getString("session_token", "");
    }

    public void saveConfig(String url, String token) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("server_url", url);
        editor.putString("session_token", token);
        editor.apply();
        this.baseUrl = url;
        this.sessionToken = token;
    }

    public void clearConfig() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("server_url");
        editor.remove("session_token");
        editor.apply();
        this.baseUrl = "";
        this.sessionToken = null;
    }

    public boolean isConnected() {
        return baseUrl != null && !baseUrl.isEmpty() && sessionToken != null && !sessionToken.isEmpty();
    }

    public String getBaseUrl() { return baseUrl; }
    public String getSessionToken() { return sessionToken; }

    private Request.Builder authRequest(String path) {
        return new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", "Bearer " + sessionToken);
    }

    // Pairing: exchange token for session
    public void pair(String pairingToken, ApiCallback callback) {
        Request request = new Request.Builder()
                .url(baseUrl + "/pair")
                .post(RequestBody.create(
                        "{\"pairing_token\":\"" + pairingToken + "\"}",
                        MediaType.get("application/json")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        PairResponse pr = gson.fromJson(body, PairResponse.class);
                        if (pr.session_token != null) {
                            sessionToken = pr.session_token;
                            saveConfig(baseUrl, sessionToken);
                        }
                        callback.onSuccess(pr);
                    } catch (Exception e) {
                        callback.onError("解析失败: " + e.getMessage());
                    }
                } else {
                    callback.onError("配对失败: " + response.code() + " " + body);
                }
            }
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("网络错误: " + e.getMessage());
            }
        });
    }

    // Health check
    public void health(ApiCallback callback) {
        client.newCall(new Request.Builder().url(baseUrl + "/health").build())
                .enqueue(new Callback() {
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        callback.onSuccess(response.isSuccessful() ? "OK" : "Error: " + response.code());
                    }
                    @Override public void onFailure(Call call, IOException e) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    // Camera single frame
    public void getCameraFrame(ApiCallback callback) {
        authRequest("/device/status").post(RequestBody.create("", null))
                .enqueue(new Callback() {
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        String body = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            callback.onSuccess(body);
                        } else {
                            callback.onError("获取失败: " + response.code());
                        }
                    }
                    @Override public void onFailure(Call call, IOException e) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    // Location heartbeat
    public void sendLocation(double lat, double lng, ApiCallback callback) {
        String json = "{\"lat\":" + lat + ",\"lng\":" + lng + "}";
        authRequest("/location")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .enqueue(new Callback() {
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        callback.onSuccess(response.isSuccessful() ? "OK" : "Error: " + response.code());
                    }
                    @Override public void onFailure(Call call, IOException e) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    // Telemetry
    public void sendTelemetry(String json, ApiCallback callback) {
        authRequest("/telemetry")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .enqueue(new Callback() {
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        callback.onSuccess(response.isSuccessful() ? "OK" : "Error: " + response.code());
                    }
                    @Override public void onFailure(Call call, IOException e) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    // Activity
    public void sendActivity(String json, ApiCallback callback) {
        authRequest("/device/activity")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .enqueue(new Callback() {
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        callback.onSuccess(response.isSuccessful() ? "OK" : "Error: " + response.code());
                    }
                    @Override public void onFailure(Call call, IOException e) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    // Screen heartbeat
    public void screenHeartbeat(String json, ApiCallback callback) {
        authRequest("/screen/heartbeat")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .enqueue(new Callback() {
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        callback.onSuccess(response.isSuccessful() ? "OK" : "Error: " + response.code());
                    }
                    @Override public void onFailure(Call call, IOException e) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    // Close session
    public void closeSession(ApiCallback callback) {
        authRequest("/session/close")
                .post(RequestBody.create("", null))
                .enqueue(new Callback() {
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        clearConfig();
                        callback.onSuccess("已断开");
                    }
                    @Override public void onFailure(Call call, IOException e) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    static class PairResponse {
        @SerializedName("session_token")
        String session_token;
        String message;
    }
}