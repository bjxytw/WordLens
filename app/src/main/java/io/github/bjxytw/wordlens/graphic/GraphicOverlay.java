package io.github.bjxytw.wordlens.graphic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;


public class GraphicOverlay extends View {
    private static final int CURSOR_COLOR = Color.CYAN;
    private static final int CURSOR_RECOGNISING_COLOR = Color.YELLOW;
    private static final int CURSOR_AREA_SIZE = 100;
    private static final float CURSOR_STROKE_WIDTH = 4.0f;

    private final Object lock = new Object();
    private final Paint cursorPaint;

    private Rect cameraCursorRect;
    private RectF cursorRect;

    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        float cursorX = getWidth() * 0.5f;
                        float cursorY = getHeight() * 0.5f;
                        float areaRadius = CURSOR_AREA_SIZE * 0.5f;
                        cursorRect = new RectF(
                                cursorX - areaRadius, cursorY - areaRadius,
                                cursorX + areaRadius, cursorY + areaRadius);
                    }
                });

        cursorPaint = new Paint();
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(CURSOR_STROKE_WIDTH);
        cursorPaint.setAntiAlias(true);
        setRecognising(false);
    }

    public void setRecognising(boolean recognising) {
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
                new RectF(cursorRect.left * widthScaleFactor,
                        cursorRect.top * heightScaleFactor,
                        cursorRect.right * widthScaleFactor,
                        cursorRect.bottom * heightScaleFactor)
                        .round(cameraCursorRect = new Rect());
            }
        }
    }

    public Rect getCameraCursorRect() {
        return cameraCursorRect;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        synchronized (lock) {
            if (cursorRect != null) {
                canvas.drawLine(cursorRect.centerX(), cursorRect.top, cursorRect.centerX(),
                        cursorRect.top + CURSOR_AREA_SIZE * 0.3f, cursorPaint);

                canvas.drawLine(cursorRect.centerX(), cursorRect.bottom - CURSOR_AREA_SIZE * 0.3f,
                        cursorRect.centerX(), cursorRect.bottom, cursorPaint);

                canvas.drawLine(cursorRect.left, cursorRect.centerY(),
                        cursorRect.left + CURSOR_AREA_SIZE * 0.3f, cursorRect.centerY(), cursorPaint);

                canvas.drawLine(cursorRect.right - CURSOR_AREA_SIZE * 0.3f, cursorRect.centerY(),
                        cursorRect.right, cursorRect.centerY(), cursorPaint);

                canvas.drawCircle(cursorRect.centerX(), cursorRect.centerY(), CURSOR_AREA_SIZE * 0.3f, cursorPaint);
            }
        }
    }
}
