package io.github.bjxytw.wordlens.processor;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.graphic.BoundingBoxGraphic;
import io.github.bjxytw.wordlens.graphic.GraphicOverlay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class TextRecognition {

    private static final String TAG = "TextRec";
    private static final int RECOGNITION_AREA_WIDTH = 200;
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

    private ImageData cropImage(ImageData data) {
        int width = data.getWidth();
        int height = data.getHeight();
        int marginVertical = (height - RECOGNITION_AREA_WIDTH) / 2;
        int cursorY = graphicOverlay.getCameraCursorRect().centerY();
        int marginHorizontalTop = cursorY - RECOGNITION_AREA_HEIGHT;
        int marginHorizontalBottom = cursorY + RECOGNITION_AREA_HEIGHT;

        byte[] yuvData = data.getData().array();
        Arrays.fill(yuvData, 0, marginVertical * width, (byte) 0);
        Arrays.fill(yuvData, (width * height - marginVertical * width),
                width * height, (byte) 0);
        for(int i = marginVertical; i < height - marginVertical; i++){
            int offset = i * width;
            Arrays.fill(yuvData, offset, offset + marginHorizontalTop, (byte) 0);
            Arrays.fill(yuvData, offset + marginHorizontalBottom,
                    offset + width, (byte) 0);
        }
        return data;
    }

    private void detectImage(ImageData data) {
        FirebaseVisionImageMetadata metadata =
                new FirebaseVisionImageMetadata.Builder()
                        .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                        .setWidth(data.getWidth())
                        .setHeight(data.getHeight())
                        .setRotation(CameraSource.ROTATION)
                        .build();

        detector.processImage(FirebaseVisionImage.fromByteBuffer(data.getData(), metadata))
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
        graphicOverlay.clearBox();
        List<FirebaseVisionText.TextBlock> blocks = results.getTextBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                for (int k = 0; k < elements.size(); k++) {
                    FirebaseVisionText.Element element = elements.get(k);
                    graphicOverlay.addBox(new BoundingBoxGraphic(graphicOverlay,  element.getBoundingBox()));

                    if (isCursorOnBox(graphicOverlay.getCameraCursorRect(),
                            element.getBoundingBox()))
                        detectedElement = element;
                }
            }
        }
        graphicOverlay.postInvalidate();
        if (detectedElement != null) {
            String text = detectedElement.getText();
            Log.i(TAG, "Text Detected: " + text);
            listener.onRecognitionResult(text, detectedElement.getBoundingBox());
        }

        processingImageData = null;
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