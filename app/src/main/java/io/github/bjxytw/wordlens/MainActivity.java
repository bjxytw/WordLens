package io.github.bjxytw.wordlens;

import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import androidx.annotation.NonNull;
import io.github.bjxytw.wordlens.camera.CameraPreview;
import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.db.DictionaryData;
import io.github.bjxytw.wordlens.db.DictionarySearch;
import io.github.bjxytw.wordlens.camera.CameraCursorGraphic;

public final class MainActivity extends AppCompatActivity
        implements TextRecognition.TextRecognitionListener {
    private static final String TAG = "MainActivity";
    private CameraSource camera = null;
    private CameraPreview preview;
    private CameraCursorGraphic cameraCursor;
    private TextRecognition textRecognition;
    private DictionarySearch dictionary;
    private ImageButton pauseButton;
    private ImageButton flashButton;
    private TextView resultTextView;
    private TextView headTextView;
    private TextView meanTextView;
    private ScrollView dictionaryScroll;
    private boolean paused;
    private boolean flashed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.cameraPreview);
        cameraCursor = findViewById(R.id.graphicOverlay);
        pauseButton = findViewById(R.id.pauseButton);
        flashButton = findViewById(R.id.flashButton);
        resultTextView = findViewById(R.id.resultText);
        headTextView = findViewById(R.id.headText);
        meanTextView = findViewById(R.id.meanText);
        dictionaryScroll = findViewById(R.id.dictionaryScrollView);

        ButtonClick buttonListener = new ButtonClick();
        pauseButton.setOnClickListener(buttonListener);
        flashButton.setOnClickListener(buttonListener);

        textRecognition = new TextRecognition(cameraCursor);
        textRecognition.setListener(this);

        if (PermissionUtil.isAllPermissionsGranted(this))
            createSources();
        else PermissionUtil.getPermissions(this);
    }

    private void createSources() {
        if (dictionary == null) dictionary = new DictionarySearch(this);
        if (camera == null) camera = new CameraSource(textRecognition);
    }

    private void startCameraSource() {
        if (camera != null) {
            try {
                preview.start(camera, cameraCursor);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                camera.release();
                camera = null;
            }
        }
    }

    @Override
    public void onRecognitionResult(String resultText, Rect boundingBox) {
        if (!paused) {
            String text = DictionarySearch.removeBothEndSymbol(resultText);
            if (text != null && !text.equals(resultTextView.getText().toString())) {
                resultTextView.setText(text);
                DictionaryData data = dictionary.search(text);
                if (data != null && !data.wordText().equals(headTextView.getText().toString())) {
                    headTextView.setText(data.wordText());
                    meanTextView.setText(data.meanText());
                    dictionaryScroll.scrollTo(0, 0);
                }
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
        Log.d(TAG, "onResume");
        super.onResume();
        setPause(false);
        setFlash(flashed);
        startCameraSource();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        preview.stop();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (camera != null) camera.release();
        if (dictionary != null) dictionary.close();
        super.onDestroy();
    }

    private void setPause(boolean pause) {
        if (pause) pauseButton.setImageResource(R.drawable.ic_play_arrow);
        else pauseButton.setImageResource(R.drawable.ic_pause);
        paused = pause;
    }

    private void setFlash(boolean on) {
        if (on) flashButton.setImageResource(R.drawable.ic_highlight_on);
        else flashButton.setImageResource(R.drawable.ic_highlight_off);
        flashed = on;
    }

    private class ButtonClick implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.pauseButton:
                    if (!paused) preview.stop();
                    else startCameraSource();
                    setPause(!paused);
                    break;
                case R.id.flashButton:
                    if (camera != null && !paused) {
                        if (!flashed) {
                            if (camera.cameraFlash(true))
                                setFlash(true);
                            else Toast.makeText(MainActivity.this,
                                    getString(R.string.flash_not_supported),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            camera.cameraFlash(false);
                            setFlash(false);
                        }
                    }
            }
        }
    }
}
