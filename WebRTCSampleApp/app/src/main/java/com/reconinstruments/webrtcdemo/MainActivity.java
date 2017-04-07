package com.reconinstruments.webrtcdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubError;
import com.pubnub.api.PubnubException;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity
{
    public static final String TAG = "MainActivity";

    Pubnub mPubNub;

    final String USER1 = "User1";
    final String USER2 = "User2";

    String userMe  = null;
    String userYou = null;

    TextView textLabel;
    Button buttonUser1;
    Button buttonUser2;
    Button buttonCall;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        textLabel   = (TextView)findViewById(R.id.myLabel);
        buttonUser1 = (Button)findViewById(R.id.buttonUser1);
        buttonUser2 = (Button)findViewById(R.id.buttonUser2);
        buttonCall  = (Button)findViewById(R.id.buttonCall);

        buttonUser1.requestFocus();
        SetButtonEnabled(buttonCall, false);
    }

    public void chooseUser(View view)
    {
        Button b = (Button)view;
        if(b == buttonUser1){ initPubNub(USER1, USER2); }
        if(b == buttonUser2){ initPubNub(USER2, USER1); }
    }

    void SetButtonEnabled(Button b, boolean enabled)
    {
        b.setEnabled(enabled);
        b.setFocusable(enabled);
    }

    public void initPubNub(String nameMe, String nameYou)
    {
        this.userMe  = nameMe;
        this.userYou = nameYou;

        SetButtonEnabled(buttonUser1, false);
        SetButtonEnabled(buttonUser2, false);
        SetButtonEnabled(buttonCall, true);
        buttonCall.requestFocus();
        textLabel.setText(nameMe);

        String stdbyChannel = this.userMe + Constants.STDBY_SUFFIX;
        this.mPubNub = new Pubnub(Constants.PUB_KEY, Constants.SUB_KEY);
        this.mPubNub.setUUID(this.userMe);
        try
        {
            // Subscribe our channel on PubNub and specify our callback handlers
            this.mPubNub.subscribe(stdbyChannel, new Callback()
            {
                @Override
                public void successCallback(String channel, Object message)
                {
                    logText("PubNub.connectCallback(" + channel + ", " + message + ")");
                    try
                    {
                        JSONObject jsonMsg = (JSONObject) message;
                        if(!jsonMsg.has(Constants.CALL_USER)){ return; }

                        // Setting 'userYou' here is unnecessary for our app implementation as
                        // you only can select 1 of 2 users (Thus you already decide beforehand
                        // who you are calling).
                        // In an implementation where you can select a unique user name,
                        // and a unique name to call, this step would become necessary (in order
                        // to know who is calling you).
                        userYou = jsonMsg.getString(Constants.CALL_USER);

                        // Now that we've successfully recieved a call from the other user,
                        // launch the view activity
                        launchViewActivity();
                    }
                    catch(JSONException e){ e.printStackTrace(); }
                }

                @Override
                public void connectCallback(String channel, Object message)
                {
                    logText("PubNub.connectCallback(" + channel + ", " + message + ")");
                }
            });
        }
        catch (PubnubException e){ e.printStackTrace(); }
    }

    public void attemptCall(View view)
    {
        initializeCall(userYou);
    }

    public void initializeCall(final String callNum)
    {
        final String callNumStdBy = callNum + Constants.STDBY_SUFFIX;
        try
        {
            // Create a JSON object with call number (other users name)
            JSONObject jsonCall = new JSONObject();
            jsonCall.put(Constants.CALL_USER, callNum);

            // Publish our call and specify our callback handlers
            mPubNub.publish(callNumStdBy, jsonCall, new Callback()
            {
                @Override
                public void successCallback(String channel, Object message)
                {
                    logText("PubNub.successCallback(" + channel + ", " + message + ")");
                    // If we successfully called the other user, launch our view activity
                    launchViewActivity();
                }

                @Override
                public void errorCallback(String channel, PubnubError error)
                {
                    logText("PubNub.errorCallback(" + channel + ", " + error + ")");
                }
            });
        }
        catch(JSONException e){ e.printStackTrace(); }
    }

    void launchViewActivity()
    {
        logText("MainActivity.LaunchViewActivity()");
        Intent intent = new Intent(MainActivity.this, ViewActivity.class);
        intent.putExtra(Constants.USER_NAME, userMe);
        intent.putExtra(Constants.CALL_USER, userYou);
        startActivity(intent);
    }

    void logText(String s)
    {
        Log.d(TAG, s);
        Message m = new Message();
        m.obj = s;
        logHandler.sendMessage(m);
    }

    private Handler logHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            Toast.makeText(MainActivity.this, (String)msg.obj, Toast.LENGTH_LONG).show();
        }
    };
}
