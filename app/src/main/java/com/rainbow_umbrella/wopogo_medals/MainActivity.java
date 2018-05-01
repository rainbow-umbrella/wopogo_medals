/*
 * WoPoGo medals.
 *
 * Original code modified from the Google's vision project: https://developers.google.com/vision/text-overview
 *
 */

package com.rainbow_umbrella.wopogo_medals;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.media.projection.MediaProjectionManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.support.v7.widget.Toolbar;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.TextView;
import android.net.Uri;
import com.android.volley.RequestQueue;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.CommonStatusCodes;

/**
 * Main activity coordinates manual entry of values, reading values from the game itself and also
 * reading from images. It then provides access to the upload of the data to the server.
 *
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static MainActivity mInstance;
    public static MainActivity instance() {
        return mInstance;
    }

    /**
     * User interface objects
     */
    private TextView mTextValue;
    private ListView mMedalListView;
    private ArrayList<Medal> mMedalList;
    private Map<String, Integer> mPreviousMedalList;
    private Map<String, String> mMedalDefs;
    private MedalMatcher mMedalMatcher;
    private MedalAdapter mMedalAdapter;
    private Toolbar mToolbar;
    public RequestQueue mRequestQueue;

    private static final int REQUEST_OCR_IMAGE                  = 9000;
    private static final int REQUEST_MEDIA_PROJECTION           = 9001;
    private static final int REQUEST_SYSTEM_ALERT_PERMISSION    = 9002;
    private static final int REQUEST_USER_PREFS                 = 9003;
    private static final int REQUEST_UPLOAD_DATA                = 9004;

    private OverlayService mOverlayService;
    private MediaProjectionManager mpm;

    public int mResultCode;
    public Intent mScreenCapResultData = null;
    private boolean mPickingPictures = false;
    private SharedPreferences mSharedPrefs;
    private SharedPreferences mPreviousSharedPrefs;
    private SharedPreferences mUserSharedPrefs;
    private String mCurrentTrainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInstance = this;

        Bundle b = getIntent().getExtras();
        if (b != null) {
            mResultCode = b.getInt(CheckPermissionsActivity.STATE_SCREEN_CAP_RESULT_CODE);
            mScreenCapResultData = b.getParcelable(CheckPermissionsActivity.STATE_SCREEN_CAP_RESULT_DATA);
        }
        // Read previous state of medals. Must be done before setting up the user interface as the
        // list adapter uses it to store any updates.
        mSharedPrefs =  getSharedPreferences(getString(R.string.shared_prefs_current), MODE_PRIVATE);
        mUserSharedPrefs = getSharedPreferences(getString(R.string.shared_prefs_user), MODE_PRIVATE);

        mCurrentTrainer = mUserSharedPrefs.getString(getString(R.string.field_trainer), "");
        mPreviousSharedPrefs = getSharedPreferences(
                getString(R.string.shared_prefs_previous, mCurrentTrainer), MODE_PRIVATE);
        readMedalList();
        loadPreviousSharedPrefs();
        // Set up UI.
        setupUserInterface();

        /**
         * Create object used to match text blocks to medals with values.
         */
        mMedalMatcher = new MedalMatcher(mMedalList);
        mRequestQueue = Volley.newRequestQueue(this);
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
            if (hasScreenCapPermission()) {
                // Launch service right away - the user has already previously granted permission
                launchMainService();
            } else {
                requestVideoPermission();
            }
        }
        else {
            // Check that the user has granted permission, and prompt them if not
            checkDrawOverlayPermission();
        }
    }

    @Override
    public void onDestroy() {
        doUnbindService();
        Intent overlayService = new Intent(this, OverlayService.class);
        stopService(overlayService);
        super.onDestroy();
    }

    private void readMedalList() {
        Context context = this;
        InputStream inputStream = context.getResources().openRawResource(R.raw.medals);
        String jsonString = new Scanner(inputStream).useDelimiter("\\A").next();
        mMedalList = new ArrayList<Medal>();
        mMedalDefs = new HashMap<String, String>();
        mPreviousMedalList = new HashMap<String, Integer>();
        try {
            JSONArray medalListJson = new JSONArray(jsonString);
            for(int i = 0; i < medalListJson.length(); i++){
                JSONObject jObject = medalListJson.getJSONObject(i);
                mMedalList.add(new Medal(jObject.getString("label"), mSharedPrefs.getInt(jObject.getString("field"), -1)));
                mMedalDefs.put(jObject.getString("label"), jObject.getString("field"));
            }
        } catch (JSONException e)
        {
        }
    }

    private void setupUserInterface() {
        setContentView(R.layout.activity_main);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        mTextValue = (TextView)findViewById(R.id.text_value);

        mMedalListView = (ListView) findViewById(R.id.medal_list_view);
        mMedalAdapter = new MedalAdapter(this, mMedalList, mSharedPrefs, mMedalDefs, mPreviousMedalList);
        mMedalListView.setAdapter(mMedalAdapter);

    }

    private void loadPreviousSharedPrefs() {
        mPreviousMedalList.clear();
        Map<String, ?> previousStoredMedals = mPreviousSharedPrefs.getAll();
        for (String key : previousStoredMedals.keySet()) {
            mPreviousMedalList.put(key, mPreviousSharedPrefs.getInt(key, -1));
        }
    }

    private void requestVideoPermission() {
        // Used to do screen captures.
        Log.i(TAG, "Requesting video permission");
        mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mpm.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    private boolean hasScreenCapPermission() {
        return mScreenCapResultData != null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mScreenCapResultData != null) {
            outState.putInt(CheckPermissionsActivity.STATE_SCREEN_CAP_RESULT_CODE, mResultCode);
            outState.putParcelable(CheckPermissionsActivity.STATE_SCREEN_CAP_RESULT_DATA, mScreenCapResultData);
        }
    }

    private void launchMainService() {
        Intent svc = new Intent(this, OverlayService.class);
        startService(svc);
        doBindService();
    }

    public void checkDrawOverlayPermission() {

        // Checks if app already has permission to draw overlays
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {

            // If not, form up an Intent to launch the permission request
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));

            // Launch Intent, with the supplied request code
            startActivityForResult(intent, REQUEST_SYSTEM_ALERT_PERMISSION);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        super.onCreateOptionsMenu(menu);
        MenuInflater mymenu = getMenuInflater();
        mymenu.inflate(R.menu.main_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear_button: {
                // Get confirmation before clearing all medal values.
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Clear all medal values ?");
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        clearAllMedals();

                    }
                });
                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
                return true;
            }
            case R.id.preferences_button: {
                // Allow user preferences to be changed.
                Intent intent = new Intent(this, UserDetailsActivity.class);
                startActivityForResult(intent, REQUEST_USER_PREFS);
                return true;
            }
            case R.id.goto_game: {
                Intent poGoIntent = getPackageManager().getLaunchIntentForPackage("com.nianticlabs.pokemongo");
                if (poGoIntent == null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    //builder.setTitle(R.string.title_activity_main);
                    builder.setMessage("Pokemon Go is not installed");
                    builder.setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                        }
                    });
                    AlertDialog alert = builder.create();
                    alert.show();
                } else {
                    poGoIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(poGoIntent);
                }
                return true;
            }
            case R.id.load_image:
                // Launch Ocr from images.
                mPickingPictures = true;
                Intent intent = new Intent(this, OcrImageActivity.class);
                startActivityForResult(intent, REQUEST_OCR_IMAGE);
                return true;
            case R.id.upload: {
                if (checkUserDetails()) {
                    confirmUpload();
                } else {

                }
                return true;
            }
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    private boolean checkUserDetails() {
        boolean success = true;
        String failureString = new String();
        if (mUserSharedPrefs.getString(getString(R.string.field_api_key), "").equals("")) {
            success = false;
            failureString = getString(R.string.error_api_key_missing) + System.lineSeparator();

        }
        if (mUserSharedPrefs.getString(getString(R.string.field_trainer), "").equals("")) {
            success = false;
            failureString += getString(R.string.error_trainer_missing) + System.lineSeparator();
        }
        if (!success) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            //builder.setTitle(R.string.title_activity_main);
            builder.setMessage(failureString);
            builder.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    // User details missing or invalid so allow them to be changed.
                    Intent intentUserDetails2 = new Intent(MainActivity.this, UserDetailsActivity.class);
                    startActivityForResult(intentUserDetails2, REQUEST_USER_PREFS);

                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }
        return success;
    }

    private void confirmUpload() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(String.format("Upload trainer \"%s\" ?",
                mCurrentTrainer));
        ArrayList<String> valuesGoneDown = new ArrayList<String>();
        for (int i = 0; i < mMedalList.size(); i++) {
            String key = mMedalDefs.get(mMedalList.get(i).mName);
            if (mPreviousMedalList.containsKey(key) && mMedalList.get(i).mValue != -1) {
                if (mMedalList.get(i).mValue < mPreviousMedalList.get(key).intValue()) {
                    valuesGoneDown.add(mMedalList.get(i).mName);
                }
            }
        }
        if (valuesGoneDown.size() > 0) {
            String msgText = "WARNING: values have gone down";
            for (int i = 0; i < valuesGoneDown.size(); i++) {
                msgText += "\n    " + valuesGoneDown.get(i);
            }
            builder.setMessage(msgText);
        }
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                uploadData();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    Messenger mService = null;
    /** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case OverlayService.MSG_SET_VALUE:
                    if (msg.arg1 != 0) {
                        /*
                        Toast.makeText(MainActivity.this,
                                "Service response: " + Integer.toString(msg.arg1),
                                Toast.LENGTH_SHORT).show();
                                */
                    } else {
                        //TODO: Debug only
                        /*
                        Toast.makeText(MainActivity.this,
                                "Service response: " + msg.obj.toString(),
                                Toast.LENGTH_SHORT).show();
                                */
                        Log.d(TAG, "Response: " + msg.obj.toString());
                        if (!checkAndSetMedals(msg.obj.toString())) {
                            Toast.makeText(MainActivity.this, "Failed to find any medals.",
                                    Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Failed to find medal: " + msg.obj.toString());
                        }
                    }
                    break;

                    case OverlayService.MSG_CLOSE_APP:
                        finishAndRemoveTask();
                        break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);
