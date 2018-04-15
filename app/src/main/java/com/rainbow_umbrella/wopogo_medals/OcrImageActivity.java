/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rainbow_umbrella.wopogo_medals;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;
import java.io.InputStream;
import java.io.BufferedInputStream;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.text.TextRecognizer;

import java.util.ArrayList;

/**
 * Activity for the multi-tracker app.  This app detects text and displays the value with the
 * rear facing camera. During detection overlay graphics are drawn to indicate the position,
 * size, and contents of each TextBlock.
 */
public final class OcrImageActivity extends Activity implements OcrTask.IOwner {
    private static final String TAG = "OcrImageActivity";

    // Intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // Permission request codes need to be < 256
    private static final int PICK_FROM_GALLERY = 3;

    // Constants used to pass extra data in the intent
    public static final String TextBlockObject = "ArrayList";
    public static final String ImageUrlObject = "Url";

    private TextRecognizer mTextRecognizer;

    private ArrayList<String> mResults;

    /**
     * Initializes the UI and creates the detector pipeline.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        //setContentView(R.layout.ocr_capture);

        mResults = new ArrayList<String>();
        // read parameters from the intent used to launch the activity.

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        try {

            Context context = getApplicationContext();
            // A text recognizer is created to find text.  An associated processor instance
            // is set to receive the text recognition results and display graphics for each text block
            // on screen.
            mTextRecognizer = new TextRecognizer.Builder(context).build();

            //textRecognizer.setProcessor(new OcrDetectorProcessor(mGraphicOverlay));

            if (!mTextRecognizer.isOperational()) {
                // Note: The first time that an app using a Vision API is installed on a
                // device, GMS will download a native libraries to the device in order to do detection.
                // Usually this completes before the app is run for the first time.  But if that
                // download has not yet completed, then the above call will not detect any text,
                // barcodes, or faces.
                //
                // isOperational() can be used to check if the required native libraries are currently
                // available.  The detectors will automatically become operational once the library
                // downloads complete on device.
                Log.w(TAG, "Detector dependencies are not yet available.");

                // Check for low storage.  If there is low storage, the native library will not be
                // downloaded, so detection will not become operational.
                IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
                boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

                if (hasLowStorage) {
                    Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
                    Log.w(TAG, getString(R.string.low_storage_error));
                }
            }

            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE}, PICK_FROM_GALLERY);
            } else {
                pickImage();

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @SuppressLint("InlinedApi")
    private void pickImage() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        startActivityForResult(galleryIntent, PICK_FROM_GALLERY);
    }


    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != PICK_FROM_GALLERY) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Pick from gallery permission granted.");
            // We have permission, so create the camerasource
            pickImage();
            return;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == PICK_FROM_GALLERY) {
            if (data != null) {
                if (data.getClipData() != null) {
                    ClipData mClipData = data.getClipData();
                    ArrayList<Uri> mArrayUri = new ArrayList<Uri>();
                    mPendingResults = mClipData.getItemCount();
                    for (int i = 0; i < mClipData.getItemCount(); i++) {
                        ClipData.Item item = mClipData.getItemAt(i);
                        Uri uri = item.getUri();
                        processImageUri(uri);
                    }
                } else if (data.getData() != null) {
                    Uri selectedImage = data.getData();
                    mPendingResults = 1;
                    processImageUri(selectedImage);
                }
            }
        }
        if (mPendingResults == 0) {
            Intent response = new Intent();
            response.putStringArrayListExtra(TextBlockObject, mResults);
            setResult(CommonStatusCodes.SUCCESS, response);
            finish();
        }
    }

    int mPendingResults = 0;

    public void processImageUri(Uri selectedImage) {
        Bitmap bitmap = null;
        BufferedInputStream bis = null;
        try
        {
            InputStream is = getContentResolver().openInputStream(selectedImage);
            bitmap = BitmapFactory.decodeStream(is);
            OcrTask mTRTask = new OcrTask(this, this);
            mTRTask.execute(bitmap);

        } catch (Exception e) {
            e.printStackTrace();
            mPendingResults--;
            if (mPendingResults == 0) {
                Intent reply = new Intent();
                reply.putStringArrayListExtra(TextBlockObject, mResults);
                setResult(CommonStatusCodes.SUCCESS, reply);
                finish();
            }
        }
    }

    @Override
    public void onTextRecognizerResponse(String response) {
        mResults.add(response);
        mPendingResults--;
        if (mPendingResults == 0) {
            Intent reply = new Intent();
            reply.putStringArrayListExtra(TextBlockObject, mResults);
            setResult(CommonStatusCodes.SUCCESS, reply);
            finish();
        }
    }

}
