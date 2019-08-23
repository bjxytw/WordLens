package io.github.bjxytw.wordlens;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.AppLaunchChecker;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import io.github.bjxytw.wordlens.camera.CameraPreview;
import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.data.DictionaryData;
import io.github.bjxytw.wordlens.db.DictionarySearch;
import io.github.bjxytw.wordlens.camera.CameraCursorGraphic;
import io.github.bjxytw.wordlens.data.LinkTextData;
import io.github.bjxytw.wordlens.settings.SettingsActivity;
import io.github.bjxytw.wordlens.settings.SettingsFragment;

public final class MainActivity extends AppCompatActivity
        implements TextRecognition.TextRecognitionListener, TextToSpeech.OnInitListener {
    private static final String TAG = "MainActivity";
    private static final String REGEX_LINK = "([a-zA-Z]{2,}+)";
    private CameraSource camera;
    private CameraPreview preview;
    private CameraCursorGraphic cameraCursor;
    private TextRecognition textRecognition;
    private DictionarySearch dictionary;
    private TextToSpeech textToSpeech;
    private ImageButton pauseButton;
    private ImageButton flashButton;
    private ImageButton dictionaryBackButton;
    private ImageButton ttsButton;
    private ImageButton copyButton;
    private TextView resultTextView;
    private TextView headTextView;
    private TextView meanTextView;
    private ScrollView dictionaryScroll;
    private LinkedList<DictionaryData> linkHistory = new LinkedList<>();
    private String recognizedText;
    private String searchEngine;
    private boolean paused;
    private boolean flashed;
    private boolean BrowserOpened;
    private boolean useCustomTabs;
    private boolean cursorVisible;
    private boolean linkToPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.cameraPreview);
        cameraCursor = findViewById(R.id.graphicOverlay);
        pauseButton = findViewById(R.id.pauseButton);
        flashButton = findViewById(R.id.flashButton);
        dictionaryBackButton = findViewById(R.id.dictionaryBackButton);
        ttsButton = findViewById(R.id.textToSpeechButton);
        resultTextView = findViewById(R.id.resultText);
        headTextView = findViewById(R.id.headText);
        meanTextView = findViewById(R.id.meanText);
        dictionaryScroll = findViewById(R.id.dictionaryScrollView);
        ImageButton settingsButton = findViewById(R.id.settingsButton);
        ImageButton searchButton = findViewById(R.id.searchButton);
        copyButton = findViewById(R.id.copyButton);

        ButtonClick buttonListener = new ButtonClick();
        pauseButton.setOnClickListener(buttonListener);
        flashButton.setOnClickListener(buttonListener);
        dictionaryBackButton.setOnClickListener(buttonListener);
        dictionaryBackButton.setVisibility(View.GONE);
        ttsButton.setOnClickListener(buttonListener);
        ttsButton.setVisibility(View.GONE);
        settingsButton.setOnClickListener(buttonListener);
        searchButton.setOnClickListener(buttonListener);
        copyButton.setOnClickListener(buttonListener);

        textRecognition = new TextRecognition(cameraCursor);
        textRecognition.setListener(this);

        dictionary = new DictionarySearch(this);

        textToSpeech = new TextToSpeech(this, this)
