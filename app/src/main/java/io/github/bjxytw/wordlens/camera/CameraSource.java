package io.github.bjxytw.wordlens.camera;


import android.Manifest;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.support.annotation.RequiresPermission;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.android.gms.common.images.Size;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.github.bjxytw.wordlens.processor.TextRecognition;
import io.github.bjxytw.wordlens.graphic.GraphicOverlay;

@SuppressWarnings("deprecation")
public class CameraSource {
    public static final int ROTATION = FirebaseVisionImageMetadata.ROTATION_90;
    private static final int ROTATION_DEGREE = 90;

    private static final int CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK;
    private static final float REQUESTED_FPS = 10.0f;
    private static final int REQUESTED_PREVIEW_WIDTH = 640;
    private static final int REQUESTED_PREVIEW_HEIGHT = 480;

    private static final String TAG = "CameraSource";

    private final GraphicOverlay graphicOverlay;

    private final Map<byte[], ByteBuffer> bytesToByteBuffer = new IdentityHashMap<>();

    private final FrameProcessingRunnable processingRunnable;
    private final Object processorLock = new Object();

    private Thread processingThread;
    private TextRecognition frameProcessor;
    private Camera camera;
    private Size size;

    private boolean supportedAutoFocus;
    private boolean supportedFlash;
    private boolean flashed;
    private List<Camera.Area> focusArea;

