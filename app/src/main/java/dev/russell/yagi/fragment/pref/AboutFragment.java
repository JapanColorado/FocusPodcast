package dev.russell.yagi.fragment.pref;

import android.app.Dialog;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDialogFragment;

import java.util.Arrays;
import java.util.List;

import dev.russell.yagi.common.util.Intents;
import dev.russell.yagi.common.util.PackageUtils;
import dev.russell.yagi.common.views.AccentMaterialDialog;
import dev.russell.yagi.theme.ThemeStore;
import dev.russell.yagi.BuildConfig;
import dev.russell.yagi.R;
import dev.russell.yagi.core.util.IntentUtils;
import dev.russell.yagi.core.util.ProjectLinks;
import dev.russell.yagi.databinding.FragmentAboutBinding;

public class AboutFragment extends AppCompatDialogFragment {

    public void shareMyApp() {
        Intents.shareText(getContext(), getString(R.string.share_to_friends_tip, PackageUtils.getAppName(getContext())) + " \n" +
                        ProjectLinks.REPOSITORY,
                "");
    }

    public void showPrivacyPolicy() {
        IntentUtils.openInBrowser(getActivity(), ProjectLinks.PRIVACY_POLICY);
    }

    public void showOpensource() {
        IntentUtils.openInBrowser(getActivity(), ProjectLinks.REPOSITORY);
    }

    @Override
    // android.support.v7.app.AppCompatDialogFragment, android.support.v4.app.DialogFragment
    public Dialog onCreateDialog(Bundle bundle) {
        FragmentAboutBinding binding = FragmentAboutBinding.inflate(LayoutInflater.from(getActivity()));
        binding.versionText.setText(getString(R.string.version, BuildConfig.VERSION_NAME));

        binding.shareApp.setOnClickListener(v -> shareMyApp());
        binding.privacyPolicy.setOnClickListener(v -> showPrivacyPolicy());
        binding.opensource.setOnClickListener(v -> showOpensource());

        List<ImageView> styleButtons = Arrays.asList(binding.privacyPolicyImage, binding.shareAppImage,
                binding.opensourceImage);
        for (ImageView styleButton : styleButtons) {
            styleButton.setColorFilter(ThemeStore.accentColor(getContext()), PorterDuff.Mode.SRC_IN);
        }
        return new AccentMaterialDialog(getContext(), R.style.MaterialAlertDialogTheme).setView(binding.getRoot()).create();
    }
}
