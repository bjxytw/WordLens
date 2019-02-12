package io.github.bjxytw.wordlens;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

public class SettingsActivity extends PreferenceActivity {
    public static final String KEY_SEARCH_ENGINE = "search_engine_list";
    public static final String KEY_CUSTOM_TABS = "custom_tabs_switch";
    private AppCompatDelegate delegate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        getDelegate().setSupportActionBar(toolbar);
        ActionBar actionBar = getDelegate().getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        getFragmentManager().beginTransaction().replace(R.id.content,
                new SettingsFragment()).commit();
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            bindPreferenceSummary(findPreference(KEY_SEARCH_ENGINE));
        }
    }

    private static class PreferenceChangeListener implements Preference.OnPreferenceChangeListener {
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private static void bindPreferenceSummary(Preference preference) {
        PreferenceChangeListener changeListener = new PreferenceChangeListener();
        preference.setOnPreferenceChangeListener(changeListener);
        changeListener.onPreferenceChange(preference, PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    private AppCompatDelegate getDelegate() {
        if (delegate == null) {
            delegate = AppCompatDelegate.create(this, null);
        }
        return delegate;
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getDelegate().onPostResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        getDelegate().onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getDelegate().onDestroy();
    }
}
