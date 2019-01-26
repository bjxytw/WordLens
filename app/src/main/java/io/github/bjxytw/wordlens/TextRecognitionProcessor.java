package io.github.bjxytw.wordlens;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
import io.github.bjxytw.wordlens.graphic.BoundingBoxGraphic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class TextRecognitionProcessor {

    private static final String TAG = "TextRec";
    private static final String SYMBOLS = "[!?,.;:()\"]";
    private static final String SQL_SEARCH = "SELECT mean FROM items WHERE word=?";
    private static final String MEAN_COL = "mean";

    private final FirebaseVisionTextRecognizer detector;
    private final TextView resultTextView;
    private final TextView meanTextView;
    private final GraphicOverlay graphicOverlay;
    private final SQLiteDatabase database;

    private ByteBuffer processingImage;
    private Size processingImageSize;

    TextRecognitionProcessor(GraphicOverlay overlay, SQLiteDatabase db,
                             TextView resultTextView, TextView meanTextView) {
        detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        graphicOverlay = overlay;
        database = db;
        this.resultTextView = resultTextView;
        this.meanTextView = meanTextView;
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
                        graphicOverlay.clearBox();

                        List<FirebaseVisionText.TextBlock> blocks = results.getTextBlocks();
                        for (int i = 0; i < blocks.size(); i++) {
                            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
                            for (int j = 0; j < lines.size(); j++) {
                                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                                for (int k = 0; k < elements.size(); k++) {
                                    processText(elements.get(k));
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
                        Log.e(TAG, "Text detection failed.", e);
                    }
                });
    }

    private void processText(FirebaseVisionText.Element element) {
        Rect boxRect = element.getBoundingBox();
        if (isCursorOnBox(graphicOverlay, boxRect)) {
            graphicOverlay.changeBox(
                    new BoundingBoxGraphic(graphicOverlay, boxRect));
            String text = adjustText(element.getText());
            Log.i(TAG, "Detected: " + text);
            resultTextView.setText(text);
            StringBuilder meanText = new StringBuilder();
            Cursor dbCursor = database.rawQuery(SQL_SEARCH, new String[]{text});
            while (dbCursor.moveToNext()) {
                meanText.append(dbCursor.getString(dbCursor.getColumnIndex(MEAN_COL))
                                .replace(" / ", "\n"));
                meanText.append("\n\n");
            }
            if (meanText.length() > 0)
                meanTextView.setText(meanText);
            dbCursor.close();
        }
    }

    private static boolean isCursorOnBox(GraphicOverlay overlay, Rect box) {
        Rect cursor = overlay.getCursorRect();

        if (cursor != null && box != null) {
            float x = cursor.centerX();
            float y = cursor.centerY();

            return x > overlay.translateX(box.left) && y > overlay.translateY(box.top)
                    && x < overlay.translateX(box.right) && y < overlay.translateY(box.bottom);
        }
        return false;
    }

    private static String adjustText(String text) {
        return text.replaceAll(SYMBOLS, "").toLowerCase();
    }

    public void stop() {
        try {
            detector.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception thrown while trying to close Text Detector: " + e);
        }
    }
}