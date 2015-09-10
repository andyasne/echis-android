package org.commcare.android.tests.processing;

import org.commcare.android.shadows.SQLiteDatabaseNative;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for the processing of Key Record files coming from the server.
 * 
 * @author ctsims
 *
 */
@Config(shadows={SQLiteDatabaseNative.class},
        application=org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricGradleTestRunner.class)
public class KeyRecordTest {

    @Before
    public void setupTests() {
        TestUtils.initializeStaticTestStorage();
    }
    
    /**
     *  Test basic parsing of key record format as it is defined in the spec. 
     */
    @Test
    public void testKeyRecordParse() {
        TestUtils.processResourceTransaction("resources/inputs/key_record_create.xml");
        //TODO: Check for existing key record in storage post-parse.
        
        //TODO: Tests to write - establish key record expiration processing
    }
}
