package io.github.bjxytw.wordlens.camera;


import android.Manifest;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import androidx.annotation.RequiresPermission;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.android.gms.common.images.Size;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.github.bjxytw.wordlens.TextRecognition;
import io.github.bjxytw.wordlens.data.ImageData;

@SuppressWarnings("deprecation")
public class CameraSource {
    public static final int ROTATION = FirebaseVisionImageMetadata.ROTATION_90;
    private static final int ROTATION_DEGREE = 90;

    private static final int CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK;
    private static final float REQUESTED_FPS = 10.0f;
    private static final int REQUESTED_PREVIEW_WIDTH = 640;
    private static final int REQUESTED_PREVIEW_HEIGHT = 480;

    private static final String TAG = "CameraSource";

    private final Map<byte[], ByteBuffer> byteArrayToByteBuffer = new IdentityHashMap<>();

    private final ProcessRunnable processRunnable;
    private final Object processLock = new Object();

    private Thread processThread;
    private TextRecognition textRecognition;
    private Camera camera;
    private Size size;
    private AutoFocusFinishedListener autoFocusListener;
    private Integer requestedZoomRatio;
    private int zoomStep;

    private boolean supportedFocus = false;
    private boolean supportedFlash = false;
    private boolean supportedZoom = false;
    private boolean flashed;
    private boolean zoomed;

    public interface AutoFocusFinishedListener {
        void onAutoFocus(boolean success);
    }

