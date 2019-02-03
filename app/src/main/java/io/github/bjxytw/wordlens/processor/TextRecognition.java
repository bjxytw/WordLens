package io.github.bjxytw.wordlens.processor;

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
import io.github.bjxytw.wordlens.camera.ImageData;
import io.github.bjxytw.wordlens.graphic.GraphicOverlay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class TextRecognition {

    private static final String TAG = "TextRec";
    private static final int RECOGNITION_AREA_WIDTH = 100;
    private static final int RECOGNITION_AREA_HEIGHT = 200;

    private final FirebaseVisionTextRecognizer detector;
    private final GraphicOverlay graphicOverlay;

    private TextRecognitionListener listener;
    private ImageData processingImageData;

    public interface TextRecognitionListener {
        void onRecognitionResult(String result, Rect boundingBox);
    }

    public TextRecognition(GraphicOverlay overlay) {
        detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        graphicOverlay = overlay;
    }

    public void setListener(TextRecognitionListener listener) {
        this.listener = listener;
    }

    public void process(ImageData data) {
        if (data != null && processingImageData == null) {
            Log.i(TAG, "Process an image");
            processingImageData = data;
            detectImage(processingImageData);
        }
    }

    public void stop() {
        try {
            detector.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception thrown while trying to close Text Detector: " + e);
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

        final ByteBuffer data = cropImage(imageData);
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
                    if (isCursorOnBox(graphicOverlay.getCameraCursorRect(),
                            element.getBoundingBox()))
                        detectedElement = element;
                }
            }
        }

        if (detectedElement != null) {
            graphicOverlay.setRecognising(true);
            String text = detectedElement.getText();
            Log.i(TAG, "Text Detected: " + text);
            listener.onRecognitionResult(text, detectedElement.getBoundingBox());
        } else graphicOverlay.setRecognising(false);
        graphicOverlay.postInvalidate();

        processingImageData = null;
    }

    private ByteBuffer cropImage(ImageData data) {
        ByteBuffer buffer = data.getData();
        byte[] array = buffer.array();

        int width = data.getWidth();
        int height = data.getHeight();

        int cursorY = graphicOverlay.getCameraCursorRect().centerX();
        int cursorX = graphicOverlay.getCameraCursorRect().centerY();

        int marginLeftX = cursorX - (RECOGNITION_AREA_WIDTH / 2);
        int marginTopY = cursorY - (RECOGNITION_AREA_HEIGHT / 2);
        int marginRightX = cursorX + (RECOGNITION_AREA_WIDTH / 2);
        int marginBottomY = cursorY + (RECOGNITION_AREA_HEIGHT / 2);

        int size = width * height;

        Arrays.fill(array, 0, marginTopY * width, (byte) 0);
        Arrays.fill(array, width * marginBottomY,
                size + half(marginTopY * width), (byte) 0);
        Arrays.fill(array, size + half(width * marginBottomY),
                array.length, (byte) 0);

        for (int i = marginTopY; i < marginBottomY; i++) {
            int offset = i * width;
            Arrays.fill(array, offset, offset + marginLeftX, (byte) 0);
            Arrays.fill(array, offset + marginRightX,
                    offset + width, (byte) 0);
        }

        for (int i = half(marginTopY); i < half(marginBottomY); i++) {
            int offset = i * width + size;
            Arrays.fill(array, offset, offset + marginLeftX - 1, (byte) 0);
            Arrays.fill(array, offset + marginRightX,
                    offset + width, (byte) 0);
        }
        return buffer;
    }

    private static int half(int size) {
        return Math.round(size * 0.5f);
    }

    private static boolean isCursorOnBox(Rect cursor, Rect box) {
        if (cursor != null && box != null) {
            float x = cursor.centerX();
            float y = cursor.centerY();
            return x > box.left && y > box.top && x < box.right && y < box.bottom;
        }
        return false;
    }

}