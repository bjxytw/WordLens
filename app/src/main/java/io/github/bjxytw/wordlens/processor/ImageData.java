package io.github.bjxytw.wordlens.processor;

import java.nio.ByteBuffer;

public class ImageData {
    private final ByteBuffer data;
    private final int width, height;
    public ImageData(ByteBuffer data, int width, int height) {
        this.data = data;
        this.width = width;
        this.height = height;
    }

    ByteBuffer getData() {
        return data;
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }
}
