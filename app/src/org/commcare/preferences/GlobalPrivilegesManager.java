package org.commcare.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import org.commcare.CommCareApplication;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;

import java.util.ArrayList;

/**
 * Created by amstone326 on 6/7/16.
 */
public class GlobalPrivilegesManager {

    private static final String GLOBAL_PRIVELEGES_FILENAME = "global-preferences-filename";

    public static final String PRIVILEGE_SUPERUSER = "dimagi_superuser";

    public static final ArrayList<String> allGlobalPrivilegesList = new ArrayList<>();
    static {
        allGlobalPrivilegesList.add(PRIVILEGE_SUPERUSER);
    }

    private static SharedPreferences getGlobalPrivilegesRecord() {
        return CommCareApplication._().getSharedPreferences(GLOBAL_PRIVELEGES_FILENAME,
                Context.MODE_PRIVATE);
    }

    /**
     *
     * @param username - the HQ web user associated with the privilege being granted
     */
    public static void enablePrivilege(String privilegeName, String username) {
        getGlobalPrivilegesRecord().edit().putBoolean(privilegeName, true).commit();
        GoogleAnalyticsUtils.reportPrivilegeEnabled(privilegeName, username);
    }

    public static void disablePrivilege(String privilegeName) {
        getGlobalPrivilegesRecord().edit().putBoolean(privilegeName, false).commit();
    }

    private static boolean isPrivilegeEnabled(String privilegeName) {
        return getGlobalPrivilegesRecord().getBoolean(privilegeName, false);
    }

    public static boolean isSuperuserPrivilegeEnabled() {
        return isPrivilegeEnabled(PRIVILEGE_SUPERUSER);
    }
}
