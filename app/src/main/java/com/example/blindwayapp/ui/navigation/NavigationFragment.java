package com.example.blindwayapp.ui.navigation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.blindwayapp.R;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NavigationFragment extends Fragment {

    private static final String TAG = "NavigationFragment";
    private static final int INPUT_SIZE = 300;
    private static final int NUM_DETECTIONS = 10;
    private static final float CONFIDENCE_THRESHOLD = 0.6f;
    private static final float IOU_THRESHOLD = 0.4f;

    // Thông số camera để tính khoảng cách
    private static final float CAMERA_HEIGHT = 0.8f; // 80cm
    private static final float CAMERA_ANGLE = 30f;
    private static final float FOCAL_LENGTH = 1000f;
    private static final int IMAGE_HEIGHT = 480;
    private static final float IMAGE_CENTER_Y = IMAGE_HEIGHT / 2f;
    private static final float WALL_DISTANCE_THRESHOLD = 1.0f;

    private PreviewView previewView;
    private Button btnCamera;
    private ImageView overlayView;
    private TextView tvDetectionStatus;
    private boolean isCameraStarted = false;
    private int frameCount = 0;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ExecutorService cameraExecutor;
    private Interpreter tfliteInterpreter;
    private TextToSpeech tts;

    private long lastAlertTime = 0;
    private static final long ALERT_COOLDOWN = 600; // 0.6 giây
    private String lastAlertText = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cameraExecutor = Executors.newSingleThreadExecutor();
        initializeTTS();

        requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        startCamera();
                    } else {
                        Log.e(TAG, "Quyền CAMERA bị từ chối");
                        Toast.makeText(requireContext(), "Cần quyền camera để sử dụng tính năng này", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_navigation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewView = view.findViewById(R.id.previewView);
        btnCamera = view.findViewById(R.id.btnCamera);
        overlayView = view.findViewById(R.id.overlayView);
        tvDetectionStatus = view.findViewById(R.id.tvDetectionStatus);

        if (isModelFileExists()) {
            btnCamera.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    startCamera();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA);
                }
            });
            initializeModel();
        } else {
            btnCamera.setEnabled(false);
            btnCamera.setText("Model không khả dụng");
            Toast.makeText(requireContext(), "File model không tồn tại", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isModelFileExists() {
        try {
            java.io.InputStream inputStream = requireContext().getAssets().open("yolo_model.tflite");
            inputStream.close();
            Log.d(TAG, "✅ Model file tồn tại và có thể mở được");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "❌ Model file không tồn tại hoặc không thể mở: " + e.getMessage());
            return false;
        }
    }

    private void initializeTTS() {
        tts = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("vi"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Ngôn ngữ Tiếng Việt không được hỗ trợ");
                } else {
                    Log.d(TAG, "Text-to-Speech khởi tạo thành công");
                }
            } else {
                Log.e(TAG, "Không thể khởi tạo Text-to-Speech");
            }
        });
    }

    private void initializeModel() {
        new Thread(() -> {
            try {
                Log.d(TAG, "Bắt đầu khởi tạo model với TensorFlow Lite Interpreter");

                MappedByteBuffer modelBuffer = loadModelFile();
                if (modelBuffer == null) {
                    Log.e(TAG, "❌ Không thể load model file");
                    return;
                }

                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(4);
                tfliteInterpreter = new Interpreter(modelBuffer, options);

                int[] inputShape = tfliteInterpreter.getInputTensor(0).shape();
                int[] outputShape = tfliteInterpreter.getOutputTensor(0).shape();

                Log.d(TAG, "✅ Model khởi tạo thành công");
                Log.d(TAG, "Input shape: " + java.util.Arrays.toString(inputShape));
                Log.d(TAG, "Output shape: " + java.util.Arrays.toString(outputShape));

            } catch (Exception e) {
                Log.e(TAG, "❌ Lỗi khởi tạo model: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Lỗi khởi tạo model: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private MappedByteBuffer loadModelFile() {
        try {
            FileInputStream inputStream = new FileInputStream(requireContext().getAssets().openFd("yolo_model.tflite").getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = requireContext().getAssets().openFd("yolo_model.tflite").getStartOffset();
            long declaredLength = requireContext().getAssets().openFd("yolo_model.tflite").getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model file: " + e.getMessage());
            return null;
        }
    }

    private void startCamera() {
        if (isCameraStarted) {
            Log.d(TAG, "Camera đã khởi động, bỏ qua.");
            return;
        }

        Log.d(TAG, "Bắt đầu khởi động camera...");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, imageProxy -> {
                    frameCount++;
                    if (frameCount % 2 == 0) {
                        analyzeImage(imageProxy);
                    } else {
                        imageProxy.close();
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        cameraSelector,
                        preview,
                        imageAnalyzer
                );

                isCameraStarted = true;
                Log.d(TAG, "✅ Camera khởi động thành công");

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "❌ Lỗi khi khởi động camera: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "❌ Ngoại lệ khi bind camera: " + e.getMessage(), e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void analyzeImage(ImageProxy imageProxy) {
        if (tfliteInterpreter == null) {
            Log.d(TAG, "❌ TensorFlow Lite Interpreter chưa được khởi tạo");
            imageProxy.close();
            return;
        }

        try {
            Bitmap bitmap = ImageUtils.imageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                Log.e(TAG, "Failed to convert ImageProxy to Bitmap");
                imageProxy.close();
                return;
            }

            Bitmap resizedBitmap = ImageUtils.resizeBitmap(bitmap, INPUT_SIZE, INPUT_SIZE);
            ImageUtils.recycleBitmap(bitmap);

            if (resizedBitmap == null) {
                Log.e(TAG, "Failed to resize bitmap");
                imageProxy.close();
                return;
            }

            ByteBuffer inputBuffer = bitmapToByteBuffer(resizedBitmap);
            ImageUtils.recycleBitmap(resizedBitmap);

            float[][][] output = new float[1][NUM_DETECTIONS][4];

            tfliteInterpreter.run(inputBuffer, output);

            int frameWidth = imageProxy.getWidth();
            int frameHeight = imageProxy.getHeight();

            List<Detection> detections = processOutput(output[0], frameWidth, frameHeight);
            List<Detection> filteredDetections = applyNMS(detections);

            Log.d(TAG, "Số vật thể detected: " + filteredDetections.size());

            requireActivity().runOnUiThread(() -> {
                updateDetectionUI(filteredDetections);
                processDetectionResults(filteredDetections, frameWidth, frameHeight);
            });

        } catch (Exception e) {
            Log.e(TAG, "Lỗi trong quá trình phân tích hình ảnh: " + e.getMessage(), e);
        } finally {
            imageProxy.close();
        }
    }

    private ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            byteBuffer.put((byte) ((pixel >> 16) & 0xFF));
            byteBuffer.put((byte) ((pixel >> 8) & 0xFF));
            byteBuffer.put((byte) (pixel & 0xFF));
        }

        byteBuffer.rewind();
        return byteBuffer;
    }

    private List<Detection> processOutput(float[][] output, int frameWidth, int frameHeight) {
        List<Detection> detections = new ArrayList<>();

        for (int i = 0; i < NUM_DETECTIONS; i++) {
            if (output[i].length < 4) continue;

            float centerX = output[i][0];
            float centerY = output[i][1];
            float width = output[i][2];
            float height = output[i][3];

            if (!isValidBoundingBox(centerX, centerY, width, height)) {
                continue;
            }

            float confidence = calculateConfidence(width, height);
            if (confidence < CONFIDENCE_THRESHOLD) continue;

            float distance = calculateDistance(centerY * frameHeight, frameHeight);
            String direction = getDirection(centerX * frameWidth, frameWidth);

            RectF bbox = convertToCornerFormat(centerX, centerY, width, height);

            Detection detection = new Detection("vật thể", confidence, bbox, distance, direction);
            detections.add(detection);
        }

        return detections;
    }

    private boolean isValidBoundingBox(float centerX, float centerY, float width, float height) {
        return !(width < 0.05f || height < 0.05f ||
                centerX < 0 || centerX > 1 ||
                centerY < 0 || centerY > 1 ||
                width > 1.5f || height > 1.5f);
    }

    private float calculateConfidence(float width, float height) {
        return Math.min((width * height) * 8.0f, 0.8f);
    }

    private float calculateDistance(float yMax, int frameHeight) {
        float normalizedY = (yMax / frameHeight) * IMAGE_HEIGHT;
        double alpha = Math.atan((normalizedY - IMAGE_CENTER_Y) / FOCAL_LENGTH);
        double totalAngle = Math.toRadians(CAMERA_ANGLE) + alpha;
        double distance = CAMERA_HEIGHT / Math.tan(totalAngle);
        return (float) Math.max(0.1, Math.round(distance * 10) / 10.0);
    }

    private String getDirection(float xCenter, int frameWidth) {
        if (xCenter < frameWidth / 3) {
            return "bên trái";
        } else if (xCenter > 2 * frameWidth / 3) {
            return "bên phải";
        } else {
            return "phía trước";
        }
    }

    private RectF convertToCornerFormat(float centerX, float centerY, float width, float height) {
        float left = Math.max(0, centerX - width / 2);
        float top = Math.max(0, centerY - height / 2);
        float right = Math.min(1, centerX + width / 2);
        float bottom = Math.min(1, centerY + height / 2);
        return new RectF(left, top, right, bottom);
    }

    private List<Detection> applyNMS(List<Detection> detections) {
        List<Detection> filteredDetections = new ArrayList<>();
        detections.sort((d1, d2) -> Float.compare(d2.confidence, d1.confidence));

        while (!detections.isEmpty()) {
            Detection best = detections.remove(0);
            filteredDetections.add(best);

            Iterator<Detection> iterator = detections.iterator();
            while (iterator.hasNext()) {
                Detection detection = iterator.next();
                float iou = calculateIOU(best.bbox, detection.bbox);
                if (iou > IOU_THRESHOLD) {
                    iterator.remove();
                }
            }
        }

        return filteredDetections;
    }

    private float calculateIOU(RectF box1, RectF box2) {
        float interLeft = Math.max(box1.left, box2.left);
        float interTop = Math.max(box1.top, box2.top);
        float interRight = Math.min(box1.right, box2.right);
        float interBottom = Math.min(box1.bottom, box2.bottom);

        float interArea = Math.max(0, interRight - interLeft) * Math.max(0, interBottom - interTop);
        float unionArea = (box1.width() * box1.height()) + (box2.width() * box2.height()) - interArea;

        return unionArea > 0 ? interArea / unionArea : 0;
    }

    private void updateDetectionUI(List<Detection> detections) {
        int detectedCount = detections.size();

        if (tvDetectionStatus != null) {
            if (detectedCount == 0) {
                tvDetectionStatus.setText("Đang quét... không phát hiện vật thể");
            } else {
                tvDetectionStatus.setText("Phát hiện: " + detectedCount + " vật thể");
            }
        }

        overlayView.setImageBitmap(null);

        if (detectedCount > 0) {
            int viewWidth = previewView.getWidth();
            int viewHeight = previewView.getHeight();

            if (viewWidth > 0 && viewHeight > 0) {
                Bitmap overlayBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(overlayBitmap);
                Paint paint = new Paint();

                for (Detection detection : detections) {
                    RectF boundingBox = detection.bbox;
                    float left = boundingBox.left * viewWidth;
                    float top = boundingBox.top * viewHeight;
                    float right = boundingBox.right * viewWidth;
                    float bottom = boundingBox.bottom * viewHeight;

                    paint.setColor(Color.GREEN);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(4f);
                    canvas.drawRect(left, top, right, bottom, paint);

                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.GREEN);
                    paint.setTextSize(36f);
                    String labelText = detection.label;

                    float textX = Math.max(10, left);
                    float textY = Math.max(40, top - 10);
                    canvas.drawText(labelText, textX, textY, paint);
                }

                overlayView.setImageBitmap(overlayBitmap);
            }
        }
    }

    private void processDetectionResults(List<Detection> detections, int frameWidth, int frameHeight) {
        if (detections == null || detections.isEmpty()) {
            checkForWall(frameHeight);
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAlertTime < ALERT_COOLDOWN) {
            return;
        }

        Detection closestDetection = findClosestDetection(detections);
        if (closestDetection != null && closestDetection.confidence > CONFIDENCE_THRESHOLD) {
            String alertText;
            if (detections.size() > 1) {
                alertText = String.format(Locale.getDefault(),
                        "Phát hiện %d vật thể, gần nhất ở %s cách %.1f mét",
                        detections.size(), closestDetection.direction, closestDetection.distance);
            } else {
                alertText = String.format(Locale.getDefault(),
                        "Phát hiện vật thể ở %s cách %.1f mét",
                        closestDetection.direction, closestDetection.distance);
            }

            if (!alertText.equals(lastAlertText)) {
                announceDetection(alertText);
                lastAlertText = alertText;
                lastAlertTime = currentTime;
            }
        }
    }

    private Detection findClosestDetection(List<Detection> detections) {
        Detection closest = null;
        float minDistance = Float.MAX_VALUE;

        for (Detection detection : detections) {
            if (detection.distance < minDistance) {
                minDistance = detection.distance;
                closest = detection;
            }
        }
        return closest;
    }

    private void checkForWall(int frameHeight) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAlertTime < ALERT_COOLDOWN) {
            return;
        }

        float sampleY = frameHeight * 0.75f;
        float distance = calculateDistance(sampleY, frameHeight);

        if (distance <= WALL_DISTANCE_THRESHOLD) {
            String alertText = String.format(Locale.getDefault(),
                    "Cảnh báo: Ở sát tường, khoảng cách %.1f mét!", distance);

            if (!alertText.equals(lastAlertText)) {
                announceDetection(alertText);
                lastAlertText = alertText;
                lastAlertTime = currentTime;
            }
        }
    }

    private void announceDetection(String message) {
        Log.d(TAG, "Thông báo: " + message);

        if (tts != null) {
            try {
                tts.stop();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
                } else {
                    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi Text-to-Speech: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        if (tfliteInterpreter != null) {
            try {
                tfliteInterpreter.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing interpreter: " + e.getMessage());
            }
        }

        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down TTS: " + e.getMessage());
            }
        }
    }

    private static class Detection {
        String label;
        float confidence;
        RectF bbox;
        float distance;
        String direction;

        Detection(String label, float confidence, RectF bbox, float distance, String direction) {
            this.label = label;
            this.confidence = confidence;
            this.bbox = bbox;
            this.distance = distance;
            this.direction = direction;
        }
    }
}