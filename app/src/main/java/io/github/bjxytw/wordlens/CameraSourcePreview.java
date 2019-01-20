package io.github.bjxytw.wordlens;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.android.gms.common.images.Size;

import java.io.IOException;

import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.graphic.GraphicOverlay;

public class CameraSourcePreview extends SurfaceView {
    private static final String TAG = "CameraPreview";

    private boolean startRequested;
    private boolean surfaceAvailable;
    private CameraSource cameraSource;
    private GraphicOverlay overlay;

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        startRequested = false;
        surfaceAvailable = false;
        getHolder().addCallback(new SurfaceCallback());
    }

    public void start(CameraSource cameraSource, GraphicOverlay overlay) throws IOException {
        if (cameraSource == null)
            stop();

        this.cameraSource = cameraSource;
        this.overlay = overlay;

        if (this.cameraSource != null) {
            startRequested = true;
            startIfReady();
        }
    }

    @SuppressLint("MissingPermission")
    private void startIfReady() throws IOException {
        if (startRequested && surfaceAvailable) {
            cameraSource.start(getHolder());
            requestLayout();
            if (overlay != null) {
                Size size = cameraSource.getPreviewSize();
                Size swappedSize = new Size(size.getHeight(), size.getWidth());
                overlay.setSizeInfo(swappedSize, new Size(getWidth(), getHeight()));
                overlay.clear();
            }
            startRequested = false;
        }
    }

    public void stop() {
        if (cameraSource != null)
            cameraSource.stop();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (cameraSource != null) {
            Size size = cameraSource.getPreviewSize();
            if (size != null) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                int previewWidth = size.getHeight();
                int previewHeight = size.getWidth();

                if (width < height * previewWidth / previewHeight)
                    height = width * previewHeight / previewWidth;

                setMeasuredDimension(width, height);
            }
        }
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surface) {
            surfaceAvailable = true;
            try {
                startIfReady();
            } catch (IOException e) {
                Log.e(TAG, "Could not start camera source.", e);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surface) {
            surfaceAvailable = false;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    }

}
