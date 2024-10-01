package com.example.stroke_test;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int SPEECH_REQUEST_CODE = 101;

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, gravitySensor;
    private TextView resultTextView, timerValue;
    private Button startMeasurementButton;

    private boolean isMeasuring = false;
    private long startTime;
    private Handler handler = new Handler();
    private Runnable timeRunnable;

    private List<String[]> sensorData = new ArrayList<>();

    private Bitmap image;  // 전역 변수로 이미지 저장
    private String userSpeech;  // 사용자 음성 저장

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI 요소 참조
        resultTextView = findViewById(R.id.resultTextView);
        timerValue = findViewById(R.id.timerValue);
        startMeasurementButton = findViewById(R.id.startMeasurementButton);

        // 측정 시작 버튼은 초기에는 숨김
        startMeasurementButton.setVisibility(View.GONE);

        // 센서 매니저 초기화
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        }

        // 시작 버튼 클릭 리스너 설정
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> {
            capturePhoto();

            startButton.setVisibility(View.GONE);
        });

        // 측정 시작 버튼 클릭 리스너 설정
        startMeasurementButton.setOnClickListener(v -> {
            // 3초 후에 센서 데이터 측정 시작
            new Handler(Looper.getMainLooper()).postDelayed(() -> startMeasuring(), 3000);
            // 안내 문구 변경
            resultTextView.setText("3초 후에 측정을 시작합니다. 준비하세요.");
        });
    }

    // 1. 사진 촬영
    private void capturePhoto() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
    }

    // 2. 음성 입력
    private void captureSpeech() {
        // "오늘 하루 어떠셨나요?" 문구 출력
        runOnUiThread(() -> resultTextView.setText("오늘 하루 어떠셨나요?"));

        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "오늘 하루 어떠셨나요?");
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        startActivityForResult(speechIntent, SPEECH_REQUEST_CODE);
    }

    // 3. 음성 입력 후 프로세스 진행
    private void processAfterSpeechInput() {
        // 안내 문구 및 측정 시작 버튼 표시
        runOnUiThread(() -> {
            resultTextView.setText("측정 시작 버튼을 누르고, 휴대폰을 한쪽 손에 쥔 채로 양 팔을 쭉 뻗어주세요.");
            startMeasurementButton.setVisibility(View.VISIBLE);
        });
    }

    // 4. 센서 데이터 측정 시작
    private void startMeasuring() {
        sensorData.clear();  // 이전 데이터 삭제
        isMeasuring = true;
        startTime = System.currentTimeMillis();

        // 센서 등록
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, 10000);  // 0.01초마다 데이터 수집
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, 10000);
        }
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, 10000);
        }

        // 측정 시작 시간 기록 및 경과 시간 업데이트 시작
        handler.post(timeRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsedMillis = System.currentTimeMillis() - startTime;
                timerValue.setText("Time: " + elapsedMillis / 1000.0 + " sec");
                handler.postDelayed(this, 10);  // 0.01초마다 업데이트
            }
        });

        // 10초 후에 측정 종료
        handler.postDelayed(this::stopMeasuring, 10000);

        // 버튼 및 안내 문구 숨김
        runOnUiThread(() -> {
            startMeasurementButton.setVisibility(View.GONE);
            resultTextView.setText("측정 중입니다. 움직이지 마세요.");
        });
    }

    // 5. 센서 데이터 측정 종료
    private void stopMeasuring() {
        isMeasuring = false;
        sensorManager.unregisterListener(this);

        // 경과 시간 업데이트 중지
        handler.removeCallbacks(timeRunnable);

        // "종료되었습니다. 잠시 기다려 주세요." 문구 및 음성 출력
        runOnUiThread(() -> {
            resultTextView.setText("종료되었습니다. 잠시 기다려 주세요.");
            playCompletionAudio();
        });

        // 서버로 데이터 전송
        sendDataToServer();
    }

    // 센서 데이터 변경 시 호출
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isMeasuring) {
            String[] dataRow = new String[10];
            dataRow[0] = String.valueOf(System.currentTimeMillis() - startTime);  // 경과 시간(ms)

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                dataRow[1] = String.valueOf(event.values[0]);  // Acc X
                dataRow[2] = String.valueOf(event.values[1]);  // Acc Y
                dataRow[3] = String.valueOf(event.values[2]);  // Acc Z
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                dataRow[4] = String.valueOf(event.values[0]);  // Gyro X
                dataRow[5] = String.valueOf(event.values[1]);  // Gyro Y
                dataRow[6] = String.valueOf(event.values[2]);  // Gyro Z
            } else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                dataRow[7] = String.valueOf(event.values[0]);  // Grav X
                dataRow[8] = String.valueOf(event.values[1]);  // Grav Y
                dataRow[9] = String.valueOf(event.values[2]);  // Grav Z
            }

            // 데이터 저장
            sensorData.add(dataRow);
        }
    }

    // 정확도 변경 시 호출 (여기서는 사용 안 함)
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // "종료되었습니다. 잠시 기다려 주세요." 음성 재생
    private void playCompletionAudio() {
        MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.completion);
        mediaPlayer.start();
    }

    // 서버로 데이터 전송
    private void sendDataToServer() {
        // 서버 URL 설정
        String serverUrl = "http://YOUR_EC2_PUBLIC_IP:5000/process";  // EC2 인스턴스의 퍼블릭 IP로 변경

        // 새로운 스레드에서 네트워크 작업 실행
        new Thread(() -> {
            try {
                // JSON 데이터 구성
                JSONObject requestData = new JSONObject();
                requestData.put("speech", userSpeech);

                // 센서 데이터 추가
                JSONArray sensorArray = new JSONArray();
                for (String[] dataRow : sensorData) {
                    JSONObject sensorJson = new JSONObject();
                    sensorJson.put("timestamp", dataRow[0]);
                    sensorJson.put("accel_x", dataRow[1]);
                    sensorJson.put("accel_y", dataRow[2]);
                    sensorJson.put("accel_z", dataRow[3]);
                    sensorJson.put("gyro_x", dataRow[4]);
                    sensorJson.put("gyro_y", dataRow[5]);
                    sensorJson.put("gyro_z", dataRow[6]);
                    sensorJson.put("gravity_x", dataRow[7]);
                    sensorJson.put("gravity_y", dataRow[8]);
                    sensorJson.put("gravity_z", dataRow[9]);
                    sensorArray.put(sensorJson);
                }
                requestData.put("sensor_data", sensorArray);

                // 이미지 데이터 추가
                if (image != null) {
                    String encodedImage = encodeImageToBase64(image);
                    requestData.put("image", encodedImage);
                }

                // 서버로 POST 요청
                URL url = new URL(serverUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // JSON 데이터 전송
                OutputStream os = connection.getOutputStream();
                os.write(requestData.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                // 서버 응답 받기
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 서버 응답 읽기
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    String serverResponse = response.toString();

                    // 결과 화면에 출력
                    runOnUiThread(() -> {
                        if (serverResponse.equalsIgnoreCase("pass")) {
                            resultTextView.setText("검사 결과: PASS");
                        } else {
                            resultTextView.setText("검사 결과: FAIL");
                        }
                    });
                } else {
                    Log.e("ServerResponse", "Failed to get valid response");
                    runOnUiThread(() -> resultTextView.setText("서버 응답 오류. 어플을 종료 후 재실행해 주세요."));
                }
                connection.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> resultTextView.setText("데이터 전송 중 오류 발생. 어플을 종료 후 재실행해 주세요."));
            }
        }).start();
    }

    // 이미지 Base64 인코딩
    public String encodeImageToBase64(Bitmap image) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);  // PNG 형식으로 변환
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);  // Base64로 인코딩
    }

    // 결과 처리
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 사진 촬영 결과 처리
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // 이미지 가져오기
            Bundle extras = data.getExtras();
            image = (Bitmap) extras.get("data");

            // 음성 입력 시작
            captureSpeech();

        } else if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // 음성 인식 결과 가져오기
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            userSpeech = results.get(0);  // 사용자 음성 텍스트 저장

            // 음성 입력 후 프로세스 진행
            processAfterSpeechInput();
        }
    }

    // 권한 요청 결과 처리 (필요한 경우에만 사용)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // 권한 요청 결과 처리 로직
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isMeasuring) {
            stopMeasuring(); // 액티비티가 일시 중지될 때 측정 멈춤
        }
    }
}
