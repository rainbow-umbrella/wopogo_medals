package com.rainbow_umbrella.wopogo_medals;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import com.google.android.gms.common.api.CommonStatusCodes;

import java.util.ArrayList;

public class UserDetailsActivity extends Activity implements View.OnClickListener {
    private static final String TAG = UserDetailsActivity.class.getSimpleName();

    EditText mUserNameEdit;
    EditText mPasswordEdit;
    AutoCompleteTextView mTrainerEdit;
    String[] mPreviousTrainers;
    ArrayList<String> mPreviousTrainerList;

    SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_prefs_layout);
        mSharedPreferences = getSharedPreferences(getString(R.string.shared_prefs_user), MODE_PRIVATE);
        setupUserInterface();
        initialiseFields();

        findViewById(R.id.cancel_button).setOnClickListener(this);
        findViewById(R.id.ok_button).setOnClickListener(this);


    }

    private void setupUserInterface() {
        mUserNameEdit = (EditText)findViewById(R.id.editUserName);
        mPasswordEdit = (EditText)findViewById(R.id.editPassword);
        mTrainerEdit = (AutoCompleteTextView) findViewById(R.id.editTrainer);

    }


    private OnClickListener mThisButtonListener = new OnClickListener() {
        public void onClick(View v) {
            mTrainerEdit.setText (((Button)v).getText().toString());
        }
    };

    public void onClick(View v) {
        if (v.getId() == R.id.ok_button) {
            storeFields();
            Intent response = new Intent();
            setResult(CommonStatusCodes.SUCCESS, response);
            finish();
        } else if (v.getId() == R.id.cancel_button) {
            Intent response = new Intent();
            setResult(CommonStatusCodes.SUCCESS, response);
            finish();
        }
    }

    public void initialiseFields() {


        mUserNameEdit.setText(mSharedPreferences.getString(getString(R.string.field_username), ""), TextView.BufferType.EDITABLE);
        mPasswordEdit.setText(mSharedPreferences.getString(getString(R.string.field_password), ""), TextView.BufferType.EDITABLE);
        String currentTrainer = mSharedPreferences.getString(getString(R.string.field_trainer), "");
        String previousTrainersString = mSharedPreferences.getString(getString(R.string.field_previous_trainers), "");
        mPreviousTrainers = previousTrainersString.split(",");
        mPreviousTrainerList = new ArrayList<String>();
        for (int i = 0; i < mPreviousTrainers.length; i++) {
            mPreviousTrainerList.add(mPreviousTrainers[i]);
        }
        ArrayAdapter<String> autoCompleteTrainerAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, mPreviousTrainerList);
        mTrainerEdit.setAdapter(autoCompleteTrainerAdapter);
        mTrainerEdit.setText(currentTrainer, true);
        mTrainerEdit.setThreshold(0);
        mTrainerEdit.setSelectAllOnFocus(true);
    }

    public void storeFields() {
        String thisTrainer = mTrainerEdit.getText().toString();
        String previousTrainers = thisTrainer;
        int count = 1;
        final int MAX_PREVIOUS_TRAINERS = 10;
        for (int i = 0; i < mPreviousTrainers.length; i++) {
            if (!mPreviousTrainers[i].equals(thisTrainer)) {
                previousTrainers += "," + mPreviousTrainers[i];
                count++;
            }
            if (count == MAX_PREVIOUS_TRAINERS) {
                break;
            }
        }

        mSharedPreferences.edit().
                putString(getString(R.string.field_username), mUserNameEdit.getText().toString()).
                putString(getString(R.string.field_password), mPasswordEdit.getText().toString()).
                putString(getString(R.string.field_trainer), mTrainerEdit.getText().toString()).
                putString(getString(R.string.field_previous_trainers), previousTrainers).
                apply();
    }
}
