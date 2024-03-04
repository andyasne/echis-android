package org.commcare.preferences;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import org.apache.commons.lang3.ArrayUtils;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.eCHIS.R;
import org.commcare.fragments.CommCarePreferenceFragment;

import java.util.Map;

/**
 * @author yanokwa
 */

public class FormEntryPreferences extends CommCarePreferenceFragment
        implements OnSharedPreferenceChangeListener {

    public static final String KEY_FONT_SIZE = "font_size";
    public static final String KEY_HELP_MODE_TRAY = "help_mode_tray";
    public static final String DEFAULT_FONTSIZE = "21";

    @NonNull
    @Override
    protected String getTitle() {
        return getString(R.string.application_name) + " > " + getString(R.string.form_entry_settings);
    }

    @Override
    protected void setupPrefClickListeners() {
        // Nothing to do here
    }

    @Override
    protected Map<String, String> getPrefKeyTitleMap() {
        return null;
    }

    @Override
    protected int getPreferencesResource() {
        return R.xml.preferences;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateFontSize();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (KEY_FONT_SIZE.equals(key)) {
            updateFontSize();
        }
    }

    private void updateFontSize() {
        ListPreference lp = (ListPreference)findPreference(KEY_FONT_SIZE);
        lp.setSummary(lp.getEntry());
    }

    public static int getQuestionFontSize() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance());
        String fontString = settings.getString(FormEntryPreferences.KEY_FONT_SIZE, DEFAULT_FONTSIZE);
        return Integer.parseInt(fontString);
    }

    public static int getButtonFontSize() {
        Resources res = CommCareApplication.instance().getResources();
        String[] buttonSizes = res.getStringArray(R.array.button_font_size_entry_values);
        String[] textSizes = res.getStringArray(R.array.font_size_entry_values);
        int size = getQuestionFontSize();
        int index = ArrayUtils.indexOf(textSizes, String.valueOf(size));
        return Integer.parseInt(buttonSizes[index]);
    }
}
