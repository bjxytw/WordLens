package io.github.bjxytw.wordlens;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.android.gms.common.images.Size;

import java.io.IOException;

import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.graphic.GraphicOverlay;

public class CameraPreview extends SurfaceView {
    private static final String TAG = "CameraPreview";

    private boolean startRequested;
    private boolean surfaceAvailable;
    private CameraSource camera;
    private GraphicOverlay overlay;

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        startRequested = false;
        surfaceAvailable = false;
        getHolder().addCallback(new SurfaceCallback());
    }

    public void start(CameraSource camera, GraphicOverlay overlay) throws IOException {
        if (camera == null) stop();

        this.camera = camera;
        this.overlay = overlay;

        if (this.camera != null) {
            startRequested = true;
            startIfReady();
        }
    }

    @SuppressLint("MissingPermission")
    private void startIfReady() throws IOException {
        if (startRequested && surfaceAvailable) {
            camera.start(getHolder());
            requestLayout();
            startRequested = false;
        }
    }

    public void stop() {
        if (overlay != null) overlay.clearBox();
        if (camera != null) camera.stop();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (camera != null &&
                event.getX() < overlay.getWidth() && event.getY() < overlay.getHeight())
            camera.cameraFocus();
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (camera != null) {
            Size size = camera.getSize();
            if (size != null) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                int cameraWidth = size.getHeight();
                int cameraHeight = size.getWidth();

                if (width < height * cameraWidth / cameraHeight) {
                    height = width * cameraHeight / cameraWidth;
                    setMeasuredDimension(width, height);
                }

                if (overlay != null) {
                    overlay.clearBox();
                    overlay.setScale(cameraWidth, cameraHeight, width, height);
                    camera.setCameraFocusArea();
                    camera.cameraFocus();
                }
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
