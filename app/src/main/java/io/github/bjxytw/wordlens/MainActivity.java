package io.github.bjxytw.wordlens;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.bjxytw.wordlens.camera.CameraCursorGraphic;
import io.github.bjxytw.wordlens.camera.CameraPreview;
import io.github.bjxytw.wordlens.camera.CameraSource;
import io.github.bjxytw.wordlens.data.DictionaryData;
import io.github.bjxytw.wordlens.data.LinkTextData;
import io.github.bjxytw.wordlens.db.DictionarySearch;
import io.github.bjxytw.wordlens.settings.SettingsActivity;
import io.github.bjxytw.wordlens.settings.SettingsFragment;

public final class MainActivity extends AppCompatActivity
        implements TextRecognition.TextRecognitionListener,
        CameraSource.AutoFocusFinishedListener, TextToSpeech.OnInitListener {
    private static final String TAG = "MainActivity";
    private static final String REGEX_LINK = "([a-zA-Z]{2,}+)";
    private FirebaseAnalytics analytics;
    private CameraSource camera;
    private CameraPreview preview;
    private CameraCursorGraphic cameraCursor;
    private TextRecognition textRecognition;
    private DictionarySearch dictionary;
    private TextToSpeech textToSpeech;
    private ImageButton pauseButton;
    private ImageButton flashButton;
    private ImageButton zoomButton;
    private ImageButton dictionaryBackButton;
    private ImageButton ttsButton;
    private ImageButton searchButton;
    private ImageButton copyButton;
    private TextView resultTextView;
    private TextView headTextView;
    private TextView meanTextView;
    private ScrollView meanView;
    private LinkedList<DictionaryData> linkHistory = new LinkedList<>();
    private String recognizedText;
    private String searchEngine;
    private Integer zoomRatio;
    private boolean paused;
    private boolean flashed;
    private boolean zoomed;
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
        zoomButton = findViewById(R.id.zoomButton);
        dictionaryBackButton = findViewById(R.id.dictionaryBackButton);
        ttsButton = findViewById(R.id.textToSpeechButton);
        resultTextView = findViewById(R.id.resultText);
        headTextView = findViewById(R.id.headText);
        meanTextView = findViewById(R.id.meanText);
        meanView = findViewById(R.id.meanView);
        searchButton = findViewById(R.id.searchButton);
        copyButton = findViewById(R.id.copyButton);

        ButtonClick buttonListener = new ButtonClick();

        pauseButton.setOnClickListener(buttonListener);
        flashButton.setOnClickListener(buttonListener);
        zoomButton.setOnClickListener(buttonListener);
        dictionaryBackButton.setOnClickListener(buttonListener);
        ttsButton.setOnClickListener(buttonListener);
        searchButton.setOnClickListener(buttonListener);
        copyButton.setOnClickListener(buttonListener);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(new MenuItemClick());

        textRecognition = new TextRecognition(cameraCursor, this);

        dictionary = new DictionarySearch(this);

        textToSpeech = new TextToSpeech(this, this);

        analytics = FirebaseAnalytics.getInstance(this);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || PermissionUtil.isAllPermissionsGranted(this))
            processAfterGranted();
        else PermissionUtil.getPermissions(this);
    }

    private void processAfterGranted() {
        if (camera == null) camera = new CameraSource(textRecognition, this);
    }

    private void startCameraSource() {
        if (camera == null) return;
        try {
            preview.cameraStart(camera, cameraCursor);
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
        String zoomRatioValue = preferences.getString(SettingsFragment.KEY_ZOOM_RATIO, "200");
        zoomRatio = zoomRatioValue == null ? null : Integer.valueOf(zoomRatioValue);
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
                            setPauseIcon(true);
                        }
                    }
                }, linkData.getStart(), linkData.getEnd(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        headTextView.setText(data.wordText());
        meanTextView.setText(spanMeanText);
        meanTextView.setMovementMethod(LinkMovementMethod.getInstance());
        meanView.scrollTo(0, 0);
    }

    @Override
    public void onRecognitionResult(String resultText) {
        if (paused) return;

        if (resultText == null) {
            Toast.makeText(this, getString(R.string.detect_failed), Toast.LENGTH_LONG).show();
            Bundle params = new Bundle();
            params.putInt("failed", 0);
            analytics.logEvent("text_detection_failed", params);
            return;
        }

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
                headTextView.setVisibility(View.VISIBLE);
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
    public void onAutoFocus(boolean success) {
        if (success) {
            cameraCursor.setAreaGraphics(
                    true, CameraCursorGraphic.AREA_FOCUS_SUCCESS_COLOR);
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                cameraCursor.setAreaGraphics(cursorVisible, CameraCursorGraphic.AREA_DEFAULT_COLOR);
            }
        }, 500L);
    }

    @Override
    public void onResume() {
        super.onResume();
        setPauseIcon(false);
        setFlashIcon(flashed);
        setZoomIcon(zoomed);
        loadPreferences();
        if (camera != null) camera.setZoomRatio(zoomRatio);
        cameraCursor.setAreaGraphics(cursorVisible, CameraCursorGraphic.AREA_DEFAULT_COLOR);
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
        preview.cameraStop();
    }

    private void setPauseIcon(boolean pause) {
        if (pause) pauseButton.setImageResource(R.drawable.ic_play);
        else pauseButton.setImageResource(R.drawable.ic_pause);
        paused = pause;
    }

    private void setFlashIcon(boolean flash) {
        if (flash) flashButton.setImageResource(R.drawable.ic_highlight_on);
        else flashButton.setImageResource(R.drawable.ic_highlight_off);
        flashed = flash;
    }

    private void setZoomIcon(boolean zoom) {
        if (zoom) zoomButton.setImageResource(R.drawable.ic_zoomed_24dp);
        else zoomButton.setImageResource(R.drawable.ic_zoom_default_24dp);
        zoomed = zoom;
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
        openInBrowser(searchURL.toString(), useCustomTabs);
    }

    private void openInBrowser(String url, boolean useCustomTabs) {
        if (useCustomTabs) {
            try {
                CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary))
                        .setStartAnimations(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                        .build();
                tabsIntent.launchUrl(this, Uri.parse(url));
                BrowserOpened = true;
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, e.toString());
                Toast.makeText(this,
                        getString(R.string.custom_tabs_cannot_open), Toast.LENGTH_LONG).show();
            }
        } else {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
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
        if (clipboardManager == null) return;

        clipboardManager.setPrimaryClip(
                ClipData.newPlainText("recognizedText", recognizedText));
        makeSnackBar(getString(R.string.copied_message));
    }

    private void makeSnackBar(String message) {
        View view = findViewById(android.R.id.content);
        if (view != null) Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
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
                case R.id.pauseButton:
                    if (paused) startCameraSource();
                    else stop();
                    setPauseIcon(!paused);
                    break;
                case R.id.flashButton:
                    if (camera != null && !paused) {
                        if (!flashed) {
                            if (camera.cameraFlash(true)) setFlashIcon(true);
                            else Toast.makeText(MainActivity.this,
                                    getString(R.string.flash_not_supported),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            camera.cameraFlash(false);
                            setFlashIcon(false);
                        }
                    }
                    break;
                case R.id.zoomButton:
                    if (camera != null && !paused) {
                        if (!zoomed) {
                            if (camera.cameraZoom(true)) setZoomIcon(true);
                            else Toast.makeText(MainActivity.this,
                                    getString(R.string.zoom_not_supported),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            camera.cameraZoom(false);
                            setZoomIcon(false);
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
                    if (BrowserOpened) break;
                    searchButton.setEnabled(false);
                    new Handler().postDelayed(new Runnable() {
                        public void run() { searchButton.setEnabled(true);
                        }
                    }, 2000L);
                    if (recognizedText != null) searchOnBrowser();
                    else makeSnackBar(getString(R.string.not_detected));
                    break;
                case R.id.copyButton:
                    copyButton.setEnabled(false);
                    new Handler().postDelayed(new Runnable() {
                        public void run() { copyButton.setEnabled(true);
                        }
                    }, 2000L);
                    if (recognizedText != null) copyToClipboard();
                    else makeSnackBar(getString(R.string.not_detected));
            }
        }
    }

    private class MenuItemClick implements Toolbar.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_settings:
                    Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(settingsIntent);
                    break;
                case R.id.menu_help:
                    openInBrowser(getString(R.string.help_url), true);
                    break;
                case R.id.menu_feedback:
                    final String[] items = {
                            getString(R.string.write_review),
                            getString(R.string.send_email)};
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(getString(R.string.send_feedback))
                            .setItems(items, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    switch (which) {
                                        case 0:
                                            Uri uri = Uri.parse(getString(R.string.market_uri) + getPackageName());
                                            Intent market = new Intent(Intent.ACTION_VIEW, uri);
                                            market.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                                                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                                                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                                            try {
                                                startActivity(market);
                                            } catch (ActivityNotFoundException e) {
                                                startActivity(new Intent(Intent.ACTION_VIEW,
                                                        Uri.parse(getString(R.string.google_play_url) + getPackageName())));
                                            }
                                            break;
                                        case 1:
                                            Intent emailIntent = new Intent(Intent.ACTION_SENDTO,
                                                    Uri.fromParts("mailto", getString(R.string.dev_email_address), null));
                                            emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject));
                                            startActivity(Intent.createChooser(emailIntent, getString(R.string.mail_chooser_title)));
                                            break;
                                    }
                                }
                            }).show();
                    break;
                case R.id.menu_share_app:
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    String shareText = getString(R.string.app_name_on_market) + " " +
                            getString(R.string.google_play_url) + getPackageName();
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                    startActivity(shareIntent);

                    Bundle params = new Bundle();
                    params.putInt("share", 0);
                    analytics.logEvent("app_share", params);
            }
            return false;
        }
    }
}
