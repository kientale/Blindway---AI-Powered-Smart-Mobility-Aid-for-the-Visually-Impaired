package com.example.blindwayapp.ui.navigation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ImageUtils {

    private static final String TAG = "ImageUtils";

    /**
     * Chuyển đổi ImageProxy sang Bitmap - Phiên bản ổn định hơn
     */
    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            if (planes == null || planes.length < 3) {
                Log.e(TAG, "Invalid image planes");
                return null;
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            // Copy Y plane
            yBuffer.get(nv21, 0, ySize);

            // Copy V plane
            vBuffer.get(nv21, ySize, vSize);

            // Copy U plane
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    imageProxy.getWidth(), imageProxy.getHeight(), null);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Rect rect = new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight());

            yuvImage.compressToJpeg(rect, 100, outputStream);
            byte[] imageBytes = outputStream.toByteArray();

            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap: " + e.getMessage());
            return null;
        }
    }

    /**
     * Resize bitmap
     */
    public static Bitmap resizeBitmap(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (bitmap == null) {
            return null;
        }

        try {
            return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        } catch (Exception e) {
            Log.e(TAG, "Error resizing bitmap: " + e.getMessage());
            return null;
        }
    }

    /**
     * Rotate bitmap
     */
    public static Bitmap rotateBitmap(Bitmap bitmap, float degrees) {
        if (bitmap == null || degrees == 0) {
            return bitmap;
        }

        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);

            return Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        } catch (Exception e) {
            Log.e(TAG, "Error rotating bitmap: " + e.getMessage());
            return bitmap;
        }
    }

    /**
     * Preprocess image for model
     */
    public static Bitmap preprocessImageForModel(ImageProxy imageProxy, int targetSize) {
        try {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                return null;
            }

            // Rotate if needed
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            if (rotationDegrees != 0) {
                bitmap = rotateBitmap(bitmap, rotationDegrees);
            }

            // Resize to target size
            return resizeBitmap(bitmap, targetSize, targetSize);

        } catch (Exception e) {
            Log.e(TAG, "Error in preprocessImageForModel: " + e.getMessage());
            return null;
        }
    }

    /**
     * Safely recycle bitmap
     */
    public static void recycleBitmap(Bitmap bitmap) {
        try {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error recycling bitmap: " + e.getMessage());
        }
    }
}