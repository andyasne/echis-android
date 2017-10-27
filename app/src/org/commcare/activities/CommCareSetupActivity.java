package org.commcare.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.fragments.ContainerFragment;
import org.commcare.fragments.InstallConfirmFragment;
import org.commcare.fragments.InstallPermissionsFragment;
import org.commcare.fragments.SelectInstallModeFragment;
import org.commcare.fragments.SetupEnterURLFragment;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.preferences.GlobalPrivilegesManager;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.ResourceEngineTask;
import org.commcare.tasks.RetrieveParseVerifyMessageListener;
import org.commcare.tasks.RetrieveParseVerifyMessageTask;
import org.commcare.utils.ConsumerAppsUtil;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.utils.Permissions;
import org.commcare.views.ManagedUi;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import java.io.IOException;
import java.security.SignatureException;
import java.util.List;

/**
 * Responsible for identifying the state of the application (uninstalled,
 * installed) and performing any necessary setup to get to a place where
 * CommCare can load normally.
 *
 * If the startup activity identifies that the app is installed properly it
 * should not ever require interaction or be visible to the user.
 *
 * @author ctsims
 */
@ManagedUi(R.layout.first_start_screen_modern)
public class CommCareSetupActivity extends CommCareActivity<CommCareSetupActivity>
        implements ResourceEngineListener, SetupEnterURLFragment.URLInstaller,
        InstallConfirmFragment.StartStopInstallCommands, RetrieveParseVerifyMessageListener,
        RuntimePermissionRequester {
    private static final String TAG = CommCareSetupActivity.class.getSimpleName();

    private static final String KEY_UI_STATE = "current_install_ui_state";
    private static final String KEY_LAST_INSTALL_MODE = "offline_install";
    private static final String KEY_FROM_EXTERNAL = "from_external";
    private static final String KEY_FROM_MANAGER = "from_manager";
    private static final String KEY_MANUAL_SMS_INSTALL = "sms-install-triggered-manually";
    private static final String KEY_ERROR_MESSAGE = "error-message";

    private static final int SMS_PERMISSIONS_REQUEST = 2;

    private static final String FORCE_VALIDATE_KEY = "validate";


    /**
     * UI configuration states.
     */
    public enum UiState {
        IN_URL_ENTRY,
        CHOOSE_INSTALL_ENTRY_METHOD,
        READY_TO_INSTALL,
        NEEDS_PERMS,
        BLANK
    }

    private UiState uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;
    private String errorMessageToDisplay;

    public static final int MENU_ARCHIVE = Menu.FIRST;
    private static final int MENU_SMS = Menu.FIRST + 2;
    private static final int MENU_FROM_LIST = Menu.FIRST + 3;

    // Activity request codes
    public static final int BARCODE_CAPTURE = 1;
    public static final int OFFLINE_INSTALL = 3;
    private static final int MULTIPLE_APPS_LIMIT = 4;
    public static final int GET_APPS_FROM_HQ = 5;

    // dialog ID
    private static final int DIALOG_INSTALL_PROGRESS = 4;

    private boolean startAllowed = true;
    private String incomingRef;
    private CommCareApp ccApp;

    /**
     * Indicates that this activity was launched from the AppManagerActivity
     */
    private boolean fromManager;

    /**
     * Indicates that this activity was launched from an outside application (such as a bit.ly
     * url entered in a browser)
     */
    private boolean fromExternal;

    private static final int INSTALL_MODE_BARCODE = 0;
    private static final int INSTALL_MODE_URL = 1;
    private static final int INSTALL_MODE_OFFLINE = 2;
    private static final int INSTALL_MODE_SMS = 3;
    private static final int INSTALL_MODE_FROM_LIST = 4;
    private static final int INSTALL_MODE_MANAGED_CONFIGURATION = 5;
    private int lastInstallMode;

    /**
     * Remember how the sms install was triggered in case orientation changes while asking for permissions
     */
    private boolean manualSMSInstall;

    private final FragmentManager fm = getSupportFragmentManager();
    private final InstallConfirmFragment startInstall = new InstallConfirmFragment();
    private final SelectInstallModeFragment installFragment = new SelectInstallModeFragment();
    private final InstallPermissionsFragment permFragment = new InstallPermissionsFragment();
    private ContainerFragment<CommCareApp> containerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fromManager = getIntent().getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);

        if (checkForMultipleAppsViolation()) {
            return;
        }

        loadIntentAndInstanceState(savedInstanceState);
        persistCommCareAppState();

        if (isSingleAppBuild()) {
            uiState = UiState.BLANK;
        }

        boolean askingForPerms =
                Permissions.acquireAllAppPermissions(this, this,
                        Permissions.ALL_PERMISSIONS_REQUEST);
        if (!askingForPerms) {
            if (isSingleAppBuild()) {
                SingleAppInstallation.installSingleApp(this, DIALOG_INSTALL_PROGRESS);
            } else if (uiState == UiState.CHOOSE_INSTALL_ENTRY_METHOD) {
                // Don't perform SMS install if we aren't on base setup state
                // (i.e. in the middle of an install)

                // With basic perms satisfied, ask user to allow SMS reading
                // for sms app install code
                performSMSInstall(false);
            }
        }
    }

    private void loadIntentAndInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            if (Intent.ACTION_VIEW.equals(this.getIntent().getAction())) {
                //We got called from an outside application, it's gonna be a wild ride!
                fromExternal = true;
                incomingRef = this.getIntent().getData().toString();
                if (incomingRef.contains(".ccz")) {
                    // make sure this is in the file system
                    boolean isFile = incomingRef.contains("file://");
                    if (isFile) {
                        // remove file:// prepend
                        incomingRef = incomingRef.substring(incomingRef.indexOf("//") + 2);
                        Intent i = new Intent(this, InstallArchiveActivity.class);
                        i.putExtra(InstallArchiveActivity.ARCHIVE_FILEPATH, incomingRef);
                        startActivityForResult(i, OFFLINE_INSTALL);
                    } else {
                        // currently down allow other locations like http://
                        fail(NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.Bad_Archive_File), true);
                    }
                } else {
                    this.uiState = UiState.READY_TO_INSTALL;
                }
            }
        } else {
            loadStateFromInstance(savedInstanceState);
        }
    }

    private void loadStateFromInstance(Bundle savedInstanceState) {
        uiState = (UiState)savedInstanceState.getSerializable(KEY_UI_STATE);
        incomingRef = savedInstanceState.getString("profileref");
        fromExternal = savedInstanceState.getBoolean(KEY_FROM_EXTERNAL);
        fromManager = savedInstanceState.getBoolean(KEY_FROM_MANAGER);
        manualSMSInstall = savedInstanceState.getBoolean(KEY_MANUAL_SMS_INSTALL);
        lastInstallMode = savedInstanceState.getInt(KEY_LAST_INSTALL_MODE);
        errorMessageToDisplay = savedInstanceState.getString(KEY_ERROR_MESSAGE);
        // Uggggh, this might not be 100% legit depending on timing, what
        // if we've already reconnected and shut down the dialog?
        startAllowed = savedInstanceState.getBoolean("startAllowed");
    }

    private void persistCommCareAppState() {
        FragmentManager fm = this.getSupportFragmentManager();

        containerFragment = (ContainerFragment<CommCareApp>)fm.findFragmentByTag("cc-app");

        if (containerFragment == null) {
            containerFragment = new ContainerFragment<>();
            fm.beginTransaction().add(containerFragment, "cc-app").commit();
        } else {
            ccApp = containerFragment.getData();
        }
    }

    /**
     * @return if installation is not allowed due to multiple apps limitations
     */
    private boolean checkForMultipleAppsViolation() {
        if (AppUtils.getInstalledAppRecords().size() >= 2
                && !GlobalPrivilegesManager.isMultipleAppsPrivilegeEnabled()
                && !BuildConfig.DEBUG) {
            Intent i = new Intent(this, MultipleAppsLimitWarningActivity.class);
            i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, fromManager);
            startActivityForResult(i, MULTIPLE_APPS_LIMIT);
            return true;
        }
        return false;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                // removes the back button from the action bar
                actionBar.setDisplayHomeAsUpEnabled(false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!fromManager && !fromExternal && MultipleAppsUtil.usableAppsPresent()) {
            // If clicking the regular app icon brought us to CommCareSetupActivity
            // (because that's where we were last time the app was up), but there are now
            // 1 or more available apps, we want to fall back to dispatch activity
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        installFragment.showOrHideErrorMessage();
        uiStateScreenTransition();
    }

    @Override
    public void onURLChosen(String url) {
        incomingRef = url;
        this.uiState = UiState.READY_TO_INSTALL;
        uiStateScreenTransition();
    }

    private void uiStateScreenTransition() {
        if (areFragmentsPaused()) {
            // Don't perform fragment transactions when the activity isn't visible
            return;
        }

        Fragment fragment;
        FragmentTransaction ft = fm.beginTransaction();

        switch (uiState) {
            case READY_TO_INSTALL:
                if (incomingRef == null || incomingRef.length() == 0) {
                    Log.e(TAG, "During install: incomingRef is empty!");
                    displayError("Empty URL provided");
                    return;
                }

                // the buttonCommands were already set when the fragment was
                // attached, no need to set them here
                fragment = startInstall;
                break;
            case IN_URL_ENTRY:
                fragment = restoreInstallSetupFragment();
                break;
            case CHOOSE_INSTALL_ENTRY_METHOD:
                fragment = installFragment;
                break;
            case NEEDS_PERMS:
                fragment = permFragment;
                break;
            case BLANK:
                fragment = new Fragment();
                break;
            default:
                return;
        }

        ft.replace(R.id.setup_fragment_container, fragment);
        ft.commit();
    }

    private Fragment restoreInstallSetupFragment() {
        Fragment fragment = null;
        List<Fragment> fgmts = fm.getFragments();
        int lastIndex = fgmts != null ? fgmts.size() - 1 : -1;
        if (lastIndex > -1) {
            fragment = fgmts.get(lastIndex);
        }
        if (!(fragment instanceof SetupEnterURLFragment)) {
            // last fragment wasn't url entry, so default to the installation method chooser
            fragment = installFragment;
        }
        return fragment;
    }

    @Override
    public int getWakeLockLevel() {
        return PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(KEY_UI_STATE, uiState);
        outState.putString("profileref", incomingRef);
        outState.putBoolean("startAllowed", startAllowed);
        outState.putInt(KEY_LAST_INSTALL_MODE, lastInstallMode);
        outState.putBoolean(KEY_FROM_EXTERNAL, fromExternal);
        outState.putBoolean(KEY_FROM_MANAGER, fromManager);
        outState.putBoolean(KEY_MANUAL_SMS_INSTALL, manualSMSInstall);
        outState.putString(KEY_ERROR_MESSAGE, errorMessageToDisplay);
        Log.v("UiState", "Saving instance state: " + outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        String result = null;
        switch (requestCode) {
            case BARCODE_CAPTURE:
                if (resultCode == Activity.RESULT_OK) {
                    result = data.getStringExtra("SCAN_RESULT");
                    String dbg = "Got url from barcode scanner: " + result;
                    Log.i(TAG, dbg);
                    lastInstallMode = INSTALL_MODE_BARCODE;
                }
                break;
            case OFFLINE_INSTALL:
                if (resultCode == Activity.RESULT_OK) {
                    lastInstallMode = INSTALL_MODE_OFFLINE;
                    result = data.getStringExtra(InstallArchiveActivity.ARCHIVE_JR_REFERENCE);
                }
                break;
            case GET_APPS_FROM_HQ:
                if (resultCode == Activity.RESULT_OK) {
                    lastInstallMode = INSTALL_MODE_FROM_LIST;
                    result = data.getStringExtra(InstallFromListActivity.PROFILE_REF);
                }
                break;
            case MULTIPLE_APPS_LIMIT:
                setResult(RESULT_CANCELED);
                finish();
                return;
        }
        if (result == null) {
            return;
        }

        if (lastInstallMode == INSTALL_MODE_FROM_LIST) {
            FirebaseAnalyticsUtil.reportFeatureUsage(AnalyticsParamValue.FEATURE_INSTALL_FROM_LIST);
        }
        setReadyToInstall(result);
    }

    private void setReadyToInstall(String reference) {
        incomingRef = reference;
        this.uiState = UiState.READY_TO_INSTALL;

        try {
            ReferenceManager.instance().DeriveReference(incomingRef);
            if (lastInstallMode == INSTALL_MODE_OFFLINE || lastInstallMode == INSTALL_MODE_FROM_LIST) {
                onStartInstallClicked();
            } else {
                uiStateScreenTransition();
            }
        } catch (InvalidReferenceException ire) {
            incomingRef = null;
            fail(Localization.get("install.bad.ref"));
        }
    }

    @Override
    public void startBlockingForTask(int id) {
        super.startBlockingForTask(id);
        this.startAllowed = false;
    }

    @Override
    public void stopBlockingForTask(int id) {
        super.stopBlockingForTask(id);
        this.startAllowed = true;
    }

    private void startResourceInstall() {
        if (startAllowed) {
            ccApp = getCommCareApp();
            containerFragment.setData(ccApp);

            CustomProgressDialog lastDialog = getCurrentProgressDialog();
            // used to tell the ResourceEngineTask whether or not it should
            // sleep before it starts, set based on whether we are currently
            // in keep trying mode.
            boolean shouldSleep = (lastDialog != null) && lastDialog.isChecked();

            ResourceEngineTask<CommCareSetupActivity> task =
                    new ResourceEngineTask<CommCareSetupActivity>(ccApp,
                            DIALOG_INSTALL_PROGRESS, shouldSleep, determineAuthorityForInstall()) {

                        @Override
                        protected void deliverResult(CommCareSetupActivity receiver,
                                                     AppInstallStatus result) {
                            switch (result) {
                                case Installed:
                                    receiver.reportSuccess(true);
                                    break;
                                case UpToDate:
                                    receiver.reportSuccess(false);
                                    break;
                                case MissingResourcesWithMessage:
                                    // fall through to more general case:
                                case MissingResources:
                                    receiver.failMissingResource(this.missingResourceException, result);
                                    break;
                                case InvalidResource:
                                    receiver.failInvalidResource(this.invalidResourceException, result);
                                    break;
                                case IncompatibleReqs:
                                    receiver.failBadReqs(badReqCode, vRequired, vAvailable, majorIsProblem);
                                    break;
                                case NoLocalStorage:
                                    receiver.failWithNotification(AppInstallStatus.NoLocalStorage);
                                    break;
                                case BadCertificate:
                                    receiver.failWithNotification(AppInstallStatus.BadCertificate);
                                    break;
                                case DuplicateApp:
                                    receiver.failWithNotification(AppInstallStatus.DuplicateApp);
                                    break;
                                default:
                                    receiver.failUnknown(AppInstallStatus.UnknownFailure);
                                    break;
                            }
                        }

                        @Override
                        protected void deliverUpdate(CommCareSetupActivity receiver,
                                                     int[]... update) {
                            receiver.updateResourceProgress(update[0][0], update[0][1], update[0][2]);
                        }

                        @Override
                        protected void deliverError(CommCareSetupActivity receiver,
                                                    Exception e) {
                            receiver.failUnknown(AppInstallStatus.UnknownFailure);
                        }
                    };

            task.connect(this);
            task.executeParallel(incomingRef);
        } else {
            Log.i(TAG, "During install: blocked a resource install press since a task was already running");
        }
    }

    private int determineAuthorityForInstall() {
        // Note that this is an imperfect way to determine the resource authority; we should
        // really be looking at the nature of the reference that is being used itself (i.e. is it
        // a file reference or a URL)
        return lastInstallMode == INSTALL_MODE_OFFLINE ?
                Resource.RESOURCE_AUTHORITY_LOCAL : Resource.RESOURCE_AUTHORITY_REMOTE;
    }

    public static CommCareApp getCommCareApp() {
        ApplicationRecord newRecord =
                new ApplicationRecord(PropertyUtils.genUUID().replace("-", ""),
                        ApplicationRecord.STATUS_UNINITIALIZED);

        return new CommCareApp(newRecord);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ARCHIVE, 0, Localization.get("menu.archive")).setIcon(android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_SMS, 1, Localization.get("menu.sms")).setIcon(android.R.drawable.stat_notify_chat);
        menu.add(0, MENU_FROM_LIST, 2, Localization.get("menu.app.list.install"));
        return true;
    }

    /**
     * Scan SMS messages for texts with profile references.
     *
     * @param installTriggeredManually if scan was triggered manually, then
     *                                 install automatically if reference is found
     */
    private void performSMSInstall(boolean installTriggeredManually) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            manualSMSInstall = installTriggeredManually;

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_SMS)) {
                DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                        SMS_PERMISSIONS_REQUEST,
                        Localization.get("permission.sms.install.title"),
                        Localization.get("permission.sms.install.message")).showNonPersistentDialog();
            } else {
                requestNeededPermissions(SMS_PERMISSIONS_REQUEST);
            }
        } else {
            scanSMSLinks();
        }
    }

    @Override
    public void requestNeededPermissions(int requestCode) {
        if (requestCode == SMS_PERMISSIONS_REQUEST) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_SMS},
                    requestCode);
        } else {
            ActivityCompat.requestPermissions(this,
                    Permissions.getAppPermissions(),
                    requestCode);
        }
    }

    /**
     * Scan the most recent incoming text messages for a message with a
     * verified link to a commcare app and install it.  Message scanning stops
     * after the number of scanned messages reaches 'SMS_CHECK_COUNT'.
     */
    private void scanSMSLinks() {
        final boolean installTriggeredManually = manualSMSInstall;
        RetrieveParseVerifyMessageTask<CommCareSetupActivity> smsProcessTask =
                new RetrieveParseVerifyMessageTask<CommCareSetupActivity>(this, getContentResolver(), installTriggeredManually) {

                    @Override
                    protected void deliverResult(CommCareSetupActivity receiver, String result) {
                        if (installTriggeredManually) {
                            if (result != null) {
                                receiver.incomingRef = result;
                                receiver.uiState = UiState.READY_TO_INSTALL;
                                receiver.lastInstallMode = INSTALL_MODE_SMS;
                                receiver.uiStateScreenTransition();
                                receiver.startResourceInstall();
                            } else {
                                // only notify if this was manually triggered
                                receiver.displayError(Localization.get("menu.sms.not.found"));
                            }
                        } else {
                            if (result != null) {
                                receiver.incomingRef = result;
                                receiver.uiState = UiState.READY_TO_INSTALL;
                                receiver.lastInstallMode = INSTALL_MODE_SMS;
                                receiver.uiStateScreenTransition();
                                Toast.makeText(receiver, Localization.get("menu.sms.ready"), Toast.LENGTH_LONG).show();
                            }
                        }
                    }

                    @Override
                    protected void deliverUpdate(CommCareSetupActivity receiver, Void... update) {
                    }

                    @Override
                    protected void deliverError(CommCareSetupActivity receiver, Exception e) {
                        if (e instanceof SignatureException) {
                            e.printStackTrace();
                            receiver.fail(Localization.get("menu.sms.not.verified"));
                        } else if (e instanceof IOException) {
                            e.printStackTrace();
                            receiver.fail(Localization.get("menu.sms.not.retrieved"));
                        } else {
                            e.printStackTrace();
                            receiver.fail(Localization.get("notification.install.unknown.title"));
                        }
                    }
                };
        smsProcessTask.connect(this);
        smsProcessTask.executeParallel();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ARCHIVE:
                clearErrorMessage();
                Intent i = new Intent(getApplicationContext(), InstallArchiveActivity.class);
                startActivityForResult(i, OFFLINE_INSTALL);
                break;
            case MENU_SMS:
                clearErrorMessage();
                performSMSInstall(true);
                break;
            case MENU_FROM_LIST:
                clearErrorMessage();
                i = new Intent(getApplicationContext(), InstallFromListActivity.class);
                startActivityForResult(i, GET_APPS_FROM_HQ);
                break;
        }
        return true;
    }

    private void fail(NotificationMessage notificationMessage, boolean showAsPinnedNotifcation) {
        String message;
        if (showAsPinnedNotifcation) {
            CommCareApplication.notificationManager().reportNotificationMessage(notificationMessage);
            message = Localization.get("notification.for.details.wrapper",
                    new String[]{notificationMessage.getTitle()});
        } else {
            message = notificationMessage.getTitle();
        }
        fail(message);
    }

    /**
     * Display an error and perform a UI transition
     */
    private void fail(String message) {
        displayError(message);
        uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;
        uiStateScreenTransition();
    }

    /**
     * Display an error without performing a UI transition
     */
    private void displayError(String message) {
        errorMessageToDisplay = message;
        installFragment.showOrHideErrorMessage();
    }

    public void clearErrorMessage() {
        errorMessageToDisplay = null;
    }

    public String getErrorMessageToDisplay() {
        return errorMessageToDisplay;
    }

    @Override
    public void reportSuccess(boolean newAppInstalled) {
        CommCareApplication.notificationManager().clearNotifications("install_update");

        if (newAppInstalled) {
            FirebaseAnalyticsUtil.reportAppInstall(getAnalyticsParamForInstallMethod(lastInstallMode));
        } else {
            Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
        }

        if (Intent.ACTION_VIEW.equals(CommCareSetupActivity.this.getIntent().getAction())) {
            // app installed from external action
            if (getIntent().getBooleanExtra(FORCE_VALIDATE_KEY, false)) {
                // force multimedia validation to ensure app shows up in multiple apps list
                Intent i = new Intent(this, CommCareVerificationActivity.class);
                i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
                startActivity(i);
            } else {
                //Call out to CommCare Home
                Intent i = new Intent(getApplicationContext(), DispatchActivity.class);
                startActivity(i);
            }
        } else {
            Intent i = new Intent(getIntent());
            setResult(RESULT_OK, i);
        }
        finish();
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, AppInstallStatus statusMissing) {
        fail(NotificationMessageFactory.message(statusMissing, new String[]{null, ure.getResource().getDescriptor(), ure.getMessage()}), ure.isMessageUseful());
    }

    @Override
    public void failInvalidResource(InvalidResourceException e, AppInstallStatus statusMissing) {
        fail(NotificationMessageFactory.message(statusMissing, new String[]{null, e.resourceName, e.getMessage()}), true);
    }

    @Override
    public void failBadReqs(int code, String vRequired, String vAvailable, boolean majorIsProblem) {
        String versionMismatch = Localization.get("install.version.mismatch", new String[]{vRequired, vAvailable});

        String error;
        if (majorIsProblem) {
            error = Localization.get("install.major.mismatch");
        } else {
            error = Localization.get("install.minor.mismatch");
        }

        fail(NotificationMessageFactory.message(AppInstallStatus.IncompatibleReqs, new String[]{null, versionMismatch, error}), true);
    }

    @Override
    public void failUnknown(AppInstallStatus unknown) {
        fail(NotificationMessageFactory.message(unknown), false);
    }

    @Override
    public void updateResourceProgress(int done, int total, int phase) {
        // perform safe localization because the localization dictionary might
        // be the resource currently being installed.
        if (!CommCareApplication.instance().isConsumerApp()) {
            // Don't change the text on the progress dialog if we are showing the generic consumer
            // apps startup dialog
            String installProgressText =
                    Localization.getWithDefault("profile.found",
                            new String[]{"" + done, "" + total},
                            "Setting up app...");
            updateProgress(installProgressText, DIALOG_INSTALL_PROGRESS);
        }
        updateProgressBar(done, total, DIALOG_INSTALL_PROGRESS);
    }

    @Override
    public void failWithNotification(AppInstallStatus statusFailState) {
        fail(NotificationMessageFactory.message(statusFailState), true);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId != DIALOG_INSTALL_PROGRESS) {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareSetupActivity");
            return null;
        }
        if (isSingleAppBuild()) {
            return ConsumerAppsUtil.getGenericConsumerAppsProgressDialog(taskId, true);
        } else {
            return generateNormalInstallDialog(taskId);
        }
    }

    private CustomProgressDialog generateNormalInstallDialog(int taskId) {
        String title = Localization.get("updates.resources.initialization");
        String message = Localization.get("updates.resources.profile");
        CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);

        CustomProgressDialog lastDialog = getCurrentProgressDialog();
        boolean isChecked = (lastDialog != null) && lastDialog.isChecked();
        String checkboxText = Localization.get("install.keep.trying");
        dialog.addCheckbox(checkboxText, isChecked);

        dialog.setCancelable(false);
        dialog.addProgressBar();
        return dialog;
    }

    @Override
    public void onStartInstallClicked() {
        if (lastInstallMode != INSTALL_MODE_OFFLINE && isNetworkNotConnected()) {
            failWithNotification(AppInstallStatus.NoConnection);
        } else {
            startResourceInstall();
        }
    }

    @Override
    public void onStopInstallClicked() {
        incomingRef = null;
        uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;
        uiStateScreenTransition();
    }

    public void setUiState(UiState newState) {
        uiState = newState;
        if (UiState.IN_URL_ENTRY.equals(uiState)) {
            lastInstallMode = INSTALL_MODE_URL;
        }
    }

    @Override
    public void downloadLinkReceived(String url) {
        if (url != null) {
            incomingRef = url;
            uiState = UiState.READY_TO_INSTALL;
            uiStateScreenTransition();
            Toast.makeText(this, Localization.get("menu.sms.ready"), Toast.LENGTH_LONG).show();
        }
        // Do not notify that url was null here because the install attempt was not user-triggered
    }

    @Override
    public void downloadLinkReceivedAutoInstall(String url) {
        if (url != null) {
            incomingRef = url;
            uiState = UiState.READY_TO_INSTALL;
            uiStateScreenTransition();
            startResourceInstall();
        } else {
            displayError(Localization.get("menu.sms.not.found"));
        }
    }

    @Override
    public void exceptionReceived(Exception e, boolean notify) {
        String errorMsg;
        if (e instanceof SignatureException) {
            e.printStackTrace();
            errorMsg = Localization.get("menu.sms.not.verified");
        } else if (e instanceof IOException) {
            e.printStackTrace();
            errorMsg = Localization.get("menu.sms.not.retrieved");
        } else {
            e.printStackTrace();
            errorMsg = Localization.get("notification.install.unknown.title");
        }
        if (notify && errorMsg != null) {
            displayError(errorMsg);
        }
    }

    /**
     * @return Is the build configured to automatically try to install an app
     * packaged up with the build without showing install options to the user.
     */
    private boolean isSingleAppBuild() {
        return BuildConfig.IS_SINGLE_APP_BUILD;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == SMS_PERMISSIONS_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.READ_SMS.equals(permissions[i]) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    scanSMSLinks();
                }
            }
        } else if (requestCode == Permissions.ALL_PERMISSIONS_REQUEST) {
            String[] requiredPerms = Permissions.getRequiredPerms();

            for (int i = 0; i < permissions.length; i++) {
                for (String requiredPerm : requiredPerms) {
                    if (requiredPerm.equals(permissions[i]) &&
                            grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        showMissingPermissionState();
                        return;
                    }
                }
            }
            // external storage perms were enabled, so setup temp storage,
            // which fails in application setup without external storage perms.
            CommCareApplication.instance().prepareTemporaryStorage();
            if (!isSingleAppBuild()) {
                uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;
                uiStateScreenTransition();
            }

            if (isSingleAppBuild()) {
                SingleAppInstallation.installSingleApp(this, DIALOG_INSTALL_PROGRESS);
            } else {
                // Since SMS asks for more permissions, call was delayed until here
                performSMSInstall(false);
            }
        }
    }

    private void showMissingPermissionState() {
        if (uiState != UiState.NEEDS_PERMS) {
            uiState = UiState.NEEDS_PERMS;
            uiStateScreenTransition();
        } else {
            InstallPermissionsFragment permFragment =
                    (InstallPermissionsFragment)getSupportFragmentManager().findFragmentById(R.id.setup_fragment_container);
            permFragment.updateDeniedState();
        }
    }

    private static String getAnalyticsParamForInstallMethod(int installModeCode) {
        switch (installModeCode) {
            case INSTALL_MODE_BARCODE:
                return AnalyticsParamValue.BARCODE_INSTALL;
            case INSTALL_MODE_OFFLINE:
                return AnalyticsParamValue.OFFLINE_INSTALL;
            case INSTALL_MODE_SMS:
                return AnalyticsParamValue.SMS_INSTALL;
            case INSTALL_MODE_URL:
                return AnalyticsParamValue.URL_INSTALL;
            default:
                return "";
        }
    }

    public void checkManagedConfiguration() {
        Log.d(TAG, "Checking managed configuration");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // Check for managed configuration
            RestrictionsManager restrictionsManager =
                    (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
            Bundle appRestrictions = restrictionsManager.getApplicationRestrictions();
            if (appRestrictions != null && appRestrictions.containsKey("profileUrl")) {
                Log.d(TAG, "Found managed configuration install URL "
                        + appRestrictions.getString("profileUrl"));
                incomingRef = appRestrictions.getString("profileUrl");
                lastInstallMode = INSTALL_MODE_MANAGED_CONFIGURATION;
                uiState = UiState.READY_TO_INSTALL;
                uiStateScreenTransition();
                startResourceInstall();
            }
        }
    }
}
