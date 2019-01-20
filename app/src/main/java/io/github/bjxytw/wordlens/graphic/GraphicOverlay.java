package io.github.bjxytw.wordlens.graphic;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.gms.common.images.Size;

import java.util.ArrayList;
import java.util.List;

public class GraphicOverlay extends View {
    private final Object lock = new Object();
    private int previewWidth;
    private float widthScaleFactor = 1.0f;
    private int previewHeight;
    private float heightScaleFactor = 1.0f;
    private final List<Graphic> graphics = new ArrayList<>();
    private int viewWidth;
    private int viewHeight;

    public abstract static class Graphic {
        private GraphicOverlay overlay;

        Graphic(GraphicOverlay overlay) {
            this.overlay = overlay;
        }

        public abstract void draw(Canvas canvas);

        float scaleX(float horizontal) {
            return horizontal * overlay.widthScaleFactor;
        }

        float scaleY(float vertical) {
            return vertical * overlay.heightScaleFactor;
        }

        float translateX(float x) {
            return scaleX(x);
        }

        float translateY(float y) {
            return scaleY(y);
        }

    }

    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void clear() {
        synchronized (lock) {
            graphics.clear();
        }
        postInvalidate();
    }

    public void add(Graphic graphic) {
        synchronized (lock) {
            graphics.add(graphic);
        }
    }

    public void setSizeInfo(Size previewSize, Size viewSize) {
        synchronized (lock) {
            this.previewWidth = previewSize.getWidth();
            this.previewHeight = previewSize.getHeight();
            this.viewWidth = viewSize.getWidth();
            this.viewHeight = viewSize.getHeight();
            requestLayout();
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        synchronized (lock) {
            if ((previewWidth != 0) && (previewHeight != 0)) {
                widthScaleFactor = (float) getWidth() / (float) previewWidth;
                heightScaleFactor = (float) getHeight() / (float) previewHeight;
            }

            for (Graphic graphic : graphics) {
                graphic.draw(canvas);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(viewWidth, viewHeight);
    }
}
