package org.commcare.android.logic;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.commcare.dalvik.BuildConfig;
import org.commcare.suite.model.Callout;
import org.commcare.suite.model.CalloutData;

import java.util.Hashtable;
import java.util.Map;

/**
 * Created by dancluna on 8/5/15.
 */
public final class BarcodeScanListenerDefaultImpl {
    public interface BarcodeScanListener {
        void onBarcodeFetch(String result, Intent intent);

        void onCalloutResult(String result, Intent intent);
    }

    public interface CalloutActionSetup {
        void onImageFound(CalloutData calloutData);
    }

    public interface CalloutAction {
        void callout();
    }

    public static final int BARCODE_FETCH = 1;
    public static final int CALLOUT = 3;

    public static void onBarcodeResult(BarcodeScanListener barcodeScanListener, int requestCode, int resultCode, Intent intent) {
        if (BuildConfig.DEBUG) {
            if (!(requestCode == BARCODE_FETCH)) {
                throw new IllegalArgumentException("requestCode should've been BARCODE_FETCH!");
            }
        }
        if (resultCode == Activity.RESULT_OK) {
            String result = intent.getStringExtra("SCAN_RESULT");
            barcodeScanListener.onBarcodeFetch(result, intent);
        }
    }

    public static void onCalloutResult(BarcodeScanListener barcodeScanListener, int requestCode, int resultCode, Intent intent) {
        if (BuildConfig.DEBUG) {
            if (!(requestCode == CALLOUT)) {
                throw new IllegalArgumentException("requestCode should've been CALLOUT!");
            }
        }
        if (resultCode == Activity.RESULT_OK) {
            String result = intent.getStringExtra("odk_intent_data");
            barcodeScanListener.onCalloutResult(result, intent);
        }
    }

    public static void callBarcodeScanIntent(Activity act) {
        Log.i("SCAN", "Using default barcode scan");
        Intent i = new Intent("com.google.zxing.client.android.SCAN");
        try {
            act.startActivityForResult(i, BARCODE_FETCH);
        } catch (ActivityNotFoundException anfe) {
            Toast.makeText(act,
                    "No barcode reader available! You can install one " +
                            "from the android market.",
                    Toast.LENGTH_LONG).show();
        }
    }

    public static CalloutAction makeCalloutAction(final Activity act, Callout callout, CalloutActionSetup calloutActionSetup) {
        final CalloutData calloutData = callout.evaluate();

        if (calloutData.getImage() != null && calloutActionSetup != null) {
            calloutActionSetup.onImageFound(calloutData);
        }

        final String actionName = calloutData.getActionName();
        final Hashtable<String, String> extras = calloutData.getExtras();

        return new CalloutAction() {
            @Override
            public void callout() {
                Log.i("SCAN", "Using barcode scan with action: " + actionName);
                Intent i = new Intent(actionName);

                for (Map.Entry<String, String> keyValue : extras.entrySet()) {
                    i.putExtra(keyValue.getKey(), keyValue.getValue());
                }
                try {
                    act.startActivityForResult(i, CALLOUT);
                } catch (ActivityNotFoundException anfe) {
                    Toast.makeText(act, "No application found for action: " + actionName, Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    public static View.OnClickListener makeCalloutOnClickListener(final Activity act, Callout callout, CalloutActionSetup calloutActionSetup) {
        if (callout == null) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callBarcodeScanIntent(act);
                }
            };
        } else {
            final CalloutAction calloutAction = makeCalloutAction(act, callout, calloutActionSetup);
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    calloutAction.callout();
                }
            };
        }
    }
}
