package com.rainbow_umbrella.wopogo_medals;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import com.google.android.gms.common.api.CommonStatusCodes;

import java.util.ArrayList;

/*
 * Activity to allow entry of the user details (username, password and trainer). Previous values of
 * trainer are also stored to allow auto completion.
 */
public class UserDetailsActivity extends Activity implements View.OnClickListener {
    private static final String TAG = UserDetailsActivity.class.getSimpleName();
    private static final String MIMETYPE_TEXT_PLAIN = new String("text/plain");
    EditText mUserNameEdit;
    EditText mPasswordEdit;
    EditText mApiKeyEdit;
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
        findViewById(R.id.paste_button).setOnClickListener(this);

    }

    private void setupUserInterface() {
        mUserNameEdit = (EditText)findViewById(R.id.editUserName);
        mPasswordEdit = (EditText)findViewById(R.id.editPassword);
        mTrainerEdit = (AutoCompleteTextView) findViewById(R.id.editTrainer);
        mApiKeyEdit = (EditText)findViewById(R.id.editApiKey);

    }

    public void onClick(View v) {
        if (v.getId() == R.id.ok_button) {
            Intent response = new Intent();
            String oldTrainer = mSharedPreferences.getString(getString(R.string.field_trainer), "");
            String newTrainer = mTrainerEdit.getText().toString();
            if (newTrainer != oldTrainer) {
                response.putExtra(getString(R.string.field_trainer), newTrainer);
                response.putExtra(getString(R.string.field_trainer_has_changed), true);
            } else {
                response.putExtra(getString(R.string.field_trainer_has_changed), false);
            }
            storeFields();
            setResult(CommonStatusCodes.SUCCESS, response);
            finish();
        } else if (v.getId() == R.id.cancel_button) {
            Intent response = new Intent();
            setResult(CommonStatusCodes.CANCELED, response);
            finish();
        } else if (v.getId() == R.id.paste_button) {
            String textToPaste = null;

            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            if (clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();

                // if you need text data only, use:
                if (clip.getDescription().hasMimeType(MIMETYPE_TEXT_PLAIN))
                    // WARNING: The item could cantain URI that points to the text data.
                    // In this case the getText() returns null and this code fails!
                    textToPaste = clip.getItemAt(0).getText().toString();

                // or you may coerce the data to the text representation:
                textToPaste = clip.getItemAt(0).coerceToText(this).toString();
            }

            if (!TextUtils.isEmpty(textToPaste))
                ((TextView) findViewById(R.id.editApiKey)).setText(textToPaste);
        }
    }

    public void initialiseFields() {


        mUserNameEdit.setText(mSharedPreferences.getString(getString(R.string.field_username), ""),
                TextView.BufferType.EDITABLE);
        mPasswordEdit.setText(mSharedPreferences.getString(getString(R.string.field_password), ""),
                TextView.BufferType.EDITABLE);
        mApiKeyEdit.setText(mSharedPreferences.getString(getString(R.string.field_api_key), ""),
                TextView.BufferType.EDITABLE);
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
                putString(getString(R.string.field_api_key), mApiKeyEdit.getText().toString()).
                putString(getString(R.string.field_previous_trainers), previousTrainers).
                apply();
    }
}
