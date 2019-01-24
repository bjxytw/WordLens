package io.github.bjxytw.wordlens.graphic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;


public class GraphicOverlay extends View {
    private static final int CURSOR_AREA_SIZE = 50;

    private final Object lock = new Object();
    private final CursorGraphic cursorGraphic;

    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;

    private BoundingBoxGraphic boundingBoxGraphic;
    private Rect cursorRect;

    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int areaRadius = CURSOR_AREA_SIZE / 2;
                int cursorX = getWidth() / 2;
                int cursorY = getHeight() / 2;
                cursorRect = new Rect(
                        cursorX - areaRadius, cursorY - areaRadius,
                        cursorX + areaRadius, cursorY + areaRadius);
            }
        });
        cursorGraphic = new CursorGraphic(this);
    }

    public void clearBox() {
        boundingBoxGraphic = null;
        postInvalidate();
    }

    public void changeBox(BoundingBoxGraphic graphic) {
        synchronized (lock) {
            boundingBoxGraphic = graphic;
        }
    }

    public void setScale (int cameraWidth, int cameraHeight,
                             int previewWidth, int previewHeight) {
        synchronized (lock) {
            widthScaleFactor = (float) previewWidth / (float) cameraWidth;
            heightScaleFactor = (float) previewHeight / (float) cameraHeight;
        }
    }

    public Rect getCursorRect() {
        return cursorRect;
    }

    public float translateX(float x) {
        return x * widthScaleFactor;
    }

    public float translateY(float y) {
        return y * heightScaleFactor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        synchronized (lock) {
            if (boundingBoxGraphic != null)
                boundingBoxGraphic.draw(canvas);
            cursorGraphic.draw(canvas);
        }
    }
}
