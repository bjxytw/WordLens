package io.github.bjxytw.wordlens;

import android.graphics.Rect;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.graphic.GraphicOverlay;
import io.github.bjxytw.wordlens.graphic.TextGraphic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class TextRecognitionProcessor {

    private static final String TAG = "TextRec";

    private final FirebaseVisionTextRecognizer detector;
    private GraphicOverlay graphicOverlay;
    private TextView resultText;

    @GuardedBy("this")
    private ByteBuffer latestImage;

    @GuardedBy("this")
    private Size latestImageSize;

    @GuardedBy("this")
    private ByteBuffer processingImage;

    @GuardedBy("this")
    private Size processingImageSize;

    TextRecognitionProcessor(GraphicOverlay overlay, TextView resultText) {
        detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        graphicOverlay = overlay;
        this.resultText = resultText;
    }

    public synchronized void process(
            ByteBuffer data, final Size frameSize) {
        latestImage = data;
        latestImageSize = frameSize;
        if (processingImage == null && processingImageSize == null) {
            processLatestImage();
        }
    }

    private synchronized void processLatestImage() {
        processingImage = latestImage;
        processingImageSize = latestImageSize;
        latestImage = null;
        latestImageSize = null;
        if (processingImage != null && processingImageSize != null) {
            processImage(processingImage, processingImageSize);
        }
    }

    private void processImage(ByteBuffer data, final Size frameSize) {

        FirebaseVisionImageMetadata metadata =
                new FirebaseVisionImageMetadata.Builder()
                        .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                        .setWidth(frameSize.getWidth())
                        .setHeight(frameSize.getHeight())
                        .setRotation(CameraSource.ROTATION)
                        .build();
        detectInVisionImage(FirebaseVisionImage.fromByteBuffer(data, metadata));
    }

    private void detectInVisionImage(FirebaseVisionImage image) {

        detector.processImage(image).addOnSuccessListener(
                new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText results) {
                        graphicOverlay.clearText();
                        List<FirebaseVisionText.TextBlock> blocks = results.getTextBlocks();
                        for (int i = 0; i < blocks.size(); i++) {
                            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
                            for (int j = 0; j < lines.size(); j++) {
                                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                                for (int k = 0; k < elements.size(); k++) {
                                    FirebaseVisionText.Element element = elements.get(k);
                                    if (detectInCursor(graphicOverlay, element)) {
                                        graphicOverlay.changeText(new TextGraphic(graphicOverlay, element));
                                        resultText.setText(element.getText());
                                    }
                                }
                            }
                        }
                        graphicOverlay.postInvalidate();
                        processLatestImage();
                    }
                })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "Text detection failed." + e);
                            }
                        });
    }

    private boolean detectInCursor (GraphicOverlay overlay, FirebaseVisionText.Element element) {
        Rect cursor = overlay.getCursorRect();
        Rect boundingBox = element.getBoundingBox();

        if (cursor != null && boundingBox != null) {
            Rect text = new Rect(
                    (int) overlay.translateX(boundingBox.left),
                    (int) overlay.translateY(boundingBox.top),
                    (int) overlay.translateX(boundingBox.right),
                    (int) overlay.translateY(boundingBox.bottom));

            int x = cursor.centerX();
            int y = cursor.centerY();

            return x > text.left && y > text.top && x < text.right && y < text.bottom;
        }
        return false;
    }

    public void stop() {
        try {
            detector.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception thrown while trying to close Text Detector: " + e);
        }
    }
}