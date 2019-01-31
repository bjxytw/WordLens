package io.github.bjxytw.wordlens.graphic;


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public class BoundingBoxGraphic {
    private static final int BOX_COLOR = Color.WHITE;
    private static final float STROKE_WIDTH = 3.0f;

    private final Paint rectPaint;
    private final GraphicOverlay overlay;
    private final Rect boundingBox;

    public BoundingBoxGraphic(GraphicOverlay overlay, Rect boundingBox) {
        this.overlay = overlay;
        this.boundingBox = boundingBox;

        rectPaint = new Paint();
        rectPaint.setColor(BOX_COLOR);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(STROKE_WIDTH);
    }

    void draw(Canvas canvas) {
        RectF rect = new RectF(boundingBox);
        rect.left = overlay.translateX(rect.left);
        rect.top = overlay.translateY(rect.top);
        rect.right = overlay.translateX(rect.right);
        rect.bottom = overlay.translateY(rect.bottom);
        canvas.drawRect(rect, rectPaint);

    }
}
