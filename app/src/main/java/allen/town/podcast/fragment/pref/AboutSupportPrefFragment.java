package allen.town.podcast.fragment.pref;

import android.os.Bundle;

import java.util.Locale;

import allen.town.podcast.BuildConfig;
import allen.town.podcast.R;
import allen.town.podcast.activity.SettingsActivity;
import allen.town.podcast.common.ui.customtabs.BrowserLauncher;
import allen.town.podcast.common.util.Constants;
import allen.town.podcast.common.util.LogUtils;
import allen.town.podcast.core.util.IntentUtils;

/** About dialog, changelog, documentation, forum, bug report and donation links. */
public class AboutSupportPrefFragment extends AbsSettingsFragment {
    private static final String PREF_ABOUT = "pref_about";
    private static final String PREF_CHANGE_LOG = "pref_changelog";
    private static final String PREF_DOCUMENTATION = "prefDocumentation";
    private static final String PREF_VIEW_FORUM = "prefViewForum";
    private static final String PREF_SEND_BUG_REPORT = "pref_report_bug";
    private static final String PREF_DONATE = "pref_donate";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_about);
        findPreference(PREF_ABOUT).setOnPreferenceClickListener(preference -> {
            new AboutFragment().show(getActivity().getSupportFragmentManager(), null);
            return true;
        });
        findPreference(PREF_CHANGE_LOG).setOnPreferenceClickListener(preference -> {
            BrowserLauncher.openUrl(getContext(), "https://focuspodcast.canny.io/changelog");
            return true;
        });
        final String documentUrl = documentationUrl();
        findPreference(PREF_DOCUMENTATION).setOnPreferenceClickListener(preference -> {
            BrowserLauncher.openUrl(getContext(), documentUrl);
            return true;
        });
        findPreference(PREF_VIEW_FORUM).setOnPreferenceClickListener(preference -> {
            BrowserLauncher.openUrl(getActivity(), "https://focuspodcast.canny.io/");
            return true;
        });
        findPreference(PREF_SEND_BUG_REPORT).setOnPreferenceClickListener(preference -> {
            LogUtils.delegateFeedback("FocusPodcast", BuildConfig.VERSION_NAME, Constants.PRODUCT_EMAIL,
                    getActivity(), getContext().getString(R.string.provider_authority));
            return true;
        });
        findPreference(PREF_DONATE).setOnPreferenceClickListener(preference -> {
            IntentUtils.openInBrowser(getActivity(), "https://ko-fi.com/focusapps");
            return true;
        });
    }

    /** The documentation exists in English, simplified and traditional Chinese. */
    private static String documentationUrl() {
        String lan = Locale.getDefault().getLanguage();
        boolean isChina = "zh".equals(lan);
        boolean isTChinese = isChina && !Locale.getDefault().getCountry().equalsIgnoreCase("cn");
        String documentUrlBase = "https://y3ep4q3r4d.k.topthink.com/@podcast-";
        if (isChina) {
            if (isTChinese) {
                documentUrlBase += "zh-tw";
            } else {
                documentUrlBase += "zh-cn";
            }
        } else {
            documentUrlBase += "en";
        }
        return documentUrlBase;
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.pref_about_support_title);
    }

    @Override
    public void invalidateSettings() {

    }
}
