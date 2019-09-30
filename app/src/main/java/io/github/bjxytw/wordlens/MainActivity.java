package io.github.bjxytw.wordlens;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
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
import io.github.bjxytw.wordlens.components.LockableBottomSheetBehavior;
import io.github.bjxytw.wordlens.data.DictionaryData;
import io.github.bjxytw.wordlens.data.LinkTextData;
import io.github.bjxytw.wordlens.db.DictionarySearch;
import io.github.bjxytw.wordlens.settings.SettingsActivity;
import io.github.bjxytw.wordlens.settings.SettingsFragment;

public final class MainActivity extends AppCompatActivity
        implements TextRecognition.TextRecognitionListener,
        CameraSource.AutoFocusFinishedListener,
        TextToSpeech.OnInitListener, TextWatcher {
    private static final String TAG = "MainActivity";
    private static final String REGEX_DICTIONARY_LINK = "([a-zA-Z]{2,}+)";
    private FirebaseAnalytics analytics;
    private CameraSource camera;
    private CameraPreview preview;
    private CameraCursorGraphic cameraCursor;
    private TextRecognition textRecognition;
    private DictionarySearch dictionary;
    private TextToSpeech textToSpeech;
    private LockableBottomSheetBehavior bottomSheetBehavior;
    private ImageButton pauseButton;
    private ImageButton flashButton;
    private ImageButton zoomButton;
    private ImageButton dictionaryBackButton;
    private ImageButton ttsButton;
    private ImageButton expandButton;
    private EditText searchTextView;
    private TextView headTextView;
    private TextView meanTextView;
    private ScrollView dictionaryScrollView;
    private String searchEngine;
    private Integer zoomRatio;
    private LinkedList<DictionaryData> linkHistory = new LinkedList<>();
    private String editingText;
    private boolean dictionaryExpanded;
    private boolean isCameraPaused;
    private boolean isCameraFlashed;
    private boolean isCameraZoomed;
    private boolean BrowserOpened;
    private boolean useCustomTabs;
    private boolean cursorVisible;
    private boolean linkToExpand;

    @SuppressLint("ClickableViewAccessibility")
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
        searchTextView = findViewById(R.id.dictionarySearchText);
        headTextView = findViewById(R.id.headText);
        meanTextView = findViewById(R.id.meanText);
        dictionaryScrollView = findViewById(R.id.dictionaryScrollView);
        expandButton = findViewById(R.id.expandButton);
        ImageButton searchButton = findViewById(R.id.searchButton);

        View dictionaryView = findViewById(R.id.dictionaryLayout);
        bottomSheetBehavior = (LockableBottomSheetBehavior)
                LockableBottomSheetBehavior.from(dictionaryView);

        ButtonClick buttonListener = new ButtonClick();

        pauseButton.setOnClickListener(buttonListener);
        flashButton.setOnClickListener(buttonListener);
        zoomButton.setOnClickListener(buttonListener);
        dictionaryBackButton.setOnClickListener(buttonListener);
        ttsButton.setOnClickListener(buttonListener);
        expandButton.setOnClickListener(buttonListener);
        searchButton.setOnClickListener(buttonListener);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(new MenuItemClick());

        searchTextView.addTextChangedListener(this);

        searchTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!dictionaryExpanded) expandDictionaryLayout(true);
                searchTextView.requestFocus();
                return false;
            }
        });

        dictionaryScrollView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                cancelEdit();
                return false;
            }
        });

        meanTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                cancelEdit();
                return false;
            }
        });

        textRecognition = new TextRecognition(cameraCursor, this);
        dictionary = new DictionarySearch(this);
        textToSpeech = new TextToSpeech(this, this);

        analytics = FirebaseAnalytics.getInstance(this);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || PermissionUtil.isAllPermissionsGranted(this))
            processAfterGrantedPermission();
        else PermissionUtil.getPermissions(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        switchCameraFlash(isCameraFlashed, false);
        switchCameraZoom(isCameraZoomed, false);
        loadPreferences();
        if (camera != null) camera.setZoomRatio(zoomRatio);
        cameraCursor.setAreaGraphics(cursorVisible, CameraCursorGraphic.AREA_DEFAULT_COLOR);
        BrowserOpened = false;
        if (!dictionaryExpanded) startCamera();
    }

    @Override
    protected void onPause() {
        preview.cameraStop();
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

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (PermissionUtil.isAllPermissionsGranted(this)) {
            Log.i(TAG, "Permission granted.");
            processAfterGrantedPermission();
        } else {
            Log.i(TAG, "Permission denied.");
            Toast.makeText(this,
                    R.string.permission_request,Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void processAfterGrantedPermission() {
        if (camera == null) {
            camera = new CameraSource(textRecognition, this);
        }
    }

    private void startCamera() {
        if (camera == null) return;
        try {
            preview.cameraStart(camera, cameraCursor);
        } catch (IOException e) {
            Log.e(TAG, "failed to start camera source.", e);
            camera.release();
            camera = null;
            return;
        }

        pauseButton.setImageResource(R.drawable.ic_pause);
        isCameraPaused = false;
    }

    private void stopCamera() {
        cameraCursor.setAreaGraphics(cursorVisible, CameraCursorGraphic.AREA_DEFAULT_COLOR);
        cameraCursor.invalidate();

        if (camera == null) return;

        preview.cameraStop();
        pauseButton.setImageResource(R.drawable.ic_play);
        isCameraPaused = true;
    }

    private void switchCameraFlash(boolean flash, boolean operate) {
        if (operate && camera != null) {
            if (flash) {
                if (!camera.cameraFlash(true)) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.flash_not_supported),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                camera.cameraFlash(false);
            }
        }

        flashButton.setImageResource(flash ? R.drawable.ic_highlight_on : R.drawable.ic_highlight_off);
        isCameraFlashed = flash;
    }

    private void switchCameraZoom(boolean zoom, boolean operate) {
        if (operate && camera != null) {
            if (zoom) {
                if (!camera.cameraZoom(true)) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.zoom_not_supported),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                camera.cameraZoom(false);
            }
        }

        zoomButton.setImageResource(zoom ? R.drawable.ic_zoomed_24dp : R.drawable.ic_zoom_default_24dp);
        isCameraZoomed = zoom;
    }

    private void loadPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        searchEngine = preferences.getString(SettingsFragment.KEY_SEARCH_ENGINE, "google");
        useCustomTabs = preferences.getBoolean(SettingsFragment.KEY_CUSTOM_TABS, true);
        String zoomRatioValue = preferences.getString(SettingsFragment.KEY_ZOOM_RATIO, "200");
        zoomRatio = zoomRatioValue == null ? null : Integer.valueOf(zoomRatioValue);
        cursorVisible = preferences.getBoolean(SettingsFragment.KEY_CURSOR_VISIBLE, false);
        linkToExpand = preferences.getBoolean(SettingsFragment.KEY_LINK_EXPAND, false);
    }

    private void setDictionaryText(DictionaryData data) {

        Matcher matcher = Pattern.compile(REGEX_DICTIONARY_LINK).matcher(data.meanText());
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
                        if (linkHistory.size() <= 1) {
                            dictionaryBackButton.setVisibility(View.VISIBLE);
                        }

                        linkHistory.add(linkDictionaryData);
                        setDictionaryText(linkDictionaryData);

                        if (linkToExpand) {
                            expandDictionaryLayout(true);
                        }
                    }
                }, linkData.getStart(), linkData.getEnd(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        headTextView.setText(data.wordText());
        meanTextView.setText(spanMeanText);
        meanTextView.setMovementMethod(LinkMovementMethod.getInstance());
        dictionaryScrollView.scrollTo(0, 0);
    }

    private void expandDictionaryLayout(boolean expand) {
        if (expand) {
            if (camera != null && !isCameraPaused) stopCamera();
        } else {
            cancelEdit();
            if (camera != null) startCamera();
        }

        dictionaryScrollView.getLayoutParams().height =
                expand ? RelativeLayout.LayoutParams.MATCH_PARENT : (int) getResources().getDimension(R.dimen.dictionary_scroll_view_height);
        dictionaryScrollView.requestLayout();
        bottomSheetBehavior.setState(expand ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED);
        expandButton.setImageResource(expand ? R.drawable.ic_fold_24dp  : R.drawable.ic_expand_24dp);
        dictionaryExpanded = expand;
    }

    private void searchOnWeb(String param) {
        String[] engineList = getResources().getStringArray(R.array.pref_search_engine_list_values);
        String[] engineUrlList = getResources().getStringArray(R.array.search_engine_url_list);

        if (engineList.length != engineUrlList.length) return;

        for (int i = 0; i < engineList.length; i++) {
            if (engineList[i].equals(searchEngine)) {
                openBrowser(engineUrlList[i] + param, useCustomTabs);
                break;
            }
        }
    }

    private void openBrowser(String url, boolean useCustomTabs) {
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

    private void cancelEdit() {
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(dictionaryScrollView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        searchTextView.clearFocus();
    }

    private void makeSnackBar(String message) {
        View view = findViewById(android.R.id.content);
        if (view != null) Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onRecognitionResult(String resultText) {
        if (isCameraPaused || dictionaryExpanded) return;

        if (resultText == null) {
            Toast.makeText(this, getString(R.string.detect_failed), Toast.LENGTH_LONG).show();
            Bundle params = new Bundle();
            params.putInt("failed", 0);
            analytics.logEvent("text_detection_failed", params);
            return;
        }

        String text = DictionarySearch.removeBothEndSymbol(resultText);
        if (text != null && dictionary != null) {
            searchTextView.setText(text);
            DictionaryData dictData = dictionary.search(text);
            if (dictData != null && (linkHistory.size() == 0 ||
                    !dictData.wordText().equals(linkHistory.getFirst().wordText()))) {
                setDictionaryText(dictData);

                dictionaryBackButton.setVisibility(View.GONE);
                ttsButton.setVisibility(View.VISIBLE);
                headTextView.setVisibility(View.VISIBLE);
                linkHistory.clear();
                linkHistory.add(dictData);
            }
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if (dictionaryExpanded) {
                expandDictionaryLayout(false);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void afterTextChanged(final Editable s) {
        if (!dictionaryExpanded || s == null) return;
        final String text = s.toString();
        editingText = text;

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!text.equals(editingText)) return;

                if (text.length() > 0) {
                    DictionaryData dictData = dictionary.search(text);
                    if (dictData != null) {
                        setDictionaryText(dictData);
                        dictionaryBackButton.setVisibility(View.GONE);
                        ttsButton.setVisibility(View.VISIBLE);
                        headTextView.setVisibility(View.VISIBLE);
                        linkHistory.clear();
                        linkHistory.add(dictData);
                    }
                }
            }
        }, 400L);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    private class ButtonClick implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.pauseButton:
                    if (isCameraPaused) startCamera();
                    else stopCamera();
                    break;
                case R.id.flashButton:
                    if (!isCameraPaused) {
                        switchCameraFlash(!isCameraFlashed, true);
                    }
                    break;
                case R.id.zoomButton:
                    if (!isCameraPaused) {
                        switchCameraZoom(!isCameraZoomed, true);
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
                    cancelEdit();
                    String searchWord = searchTextView.getText().toString();
                    if (searchWord.length() > 0) searchOnWeb(searchWord);
                    else makeSnackBar(getString(R.string.not_detected));
                    break;
                case R.id.expandButton:
                    expandDictionaryLayout(!dictionaryExpanded);
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
                    openBrowser(getString(R.string.help_url), true);
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
