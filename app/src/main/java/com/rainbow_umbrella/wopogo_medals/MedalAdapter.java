package com.rainbow_umbrella.wopogo_medals;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.View.OnFocusChangeListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.TextView;
import android.support.v4.content.ContextCompat;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayList;
/*
 * Adapter to provide the current values of medals to be displayed as a list view. The values are
 * edittable.
 */
public class MedalAdapter extends BaseAdapter {
    private Context mContext;
    private LayoutInflater mInflater;
    private ArrayList<Medal> mDataSource;
    private SharedPreferences mSharedPreferences;

    public MedalAdapter(Context context, ArrayList<Medal> items, SharedPreferences sharedPreferences) {
        mContext = context;
        mDataSource = items;
        mSharedPreferences = sharedPreferences;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return mDataSource.size();
    }

    @Override
    public Object getItem(int position) {
        return mDataSource.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get view for row item
        View rowView = mInflater.inflate(R.layout.list_item_medal, parent, false);
        TextView nameTextView =
                (TextView) rowView.findViewById(com.rainbow_umbrella.wopogo_medals.R.id.medal_list_name);

        EditText valueTextView =
                (EditText) rowView.findViewById(com.rainbow_umbrella.wopogo_medals.R.id.medal_list_value);
        Medal medal = (Medal) getItem(position);

        nameTextView.setText(medal.mName);
        valueTextView.setTag(position);
        valueTextView.setOnFocusChangeListener(new OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                /* When focus is lost check that the text field
                 * has valid values.
                 */
                EditText edit = (EditText) v;
                int index = (Integer) v.getTag();
                if (hasFocus) {
                    if (edit.getText().toString().equals("?")) {
                        edit.setText("");
                        edit.setTextColor(ContextCompat.getColor(mContext, R.color.green));
                    }
//                    showKeyboard(v);
                } else {
                    Medal currentMedal = mDataSource.get(index);
                    if (edit.getText().toString().equals("")) {
                        edit.setText("?");
                        edit.setTextColor(ContextCompat.getColor(mContext, R.color.red));
                        mDataSource.get(index).mValue = -1;
                    } else {
                        edit.setTextColor(ContextCompat.getColor(mContext, R.color.green));
                        currentMedal.mValue = Integer.parseInt(edit.getText().toString());
                    }
                    mSharedPreferences.edit().putInt(currentMedal.mName, currentMedal.mValue).apply();
//                    hideKeyboard(v);
                }
            }
        });
        /*
        valueTextView.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                ListView lv = (ListView)parent;
                if(actionId == EditorInfo.IME_ACTION_NEXT &&
                        lv != null &&
                        position >= lv.getLastVisiblePosition() &&
                        position != mDataSource.size() - 1) {  //audit object holds the data for the adapter
                    lv.smoothScrollToPosition(position + 1);
                    lv.postDelayed(new Runnable() {
                        public void run() {
                            //TODO: Need to work out how to find the next edit box.
                            TextView nextField = (TextView)holderf.qtyf.focusSearch(View.FOCUS_DOWN);
                            if(nextField != null) {
                                nextField.requestFocus();
                            }
                        }
                    }, 200);
                    return true;
                }
                return false;
            }
        });
        */
        if (medal.mValue == -1) {
            valueTextView.setText("?");
            valueTextView.setTextColor(ContextCompat.getColor(mContext, R.color.red));
        } else {
            valueTextView.setText(Integer.toString(medal.mValue));
            valueTextView.setTextColor(ContextCompat.getColor(mContext, R.color.green));
        }
        return rowView;
    }

    public void hideKeyboard(View view) {
        InputMethodManager inputMethodManager =(InputMethodManager)mContext.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void showKeyboard(View view) {
        InputMethodManager inputMethodManager =(InputMethodManager)mContext.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInput(view, 0);
    }
}

