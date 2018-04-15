package com.rainbow_umbrella.wopogo_medals;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class CheckPermissionsActivity extends Activity {
    private static final String TAG = CheckPermissionsActivity.class.getSimpleName();

    private static final int REQUEST_MEDIA_PROJECTION           = 9001;
    private static final int REQUEST_SYSTEM_ALERT_PERMISSION    = 9002;
    private MediaProjectionManager mpm;

    private static final String STATE_SCREEN_CAP_RESULT_CODE = "screen_cap_result_code";
    private static final String STATE_SCREEN_CAP_RESULT_DATA = "screen_cap_result_data";

    public int mResultCode;
    public Intent mScreenCapResultData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt(STATE_SCREEN_CAP_RESULT_CODE);
            if (mResultCode != 0) {
                mScreenCapResultData = savedInstanceState.getParcelable(STATE_SCREEN_CAP_RESULT_DATA);
            }
        }

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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mScreenCapResultData != null) {
            outState.putInt(STATE_SCREEN_CAP_RESULT_CODE, mResultCode);
            outState.putParcelable(STATE_SCREEN_CAP_RESULT_DATA, mScreenCapResultData);
        }
    }

    private void launchMainService() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra(STATE_SCREEN_CAP_RESULT_CODE, mResultCode);
        mainIntent.putExtra(STATE_SCREEN_CAP_RESULT_DATA, mScreenCapResultData);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
        finish();
        startActivity(mainIntent);
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
            } else {
                Toast.makeText(this, "Sorry. Can't draw overlays without permission...",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled");
                Toast.makeText(this, "Sorry. Can't read medals without permission...",
                        Toast.LENGTH_SHORT).show();

                return;
            }
            mResultCode = resultCode;
            mScreenCapResultData = data;
            launchMainService();
        } else
            super.onActivityResult(requestCode, resultCode, data);
    }

};
