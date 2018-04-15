package com.rainbow_umbrella.wopogo_medals;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.app.PendingIntent;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.util.ArrayList;
import android.graphics.Bitmap;

import com.google.android.gms.vision.text.TextRecognizer;

/**
 * Created by matt on 08/08/2016.
 */

public class OverlayService extends Service implements View.OnTouchListener, OcrTask.IOwner {

  private static final String TAG = OverlayService.class.getSimpleName();

  ArrayList<Messenger> mClients = new ArrayList<Messenger>();
  /** Holds last value set by a client. */
  int mValue = 0;

  /**
   * Command to the service to register a client, receiving callbacks
   * from the service.  The Message's replyTo field must be a Messenger of
   * the client where callbacks should be sent.
   */
  static final int MSG_REGISTER_CLIENT = 1;

  /**
   * Command to the service to unregister a client, ot stop receiving callbacks
   * from the service.  The Message's replyTo field must be a Messenger of
   * the client as previously given with MSG_REGISTER_CLIENT.
   */
  static final int MSG_UNREGISTER_CLIENT = 2;

  /**
   * Command to service to set a new value.  This can be sent to the
   * service to supply a new value, and will be sent by the service to
   * any registered clients with the new value.
   */
  static final int MSG_SET_VALUE = 3;

  static final int MSG_SEND_TEXT_BLOCK = 4;

  static final int MSG_HIDE_BUTTON = 5;
  static final int MSG_SHOW_BUTTON = 6;

  static final int MSG_CLOSE_APP = 7;

  ScreenGrabTask mScreenGrabTask = null;

