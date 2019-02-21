package io.github.bjxytw.wordlens;

import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.data.ImageData;
import io.github.bjxytw.wordlens.camera.CameraCursorGraphic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class TextRecognition {
    private static final String TAG = "TextRecognition";
    private final FirebaseVisionTextRecognizer detector;
    private final CameraCursorGraphic cursor;
    private TextRecognitionListener listener;
    private ImageData processingData;

    public interface TextRecognitionListener {
        void onRecognitionResult(String result);
    }

    TextRecognition(CameraCursorGraphic cursor) {
        detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        this.cursor = cursor;
    }

    void setListener(TextRecognitionListener listener) {
        this.listener = listener;
    }

    public void process(ImageData data) {
        if (data != null && processingData == null) {
            processingData = data;
            detectImage(processingData);
        }
    }

    public void stop() {
        try {
            detector.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void detectImage(final ImageData imageData) {
        FirebaseVisionImageMetadata metadata =
                new FirebaseVisionImageMetadata.Builder()
                        .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                        .setWidth(imageData.getWidth())
                        .setHeight(imageData.getHeight())
                        .setRotation(CameraSource.ROTATION)
                        .build();

        final ByteBuffer data = fillImageMargin(imageData);
        detector.processImage(FirebaseVisionImage.fromByteBuffer(data, metadata))
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText results) {
                        processResult(results);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Text detection failed.", e);
                    }
                });
    }

    private void processResult(FirebaseVisionText results) {
        FirebaseVisionText.Element detectedElement = null;
        List<FirebaseVisionText.TextBlock> blocks = results.getTextBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                for (int k = 0; k < elements.size(); k++) {
                    FirebaseVisionText.Element element = elements.get(k);
                    if (isCursorOnBox(cursor.getCameraCursorRect(), element.getBoundingBox()))
                        detectedElement = element;
                }
            }
        }

        if (detectedElement != null && listener != null) {
            cursor.setCursorRecognizing(true);
            listener.onRecognitionResult(detectedElement.getText());
        } else cursor.setCursorRecognizing(false);
        cursor.postInvalidate();

        processingData = null;
    }

    private ByteBuffer fillImageMargin(ImageData data) {
        ByteBuffer buffer = data.getData();
        byte[] array = buffer.array();

        int width = data.getWidth();
        int height = data.getHeight();
        int size = width * height;
        byte initValue = (byte) 0;

        Rect area = cursor.getCameraRecognitionRect();
        if (area != null) {
            Arrays.fill(array, 0, area.top * width, initValue);
            Arrays.fill(array, width * area.bottom,
                    size + half(area.top * width), initValue);
            Arrays.fill(array, size + half(width * area.bottom),
                    array.length, initValue);

            for (int i = area.top; i < area.bottom; i++) {
                int offset = i * width;
                Arrays.fill(array, offset, offset + area.left, initValue);
                Arrays.fill(array, offset + area.right,
                        offset + width, initValue);
            }

            for (int i = half(area.top); i < half(area.bottom); i++) {
                int offset = i * width + size;
                Arrays.fill(array, offset, offset + area.left - 1, initValue);
                Arrays.fill(array, offset + area.right,
                        offset + width, initValue);
            }
        }
        return buffer;
    }

    private static int half(int size) {
        return Math.round(size * 0.5f);
    }

    private static boolean isCursorOnBox(Rect cursor, Rect box) {
        if (cursor != null && box != null) {
            float x = cursor.centerY();
            float y = cursor.centerX();
            return x > box.left && y > box.top && x < box.right && y < box.bottom;
        }
        return false;
    }

}