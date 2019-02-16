package io.github.bjxytw.wordlens.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.FloatRange;


public class CameraCursorGraphic extends View {
    private static final int CURSOR_COLOR = Color.CYAN;
    private static final int CURSOR_RECOGNISING_COLOR = Color.YELLOW;
    private static final int CURSOR_AREA_SIZE = 90;
    private static final float CURSOR_STROKE_WIDTH = 4.0f;

    private static final int RECOGNITION_AREA_COLOR = Color.LTGRAY;
    @FloatRange(from = 0.0, to = 1.0)
    private static final float RECOGNITION_AREA_WIDTH_RATIO = 0.6f;
    @FloatRange(from = 0.0, to = 1.0)
    private static final float RECOGNITION_AREA_HEIGHT_RATIO = 0.25f;

    private final Object lock = new Object();
    private final Paint cursorPaint;
    private final Paint areaPaint;

    private Rect cameraCursorRect;
    private Rect cameraRecognitionRect;
    private RectF cursorRect;
    private RectF recognitionRect;

    private boolean areaVisible;


    public CameraCursorGraphic(Context context, AttributeSet attrs) {
        super(context, attrs);
        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        float cursorX = getWidth() * 0.5f;
                        float cursorY = getHeight() * 0.5f;
                        float cursorAreaRadius = CURSOR_AREA_SIZE * 0.5f;
                        float recognitionAreaRadiusX = getWidth() * RECOGNITION_AREA_WIDTH_RATIO * 0.5f;
                        float recognitionAreaRadiusY = getHeight() * RECOGNITION_AREA_HEIGHT_RATIO * 0.5f;

                        cursorRect = new RectF(
                                cursorX - cursorAreaRadius, cursorY - cursorAreaRadius,
                                cursorX + cursorAreaRadius, cursorY + cursorAreaRadius);

                        recognitionRect = new RectF(
                                cursorX - recognitionAreaRadiusX, cursorY - recognitionAreaRadiusY,
                                cursorX + recognitionAreaRadiusX, cursorY + recognitionAreaRadiusY);
                    }
                });
        cursorPaint = new Paint();
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(CURSOR_STROKE_WIDTH);
        cursorPaint.setAntiAlias(true);

        areaPaint = new Paint();
        areaPaint.setStyle(Paint.Style.STROKE);
        areaPaint.setStrokeWidth(CURSOR_STROKE_WIDTH);
        areaPaint.setColor(RECOGNITION_AREA_COLOR);
        areaPaint.setAntiAlias(true);

        setCursorRecognizing(false);
        areaVisible = false;
    }

    public void setCursorRecognizing(boolean recognising) {
        synchronized (lock) {
            if (recognising) cursorPaint.setColor(CURSOR_RECOGNISING_COLOR);
            else cursorPaint.setColor(CURSOR_COLOR);
        }
    }

    public void setScale(int cameraWidth, int cameraHeight,
                         int previewWidth, int previewHeight) {
        synchronized (lock) {
            float widthScaleFactor = (float) cameraWidth / (float) previewWidth;
            float heightScaleFactor = (float) cameraHeight / (float) previewHeight;

            if (cursorRect != null) {
                cameraCursorRect = new Rect();
                translateRect(cursorRect, cameraCursorRect, widthScaleFactor, heightScaleFactor, previewWidth);
            }
            if (recognitionRect != null) {
                cameraRecognitionRect = new Rect();
                translateRect(recognitionRect, cameraRecognitionRect, widthScaleFactor, heightScaleFactor, previewWidth);
            }
        }
    }

    private static void translateRect(RectF viewRect, Rect cameraRect,
                               float widthScaleFactor, float heightScaleFactor, int previewWidth) {
        new RectF(viewRect.top * heightScaleFactor,
                (previewWidth - viewRect.right) * widthScaleFactor,
                viewRect.bottom * heightScaleFactor,
                (previewWidth - viewRect.left) * widthScaleFactor)
                .round(cameraRect);
    }

    public Rect getCameraCursorRect() {
        return cameraCursorRect;
    }

    public Rect getCameraRecognitionRect() {
        return cameraRecognitionRect;
    }

    public void setAreaVisible(boolean visible) {
        areaVisible = visible;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        synchronized (lock) {
            drawCursor(canvas);
            if (areaVisible) drawArea(canvas);
        }
    }

    private void drawCursor(Canvas canvas) {
        if (cursorRect != null) {
            float[] pts = new float[]{
                    cursorRect.centerX(), cursorRect.top, cursorRect.centerX(),
                    cursorRect.top + CURSOR_AREA_SIZE * 0.3f,

                    cursorRect.centerX(), cursorRect.bottom - CURSOR_AREA_SIZE * 0.3f,
                    cursorRect.centerX(), cursorRect.bottom,

                    cursorRect.left, cursorRect.centerY(),
                    cursorRect.left + CURSOR_AREA_SIZE * 0.3f, cursorRect.centerY(),

                    cursorRect.right - CURSOR_AREA_SIZE * 0.3f, cursorRect.centerY(),
                    cursorRect.right, cursorRect.centerY()
            };
            canvas.drawLines(pts, cursorPaint);
            canvas.drawCircle(cursorRect.centerX(), cursorRect.centerY(), CURSOR_AREA_SIZE * 0.3f, cursorPaint);
        }
    }

    private void drawArea(Canvas canvas) {
        if (recognitionRect != null) {
            float length = 40.0f;
            float left = recognitionRect.left;
            float top = recognitionRect.top;
            float right = recognitionRect.right;
            float bottom = recognitionRect.bottom;

            float[] pts = new float[]{
                    left - 1, top, left + length, top,
                    left - 1, bottom, left + length, bottom,
                    left, top, left, top + length,
                    right, top, right, top + length,
                    right - length, top, right + 1, top,
                    right - length, bottom, right + 1, bottom,
                    left, bottom - length, left, bottom,
                    right, bottom - length, right, bottom
            };
            canvas.drawLines(pts, areaPaint);
        }
    }
}