/*
            Toast.makeText(MainActivity.this,
                    "Attached",
                    Toast.LENGTH_SHORT).show();
*/
            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null,
                        OverlayService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);

            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }

        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;

            Toast.makeText(MainActivity.this,
                    "Disconnected",
                    Toast.LENGTH_SHORT).show();

        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        bindService(new Intent(MainActivity.this,
                OverlayService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
/*
        Toast.makeText(MainActivity.this,
                "Binding",
                Toast.LENGTH_SHORT).show();
*/
    }

    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null,
                            OverlayService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
/*
            Toast.makeText(MainActivity.this,
                    "Unbinding",
                    Toast.LENGTH_SHORT).show();
*/
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SYSTEM_ALERT_PERMISSION) {
            // Double-check that the user granted it, and didn't just dismiss the request
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
                if (hasScreenCapPermission()) {
                    launchMainService();
                } else {
                    requestVideoPermission();
                }
            }
            else {
                Toast.makeText(this, "Sorry. Can't draw overlays without permission...",
                        Toast.LENGTH_SHORT).show();
            }
        }
        else if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled");
                return;
            }
            mResultCode = resultCode;
            mScreenCapResultData = data;
            launchMainService();
        } else if (requestCode == REQUEST_OCR_IMAGE) {
            mPickingPictures = false;
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    ArrayList<String> texts = data.getStringArrayListExtra(OcrImageActivity.TextBlockObject);
                    boolean found = false;
                    for (int i = 0; i < texts.size(); i++) {
                        if (checkAndSetMedals(texts.get(i))) {
                            found = true;
                        }
                    }
                    if (!found && texts.size() > 1) {
                        Toast.makeText(this, getText(R.string.toast_failed_to_find), Toast.LENGTH_SHORT).show();
                    }
                    Log.d(TAG, "Text read: " + texts);
                } else {
                    Log.d(TAG, "No Text captured, intent data is null");
                }
            } else {
                Toast.makeText(this, "OCR reader failed: " +
                        String.format(getString(R.string.ocr_error),
                        CommonStatusCodes.getStatusCodeString(resultCode)), Toast.LENGTH_SHORT).show();
            }
        }
        else if (requestCode == REQUEST_UPLOAD_DATA) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    try {
                        JSONObject response = new JSONObject(data.getStringExtra("response"));
                        boolean success = response.getBoolean("success");
                        String msgResponse = response.getString("response");
                        if (success) {
                            msgResponse = "Success: " + msgResponse;
                        } else {
                            msgResponse = "Failed: " + msgResponse;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        //builder.setTitle(R.string.title_activity_main);
                        builder.setMessage(msgResponse);
                        builder.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                            }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                        if (success) {
                            updatePreviousValues();
                        }
                    } catch (JSONException e) {

                    }
                }
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Failed: " + data.getStringExtra("response"));
                builder.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();

            }
            mRequestQueue. getCache().clear();
        }
        else if (requestCode == REQUEST_USER_PREFS) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null && data.getBooleanExtra(getString(R.string.field_trainer_has_changed), false)) {
                    mCurrentTrainer = data.getStringExtra(getString(R.string.field_trainer));
                    mPreviousSharedPrefs = getSharedPreferences(getString(R.string.shared_prefs_previous, mCurrentTrainer), MODE_PRIVATE);
                    loadPreviousSharedPrefs();
                    mMedalAdapter.notifyDataSetChanged();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }


    }

    private void updatePreviousValues() {
        Editor editor = mPreviousSharedPrefs.edit();
        for (int i = 0; i < mMedalList.size(); i++) {
            if (mMedalList.get(i).mValue != -1) {
                String key = mMedalDefs.get(mMedalList.get(i).mName);
                mPreviousMedalList.put(key, mMedalList.get(i).mValue);
                editor.putInt(key, mMedalList.get(i).mValue);
            }
        }
        editor.apply();
        mMedalAdapter.notifyDataSetChanged();
    }

    public boolean checkAndSetMedals(String text) {
        boolean result = false;
        Medal medal = mMedalMatcher.getMatch(text);
        if (medal != null) {
            for (int i = 0; i < mMedalList.size(); i++) {
                if (mMedalList.get(i).mName.equals(medal.mName)) {
                    mMedalList.get(i).mValue = medal.mValue;
                    mMedalAdapter.notifyDataSetChanged();
                    mSharedPrefs.edit().putInt(mMedalDefs.get(medal.mName), medal.mValue).apply();
                    result = true;
                    Toast.makeText(this,
                            getString(R.string.toast_updated_medal, medal.mName, medal.mValue),
                            Toast.LENGTH_LONG).show();
                }
            }
        }
        return result;
    }

    private void clearAllMedals() {
        for (Medal medal : mMedalList) {
            medal.mValue = -1;
        }
        mMedalAdapter.notifyDataSetChanged();
        mSharedPrefs.edit().clear().apply();
    }

    private void uploadData() {
        Intent intent = new Intent(this, SendMedalsActivity.class);
        intent.putExtra(getString(R.string.field_username),
                mUserSharedPrefs.getString(getString(R.string.field_username), ""));
        intent.putExtra(getString(R.string.field_password),
                mUserSharedPrefs.getString(getString(R.string.field_password), ""));
        intent.putExtra(getString(R.string.field_trainer),
                mCurrentTrainer);
        intent.putExtra(getString(R.string.field_api_key),
                mUserSharedPrefs.getString(getString(R.string.field_api_key), ""));
        ArrayList<String> medalNames = new ArrayList<String>();
        ArrayList<Integer> medalValues = new ArrayList<Integer>();
        for (int i = 0; i < mMedalList.size(); i++) {
            String key = mMedalDefs.get(mMedalList.get(i).mName);
            int newValue = mMedalList.get(i).mValue;
            int oldValue;
            if (mPreviousMedalList.containsKey(key)) {
                oldValue = mPreviousMedalList.get(key);
            } else {
                oldValue = -1;
            }
            if (newValue != -1 && newValue != oldValue) {
                medalNames.add(mMedalDefs.get(mMedalList.get(i).mName));
                medalValues.add(mMedalList.get(i).mValue);
            }
        }
        intent.putStringArrayListExtra("medalNames", medalNames);
        intent.putIntegerArrayListExtra("medalValues", medalValues);
        intent.putExtra("queue", mRequestQueue.toString());
        startActivityForResult(intent, REQUEST_UPLOAD_DATA);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mIsBound) {
            try {
                Message msg = Message.obtain(null,
                        OverlayService.MSG_HIDE_BUTTON);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (Exception e) {

            }
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.FLAG_EDITOR_ACTION || keyCode == KeyEvent.KEYCODE_ENTER) {
            View current = getCurrentFocus();
            if (current != null) {
                current.clearFocus();
                return true;
            }
            View view = findViewById(android.R.id.content);
            if ( view != null) {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
        return false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mIsBound && !mPickingPictures) {
            try {
                Message msg = Message.obtain(null,
                        OverlayService.MSG_SHOW_BUTTON);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (Exception e) {

            }
        }

    }
    @Override
    public void onBackPressed() {
        // TODO Auto-generated method stub
        Intent overlayService = new Intent(this, OverlayService.class);
        stopService(overlayService);
        super.onBackPressed();
        finishAndRemoveTask();
    }
}