    public CameraSource(GraphicOverlay overlay, TextRecognition frameProcessor) {
        graphicOverlay = overlay;
        processingRunnable = new FrameProcessingRunnable();
        this.frameProcessor = frameProcessor;
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    public synchronized void start(SurfaceHolder surfaceHolder) throws IOException {
        if (camera != null) return;

        camera = createCamera();
        camera.setPreviewDisplay(surfaceHolder);
        camera.startPreview();

        processingThread = new Thread(processingRunnable);
        processingRunnable.setActive(true);
        processingThread.start();
    }

    public synchronized void stop() {
        processingRunnable.setActive(false);
        if (processingThread != null) {
            try {
                processingThread.join();
            } catch (InterruptedException e) {
                Log.d(TAG, "Frame processing thread interrupted on release.");
            }
            processingThread = null;
        }

        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallbackWithBuffer(null);
            try {
                camera.setPreviewDisplay(null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear camera preview: " + e);
            }
            camera.release();
            camera = null;
        }
        bytesToByteBuffer.clear();
    }

    public void release() {
        synchronized (processorLock) {
            stop();
            if (frameProcessor != null)
                frameProcessor.stop();
        }
    }

    private Camera createCamera() throws IOException {
        int requestedCameraId = getIdForRequestedCamera();
        if (requestedCameraId == -1)
            throw new IOException("Could not find requested camera.");
        Camera camera = Camera.open(requestedCameraId);

        Size previewSize = selectPreviewSize(camera);
        if (previewSize == null)
            throw new IOException("Could not find suitable preview size.");
        size = previewSize;

        Log.i(TAG, "Selected preview size: " + this.size.toString());

        int[] previewFpsRange = selectPreviewFpsRange(camera);
        if (previewFpsRange == null)
            throw new IOException("Could not find suitable preview frames per second range.");

        int minFps = previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
        int maxFps = previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
        Log.i(TAG, "FPS Range: " + (float) minFps / 1000.0f
                + " ~ " + (float) maxFps / 1000.0f);

        Camera.Parameters parameters = camera.getParameters();

        parameters.setPreviewSize(size.getWidth(), size.getHeight());
        parameters.setPreviewFpsRange(minFps, maxFps);
        parameters.setPreviewFormat(ImageFormat.NV21);

        parameters.setRotation(ROTATION_DEGREE);
        camera.setDisplayOrientation(ROTATION_DEGREE);

        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_MACRO)
                && parameters.getMaxNumFocusAreas() > 0) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
            supportedAutoFocus = true;
        } else {
            supportedAutoFocus = false;
            Log.i(TAG, "Camera auto focus is not supported on this device.");
        }

        if (parameters.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_TORCH)) {
            supportedFlash = true;
            if (flashed) parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        } else {
            supportedFlash = false;
            Log.i(TAG, "Camera flash is not supported on this device.");
        }

        camera.setParameters(parameters);

        camera.setPreviewCallbackWithBuffer(new CameraPreviewCallback());

        // Four frame buffers are needed for working with the camera.
        //camera.addCallbackBuffer(createPreviewBuffer(size));
        //camera.addCallbackBuffer(createPreviewBuffer(size));
        //camera.addCallbackBuffer(createPreviewBuffer(size));

        camera.addCallbackBuffer(createPreviewBuffer(size));

        return camera;
    }

    public synchronized void cameraFocus() {
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            if (focusArea != null && parameters != null) {
                parameters.setFocusAreas(focusArea);
                camera.setParameters(parameters);
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        Log.d(TAG, "onAutoFocus: " + success);
                    }
                });
            }
        }
    }

    public synchronized boolean cameraFlash(boolean on){
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            if (supportedFlash && parameters != null) {
                if (on) parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                else parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(parameters);
                flashed = on;
                return true;
            }
        }
        return false;
    }

    public void setCameraFocusArea() {
        Rect rect = graphicOverlay.getCameraCursorRect();
        if (supportedAutoFocus && rect != null) {
            focusArea = new ArrayList<>();
            focusArea.add(new Camera.Area(
                    new Rect(calculateFocusPoint(rect.left, size.getWidth()),
                            calculateFocusPoint(rect.top, size.getHeight()),
                            calculateFocusPoint(rect.right, size.getWidth()),
                            calculateFocusPoint(rect.bottom, size.getHeight())), 1));
        }
    }

    private static int calculateFocusPoint(int point, int cameraSize) {
        return point * 2000 / cameraSize - 1000;
    }

    private static int getIdForRequestedCamera() {
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CAMERA_FACING_BACK)
                return i;
        }
        return -1;
    }

    public Size getSize() {
        return size;
    }

    private byte[] createPreviewBuffer(Size previewSize) {

        int bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
        long sizeInBits = (long) previewSize.getHeight() * previewSize.getWidth() * bitsPerPixel;
        int bufferSize = (int) Math.ceil(sizeInBits / 8.0d) + 1;

        byte[] byteArray = new byte[bufferSize];
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        if (!buffer.hasArray() || (buffer.array() != byteArray))
            throw new IllegalStateException("Failed to create valid buffer for camera source.");

        bytesToByteBuffer.put(byteArray, buffer);
        return byteArray;
    }

    private class CameraPreviewCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            processingRunnable.setNextFrame(data, camera);
        }
    }

    private class FrameProcessingRunnable implements Runnable {

        private final Object lock = new Object();
        private boolean active = true;

        private ByteBuffer pendingFrameData;

        FrameProcessingRunnable() {
        }

        void setActive(boolean active) {
            synchronized (lock) {
                this.active = active;
                lock.notifyAll();
            }
        }

        void setNextFrame(byte[] data, Camera camera) {
            synchronized (lock) {
                if (pendingFrameData != null) {
                    camera.addCallbackBuffer(pendingFrameData.array());
                    pendingFrameData = null;
                }

                if (!bytesToByteBuffer.containsKey(data)) {
                    Log.d(TAG, "Skipping frame. Could not find ByteBuffer associated "
                            + "with the image data from the camera.");
                    return;
                }
                pendingFrameData = bytesToByteBuffer.get(data);
                lock.notifyAll();
            }
        }

        @Override
        public void run() {
            ByteBuffer data;

            while (true) {
                synchronized (lock) {
                    while (active && (pendingFrameData == null)) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Frame processing loop terminated.", e);
                            return;
                        }
                    }

                    if (!active) return;

                    data = pendingFrameData;
                    pendingFrameData = null;
                }

                try {
                    synchronized (processorLock) {
                        frameProcessor.process(new ImageData(data, size.getWidth(), size.getHeight()));
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Exception thrown from receiver.", t);
                } finally {
                    camera.addCallbackBuffer(data.array());
                }
            }
        }

    }

    private static Size selectPreviewSize(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Size> validPreviewSizes = new ArrayList<>();
        Size selectedPreviewSize = null;

        for (android.hardware.Camera.Size previewSize : supportedPreviewSizes)
            validPreviewSizes.add(new Size(previewSize.width, previewSize.height));

        int minDiff = Integer.MAX_VALUE;
        for (Size previewSize : validPreviewSizes) {
            int diff = Math.abs(previewSize.getWidth() - REQUESTED_PREVIEW_WIDTH)
                            + Math.abs(previewSize.getHeight() - REQUESTED_PREVIEW_HEIGHT);
            if (diff < minDiff) {
                selectedPreviewSize = previewSize;
                minDiff = diff;
            }
        }

        return selectedPreviewSize;
    }

    private static int[] selectPreviewFpsRange(Camera camera) {

        int desiredPreviewFpsScaled = (int) (REQUESTED_FPS * 1000.0f);

        int[] selectedFpsRange = null;
        int minDiff = Integer.MAX_VALUE;
        List<int[]> previewFpsRangeList = camera.getParameters().getSupportedPreviewFpsRange();
        for (int[] range : previewFpsRangeList) {

            int deltaMin = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
            int deltaMax = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
            int diff = Math.abs(deltaMin) + Math.abs(deltaMax);
            if (diff < minDiff) {
                selectedFpsRange = range;
                minDiff = diff;
            }
        }
        return selectedFpsRange;
    }

}
