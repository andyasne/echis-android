package org.commcare.android.tests;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.mocks.HttpRequestEndpointsMock;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.network.DebugDataPullResponseFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class KeyAndDataPullTest {
    private final static String APP_BASE = "jr://resource/commcare-apps/form_nav_tests/";
    private static ResultAndError<DataPullTask.PullTaskResult> dataPullResult;

    @Before
    public void setup() {
        TestAppInstaller.installApp(APP_BASE + "profile.ccpr");
    }

    @Test
    public void dataPullWithMissingRemoteKeyRecord() {
        runDataPull(new Integer[] {200});
        Assert.assertEquals(DataPullTask.PullTaskResult.UNKNOWN_FAILURE, dataPullResult.data);
        Assert.assertEquals("Unable to generate encryption key", dataPullResult.errorMessage);
    }

    private static void runDataPull(Integer[] resultCodes) {
        HttpRequestEndpointsMock.setCaseFetchResponseCodes(resultCodes);

        DebugDataPullResponseFactory dataPullRequestor =
                new DebugDataPullResponseFactory(APP_BASE + "simple_data_restore.xml");
        DataPullTask<Object> task =
                new DataPullTask<Object>("test", "123", "fake.server.com", RuntimeEnvironment.application, dataPullRequestor) {
                    @Override
                    protected void deliverResult(Object o, ResultAndError<PullTaskResult> pullTaskResultResultAndError) {
                        dataPullResult = pullTaskResultResultAndError;
                    }

                    @Override
                    protected void deliverUpdate(Object o, Integer... update) {

                    }

                    @Override
                    protected void deliverError(Object o, Exception e) {

                    }
                };
        task.connect(TestAppInstaller.fakeConnector);
        task.execute();

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
    }

    @Test
    public void dataPullWithLocalKeys() {
        useLocalKeys();
        runDataPull(new Integer[]{200});
    }

    private static void useLocalKeys() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        app.getAppPreferences().edit().putString("key_server", null).commit();
    }

    @Test
    public void dataPullServerError() {
        useLocalKeys();
        runDataPull(new Integer[]{500});
        Assert.assertEquals(DataPullTask.PullTaskResult.SERVER_ERROR, dataPullResult.data);
    }

    @Test
    public void dataPullAuthFailed() {
        useLocalKeys();
        runDataPull(new Integer[]{401});
        Assert.assertEquals(DataPullTask.PullTaskResult.AUTH_FAILED, dataPullResult.data);
    }

    @Test
    public void dataPullRecover() {
        useLocalKeys();
        TestAppInstaller.buildTestUser("test", "123");
        TestAppInstaller.login("test", "123");
        runDataPull(new Integer[]{412, 200});
        Assert.assertEquals(DataPullTask.PullTaskResult.DOWNLOAD_SUCCESS, dataPullResult.data);
    }
}
