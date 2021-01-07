package org.commcare.commcaresupportlibrary.identity.model;

import android.os.Parcel;
import android.os.Parcelable;

@SuppressWarnings("unused")
public class RegistrationResult implements Parcelable {

    private String guid;

    /**
     * Result of the identity enrollment workflow
     *
     * @param guid Global unique id generated by the Identity Provder as part of the registration/enrollment workflow
     */
    public RegistrationResult(String guid) {
        this.guid = guid;
    }

    protected RegistrationResult(Parcel in) {
        guid = in.readString();
    }

    public static final Creator<RegistrationResult> CREATOR = new Creator<RegistrationResult>() {
        @Override
        public RegistrationResult createFromParcel(Parcel in) {
            return new RegistrationResult(in);
        }

        @Override
        public RegistrationResult[] newArray(int size) {
            return new RegistrationResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(guid);
    }

    public String getGuid() {
        return guid;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RegistrationResult)) {
            return false;
        }
        RegistrationResult other = (RegistrationResult)o;
        if (!guid.equals(other.guid)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = guid.hashCode();
        return hash;
    }
}
