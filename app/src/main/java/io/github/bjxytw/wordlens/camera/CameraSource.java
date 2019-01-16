package io.github.bjxytw.wordlens.camera;

        import android.Manifest;
        import android.annotation.SuppressLint;
        import android.graphics.ImageFormat;
        import android.hardware.Camera;
        import android.hardware.Camera.CameraInfo;
        import android.support.annotation.Nullable;
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
    public static final int CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK;

    private static final String TAG = "CameraSource";

    private static final float ASPECT_RATIO_TOLERANCE = 0.01f;

    private Camera camera;

    private Size previewSize;


    private static final float REQUESTED_FPS = 20.0f;
    private static final int REQUESTED_PREVIEW_WIDTH = 640;
    private static final int REQUESTED_PREVIEW_HEIGHT = 480;
    private static final boolean REQUESTED_AUTO_FOCUS = true;

    private final GraphicOverlay graphicOverlay;

    private Thread processingThread;

    private final FrameProcessingRunnable processingRunnable;

    private final Object processorLock = new Object();
    private TextRecognitionProcessor frameProcessor;

    private final Map<byte[], ByteBuffer> bytesToByteBuffer = new IdentityHashMap<>();

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

            if (frameProcessor != null) {
                frameProcessor.stop();
            }
        }
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

    public Size getPreviewSize() {
        return previewSize;
    }

    @SuppressLint("InlinedApi")
    private Camera createCamera() throws IOException {
        int requestedCameraId = getIdForRequestedCamera();
        if (requestedCameraId == -1)
            throw new IOException("Could not find requested camera.");
        Camera camera = Camera.open(requestedCameraId);

        SizePair sizePair = selectSizePair(camera);
        if (sizePair == null)
            throw new IOException("Could not find suitable preview size.");
        Size pictureSize = sizePair.pictureSize();
        previewSize = sizePair.previewSize();

        Log.i(TAG, "Selected preview size: " + previewSize.toString());

        int[] previewFpsRange = selectPreviewFpsRange(camera);
        if (previewFpsRange == null)
            throw new IOException("Could not find suitable preview frames per second range.");

        int minFps = previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
        int maxFps = previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
        Log.i(TAG, "FPS Range: " + (float) minFps / 1000.0f
                + " ~ " + (float) maxFps / 1000.0f);

        Camera.Parameters parameters = camera.getParameters();

        if (pictureSize != null)
            parameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());

        parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
        parameters.setPreviewFpsRange(minFps, maxFps);
        parameters.setPreviewFormat(ImageFormat.NV21);

        camera.setDisplayOrientation(ROTATION_DEGREE);
        parameters.setRotation(ROTATION_DEGREE);


        if (REQUESTED_AUTO_FOCUS) {
            if (parameters.getSupportedFocusModes()
                    .contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            else Log.i(TAG, "Camera auto focus is not supported on this device.");
        }

        camera.setParameters(parameters);

        // Four frame buffers are needed for working with the camera:
        //
        //   one for the frame that is currently being executed upon in doing detection
        //   one for the next pending frame to process immediately upon completing detection
        //   two for the frames that the camera uses to populate future preview images
        //
        // Through trial and error it appears that two free buffers, in addition to the two buffers
        // used in this code, are needed for the camera to work properly.  Perhaps the camera has
        // one thread for acquiring images, and another thread for calling into user code.  If only
        // three buffers are used, then the camera will spew thousands of warning messages when
        // detection takes a non-trivial amount of time.
        camera.setPreviewCallbackWithBuffer(new CameraPreviewCallback());
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        return camera;
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

    private static class SizePair {
        private final Size preview;
        private Size picture;

        SizePair(
                android.hardware.Camera.Size previewSize,
                @Nullable android.hardware.Camera.Size pictureSize) {
            preview = new Size(previewSize.width, previewSize.height);
            if (pictureSize != null)
                picture = new Size(pictureSize.width, pictureSize.height);
        }

        Size previewSize() {
            return preview;
        }

        @Nullable
        Size pictureSize() {
            return picture;
        }
    }

    private static SizePair selectSizePair(Camera camera) {
        List<SizePair> validPreviewSizes = generateValidPreviewSizeList(camera);

        SizePair selectedPair = null;
        int minDiff = Integer.MAX_VALUE;
        for (SizePair sizePair : validPreviewSizes) {
            Size size = sizePair.previewSize();
            int diff =
                    Math.abs(size.getWidth() - REQUESTED_PREVIEW_WIDTH)
                            + Math.abs(size.getHeight() - REQUESTED_PREVIEW_HEIGHT);
            if (diff < minDiff) {
                selectedPair = sizePair;
                minDiff = diff;
            }
        }

        return selectedPair;
    }

    private static List<SizePair> generateValidPreviewSizeList(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> supportedPreviewSizes =
                parameters.getSupportedPreviewSizes();
        List<Camera.Size> supportedPictureSizes =
                parameters.getSupportedPictureSizes();
        List<SizePair> validPreviewSizes = new ArrayList<>();
        for (android.hardware.Camera.Size previewSize : supportedPreviewSizes) {
            float previewAspectRatio = (float) previewSize.width / (float) previewSize.height;

            for (android.hardware.Camera.Size pictureSize : supportedPictureSizes) {
                float pictureAspectRatio = (float) pictureSize.width / (float) pictureSize.height;
                if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                    validPreviewSizes.add(new SizePair(previewSize, pictureSize));
                    break;
                }
            }
        }

        if (validPreviewSizes.size() == 0) {
            Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size");
            for (android.hardware.Camera.Size previewSize : supportedPreviewSizes) {
                validPreviewSizes.add(new SizePair(previewSize, null));
            }
        }

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
                    Log.d(
                            TAG,
                            "Skipping frame. Could not find ByteBuffer associated with the image "
                                    + "data from the camera.");
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

                    if (!active) {
                        return;
                    }

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

    private void cleanScreen() {
        graphicOverlay.clear();
    }
}
