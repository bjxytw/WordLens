package io.github.bjxytw.wordlens.settings;

import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import io.github.bjxytw.wordlens.R;

public class SettingsFragment extends PreferenceFragment {
    public static final String KEY_SEARCH_ENGINE = "search_engine_list";
    public static final String KEY_CUSTOM_TABS = "custom_tabs_switch";
    public static final String KEY_CURSOR_VISIBLE = "link_cursor_visible_switch";
    public static final String KEY_LINK_PAUSE = "link_pause_switch";
    public static final String KEY_LICENSE_MENU = "license";
    public static final String KEY_TTS_SETTINGS = "tts";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        bindPreferenceSummary(findPreference(KEY_SEARCH_ENGINE));
        Preference licensePref = findPreference(KEY_LICENSE_MENU);
        Preference ttsPref = findPreference(KEY_TTS_SETTINGS);
        licensePref.setOnPreferenceClickListener(new PreferenceClickListener());
        ttsPref.setOnPreferenceClickListener(new PreferenceClickListener());
    }

    private class PreferenceClickListener implements Preference.OnPreferenceClickListener {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (preference.getKey()) {
                case KEY_LICENSE_MENU:
                    startActivity(new Intent(getActivity(), OssLicensesMenuActivity.class));
                    break;
                case KEY_TTS_SETTINGS:
                    Intent intent = new Intent();
                    intent.setAction("com.android.settings.TTS_SETTINGS");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
            }
            return true;
        }
    }

    private class PreferenceChangeListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                preference.setSummary(index >= 0  ? listPreference.getEntries()[index] : null);
            } else preference.setSummary(stringValue);
            return true;
        }
    }

    private void bindPreferenceSummary(Preference preference) {
        PreferenceChangeListener changeListener = new PreferenceChangeListener();
        preference.setOnPreferenceChangeListener(changeListener);
        changeListener.onPreferenceChange(preference, PreferenceManager
                .getDefaultSharedPreferences(preference.getContext())
                .getString(preference.getKey(), ""));
    }
}

