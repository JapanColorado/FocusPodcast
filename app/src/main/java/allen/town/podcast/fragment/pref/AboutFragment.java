package allen.town.podcast.fragment.pref;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDialogFragment;

import java.util.Arrays;
import java.util.List;

import allen.town.focus_common.util.Intents;
import allen.town.focus_common.util.PackageUtils;
import allen.town.focus_common.views.AccentMaterialDialog;
import code.name.monkey.appthemehelper.ThemeStore;
import allen.town.podcast.BuildConfig;
import allen.town.podcast.R;
import allen.town.podcast.core.util.IntentUtils;
import allen.town.podcast.databinding.FragmentAboutBinding;

public class AboutFragment extends AppCompatDialogFragment {

    public void shareMyApp() {
        Intents.shareText(getContext(), getString(R.string.share_to_friends_tip, PackageUtils.getAppName(getContext())) + " \n" +
                        "https://play.google.com/store/apps/details?id=allen.town.focus.podcast",
                "");
    }

    public void showPrivacyPolicy() {
        IntentUtils.openInBrowser(getActivity(), "https://sites.google.com/view/focus-podcast-privacy-policy/");
    }

    public void showOpensource() {
        IntentUtils.openInBrowser(getActivity(), "https://github.com/allentown521/FocusPodcast/");
    }

    public void followMe() {
        String url = "https://twitter.com/allentown521";
        Uri parse = Uri.parse(url);
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.setData(parse);

        if (!Intents.startActivity(getActivity(), intent)) {
            IntentUtils.openInBrowser(getActivity(), url);
        }

    }

    public void rateMe() {
        Uri parse = Uri.parse("market://details?id=allen.town.focus.podcast");
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.setData(parse);
        Intents.startActivity(getContext(), intent);

    }

    public void moreAppsOfUs() {
        Uri parse = Uri.parse("https://play.google.com/store/apps/dev?id=8458616364286916829");
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.setData(parse);
        Intents.startActivity(getContext(), intent);

    }

    @Override
    // android.support.v7.app.AppCompatDialogFragment, android.support.v4.app.DialogFragment
    public Dialog onCreateDialog(Bundle bundle) {
        FragmentAboutBinding binding = FragmentAboutBinding.inflate(LayoutInflater.from(getActivity()));
        binding.versionText.setText(getString(R.string.version, BuildConfig.VERSION_NAME));

        binding.shareApp.setOnClickListener(v -> shareMyApp());
        binding.privacyPolicy.setOnClickListener(v -> showPrivacyPolicy());
        binding.opensource.setOnClickListener(v -> showOpensource());
        binding.twitterFollowMe.setOnClickListener(v -> followMe());
        binding.rateMe.setOnClickListener(v -> rateMe());
        binding.moreAppsOfUs.setOnClickListener(v -> moreAppsOfUs());

        List<ImageView> styleButtons = Arrays.asList(binding.twitterImage, binding.rateImage,
                binding.privacyPolicyImage, binding.moreAppsOfUsImage, binding.shareAppImage,
                binding.opensourceImage);
        for (ImageView styleButton : styleButtons) {
            styleButton.setColorFilter(ThemeStore.accentColor(getContext()), PorterDuff.Mode.SRC_IN);
        }
        return new AccentMaterialDialog(getContext(), R.style.MaterialAlertDialogTheme).setView(binding.getRoot()).create();
    }
}
