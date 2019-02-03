package io.github.bjxytw.wordlens.camera;

import java.nio.ByteBuffer;

public class ImageData {
    private final ByteBuffer data;
    private final int width, height;
    ImageData(ByteBuffer data, int width, int height) {
        this.data = data;
        this.width = width;
        this.height = height;
    }

    public ByteBuffer getData() {
        return data;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
