package io.github.bjxytw.wordlens.settings;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import io.github.bjxytw.wordlens.R;

public class SettingsActivity extends PreferenceActivity {
    private AppCompatDelegate appCompatDelegate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate delegate = getDelegate();
        delegate.installViewFactory();
        delegate.onCreate(savedInstanceState);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        delegate.setSupportActionBar(toolbar);
        ActionBar actionBar = delegate.getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        getFragmentManager().beginTransaction().replace(R.id.content,
                new SettingsFragment()).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private AppCompatDelegate getDelegate() {
        if (appCompatDelegate == null)
            appCompatDelegate = AppCompatDelegate.create(this, null);
        return appCompatDelegate;
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
