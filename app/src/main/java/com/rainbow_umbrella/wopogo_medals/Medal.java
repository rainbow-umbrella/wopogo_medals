package com.rainbow_umbrella.wopogo_medals;

/*
 * Basic class to allow an integer value to be associated with a string key. Note, the current keys
 * are not consistent: within the application the key values are the strings used by the Pokemon go.
 * These are then translated into the key values for the wopogo website before upload.
 */

public class Medal {

    public String mName;
    public int mValue;

    public Medal(String name) {
        mName = name;
        mValue = -1;
    }
    public Medal(String name, int value) {
        mName = name;
        mValue = value;
    }
}