;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || PermissionUtil.isAllPermissionsGranted(this))
            processAfterGranted();
        else PermissionUtil.getPermissions(this);
    }

    private void processAfterGranted() {
        if (camera == null) camera = new CameraSource(textRecognition);

        if (!AppLaunchChecker.hasStartedFromLauncher(getApplicationContext())) {
            showTutorialWindow(this, cameraCursor);
            AppLaunchChecker.onActivityCreate(this);
        }
    }

    private void startCameraSource() {
        if (camera == null) return;
        try {
            preview.start(camera, cameraCursor);
        } catch (IOException e) {
            Log.e(TAG, "failed to start camera source.", e);
            camera.release();
            camera = null;
        }
    }

    private void loadPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        searchEngine = preferences.getString(SettingsFragment.KEY_SEARCH_ENGINE, "google");
        useCustomTabs = preferences.getBoolean(SettingsFragment.KEY_CUSTOM_TABS, true);
        cursorVisible = preferences.getBoolean(SettingsFragment.KEY_CURSOR_VISIBLE, false);
        linkToPause = preferences.getBoolean(SettingsFragment.KEY_LINK_PAUSE, false);
    }

    private void setDictionaryText(DictionaryData data) {
        Matcher matcher = Pattern.compile(REGEX_LINK).matcher(data.meanText());
        List<LinkTextData> linkDataList = new ArrayList<>();
        while (matcher.find()) {
            String findText =  matcher.group();
            if (!findText.equalsIgnoreCase(data.wordText())) {
                linkDataList.add(new LinkTextData(matcher.start(), matcher.end(), findText));
            }
        }

        SpannableString spanMeanText = new SpannableString(data.meanText());
        for (final LinkTextData linkData : linkDataList) {
            final DictionaryData linkDictionaryData = dictionary.searchDirect(linkData.getText());
            if (linkDictionaryData != null) {
                spanMeanText.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        if (linkHistory.size() <= 1)
                            dictionaryBackButton.setVisibility(View.VISIBLE);
                        linkHistory.add(linkDictionaryData);
                        setDictionaryText(linkDictionaryData);
                        if (linkToPause) {
                            stop();
                            setPause(true);
                        }
                    }
                }, linkData.getStart(), linkData.getEnd(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        headTextView.setText(data.wordText());
        meanTextView.setText(spanMeanText);
        meanTextView.setMovementMethod(LinkMovementMethod.getInstance());
        dictionaryScroll.scrollTo(0, 0);
    }

    @Override
    public void onRecognitionResult(String resultText) {
        if (paused) return;
        String text = DictionarySearch.removeBothEndSymbol(resultText);
        if (text != null && dictionary != null && !text.equals(recognizedText)) {
            recognizedText = text;
            resultTextView.setText(text);
            DictionaryData data = dictionary.search(text);
            if (data != null && (linkHistory.size() == 0 ||
                    !data.wordText().equals(linkHistory.getFirst().wordText()))) {
                setDictionaryText(data);
                dictionaryBackButton.setVisibility(View.GONE);
                ttsButton.setVisibility(View.VISIBLE);
                linkHistory.clear();
                linkHistory.add(data);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (PermissionUtil.isAllPermissionsGranted(this)) {
            Log.i(TAG, "Permission granted.");
            processAfterGranted();
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
        setPause(false);
        setFlash(flashed);
        loadPreferences();
        cameraCursor.setAreaVisible(cursorVisible);
        BrowserOpened = false;
        startCameraSource();
    }

    @Override
    protected void onPause() {
        stop();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (camera != null) camera.release();
        if (dictionary != null) dictionary.close();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private void stop() {
        preview.stop();
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

    private void searchOnBrowser() {
        StringBuilder searchURL = new StringBuilder();
        switch (searchEngine) {
            case "google":
                searchURL.append(getString(R.string.google_search_url));
                break;
            case "yahoo":
                searchURL.append(getString(R.string.yahoo_search_url));
                break;
            case "bing":
                searchURL.append(getString(R.string.bing_search_url));
                break;
            case "wikipedia_jp":
                searchURL.append(getString(R.string.wikipedia_jp_search_url));
                break;
            case "wikipedia_en":
                searchURL.append(getString(R.string.wikipedia_en_search_url));
                break;
            case "eijiro":
                searchURL.append(getString(R.string.eijiro_search_url));
                break;
            default: return;
        }
        searchURL.append(recognizedText);

        if (useCustomTabs) {
            try {
                CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary))
                        .setStartAnimations(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                        .build();
                tabsIntent.launchUrl(this, Uri.parse(searchURL.toString()));
                BrowserOpened = true;
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, e.toString());
                Toast.makeText(this,
                        getString(R.string.custom_tabs_cannot_open), Toast.LENGTH_LONG).show();
            }
        } else {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(searchURL.toString()));
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, e.toString());
                Toast.makeText(this,
                        getString(R.string.browser_cannot_open), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void copyToClipboard() {
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        View view = findViewById(android.R.id.content);
        if (clipboardManager == null || view == null) return;

        copyButton.setEnabled(false);
        new Handler().postDelayed(new Runnable() {
            public void run() {
                copyButton.setEnabled(true);
            }
        }, 2000L);

        clipboardManager.setPrimaryClip(
                ClipData.newPlainText("recognizedText", recognizedText));
        Snackbar.make(view, getString(R.string.copied_message), Snackbar.LENGTH_SHORT).show();

    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            if (textToSpeech != null) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, R.string.tts_not_supported,
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Log.e(TAG, "TTS initialization failed");
        }
    }

    private class ButtonClick implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.settingsButton:
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                    break;
                case R.id.pauseButton:
                    if (paused) startCameraSource();
                    else stop();
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
                    break;
                case R.id.dictionaryBackButton:
                    if (linkHistory.size() >= 2) {
                        setDictionaryText(linkHistory.get(linkHistory.size() - 2));
                        if (linkHistory.size() == 2)
                            dictionaryBackButton.setVisibility(View.GONE);
                        linkHistory.removeLast();
                    }
                    break;
                case R.id.textToSpeechButton:
                    if (linkHistory.size() > 0 && textToSpeech != null) {
                        textToSpeech.speak(linkHistory.getLast().wordText(),
                                TextToSpeech.QUEUE_FLUSH, null, "WordText");
                    }
                    break;
                case R.id.searchButton:
                    if (!BrowserOpened && recognizedText != null)
                        searchOnBrowser();
                    break;
                case R.id.copyButton:
                    if (recognizedText != null)
                        copyToClipboard();
            }
        }
    }

    private static void showTutorialWindow(final Context context, final View view) {
        view.post(new Runnable() {
            @Override
            public void run() {
                final PopupWindow tutorialPopup = new PopupWindow(context);
                TextView tutorialText = new TextView(context);
                tutorialText.setText(context.getString(R.string.tutorial_message));
                tutorialText.setTextColor(Color.WHITE);
                tutorialText.setTextSize(17.0f);
                tutorialText.setShadowLayer(2.0f, 0.0f, 0.0f,
                        ContextCompat.getColor(context, R.color.colorPopupShadow));
                tutorialPopup.setContentView(tutorialText);
                tutorialPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                tutorialPopup.setOutsideTouchable(true);
                tutorialPopup.setAnimationStyle(R.style.PopupAnimation);
                tutorialPopup.showAtLocation(view, Gravity.TOP, 0, 120);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        tutorialPopup.dismiss();
                    }
                }, 6000L);
            }
        });
    }
}
