package org.commcare.tasks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareFormDumpActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.models.database.SqlStorage;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.ReflectionUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.ProcessIssues;
import org.commcare.views.widgets.MediaWidget;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author ctsims
 */
public abstract class DumpTask extends CommCareTask<String, String, Boolean, CommCareFormDumpActivity>{

    private Context c;
    private FormUploadResult[] results;
    private File dumpFolder;

    private static final String TAG = DumpTask.class.getSimpleName();

    public static final int BULK_DUMP_ID = 23456;

    public DumpTask(Context c) {
        this.c = c;
        taskId = DumpTask.BULK_DUMP_ID;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        //These will never get Zero'd otherwise
        c = null;
        results = null;
    }

    private static final String[] SUPPORTED_FILE_EXTS = {".xml", ".jpg", ".3gpp", ".3gp"};

    private FormUploadResult dumpInstance(File folder, SecretKeySpec key) throws FileNotFoundException {

        Logger.log(TAG, "Dumping form instance at folder: " + folder);

        File[] files = folder.listFiles(File::isFile);

        Logger.log(TAG, "Dumping files: " + Arrays.toString(files));

        File myDir = new File(dumpFolder, folder.getName());
        myDir.mkdirs();

        if(files == null) {
            //make sure external storage is available to begin with.
            String state = Environment.getExternalStorageState();
            if (!Environment.MEDIA_MOUNTED.equals(state)) {
                //If so, just bail as if the user had logged out.
                throw new SessionUnavailableException("External Storage Removed");
            } else {
                throw new FileNotFoundException("No directory found at: " + folder.getAbsoluteFile());
            }
        }

        //If we're listening, figure out how much (roughly) we have to send
        long bytes = 0;
        for (File file : files) {
            //Make sure we'll be sending it
            boolean supported = false;
            for (String ext : SUPPORTED_FILE_EXTS) {
                if (file.getName().endsWith(ext)) {
                    supported = true;
                    break;
                }
            }
            if (!supported) {
                continue;
            }

            bytes += file.length();
        }

        //this.startSubmission(submissionNumber, bytes);

        final Cipher decrypter = FormUploadUtil.getDecryptCipher(key);

        /* Encrypted files need to copied to the SD Card in their original form, reason
         * being the decryption key is associated with to the user and device and therefore
         * not available in the target device
         */
        for (File file : files) {
            try {
                if (file.getName().endsWith(".xml"))
                    FileUtil.copyFile(file, new File(myDir, file.getName()), decrypter, null);
                else if (file.getName().endsWith(MediaWidget.AES_EXTENSION))
                    FileUtil.copyFile(file, new File(myDir, MediaWidget.removeAESExtension(file.getName())), decrypter, null);
                else
                    FileUtil.copyFile(file, new File(myDir, file.getName()));
            } catch (IOException ie) {
                Logger.log(TAG, "Error copying file: " + file + " exception: " + ie.getMessage());
                publishProgress(("File writing failed: " + ie.getMessage()));
                return FormUploadResult.FAILURE;
            }
        }
        return FormUploadResult.FULL_SUCCESS;
    }

    @SuppressLint("NewApi")
    @Override
    protected Boolean doTaskBackground(String... params) {

        // ensure that SD is available, writable, and not emulated

        boolean mExternalStorageAvailable = false;
        boolean mExternalStorageWriteable = false;

        boolean mExternalStorageEmulated = ReflectionUtil.mIsExternalStorageEmulatedHelper();

        String state = Environment.getExternalStorageState();

        ArrayList<String> externalMounts = FileUtil.getExternalMounts();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // We can read and write the media
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // We can only read the media
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            // Something else is wrong. It may be one of many other states, but all we need
            //  to know is we can neither read nor write
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }

        if (!mExternalStorageAvailable) {
            publishProgress(Localization.get("bulk.form.sd.unavailable"));
            return false;
        }
        if (!mExternalStorageWriteable) {
            publishProgress(Localization.get("bulk.form.sd.unwritable"));
            return false;
        }
        if (mExternalStorageEmulated && externalMounts.size() == 0) {
            publishProgress(Localization.get("bulk.form.sd.emulated"));
            return false;
        }

        String folderName = Localization.get("bulk.form.foldername");
        String directoryPath = FileUtil.getDumpDirectory(c);

        if (directoryPath == null) {
            publishProgress(Localization.get("bulk.form.sd.emulated"));
            return false;
        }

        File dumpDirectory = new File(directoryPath+"/"+folderName);

        if (dumpDirectory.exists() && dumpDirectory.isDirectory()) {
            dumpDirectory.delete();
        }

        dumpDirectory.mkdirs();

        SqlStorage<FormRecord> storage = CommCareApplication.instance().getUserStorage(FormRecord.class);
        Vector<Integer> ids = StorageUtils.getUnsentOrUnprocessedFormIdsForCurrentApp(storage);

        if (ids.size() > 0) {
            FormRecord[] records = new FormRecord[ids.size()];
            for (int i = 0; i < ids.size(); ++i) {
                records[i] = storage.read(ids.elementAt(i));
            }

            dumpFolder = dumpDirectory;

            results = new FormUploadResult[records.length];
            for (int i = 0; i < records.length; ++i) {
                //Assume failure
                results[i] = FormUploadResult.FAILURE;
            }

            publishProgress(Localization.get("bulk.form.start"));

            for (int i = 0; i < records.length; ++i) {
                FormRecord record = records[i];
                try {
                    //If it's unsent, go ahead and send it
                    if (FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
                        File folder;
                        try {
                            folder = new File(record.getFilePath()).getCanonicalFile().getParentFile();
                        } catch (IOException e) {
                            Logger.log(LogTypes.TYPE_ERROR_WORKFLOW,
                                    "Bizarre. Exception just getting the file reference. Not removing." + getExceptionText(e));
                            continue;
                        }

                        //Good!
                        //Time to Send!
                        try {
                            results[i] = dumpInstance(folder, new SecretKeySpec(record.getAesKey(), "AES"));

                        } catch (FileNotFoundException e) {
                            if (CommCareApplication.instance().isStorageAvailable()) {
                                //If storage is available generally, this is a bug in the app design
                                Logger.log(LogTypes.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
                            } else {
                                //Otherwise, the SD card just got removed, and we need to bail anyway.
                                CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
                                break;
                            }
                            continue;
                        }

                        // Check for success
                        if (results[i] == FormUploadResult.FULL_SUCCESS) {
                            record.logPendingDeletion(TAG, "we are performing a form dump to external storage");
                            FormRecordCleanupTask.wipeRecord(record);
                            publishProgress(Localization.get("bulk.form.dialog.progress",new String[]{""+i, ""+results[i]}));
                        }
                    }
                } catch (SessionUnavailableException sue) {
                    this.cancel(false);
                    return false;
                } catch (Exception e) {
                    //Just try to skip for now. Hopefully this doesn't wreck the model :/
                    Logger.log(LogTypes.TYPE_ERROR_DESIGN, "Totally Unexpected Error during form dump task" + getExceptionText(e));
                }
            }

            FormUploadResult result = FormUploadResult.getWorstResult(results);
            return result == FormUploadResult.FULL_SUCCESS;

        } else {
            publishProgress(Localization.get("bulk.form.no.unsynced"));
            return false;
        }
    }

    private String getExceptionText (Exception e) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(bos));
            return new String(bos.toByteArray());
        } catch(Exception ex) {
            return null;
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
    }

}
