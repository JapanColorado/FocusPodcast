package allen.town.focus_common.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

import allen.town.focus_common.R;
import allen.town.focus_common.databinding.WebdavSettingDialogBinding;
import allen.town.focus_common.util.BasePreferenceUtil;
import allen.town.focus_common.views.AccentMaterialDialog;

public class WebDAVSettingDialog extends AppCompatDialogFragment {


    public static WebDAVSettingDialog show(FragmentManager fragmentManager) {
        WebDAVSettingDialog basicAuthDialog = new WebDAVSettingDialog();
        basicAuthDialog.show(fragmentManager, (String) null);
        return basicAuthDialog;
    }


    @Override
    // android.support.v7.app.AppCompatDialogFragment, android.support.v4.app.DialogFragment
    public Dialog onCreateDialog(Bundle bundle) {
        WebdavSettingDialogBinding inflate = WebdavSettingDialogBinding.inflate(getLayoutInflater());

        inflate.login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BasePreferenceUtil.setWebDevUrl(inflate.server.getText().toString());
                BasePreferenceUtil.setWebDevUser(inflate.user.getText().toString());
                BasePreferenceUtil.setWebDevPass(inflate.password.getText().toString());
                dismiss();
            }
        });

        inflate.server.setText(BasePreferenceUtil.getWebDevUrl());
        inflate.user.setText(BasePreferenceUtil.getWebDevUser());
        inflate.password.setText(BasePreferenceUtil.getWebDevPass());

        AccentMaterialDialog alertDialog = new AccentMaterialDialog(requireContext(),
                R.style.MaterialAlertDialogTheme);
        alertDialog.setView(inflate.getRoot());
        alertDialog.setTitle("WebDAV");
        return alertDialog.create();
    }

    @Override // android.support.v4.app.Fragment
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        getDialog().setCanceledOnTouchOutside(true);
    }

}
