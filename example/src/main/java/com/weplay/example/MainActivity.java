package com.weplay.example;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.weplay.sdk.WPCallback;
import com.weplay.sdk.WePlaySdk;

import java.text.DecimalFormat;

/**
 * Example main activity.
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private LottieAnimationView animBg;
    private TextView timeLabel, scoreLabel;
    private static long lastClickTime = System.currentTimeMillis();
    private long startTimestamp;
    private boolean start = false;
    private final DecimalFormat df2 = new DecimalFormat("#.00");
    private static final int MSG_COUNT = 0x51;
    private static final int MSG_STOP = 0x52;
    private double useTime = 0;

    private final Handler handler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_COUNT:
                    useTime = (System.currentTimeMillis() - startTimestamp) / 1000.0;
                    timeLabel.setText(scale3(useTime) + " seconds");
                    startCount(10);
                    break;
                case MSG_STOP:
                    double score = calScore(useTime);
                    scoreLabel.setText("score:" + score);
                    YoYo.with(Techniques.Flash).playOn(scoreLabel);
                    handler.removeMessages(MSG_COUNT);
                    // report score to weplay
                    WePlaySdk.reportScore(score, new WPCallback() {
                        @Override
                        public void onSuccess() {
                            // Report score success
                        }

                        @Override
                        public void onFailure(String err) {
                            // Report score failure
                        }
                    });
                    break;
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView buttonPlay = findViewById(R.id.btnStop);
        buttonPlay.setOnClickListener(this);
        animBg = findViewById(R.id.animBg);
        timeLabel = findViewById(R.id.timeLabel);
        scoreLabel = findViewById(R.id.scoreLabel);

        // WePlaySDK initialize.
        WePlaySdk.initialize("612dbb7ec09be353a75d9002", this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // WePlaySDK request file read permission.
        WePlaySdk.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onClick(View v) {
        if (System.currentTimeMillis() - lastClickTime < 200) {
            return;
        }
        lastClickTime = System.currentTimeMillis();

        if (v.getId() == R.id.btnStop) {
            if (start) {
                // do stop
                start = false;
                animBg.setVisibility(View.INVISIBLE);
                animBg.cancelAnimation();
                this.stopCount();
                YoYo.with(Techniques.FadeIn).playOn(timeLabel);

            } else {
                // do start
                startTimestamp = System.currentTimeMillis();
                start = true;
                animBg.setVisibility(View.VISIBLE);
                animBg.playAnimation();
                this.startCount(0);
                scoreLabel.setText("");
                YoYo.with(Techniques.FadeIn).playOn(scoreLabel);
            }
        }
    }

    public void startCount(long delay) {
        Message msg = handler.obtainMessage(MSG_COUNT);
        if (delay == 0) {
            handler.sendMessage(msg);
        } else {
            handler.sendMessageDelayed(msg, delay);
        }
    }

    public void stopCount() {
        Message msg = handler.obtainMessage(MSG_STOP);
        handler.sendMessage(msg);
    }

    public double calScore(double time) {
        if (time < 0) {
            return 0;
        } else if (time >= 0 && time < 10) {
            return Math.pow(time, 2);
        } else if (time >= 10 && time < 20) {
            return Math.pow((time - 20), 2);
        } else {
            return 0;
        }
    }

    public String scale3(Double d) {
        if (d == null) {
            return "#0.000";
        }
        return df2.format(Double.parseDouble(String.valueOf(d)));
    }

}