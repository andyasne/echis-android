package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;
import androidx.fragment.app.FragmentResultListener;

import org.auderenow.healthpulse.dxa.mobilesdk.RDTInterpreterResult;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.dalvik.eCHIS.R;
import org.auderenow.healthpulse.dxa.mobilesdk.CaptureFragment;

/**
 * Handles integration with the Audere HealthPulse AI Mobile SDK
 *
 * @author Audere - Rob Jarrett and Yohann Richard
 */

public class CaptureRdtActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportFragmentManager().addFragmentOnAttachListener(new FragmentOnAttachListener() {
            @Override
            public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
                if (fragment.getId() == R.id.capture_rdt_fragment) {
                    configureFragment(fragment);
                }
            }
        });

        getSupportFragmentManager().setFragmentResultListener(CaptureFragment.CvResult, this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                handleCvResult(bundle);
            }
        });

        getSupportFragmentManager().setFragmentResultListener(CaptureFragment.CvError, this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                handleCvError(bundle);
            }
        });
        setContentView(R.layout.activity_capture_rdt);
    }

    private void configureFragment(Fragment fragment) {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String licenseKey = extras.getString("licenseKey", "");
            if (licenseKey != null && !licenseKey.isEmpty()) {
                Bundle arguments = new Bundle();
                arguments.putString(CaptureFragment.LicenseKey, licenseKey);

                String subAppId = ReportingUtils.getAppName().replace(" ", "");
                arguments.putString(CaptureFragment.SubAppId, subAppId);

                String titleText = extras.getString("titleText", "");
                arguments.putString(CaptureFragment.TitleText, titleText);

                arguments.putBoolean(CaptureFragment.IncludeTestAreaImage, true);
                arguments.putBoolean(CaptureFragment.ReturnInterpretedImage, true);

                fragment.setArguments(arguments);
            }
        }
    }

    private void handleCvResult(Bundle bundle) {
        RDTInterpreterResult result = RDTInterpreterResult.fromBundle(bundle);

        String interpretation = result.aiInterpretation != null ? result.aiInterpretation.classification.name() : "UNKNOWN";
        String interpretationConcerns = result.concerns != null && result.concerns.length > 0 ? String.join(", ", result.concerns) : "";
        String imageQualityConcerns = result.imageQuality != null && result.imageQuality.concerns.length > 0 ? String.join(", ", result.imageQuality.concerns) : "";
        String resultText = getIntent().getExtras().getString("resultText", "");
        if (!resultText.isEmpty()) {
            resultText = resultText.replace("%INTERPRETATION%", !interpretation.isEmpty() ? interpretation : "UNKNOWN");
        } else {
            boolean hideResult = getIntent().getExtras().getString("hideResult", "false").equalsIgnoreCase("true");
            resultText = "Image has been captured" + (hideResult ? "" : (!interpretation.isEmpty() ? (", interpretation is " + interpretation) : "UNKNOWN"));
        }
        Intent intentResults = new Intent();
        intentResults.putExtra("odk_intent_data", resultText);

        Bundle bundleResults = new Bundle();
        bundleResults.putString("hasInterpretationConcerns", Boolean.toString(!interpretationConcerns.isEmpty()));
        bundleResults.putString("interpretationConcerns", interpretationConcerns);
        bundleResults.putString("hasImageQualityConcerns", Boolean.toString(!imageQualityConcerns.isEmpty()));
        bundleResults.putString("imageQualityConcerns", imageQualityConcerns);
        bundleResults.putString("aiInterpretation", interpretation);
        if (result.aiInterpretation != null && result.aiInterpretation.testDetails != null) {
            bundleResults.putString("c_result", Boolean.toString(result.aiInterpretation.testDetails.containsKey("c_result") && Boolean.TRUE.equals(result.aiInterpretation.testDetails.get("c_result"))));
            bundleResults.putString("t0_result", Boolean.toString(result.aiInterpretation.testDetails.containsKey("t0_result") && Boolean.TRUE.equals(result.aiInterpretation.testDetails.get("t0_result"))));
            if (result.aiInterpretation.testDetails.containsKey("t1_result")) {
                bundleResults.putString("t1_result", Boolean.toString(Boolean.TRUE.equals(result.aiInterpretation.testDetails.get("t1_result"))));
            } else {
                bundleResults.putString("t1_result", "");
            }
        } else {
            bundleResults.putString("c_result", "");
            bundleResults.putString("t0_result", "");
            bundleResults.putString("t1_result", "");
        }
        bundleResults.putString("sampleImage", Boolean.toString(result.sampleImage));
        bundleResults.putString("imagePath", Uri.parse(result.imageUri).getPath());
        bundleResults.putString("testAreaPath", result.testAreaUri != null && !result.testAreaUri.isEmpty() ? Uri.parse(result.testAreaUri).getPath() : "");
        intentResults.putExtra("odk_intent_bundle", bundleResults);
        setResult(Activity.RESULT_OK, intentResults);

        finish();
    }

    private void handleCvError(Bundle bundle) {
        Intent intentResults = new Intent();
        intentResults.putExtra("odk_intent_data", "Error: " + bundle.getString(CaptureFragment.ErrorMessage, "Unknown"));
        setResult(Activity.RESULT_OK, intentResults);
        finish();
    }
}
