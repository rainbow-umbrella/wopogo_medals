package com.rainbow_umbrella.wopogo_medals;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.VolleyLog;
import com.android.volley.VolleyError;
import com.google.android.gms.common.api.CommonStatusCodes;
/*
 * Upload updated medal values to the wopogo.uk server.
 *
 */
public class SendMedalsActivity extends Activity {

    private static final String TAG = SendMedalsActivity.class.getSimpleName();
    String mUsername;
    String mPassword;
    String mTrainer;
    ArrayList<String> mMedalNames;
    ArrayList<Integer> mMedalValues;
    boolean mFirstParameter = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = "http://www.rainbow-umbrella.com/wopogo/upload.php";

        Bundle b = getIntent().getExtras();
        if (b != null) {
            mUsername = b.getString("username");
            mPassword = b.getString("password");
            mTrainer = b.getString("trainer");
            mMedalNames = b.getStringArrayList("medalNames");
            mMedalValues = b.getIntegerArrayList("medalValues");

        }
        JSONObject body = new JSONObject();

        try {
            body.put("user_name", mUsername);
            body.put("password", mPassword);
            body.put("trainer", mTrainer);
            body.put("action", "update");
            JSONArray medals = new JSONArray();
            for (int i = 0; i < mMedalNames.size(); i++) {
                if (mMedalValues.get(i) != -1) {
                    JSONObject medal = new JSONObject();
                    medal.put("field", mMedalNames.get(i));
                    medal.put("value", mMedalValues.get(i));
                    medals.put(medal);
                }
            }
            body.put("medals", medals);
            Log.d(TAG, "Message to send: " + body.toString());
        } catch (JSONException e)
        {

        }
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            VolleyLog.v("Response:%n %s", response.toString(4));
                            Intent reply = new Intent();
                            reply.putExtra("response", response.toString());
                            setResult(CommonStatusCodes.SUCCESS, reply);
                            finish();

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                VolleyLog.e("Error: ", error.getMessage());
                Intent reply = new Intent();
                reply.putExtra("error", error.getMessage());
                setResult(CommonStatusCodes.ERROR, reply);
                finish();
            }
        });
        queue.add(request);

    }
}

