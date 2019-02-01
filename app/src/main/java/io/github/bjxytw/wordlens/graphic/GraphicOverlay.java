package io.github.bjxytw.wordlens.graphic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.List;


public class GraphicOverlay extends View {
    private static final int CURSOR_COLOR = Color.CYAN;
    private static final int CURSOR_FOCUSING_COLOR = Color.YELLOW;
    private static final int CURSOR_AREA_SIZE = 50;
    private static final float CURSOR_STROKE_WIDTH = 4.0f;

    private final Object lock = new Object();
    private final Paint cursorPaint;
    private final List<BoundingBoxGraphic> boundingBoxList = new ArrayList<>();

    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;

    private Rect cameraCursorRect;
    private RectF cursorRect;
    private CameraImageGraphic imageGraphic;

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
        setFocusing(false);
    }

    public void clearBox() {
        synchronized (lock) {
            boundingBoxList.clear();
        }
        postInvalidate();
    }

    public void addBox(BoundingBoxGraphic boundingBox) {
        synchronized (lock) {
            boundingBoxList.add(boundingBox);
        }
    }

    public void setFocusing(boolean focusing) {
        if (focusing) cursorPaint.setColor(CURSOR_FOCUSING_COLOR);
        else cursorPaint.setColor(CURSOR_COLOR);
    }

    public void setScale (int cameraWidth, int cameraHeight,
                             int previewWidth, int previewHeight) {
        synchronized (lock) {
            widthScaleFactor = (float) previewWidth / (float) cameraWidth;
            heightScaleFactor = (float) previewHeight / (float) cameraHeight;

            if (cursorRect != null) {
                new RectF(cursorRect.left / widthScaleFactor,
                        cursorRect.top / heightScaleFactor,
                        cursorRect.right / widthScaleFactor,
                        cursorRect.bottom / heightScaleFactor)
                        .round(cameraCursorRect = new Rect());
            }
        }
    }

    public Rect getCameraCursorRect() {
        return cameraCursorRect;
    }

    public float translateX(float x) { return x * widthScaleFactor; }

    public float translateY(float y) { return y * heightScaleFactor; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        synchronized (lock) {
            for (BoundingBoxGraphic boundingBox : boundingBoxList)
                boundingBox.draw(canvas);
            if (imageGraphic != null)
                imageGraphic.draw(canvas);
        }
        if (cursorRect != null) {
            canvas.drawLine(cursorRect.centerX(), cursorRect.top,
                    cursorRect.centerX(), cursorRect.bottom, cursorPaint);
            canvas.drawLine(cursorRect.left, cursorRect.centerY(),
                    cursorRect.right, cursorRect.centerY(), cursorPaint);
        }
    }

    public void setImageGraphic(CameraImageGraphic imageGraphic) {
        this.imageGraphic = imageGraphic;
    }



}
