package com.rainbow_umbrella.wopogo_medals;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import com.android.volley.AuthFailureError;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.VolleyLog;
import com.android.volley.VolleyError;
import com.android.volley.ParseError;
import com.android.volley.NetworkResponse;
import com.android.volley.toolbox.HttpHeaderParser;
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
    String mApiKey;
    ArrayList<String> mMedalNames;
    ArrayList<Integer> mMedalValues;
    boolean mFirstParameter = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String url = getString(R.string.website_url);
        final String format = getString(R.string.timestamp_format);
        long dateInMillis = System.currentTimeMillis();
        final SimpleDateFormat sdf = new SimpleDateFormat(format);
        String timeString = sdf.format(new Date(dateInMillis));

        Bundle b = getIntent().getExtras();
        if (b != null) {
            mUsername = b.getString(getString(R.string.field_username));
            mPassword = b.getString(getString(R.string.field_password));
            mApiKey = b.getString(getString(R.string.field_api_key));
            mTrainer = b.getString(getString(R.string.field_trainer));
            mMedalNames = b.getStringArrayList("medalNames");
            mMedalValues = b.getIntegerArrayList("medalValues");

        }
        JSONObject body = new JSONObject();
        JSONArray stats = new JSONArray();

        try {
            //body.put("user_name", mUsername);
            //body.put("password", mPassword);
            //body.put("trainer", mTrainer);
            for (int i = 0; i < mMedalNames.size(); i++) {
                JSONObject medal = new JSONObject();
                medal.put("trainer", mTrainer);
                medal.put("timestamp", timeString);
                medal.put("stat", mMedalNames.get(i));
                medal.put("value", String.valueOf(mMedalValues.get(i)));
                stats.put(medal);
            }
            body.put("stats", stats);
            Log.d(TAG, "Message to send: " + body.toString());
        } catch (JSONException e)
        {

        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            VolleyLog.v("Response:%n %s", response.toString(4));
                            Intent reply = new Intent();
                            reply.putExtra("response", response.toString());
                            setResult(CommonStatusCodes.SUCCESS, reply);
                            SendMedalsActivity.this.finish();
                            finish();

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                VolleyLog.e("Error: ", error.getMessage());
                String responseString = new String("Unknown error.");
                if (error.networkResponse != null) {
                    String dataString = new String(error.networkResponse.data);
                    try {
                        boolean handled = false;
                        JSONObject body = new JSONObject(dataString);
                        if (body != null) {
                            JSONArray errors = body.getJSONArray("errors");
                            if (errors != null && errors.length() > 0) {
                                JSONObject errorItem = errors.getJSONObject(0);
                                if (errorItem != null) {
                                    responseString = errorItem.getString("text");
                                    if (responseString.equals(getString(R.string.error_not_logged_in))) {
                                        responseString = getString(R.string.error_api_key_is_invalid);
                                    }
                                    handled = true;
                                }
                            }
                        }
                        if (!handled) {
                            responseString = String.valueOf(error.networkResponse.statusCode) + ":" + dataString;
                        }
                    } catch (JSONException e) {
                        responseString = String.valueOf(error.networkResponse.statusCode) + ":" + dataString;
                    }
                } else if (error instanceof NoConnectionError) {
                    responseString = "No connection.";
                } else if (error instanceof TimeoutError) {
                    responseString = "Connection attempt timed out.";
                } else if (error instanceof AuthFailureError) {
                    responseString = "Failed authorisation.";
                }
                Intent reply = new Intent();
                reply.putExtra("response", responseString);
                setResult(CommonStatusCodes.ERROR, reply);
                SendMedalsActivity.this.finish();
                finish();
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("x-api-key", mApiKey);
                params.put("Content-Type", "application/json");

                return params;
            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                try {
                    String jsonString = new String(response.data,
                            HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                    JSONObject jsonResponse = new JSONObject();
                    if (response.statusCode == 200) {
                        jsonResponse.put("success", true);
                        jsonResponse.put("response", jsonString);
                    } else {
                        jsonResponse.put("success", false);
                        jsonResponse.put("response", new JSONObject(jsonString));
                    }
                    //jsonResponse.put("headers", new JSONArray(response.headers));
                    return Response.success(jsonResponse,
                            HttpHeaderParser.parseCacheHeaders(response));
                } catch (UnsupportedEncodingException e) {
                    return Response.error(new ParseError(e));
                } catch (JSONException je) {
                    return Response.error(new ParseError(je));
                }
            }
        };
        request.setShouldCache(false);
        MainActivity.instance().mRequestQueue.add(request);

    }
}

