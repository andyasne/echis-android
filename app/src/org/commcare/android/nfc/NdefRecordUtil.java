package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.nfc.NdefRecord;
import android.os.Build;
import android.util.Pair;

import org.javarosa.core.services.locale.Localization;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;

/**
 * Created by amstone326 on 9/8/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NdefRecordUtil {

    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    protected static final String READ_ERROR_UNSUPPORTED_KEY = "nfc.read.error.unsupported";
    protected static final String READ_ERROR_MISMATCH_KEY = "nfc.read.error.mismatch";

    protected static Pair<String,Boolean> readValueFromRecord(NdefRecord record,
                                                              String userSpecifiedType,
                                                              String userSpecifiedDomain) {
        switch (record.getTnf()) {
            case NdefRecord.TNF_WELL_KNOWN:
                return handleWellKnownTypeRecord(record, userSpecifiedType);
            case NdefRecord.TNF_EXTERNAL_TYPE:
                return handleExternalTypeRecord(record, userSpecifiedType, userSpecifiedDomain);
            default:
                return new Pair<>(READ_ERROR_UNSUPPORTED_KEY, false);
        }
    }

    private static Pair<String,Boolean> handleWellKnownTypeRecord(NdefRecord record,
                                                                  String userSpecifiedType) {
        if (Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)) {
            if (userSpecifiedType.equals("text")) {
                return new Pair<>(readValueFromTextRecord(record), true);
            } else {
                return new Pair<>(READ_ERROR_MISMATCH_KEY, false);
            }
        } else {
            return new Pair<>(READ_ERROR_UNSUPPORTED_KEY, false);
        }
    }

    private static Pair<String,Boolean> handleExternalTypeRecord(NdefRecord record,
                                                                 String userSpecifiedType,
                                                                 String userSpecifiedDomain) {
        String typeAsString = new String(record.getType(), UTF8_CHARSET);
        if (typeAsString.equals(userSpecifiedDomain + ":" + userSpecifiedType)) {
            return new Pair<>(new String(record.getPayload(), UTF8_CHARSET), true);
        } else {
            return new Pair<>(READ_ERROR_MISMATCH_KEY, false);
        }
    }

    private static String readValueFromTextRecord(NdefRecord textTypeRecord) {
        byte[] fullPayload = textTypeRecord.getPayload();
        // The payload includes a prefix denoting the language, so we need to parse that off
        int langBytesLength = fullPayload[0]; // status byte
        int lengthOfPrefix = langBytesLength + 1; // add 1 for the status byte itself
        byte[] payloadWithoutLang = Arrays.copyOfRange(fullPayload, lengthOfPrefix, fullPayload.length);
        return new String(payloadWithoutLang, UTF8_CHARSET);
    }

    protected static NdefRecord createNdefRecord(String userSpecifiedType,
                                                 String userSpecifiedDomain,
                                                 String payloadToWrite) {
        if (NfcActivity.isCommCareSupportedWellKnownType(userSpecifiedType)) {
            return createWellKnownTypeRecord(userSpecifiedType, payloadToWrite);
        } else {
            return createExternalRecord(userSpecifiedType, userSpecifiedDomain, payloadToWrite);
        }
    }

    private static NdefRecord createWellKnownTypeRecord(String type, String payload) {
        if (type.equals("text")) {
            return createTextRecord(payload);
        } else {
            throw new IllegalArgumentException(Localization.get("nfc.well.known.type.not.supported"));
        }
    }

    private static NdefRecord createTextRecord(String payload) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return NdefRecord.createTextRecord(null, payload);
        } else {
            return createTextRecord(payload, Locale.getDefault());
        }
    }

    // Copied from https://developer.android.com/guide/topics/connectivity/nfc/nfc.html#well-known-text
    public static NdefRecord createTextRecord(String payload, Locale locale) {
        byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));
        byte[] textBytes = payload.getBytes(UTF8_CHARSET);

        int utfBit = 0;
        char status = (char)(utfBit + langBytes.length);
        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte)status;

        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);

        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
    }

    private static NdefRecord createExternalRecord(String type, String domain, String payload) {
        return NdefRecord.createExternal(domain, type, payload.getBytes(UTF8_CHARSET));
    }
}
