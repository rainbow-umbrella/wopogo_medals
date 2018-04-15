package com.rainbow_umbrella.wopogo_medals;

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
