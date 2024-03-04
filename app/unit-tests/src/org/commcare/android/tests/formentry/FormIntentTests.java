package org.commcare.android.tests.formentry;

import android.content.Intent;
import android.os.Environment;
import android.view.View;
import android.widget.ImageButton;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.eCHIS.R;
import org.commcare.utils.CompoundIntentList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;

import java.util.ArrayList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertNull;

/**
 * @author Clayton Sims
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FormIntentTests {

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/form_entry_tests/profile.ccpr",
                "test", "123");
    }

    /**
     * Test different behaviors for possibly grouped intent callout views
     */
    @Test
    public void testIntentCalloutAggregation() {
        FormEntryActivity formEntryActivity = ActivityLaunchUtils.launchFormEntry("m0-f0");
        navigateFormStructure(formEntryActivity);
    }

    private void navigateFormStructure(FormEntryActivity formEntryActivity) {

        ImageButton nextButton = formEntryActivity.findViewById(R.id.nav_btn_next);

        testStandaloneIntent(formEntryActivity);

        nextButton.performClick();

        testMultipleIntent(formEntryActivity);

        nextButton.performClick();

        testMixedIntents(formEntryActivity);
    }

    private void testStandaloneIntent(FormEntryActivity formEntryActivity) {
        CompoundIntentList callout = formEntryActivity.getODKView().getAggregateIntentCallout();
        assertNull("incorrectly aggregated intent callout", callout);
        assertEquals("Dispatch button visibility", View.GONE, formEntryActivity.findViewById(R.id.multiple_intent_dispatch_button).getVisibility());
    }

    private void testMultipleIntent(FormEntryActivity formEntryActivity) {
        CompoundIntentList callout = formEntryActivity.getODKView().getAggregateIntentCallout();

        assertEquals("Incorrect number of callout aggregations", 3, callout.getNumberOfCallouts());

        Intent compoundIntentObject = callout.getCompoundedIntent();
        String action = compoundIntentObject.getAction();
        ArrayList<String> indices =
                compoundIntentObject.getStringArrayListExtra(CompoundIntentList.EXTRA_COMPOUND_DATA_INDICES);

        assertEquals("Incorreclty aggregated callout action", "org.commcare.dalvik.eCHIS.action.PRINT", action);

        String testIndex = "1,1_0,0";

        assertTrue("Compound index set missing element: " + testIndex, indices.contains(testIndex));

        String contextualizedBundleValue = compoundIntentObject.getBundleExtra(testIndex).getString("contextualized_value");

        assertEquals("Contextualized bundle value reference", "1", contextualizedBundleValue);
        assertEquals("Dispatch button visibility", View.VISIBLE, formEntryActivity.findViewById(R.id.multiple_intent_dispatch_button).getVisibility());
    }

    private void testMixedIntents(FormEntryActivity formEntryActivity) {
        CompoundIntentList callout = formEntryActivity.getODKView().getAggregateIntentCallout();
        assertNull("Should not have aggregated mixed intents", callout);
        assertEquals("Dispatch button visibility", View.GONE, formEntryActivity.findViewById(R.id.multiple_intent_dispatch_button).getVisibility());
    }
}