    public CameraSource(TextRecognition textRecognition, AutoFocusFinishedListener listener) {
        processRunnable = new ProcessRunnable();
        this.textRecognition = textRecognition;
        autoFocusListener = listener;
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    synchronized void start(SurfaceHolder surfaceHolder) throws IOException {
        if (camera != null) return;

        camera = createCamera();
        camera.setPreviewDisplay(surfaceHolder);
        camera.startPreview();

        processThread = new Thread(processRunnable);
        processRunnable.setActive(true);
        processThread.start();
    }

    synchronized void stop() {
        processRunnable.setActive(false);
        if (processThread != null) {
            try {
                processThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
            processThread = null;
        }

        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallbackWithBuffer(null);
            try {
                camera.setPreviewDisplay(null);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
            camera.release();
            camera = null;
        }
        byteArrayToByteBuffer.clear();
    }

    public void release() {
        synchronized (processLock) {
            stop();
            if (textRecognition != null)
                textRecognition.stop();
        }
    }

    private Camera createCamera() throws IOException {
        int cameraId = getCameraId();
        if (cameraId == -1)
            throw new IOException("Could not find camera.");
        Camera camera = Camera.open(cameraId);
        Camera.Parameters parameters = camera.getParameters();

        Size previewSize = selectPreviewSize(parameters);
        if (previewSize == null) throw new IOException("Could not find preview size.");
        size = previewSize;

        int[] previewFpsRange = selectFpsRange(parameters);
        if (previewFpsRange == null) throw new IOException("Could not find FPS range.");

        int minFps = previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
        int maxFps = previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];

        parameters.setPreviewSize(size.getWidth(), size.getHeight());
        parameters.setPreviewFpsRange(minFps, maxFps);
        parameters.setPreviewFormat(ImageFormat.NV21);

        parameters.setRotation(ROTATION_DEGREE);
        camera.setDisplayOrientation(ROTATION_DEGREE);

        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null) {
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_MACRO)
                    && parameters.getMaxNumFocusAreas() > 0) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
                supportedFocus = true;
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else Log.i(TAG, "Camera auto focus is not supported on this device.");
        } else Log.i(TAG, "Camera auto focus is not supported on this device.");

        List<String> flashModes = parameters.getSupportedFlashModes();
        if (flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
            supportedFlash = true;
            if (flashed) parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        } else {
            Log.i(TAG, "Camera flash is not supported on this device.");
        }

        if (parameters.isZoomSupported()) {
            Integer step = selectZoomStep(parameters);
            if (step != null){
                zoomStep = step;
                supportedZoom = true;
                if (zoomed) parameters.setZoom(zoomStep);
            }
        } else {
            Log.i(TAG, "Camera zoom is not supported on this device.");
        }

        camera.setParameters(parameters);

        camera.setPreviewCallbackWithBuffer(new CameraCallback());
        camera.addCallbackBuffer(createPreviewBuffer(size));

        return camera;
    }

    synchronized boolean cameraFocus() {
        if (camera == null || !supportedFocus) return false;

        camera.cancelAutoFocus();
        try {
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    autoFocusListener.onAutoFocus(success);
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, e.toString());
            return false;
        }

        return true;
    }

    public synchronized boolean cameraFlash(boolean flash){
        if (camera == null || !supportedFlash) return false;

        Camera.Parameters parameters = camera.getParameters();
        if (parameters == null) return false;

        if (flash) parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        else parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(parameters);
        flashed = flash;
        return true;
    }

    public synchronized boolean cameraZoom(boolean zoom) {
        if (camera == null || !supportedZoom) return false;

        Camera.Parameters parameters = camera.getParameters();
        if (parameters == null) return false;

        if (zoom) parameters.setZoom(zoomStep);
        else parameters.setZoom(0);
        camera.setParameters(parameters);
        zoomed = zoom;
        return true;
    }

    void setCameraFocusArea(Rect rect) {
        if (!supportedFocus || rect == null) return;

        List<Camera.Area> focusArea = new ArrayList<>();
        focusArea.add(new Camera.Area(
                new Rect(calculateFocusPoint(rect.left, size.getWidth()),
                        calculateFocusPoint(rect.top, size.getHeight()),
                        calculateFocusPoint(rect.right, size.getWidth()),
                        calculateFocusPoint(rect.bottom, size.getHeight())), 1));

        Camera.Parameters parameters = camera.getParameters();
        if (parameters == null) return;

        parameters.setFocusAreas(focusArea);
        camera.setParameters(parameters);
    }

    public void setZoomRatio(int ratio) {
        requestedZoomRatio = ratio;
    }

    Size getSize() {
        return size;
    }

    private byte[] createPreviewBuffer(Size previewSize) {

        int bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
        long sizeInBits = (long) previewSize.getHeight() * previewSize.getWidth() * bitsPerPixel;
        int bufferSize = (int) Math.ceil(sizeInBits / 8.0d) + 1;

        byte[] byteArray = new byte[bufferSize];
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        if (!buffer.hasArray() || (buffer.array() != byteArray))
            throw new IllegalStateException("Failed to create buffer.");

        byteArrayToByteBuffer.put(byteArray, buffer);
        return byteArray;
    }

    private class CameraCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            processRunnable.setNextFrame(data, camera);
        }
    }

    private class ProcessRunnable implements Runnable {
        private final Object lock = new Object();
        private boolean active = true;
        private ByteBuffer processingData;

        ProcessRunnable() {}

        @Override
        public void run() {
            ByteBuffer data;
            while (true) {
                synchronized (lock) {
                    while (active && (processingData == null)) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.toString());
                            return;
                        }
                    }
                    if (!active) return;

                    data = processingData;
                    processingData = null;
                }
                try {
                    synchronized (processLock) {
                        textRecognition.process(new ImageData(data, size.getWidth(), size.getHeight()));
                    }
                } catch (Throwable t) {
                    Log.e(TAG, t.toString());
                } finally {
                    camera.addCallbackBuffer(data.array());
                }
            }
        }

        void setNextFrame(byte[] data, Camera camera) {
            synchronized (lock) {
                if (processingData != null) {
                    camera.addCallbackBuffer(processingData.array());
                    processingData = null;
                }
                if (!byteArrayToByteBuffer.containsKey(data)) {
                    Log.w(TAG, "Could not find ByteBuffer.");
                    return;
                }
                processingData = byteArrayToByteBuffer.get(data);
                lock.notifyAll();
            }
        }

        void setActive(boolean active) {
            synchronized (lock) {
                this.active = active;
                lock.notifyAll();
            }
        }
    }

    private Integer selectZoomStep(Camera.Parameters parameters) {
        if (requestedZoomRatio == null) return null;
        List<Integer> zoomRatios = parameters.getZoomRatios();
        Integer selectedZoomStep = null;
        int minDiff = Integer.MAX_VALUE;

        int step = 0;
        for (int ratio : zoomRatios) {
            int diff = Math.abs(requestedZoomRatio - ratio);
            if (diff < minDiff) {
                selectedZoomStep = step;
                minDiff = diff;
            }
            step++;
        }
        return selectedZoomStep;
    }

    private static int calculateFocusPoint(int point, int cameraSize) {
        return point * 2000 / cameraSize - 1000;
    }

    private static int getCameraId() {
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CAMERA_FACING_BACK)
                return i;
        }
        return -1;
    }

    private static Size selectPreviewSize(Camera.Parameters parameters) {
        List<Camera.Size> cameraSizeList = parameters.getSupportedPreviewSizes();
        List<Size> sizeList = new ArrayList<>();
        Size selectedPreviewSize = null;
        int minDiff = Integer.MAX_VALUE;

        for (android.hardware.Camera.Size previewSize : cameraSizeList)
            sizeList.add(new Size(previewSize.width, previewSize.height));

        for (Size previewSize : sizeList) {
            int diff = Math.abs(previewSize.getWidth() - REQUESTED_PREVIEW_WIDTH)
                    + Math.abs(previewSize.getHeight() - REQUESTED_PREVIEW_HEIGHT);
            if (diff < minDiff) {
                selectedPreviewSize = previewSize;
                minDiff = diff;
            }
        }
        return selectedPreviewSize;
    }

    private static int[] selectFpsRange(Camera.Parameters parameters) {
        int fpsScale = (int) (REQUESTED_FPS * 1000.0f);
        List<int[]> fpsRangeList = parameters.getSupportedPreviewFpsRange();
        int[] selectedFpsRange = null;
        int minDiff = Integer.MAX_VALUE;

        for (int[] range : fpsRangeList) {
            int diff = Math.abs(fpsScale - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX])
                    + Math.abs(fpsScale - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
            if (diff < minDiff) {
                selectedFpsRange = range;
                minDiff = diff;
            }
        }
        return selectedFpsRange;
    }

}
