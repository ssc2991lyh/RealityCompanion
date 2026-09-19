package com.realitycompanion;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
    private EditText etServerUrl, etPairingToken;
    private Button btnConnect, btnDisconnect, btnCamera, btnLocation, btnTelemetry, btnActivity;
    private TextView tvStatus, tvLog;
    private ApiClient apiClient;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerUrl = findViewById(R.id.etServerUrl);
        etPairingToken = findViewById(R.id.etPairingToken);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnCamera = findViewById(R.id.btnCamera);
        btnLocation = findViewById(R.id.btnLocation);
        btnTelemetry = findViewById(R.id.btnTelemetry);
        btnActivity = findViewById(R.id.btnActivity);
        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);

        apiClient = ApiClient.getInstance(this);

        // Restore saved config
        if (apiClient.isConnected()) {
            etServerUrl.setText(apiClient.getBaseUrl());
            setConnected(true);
        }

        btnConnect.setOnClickListener(v -> {
            String url = etServerUrl.getText().toString().trim();
            String token = etPairingToken.getText().toString().trim();
            if (url.isEmpty() || token.isEmpty()) {
                showSnack("请填写服务器地址和配对令牌");
                return;
            }
            apiClient.saveConfig(url, "");
            log("正在配对...");
            apiClient.pair(token, new ApiClient.ApiCallback() {
                @Override public void onSuccess(Object result) {
                    runOnUiThread(() -> {
                        setConnected(true);
                        log("配对成功！");
                        showSnack("配对成功");
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        log("配对失败: " + error);
                        showSnack("配对失败: " + error);
                    });
                }
            });
        });

        btnDisconnect.setOnClickListener(v -> {
            apiClient.closeSession(new ApiClient.ApiCallback() {
                @Override public void onSuccess(Object result) {
                    runOnUiThread(() -> {
                        setConnected(false);
                        log("已断开连接");
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> log("断开失败: " + error));
                }
            });
        });

        btnCamera.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                apiClient.getCameraFrame(new ApiClient.ApiCallback() {
                    @Override public void onSuccess(Object result) {
                        runOnUiThread(() -> log("摄像头: " + result.toString().substring(0, Math.min(200, result.toString().length()))));
                    }
                    @Override public void onError(String error) {
                        runOnUiThread(() -> log("摄像头错误: " + error));
                    }
                });
            }
        });

        btnLocation.setOnClickListener(v -> {
            if (checkLocationPermission()) {
                // Try to get last known location
                try {
                    android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
                    android.location.Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                    if (loc == null) loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                    if (loc != null) {
                        apiClient.sendLocation(loc.getLatitude(), loc.getLongitude(), new ApiClient.ApiCallback() {
                            @Override public void onSuccess(Object result) {
                                runOnUiThread(() -> log("位置上报成功: " + loc.getLatitude() + ", " + loc.getLongitude()));
                            }
                            @Override public void onError(String error) {
                                runOnUiThread(() -> log("位置上报失败: " + error));
                            }
                        });
                    } else {
                        log("无法获取位置，请检查GPS");
                    }
                } catch (SecurityException e) {
                    log("位置权限不足: " + e.getMessage());
                }
            }
        });

        btnTelemetry.setOnClickListener(v -> {
            String json = "{\"source\":\"app\",\"measurements\":[{\"type\":\"heart_rate\",\"value\":72,\"unit\":\"bpm\"}]}";
            apiClient.sendTelemetry(json, new ApiClient.ApiCallback() {
                @Override public void onSuccess(Object result) {
                    runOnUiThread(() -> log("健康数据已上报"));
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> log("上报失败: " + error));
                }
            });
        });

        btnActivity.setOnClickListener(v -> {
            String json = "{\"state\":\"idle\",\"duration_minutes\":0}";
            apiClient.sendActivity(json, new ApiClient.ApiCallback() {
                @Override public void onSuccess(Object result) {
                    runOnUiThread(() -> log("活动状态已上报"));
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> log("上报失败: " + error));
                }
            });
        });
    }

    private void setConnected(boolean connected) {
        btnConnect.setEnabled(!connected);
        btnDisconnect.setEnabled(connected);
        btnCamera.setEnabled(connected);
        btnLocation.setEnabled(connected);
        btnTelemetry.setEnabled(connected);
        btnActivity.setEnabled(connected);
        tvStatus.setText(connected ? R.string.status_connected : R.string.status_disconnected);
        tvStatus.setTextColor(getColor(connected ? R.color.purple_500 : R.color.red_500));
    }

    private void log(String msg) {
        runOnUiThread(() -> {
            tvLog.append("[" + getTimeStr() + "] " + msg + "\n");
            // Auto scroll
            tvLog.post(() -> {
                android.widget.ScrollView sv = (android.widget.ScrollView) tvLog.getParent();
                sv.fullScroll(android.view.View.FOCUS_DOWN);
            });
        });
    }

    private String getTimeStr() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }

    private void showSnack(String msg) {
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show();
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
                showSnack("权限已授予");
            } else {
                showSnack("权限被拒绝，部分功能不可用");
            }
        }
    }
}