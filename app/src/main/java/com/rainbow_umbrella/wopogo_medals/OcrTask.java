package com.rainbow_umbrella.wopogo_medals;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Retrieves text blocks from a bitmap in a separate thread using the Google Vision library. The
 * response is returned as a JSON formatted string which represents an array of text blocks where
 * each text block is an array of strings representing the lines. The calling activity/service
 * should implement the OcrTask.IOwner interface.
 */

public class OcrTask extends AsyncTask<Bitmap, Integer, String >  {

    static final String TAG =  OcrTask.class.getSimpleName();

    public interface IOwner {
        public void onTextRecognizerResponse(String response);
    }

    private IOwner mOwner;
    private Context mContext;

    public OcrTask(IOwner owner, Context context) {
        mOwner = owner;
        mContext = context;
    }

    // Runs in UI before background thread is called
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // Do something like display a progress bar
    }


    // This is run in a background thread
    @Override
    protected String doInBackground(Bitmap ... params) {

        TextRecognizer textRecognizer = new TextRecognizer.Builder(mContext).build();
        // get the string from params, which is an array
        Frame theFrame = new Frame.Builder().setBitmap(params[0]).build();
        //Context context = getApplicationContext();
        SparseArray<TextBlock> textBlocks = textRecognizer.detect(theFrame);
        textRecognizer.release();
        Log.d(TAG, "textBlocks" + textBlocks.toString());
        // Sort the text blocks into vertical order.
        TextBlockComparator[] orderList = new TextBlockComparator[textBlocks.size()];
        for (int i = 0; i < textBlocks.size(); i++) {
            orderList[i] = new TextBlockComparator(textBlocks.get(textBlocks.keyAt(i)), i);
        }
        Arrays.sort(orderList);
        String result = new String();
        result = "[";
        boolean first = true;
        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock textBlock = textBlocks.get(textBlocks.keyAt(orderList[i].mIndex));
            if (!first) {
                result += ",";
            }
            first = false;
            result += "[";
            first = false;
            boolean firstLine = true;
            for (Text line : textBlock.getComponents()) {
                if (!firstLine) {
                    result += ",";
                }
                firstLine = false;
                String lineValue = line.getValue().replace("\"", "\\\"");
                result += "\"" + lineValue + "\"";
            }
            result += "]";
        }
        result +="]";

        return result;

    }

    // This is called from background thread but runs in UI
    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);

        // Do things like update the progress bar
    }

    // This runs in UI when background thread finishes
    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        mOwner.onTextRecognizerResponse(result);
        // Do things like hide the progress bar or change a TextView
    }


};
