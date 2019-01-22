package io.github.bjxytw.wordlens.graphic;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

class CursorGraphic{
    private static final int CURSOR_COLOR = Color.CYAN;
    private static final float STROKE_WIDTH = 4.0f;

    private final Paint paint;

    private GraphicOverlay overlay;

    CursorGraphic(GraphicOverlay overlay) {
        this.overlay = overlay;
        paint = new Paint();

        paint.setColor(CURSOR_COLOR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(STROKE_WIDTH);
    }

    void draw(Canvas canvas) {
        RectF rect = new RectF(overlay.getCursorRect());

        canvas.drawLine(rect.centerX(), rect.top, rect.centerX(), rect.bottom, paint);
        canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY() , paint);
    }
}
