package com.rainbow_umbrella.wopogo_medals;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Grab a copy of the screen using the Media Projection class and return it as a bitmap. Currently
 * hardcoded to be used by the OverlayService.
 *
 */
public class ScreenGrabTask extends AsyncTask<String, Integer, Bitmap> {
    private static final String TAG = ScreenGrabTask.class.getSimpleName();

    private int mResultCode;
    private Intent mResultData;
    private DisplayMetrics mMetrics;
    private int mScreenDensity;
    private MediaProjectionManager mMediaProjectionManager;
    private boolean mReady = false;
    private OverlayService mOwner;

    public ScreenGrabTask(OverlayService owner,
                          int resultCode,
                          Intent resultData,
                          DisplayMetrics metrics,
                          MediaProjectionManager mediaProjectionManager) {
        mOwner = owner;
        mResultCode = resultCode;
        mResultData = resultData;
        mMetrics = metrics;
        mScreenDensity = mMetrics.densityDpi;
        mMediaProjectionManager = mediaProjectionManager;
    }


    // Runs in UI before background thread is called
    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        // Do something like display a progress bar
    }

    private MediaProjection mMediaProjection;
    private ImageReader mImageReader;
    private Bitmap mResultBitmap;

    // This is run in a background thread
    @Override
    protected Bitmap doInBackground(String ... params) {
        // get the string from params, which is an array
        String myString = params[0];

        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
        mImageReader = ImageReader.newInstance(mMetrics.widthPixels, mMetrics.heightPixels,
                PixelFormat.RGBA_8888, 2);
        //mImageReader.setOnImageAvailableListener(new ScreenGrabTask.ImageListener(), null);

        mMediaProjection.createVirtualDisplay("screen-mirror", mMetrics.widthPixels,
                mMetrics.heightPixels, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);

        // Do something that takes a long time, for example:
        try {
            Image image = mImageReader.acquireLatestImage();
            while (image == null) {
                Thread.currentThread();
                Thread.sleep(100);
                image = mImageReader.acquireLatestImage();
            }
            mMediaProjection.stop();
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            int offset = 0;
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * mMetrics.widthPixels;
// create bitmap
            mResultBitmap = Bitmap.createBitmap(mMetrics.widthPixels+rowPadding/pixelStride,
                    mMetrics.heightPixels, Bitmap.Config.ARGB_8888);
            mResultBitmap.copyPixelsFromBuffer(buffer);
            image.close();
        } catch (InterruptedException e)
        {
            mResultBitmap = null;
        }

        return mResultBitmap;

    }

    // This is called from background thread but runs in UI
    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);

        // Do things like update the progress bar
    }

    // This runs in UI when background thread finishes
    @Override
    protected void onPostExecute(Bitmap result) {
        super.onPostExecute(result);
        mOwner.onScreenGrab(mResultBitmap);
        // Do things like hide the progress bar or change a TextView
    }


    private class ImageListener implements ImageReader.OnImageAvailableListener {

        int count;

        @Override
        public void onImageAvailable(ImageReader imageReader) {
            String msg = null;

            try {
                Image image = imageReader.acquireNextImage();

                if (image == null) {
                    msg = "onImageAvailable: no image";
                    return;
                } else {
                    msg = "onImageAvailable: image processed";
                    final Image.Plane[] planes = image.getPlanes();
                    final ByteBuffer buffer = planes[0].getBuffer();
                    int offset = 0;
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mMetrics.widthPixels;
// create bitmap
                    mResultBitmap = Bitmap.createBitmap(mMetrics.widthPixels+rowPadding/pixelStride,
                            mMetrics.heightPixels, Bitmap.Config.ARGB_8888);
                    mResultBitmap.copyPixelsFromBuffer(buffer);
                    image.close();
                    mReady = true;
                }

            } catch (Exception e) {
                e.printStackTrace();

                msg = "onImageAvailable: " + e;
            }
            Log.i(TAG, msg);
        }
    }
    private void setUpImageReader() {
        if (mImageReader != null) {
            return;
        }
    }

}
