package io.github.bjxytw.wordlens;

import android.graphics.Rect;
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

    private ByteBuffer processingImage;
    private Size processingImageSize;

    TextRecognitionProcessor(GraphicOverlay overlay, TextView resultText) {
        detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        graphicOverlay = overlay;
        this.resultText = resultText;
    }

    public void process(ByteBuffer data, final Size size) {

        if (data != null && size != null &&
                processingImage == null && processingImageSize == null) {

                Log.i(TAG, "Process an image");
                processingImage = data;
                processingImageSize = size;
                detectImage(processingImage, processingImageSize);
        }
    }


    private void detectImage(ByteBuffer data, final Size frameSize) {
        FirebaseVisionImageMetadata metadata =
                new FirebaseVisionImageMetadata.Builder()
                        .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                        .setWidth(frameSize.getWidth())
                        .setHeight(frameSize.getHeight())
                        .setRotation(CameraSource.ROTATION)
                        .build();

        detector.processImage(FirebaseVisionImage.fromByteBuffer(data, metadata))
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
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
                                    if (isInCursor(graphicOverlay, element)) {
                                        graphicOverlay.changeText(new TextGraphic(graphicOverlay, element));
                                        resultText.setText(element.getText());
                                        Log.i(TAG, "Detected: " + element.getText());
                                    }
                                }
                            }
                        }
                        graphicOverlay.postInvalidate();
                        processingImage = null;
                        processingImageSize = null;
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Text detection failed." + e);
                    }
                });
    }

    private boolean isInCursor(GraphicOverlay overlay, FirebaseVisionText.Element element) {
        Rect cursor = overlay.getCursorRect();
        Rect text = element.getBoundingBox();

        if (cursor != null && text != null) {
            float x = cursor.centerX();
            float y = cursor.centerY();

            return x > overlay.translateX(text.left) && y > overlay.translateY(text.top)
                    && x < overlay.translateX(text.right) && y < overlay.translateY(text.bottom);
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