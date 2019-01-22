package io.github.bjxytw.wordlens.graphic;


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.google.firebase.ml.vision.text.FirebaseVisionText;

public class TextGraphic {
    private static final int TEXT_COLOR = Color.WHITE;
    private static final float STROKE_WIDTH = 4.0f;

    private final Paint rectPaint;
    private final FirebaseVisionText.Element text;
    private final GraphicOverlay overlay;

    public TextGraphic(GraphicOverlay overlay, FirebaseVisionText.Element text) {
        this.overlay = overlay;
        this.text = text;

        rectPaint = new Paint();
        rectPaint.setColor(TEXT_COLOR);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(STROKE_WIDTH);
    }

    void draw(Canvas canvas) {
        if (text == null)
            throw new IllegalStateException("Attempting to draw a null text.");

        RectF rect = new RectF(text.getBoundingBox());
        rect.left = overlay.translateX(rect.left);
        rect.top = overlay.translateY(rect.top);
        rect.right = overlay.translateX(rect.right);
        rect.bottom = overlay.translateY(rect.bottom);
        canvas.drawRect(rect, rectPaint);

    }
}
