package com.weplay.sdk;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WePlaySdk {

    private static final String TAG = WePlaySdk.class.getSimpleName();
    private static final String URL = "http://47.119.121.220:3001";
    private static String userId = null;
    private static String loginStatus = null;
    private static final int MAX_TRY = 30;
    private static int tryCount;
    private static final int MSG_CHECK = 0x61;
    private static String GAME_ID = null;
    private static String sessionId = null;
    private static final int PERMISSION_REQUEST_STORAGE = 0x101;

    private WePlaySdk() {

    }

    public static void initialize(@NonNull String gameId, @NonNull Activity activity) {
        GAME_ID = gameId;
        checkFilePermission(activity);
    }

    private static void checkFilePermission(@NonNull Activity activity) {
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_STORAGE);
            } else {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_STORAGE);
            }
        } else {
            // crate weplay session
            createSession();
        }
    }

    public static void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_STORAGE) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted.
                createSession();
            } else {
                // Permission request was denied.
                Log.e(TAG, "READ_EXTERNAL_STORAGE has been denied.");
            }
        }
    }

    private static final Handler handler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_CHECK) {
                checkAppResponse(null, null);
            }
        }
    };

    /**
     * Create session
     */
    private static void createSession() {
        sessionId = getSessionIdFromUserData();
        login();
    }

    /**
     * Login to server
     */
    private static void login() {
        if (sessionId == null || sessionId.length() == 0) {
            Log.e(TAG, "Login onFailure: No sessionId");
            return;
        }
        tryCount = 0;
        OkHttpClient okHttpClient = new OkHttpClient();
        RequestBody formBody = new FormBody.Builder()
                .add("gameId", GAME_ID)
                .add("sessionId", sessionId)
                .build();

        Request request = new Request.Builder()
                .url(URL + "/login/sdkLogin")
                .post(formBody)
                .build();

        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Login onFailure: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    JSONObject respObj = JSONObject.parseObject(response.body().string());
                    if (respObj != null) {
                        if (respObj.getInteger("code") == 200) {
                            userId = respObj.getJSONObject("data").getString("userId");
                            if (userId != null) {
                                // loop query
                                handler.sendMessageDelayed(handler.obtainMessage(MSG_CHECK), 1000);
                            }
                        }
                    }
                    Log.d(TAG, "onResponse: " + respObj);
                }
            }
        });
    }

    /**
     * check weplay app response.
     * if has score, after confirm login do report score.
     *
     * @param score score
     * @param callback callback
     */
    private static void checkAppResponse(Double score, WPCallback callback) {
        if (score == null) {
            tryCount++;
            if (tryCount > MAX_TRY) {
                // notify failure login
                handler.removeMessages(MSG_CHECK);
                Log.e(TAG, "check over max try. ");
                return;
            }
        }

        OkHttpClient okHttpClient = new OkHttpClient();
        RequestBody formBody = new FormBody.Builder()
                .add("userId", userId)
                .add("gameId", GAME_ID)
                .add("sessionId", sessionId)
                .build();

        Request request = new Request.Builder()
                .url(URL + "/login/checkLoginStatus")
                .post(formBody)
                .build();

        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e(TAG, "CheckLoginStatus onFailure: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    JSONObject respObj = JSONObject.parseObject(response.body().string());
                    Log.d(TAG, "onResponse: " + respObj);
                    if (respObj != null) {
                        if (respObj.getInteger("code") == 200) {
                            loginStatus = respObj.getJSONObject("data").getString("status");
                            if ("Approved".equals(loginStatus)) {
                                handler.removeMessages(MSG_CHECK);
                                doReport(score, callback);
                            } else if ("Cancel".equals(loginStatus)) {
                                handler.removeMessages(MSG_CHECK);
                                Log.e(TAG, "User cancel login. ");
                            }
                        }
                    }
                }

            }
        });

        if (score == null) {
            // do loop
            handler.sendMessageDelayed(handler.obtainMessage(MSG_CHECK), 1000);
        }
    }

    public static void reportScore(double score, WPCallback callback) {
        tryCount = 0;
        if (userId == null) {
            Log.e(TAG, "No login info. ");
            callback.onFailure("No login info");
            return;
        }
        if (!"Approved".equals(loginStatus)) {
            // check login status again.
            checkAppResponse(score, callback);
            return;
        }

        doReport(score, callback);
    }

    private static void doReport(double score, WPCallback callback) {
        OkHttpClient okHttpClient = new OkHttpClient();
        RequestBody formBody = new FormBody.Builder()
                .add("userId", userId)
                .add("gameId", GAME_ID)
                .add("sessionId", sessionId)
                .add("score", String.valueOf(score))
                .build();

        Request request = new Request.Builder()
                .url(URL + "/score/reportScore")
                .post(formBody)
                .build();

        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Report score onFailure: " + e.getMessage());
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    JSONObject respObj = JSONObject.parseObject(response.body().string());
                    Log.d(TAG, "onResponse: " + respObj);
                    if (respObj != null) {
                        if (respObj.getInteger("code") == 200) {
                            callback.onSuccess();
                        }
                    }
                }
            }
        });
    }

    private static String getSessionIdFromUserData() {
        String ret;
        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "/Download/userdata";
        File userdata = new File(filePath);
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(userdata));
            String line =bufferedReader.readLine();
            StringBuilder stringBuffer = new StringBuilder();
            while (line != null){
                stringBuffer.append(line);
                line = bufferedReader.readLine();
            }
            org.json.JSONObject myJsonObject =  new org.json.JSONObject(stringBuffer.toString());
            ret = myJsonObject.getString( "session_id" );
        } catch (Exception e) {
            e.printStackTrace();
            ret = e.getMessage();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ignore) {
                    // do nothing.
                }
            }
        }
        return ret;
    }

}