  static final int REQUEST_SCREEN_GRAB = 9010;
  /**
   * Handler of incoming messages from clients.
   */
  class IncomingHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_REGISTER_CLIENT:
          mClients.add(msg.replyTo);
          break;
        case MSG_UNREGISTER_CLIENT:
          mClients.remove(msg.replyTo);
          break;
        case MSG_SET_VALUE:
          mValue = msg.arg1;
          for (int i=mClients.size()-1; i>=0; i--) {
            try {
              mClients.get(i).send(Message.obtain(null,
                      MSG_SET_VALUE, mValue, 0));
            } catch (RemoteException e) {
              // The client is dead.  Remove it from the list;
              // we are going through the list from back to front
              // so this is safe to do inside the loop.
              mClients.remove(i);
            }
          }
          break;
        case MSG_HIDE_BUTTON:
          overlayButton.setVisibility(View.INVISIBLE);
          break;
        case MSG_SHOW_BUTTON:
          overlayButton.setVisibility(View.VISIBLE);
        default:
          super.handleMessage(msg);
      }
    }
  }

  /**
   * Target we publish for clients to send messages to IncomingHandler.
   */
  final Messenger mMessenger = new Messenger(new IncomingHandler());


  private WindowManager windowManager;

  private View floatyView;
  public View overlayButton;

  @Override
  public IBinder onBind(Intent intent) {
      return mMessenger.getBinder();
  }

  private static int FOREGROUND_ID=1234;

  public static int REQUEST_CLOSE=9999;

  private DisplayMetrics mMetrics;
  private MediaProjectionManager mMediaProjectionManager;

  private TextRecognizer mTextRecognizer;
  @Override
  public void onCreate() {

    super.onCreate();

    windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    mMetrics = new DisplayMetrics();
    mMediaProjectionManager = (MediaProjectionManager) getSystemService
            (Context.MEDIA_PROJECTION_SERVICE);

    mTextRecognizer = new TextRecognizer.Builder(this).build();

    MainActivity.instance().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

    addOverlayView();

    Notification.Builder b=new Notification.Builder(this);
/*
    Intent intent = new Intent(this, MainActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
*/
    Intent notificationIntent = new Intent("action_close_app");
    PendingIntent contentIntent = PendingIntent.getBroadcast(
            this, REQUEST_CLOSE,
            notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    startForeground(FOREGROUND_ID,
            b.setOngoing(true)
                    .setContentTitle(getString(R.string.title_activity_main))
                    .setContentText("Click me to stop WoPoGo Medals")
                    .setSmallIcon(R.drawable.icon_status)
                    .setTicker(getString(R.string.screen_cap))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build());

    IntentFilter filter = new IntentFilter();
    filter.addAction("action_close_app");
    registerReceiver(receiver, filter);
  }

  private WindowManager.LayoutParams layoutParams;
  private void addOverlayView() {

    layoutParams =
            new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

    layoutParams.gravity = Gravity.TOP | Gravity.LEFT; //Gravity.NO_GRAVITY; // was Gravity.CENTER | Gravity.START;
    DisplayMetrics displayMetrics = new DisplayMetrics();
    windowManager.getDefaultDisplay().getMetrics(displayMetrics);
    int height = displayMetrics.heightPixels;
    int width = displayMetrics.widthPixels;
    // TODO: 48 = icon size.
    layoutParams.x = (displayMetrics.widthPixels - 48) / 2;
    layoutParams.y = (displayMetrics.heightPixels - 48) / 2;

    FrameLayout interceptorLayout = new FrameLayout(this) {

      @Override
      public boolean dispatchKeyEvent(KeyEvent event) {

        // Only fire on the ACTION_DOWN event, or you'll get two events (one for _DOWN, one for _UP)
        if (event.getAction() == KeyEvent.ACTION_DOWN) {

          // Check if the HOME button is pressed
          if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {

            Log.v(TAG, "BACK Button Pressed");

            // As we've taken action, we'll return true to prevent other apps from consuming the event as well
            return true;
          }
        }

        // Otherwise don't intercept the event
        return super.dispatchKeyEvent(event);
      }
    };

    floatyView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.floating_view, interceptorLayout);
    overlayButton = floatyView.findViewById(R.id.overlay_button);
    overlayButton.setOnTouchListener(this);
    overlayButton.setVisibility(View.INVISIBLE);
    windowManager.addView(floatyView, layoutParams);


  }

  @Override
  public void onDestroy() {

    super.onDestroy();

    if (floatyView != null) {

      windowManager.removeView(floatyView);

      floatyView = null;
    }
    unregisterReceiver(receiver);
  }

  private void sendMessage(String text) {
    Message msg = Message.obtain(null, MSG_SET_VALUE, 0, 0);
    msg.obj = text;
    for (int i=mClients.size()-1; i>=0; i--) {
      try {
        mClients.get(i).send(msg);
      } catch (RemoteException e) {
        // The client is dead.  Remove it from the list;
        // we are going through the list from back to front
        // so this is safe to do inside the loop.
        mClients.remove(i);
      }
    }
  }

  private float downRawX, downRawY, dX, dY;
  private final float CLICK_DRAG_TOLERANCE = 10.0f;
  public boolean performClick() {

    if (mScreenGrabTask == null) {
      overlayButton.setVisibility(View.INVISIBLE);


      mScreenGrabTask = new ScreenGrabTask(this, MainActivity.instance().mResultCode,
              MainActivity.instance().mScreenCapResultData, mMetrics, mMediaProjectionManager);
      mScreenGrabTask.execute("Value");
    }
    return true;
  }

  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {

    Log.v(TAG, "onTouch...");

    int action = motionEvent.getAction();
    if (action == MotionEvent.ACTION_DOWN) {

      downRawX = motionEvent.getRawX();
      downRawY = motionEvent.getRawY();
      dX = layoutParams.x - downRawX;
      dY = layoutParams.y - downRawY;

      return true; // Consumed

    } else if (action == MotionEvent.ACTION_MOVE) {

      int viewWidth = 48;
      int viewHeight = 48;

      int parentWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
      int parentHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

      float newX = motionEvent.getRawX() + dX;
      newX = Math.max(0, newX); // Don't allow the FAB past the left hand side of the parent
      newX = Math.min(parentWidth - viewWidth, newX); // Don't allow the FAB past the right hand side of the parent

      float newY = motionEvent.getRawY() + dY;
      Log.i(TAG, "NewY = " + Float.toString(newY));
      newY = Math.max(0, newY); // Don't allow the FAB past the top of the parent
      newY = Math.min(parentHeight - viewHeight, newY); // Don't allow the FAB past the bottom of the parent
      layoutParams.x = (int)newX;
      layoutParams.y = (int)newY;
      windowManager.updateViewLayout(floatyView, layoutParams);

/*
      view.animate()
              .x(newX)
              .y(newY)
              .setDuration(0)
              .start();

*/
      return true; // Consumed

    } else if (action == MotionEvent.ACTION_UP) {

      float upRawX = motionEvent.getRawX();
      float upRawY = motionEvent.getRawY();

      float upDX = upRawX - downRawX;
      float upDY = upRawY - downRawY;

      if (Math.abs(upDX) < CLICK_DRAG_TOLERANCE && Math.abs(upDY) < CLICK_DRAG_TOLERANCE) { // A click
        return performClick();
      } else { // A drag
        return true; // Consumed
      }


    }
    return false;
  }

  OcrTask mOcrTask = null;

  public void onScreenGrab(Bitmap bitmap) {
    Log.d(TAG, "Received bitmap");
    mOcrTask = new OcrTask(this, this);
    mOcrTask.execute(bitmap);
    mScreenGrabTask = null;
  }
/*
    // Kill service
    onDestroy();
*/

  @Override
  public void onTextRecognizerResponse(String response) {
    overlayButton.setVisibility(View.VISIBLE);
    sendMessage(response);
    mOcrTask = null;
  }

  /**
   * Handle broadcast from Notification and close the app.
   */
  private BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      Message msg = Message.obtain(null, MSG_CLOSE_APP, 0, 0);
      msg.obj = "CLOSE_APP";
      for (int i=mClients.size()-1; i>=0; i--) {
        try {
          mClients.get(i).send(msg);
        } catch (RemoteException e) {
          // The client is dead.  Remove it from the list;
          // we are going through the list from back to front
          // so this is safe to do inside the loop.
          mClients.remove(i);
        }
      }
    }
  };

}
