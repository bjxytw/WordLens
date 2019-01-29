package io.github.bjxytw.wordlens;

import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import androidx.annotation.NonNull;
import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.db.DictionarySearch;
import io.github.bjxytw.wordlens.graphic.GraphicOverlay;
import io.github.bjxytw.wordlens.processor.TextRecognition;

public final class MainActivity extends AppCompatActivity
        implements TextRecognition.TextRecognitionListener {
    private static final String TAG = "MainActivity";
    private CameraSource camera = null;
    private CameraPreview preview;
    private TextRecognition textRecognition;
    private DictionarySearch dictionary;
    private GraphicOverlay graphicOverlay;
    private TextView resultTextView;
    private TextView headTextView;
    private TextView meanTextView;
    private ScrollView dictionaryScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.cameraPreview);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        resultTextView = findViewById(R.id.resultText);
        headTextView = findViewById(R.id.headText);
        meanTextView = findViewById(R.id.meanText);
        dictionaryScroll = findViewById(R.id.dictionaryScrollView);

        textRecognition = new TextRecognition(graphicOverlay);
        textRecognition.setListener(this);

        if (PermissionUtil.isAllPermissionsGranted(this))
            createSources();
        else PermissionUtil.getPermissions(this);
    }

    private void createSources() {
        if (dictionary == null) dictionary = new DictionarySearch(this);
        if (camera == null) camera = new CameraSource(graphicOverlay, textRecognition);
    }

    private void startCameraSource() {
        if (camera != null) {
            try {
                preview.start(camera, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                camera.release();
                camera = null;
            }
        }
    }

    @Override
    public void onRecognitionResult(String resultText) {
        String text = DictionarySearch.removeBothEndSymbol(resultText);
        if (text != null && !text.equals(resultTextView.getText().toString())) {
            resultTextView.setText(text);
            DictionarySearch.DictionaryData data = dictionary.search(text);
            if (data != null && !data.wordText().equals(headTextView.getText().toString())) {
                headTextView.setText(data.wordText());
                meanTextView.setText(data.meanText());
                dictionaryScroll.scrollTo(0, 0);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (PermissionUtil.isAllPermissionsGranted(this)) {
            Log.i(TAG, "Permission granted.");
            createSources();
        } else {
            Log.i(TAG, "Permission denied.");
            Toast.makeText(this,
                    R.string.permission_request,Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (camera != null) camera.release();
        if (dictionary != null) dictionary.close();
    }

}
