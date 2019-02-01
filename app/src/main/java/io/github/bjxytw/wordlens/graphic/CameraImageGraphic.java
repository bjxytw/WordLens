package io.github.bjxytw.wordlens.graphic;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

public class CameraImageGraphic {

    private final Bitmap bitmap;

    public CameraImageGraphic(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    void draw(Canvas canvas) {
        canvas.drawBitmap(bitmap, null, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), null);
    }
}
