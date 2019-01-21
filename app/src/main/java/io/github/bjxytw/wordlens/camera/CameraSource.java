package io.github.bjxytw.wordlens.camera;


import android.Manifest;
import android.annotation.SuppressLint;
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
import java.lang.Thread.State;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.github.bjxytw.wordlens.TextRecognitionProcessor;
import io.github.bjxytw.wordlens.graphic.GraphicOverlay;

@SuppressLint("MissingPermission")
@SuppressWarnings("deprecation")
public class CameraSource {
    @SuppressLint("InlinedApi")

    public static final int ROTATION = FirebaseVisionImageMetadata.ROTATION_90;
    public static final int ROTATION_DEGREE = 90;

    private static final int CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK;
    private static final float REQUESTED_FPS = 20.0f;
    private static final int REQUESTED_PREVIEW_WIDTH = 640;
    private static final int REQUESTED_PREVIEW_HEIGHT = 480;
    private static final int FOCUS_AREA_SIZE = 100;

    private static final String TAG = "CameraSource";

    private final GraphicOverlay graphicOverlay;

    private final Map<byte[], ByteBuffer> bytesToByteBuffer = new IdentityHashMap<>();

    private final FrameProcessingRunnable processingRunnable;
    private final Object processorLock = new Object();

    private Thread processingThread;
    private TextRecognitionProcessor frameProcessor;
    private Camera camera;
    private Size previewSize;

    private boolean supportedAutoFocus;

    public CameraSource(GraphicOverlay overlay) {
        graphicOverlay = overlay;
        graphicOverlay.clear();
        processingRunnable = new FrameProcessingRunnable();
        frameProcessor = new TextRecognitionProcessor();
    }

    public void release() {
        synchronized (processorLock) {
            stop();
            processingRunnable.release();
            cleanScreen();

            if (frameProcessor != null)
                frameProcessor.stop();
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    public synchronized void start(SurfaceHolder surfaceHolder) throws IOException {
        if (camera != null) return;

        camera = createCamera();
        camera.setPreviewDisplay(surfaceHolder);
        camera.startPreview();
        setCameraFocus();

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

    @SuppressLint("InlinedApi")
    private Camera createCamera() throws IOException {
        int requestedCameraId = getIdForRequestedCamera();
        if (requestedCameraId == -1)
            throw new IOException("Could not find requested camera.");
        Camera camera = Camera.open(requestedCameraId);

        Size size = selectPreviewSize(camera);
        if (size == null)
            throw new IOException("Could not find suitable preview size.");
        previewSize = size;

        Log.i(TAG, "Selected preview size: " + previewSize.toString());

        int[] previewFpsRange = selectPreviewFpsRange(camera);
        if (previewFpsRange == null)
            throw new IOException("Could not find suitable preview frames per second range.");

        int minFps = previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
        int maxFps = previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
        Log.i(TAG, "FPS Range: " + (float) minFps / 1000.0f
                + " ~ " + (float) maxFps / 1000.0f);

        Camera.Parameters parameters = camera.getParameters();

        parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
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

        camera.setParameters(parameters);

        // Four frame buffers are needed for working with the camera.
        camera.setPreviewCallbackWithBuffer(new CameraPreviewCallback());
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));

        return camera;
    }

    public void setCameraFocus() {
        if (supportedAutoFocus) {
            Camera.Parameters parameters = camera.getParameters();
            int x = previewSize.getWidth() / 2;
            int y = previewSize.getHeight() / 2;

            int areaRadius = FOCUS_AREA_SIZE / 2;
            List<Camera.Area> focusArea = new ArrayList<>();
            focusArea.add(new Camera.Area(new Rect(
                    x - areaRadius, y - areaRadius,
                    x + areaRadius, y + areaRadius), 1));
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

    private static int getIdForRequestedCamera() {
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CAMERA_FACING_BACK)
                return i;
        }
        return -1;
    }

    private void cleanScreen() {
        graphicOverlay.clear();
    }

    public Size getPreviewSize() {
        return previewSize;
    }

    @SuppressLint("InlinedApi")
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

        FrameProcessingRunnable() {}

        @SuppressLint("Assert")
        void release() {
            assert (processingThread.getState() == State.TERMINATED);
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

        @SuppressLint("InlinedApi")
        @SuppressWarnings("GuardedBy")
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
                        Log.d(TAG, "Process an image");
                        frameProcessor.process(
                                data,
                                previewSize,
                                graphicOverlay);
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
        List<Size> validPreviewSizes = generateValidPreviewSizeList(camera);

        Size selectedPreviewSize = null;
        int minDiff = Integer.MAX_VALUE;
        for (Size previewSize : validPreviewSizes) {
            int diff =
                    Math.abs(previewSize.getWidth() - REQUESTED_PREVIEW_WIDTH)
                            + Math.abs(previewSize.getHeight() - REQUESTED_PREVIEW_HEIGHT);
            if (diff < minDiff) {
                selectedPreviewSize = previewSize;
                minDiff = diff;
            }
        }

        return selectedPreviewSize;
    }

    private static List<Size> generateValidPreviewSizeList(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> supportedPreviewSizes =
                parameters.getSupportedPreviewSizes();
        List<Size> validPreviewSizes = new ArrayList<>();
        for (android.hardware.Camera.Size previewSize : supportedPreviewSizes)
            validPreviewSizes.add(new Size(previewSize.width, previewSize.height));

        return validPreviewSizes;
    }

    @SuppressLint("InlinedApi")
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
