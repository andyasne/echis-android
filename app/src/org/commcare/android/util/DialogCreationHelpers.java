package org.commcare.android.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class DialogCreationHelpers {
    public static AlertDialog buildAboutCommCareDialog(Activity activity) {
        final String commcareVersion = CommCareApplication._().getCurrentVersionString();

        LayoutInflater li = LayoutInflater.from(activity);
        View view = li.inflate(R.layout.scrolling_info_dialog, null);
        TextView aboutText = (TextView)view.findViewById(R.id.dialog_text);

        String msg = activity.getString(R.string.aboutdialog, commcareVersion);
        Spannable markdownText = MarkupUtil.returnMarkdown(activity, msg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            aboutText.setText(markdownText);
        } else {
            aboutText.setText(markdownText.toString());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("About CommCare");
        builder.setView(view);

        return builder.create();
    }

    public static AlertDialog buildPermissionRequestDialog(Activity activity, final RuntimePermissionRequester permRequester, String title, String body) {
        LayoutInflater li = LayoutInflater.from(activity);
        View view = li.inflate(R.layout.scrolling_info_dialog, null);
        TextView aboutText = (TextView)view.findViewById(R.id.dialog_text);

        aboutText.setText(body);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setView(view);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                permRequester.requestNeededPermissions();
                dialog.dismiss();
            }
        });

        return builder.create();
    }
}
