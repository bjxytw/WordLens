package io.github.bjxytw.wordlens;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import io.github.bjxytw.wordlens.graphic.CameraImageGraphic;
import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.graphic.GraphicOverlay;
import io.github.bjxytw.wordlens.graphic.TextGraphic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class TextRecognitionProcessor {

    private static final String TAG = "TextRec";

    private final FirebaseVisionTextRecognizer detector;

    @GuardedBy("this")
    private Bitmap latestImage;


    @GuardedBy("this")
    private Bitmap processingImage;


    public TextRecognitionProcessor() {
        detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();

    }

    public synchronized void process(ByteBuffer data, Size size, final GraphicOverlay graphicOverlay) {
        latestImage = getBitmap(data, size);
        if (processingImage == null)
            processLatestImage(graphicOverlay);
    }

    private synchronized void processLatestImage(final GraphicOverlay graphicOverlay) {
        processingImage = latestImage;
        latestImage = null;
        if (processingImage != null)
            processImage(processingImage, graphicOverlay);
    }

    private void processImage(
            Bitmap data, final GraphicOverlay graphicOverlay) {
        detectInVisionImage(data, graphicOverlay);
    }

    private void detectInVisionImage(
            final Bitmap originalCameraImage,
            final GraphicOverlay graphicOverlay) {

        FirebaseVisionImage image =
                FirebaseVisionImage.fromBitmap(originalCameraImage);

        Task<FirebaseVisionText> detectInImage = detector.processImage(image);
        detectInImage.addOnSuccessListener(
                new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText results) {
                        graphicOverlay.clear();

                        CameraImageGraphic imageGraphic =
                                new CameraImageGraphic(graphicOverlay, originalCameraImage);
                        graphicOverlay.add(imageGraphic);


                        List<FirebaseVisionText.TextBlock> blocks = results.getTextBlocks();
                        for (int i = 0; i < blocks.size(); i++) {
                            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
                            for (int j = 0; j < lines.size(); j++) {
                                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                                for (int k = 0; k < elements.size(); k++) {
                                    GraphicOverlay.Graphic textGraphic
                                            = new TextGraphic(graphicOverlay,elements.get(k));
                                    graphicOverlay.add(textGraphic);
                                }
                            }
                        }
                        graphicOverlay.postInvalidate();
                        processLatestImage(graphicOverlay);
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

    public void stop() {
        try {
            detector.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception thrown while trying to close Text Detector: " + e);
        }
    }

    @Nullable
    private Bitmap getBitmap(ByteBuffer data, Size frameSize) {
        data.rewind();
        byte[] imageInBuffer = new byte[data.limit()];
        data.get(imageInBuffer, 0, imageInBuffer.length);
        try {
            YuvImage image = new YuvImage(imageInBuffer, ImageFormat.NV21,
                    frameSize.getWidth(), frameSize.getHeight(), null);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, frameSize.getWidth(), frameSize.getHeight()), 80, stream);

            Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

            stream.close();
            Matrix matrix = new Matrix();
            matrix.postRotate(CameraSource.ROTATION_DEGREE);
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);

        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
        return null;
    }

}