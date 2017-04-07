package com.reconinstruments.webrtcdemo;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.reconinstruments.webrtcdemo.servers.XirSysRequest;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import me.kevingleason.pnwebrtc.PnPeer;
import me.kevingleason.pnwebrtc.PnRTCClient;
import me.kevingleason.pnwebrtc.PnRTCListener;
import me.kevingleason.pnwebrtc.PnSignalingParams;

public class ViewActivity extends Activity
{
    public static final String TAG                   = "ViewActivity";

    public static final String VIDEO_TRACK_ID        = "videoPN";
    public static final String AUDIO_TRACK_ID        = "audioPN";
    public static final String LOCAL_MEDIA_STREAM_ID = "localStreamPN";

    boolean showLogTextOnScreen = false;
    TextView mTextLog;
    GLSurfaceView mGLView;

    PnRTCClient pnRTCClient;
    VideoSource localVideoSource;
    VideoRenderer.Callbacks localRender;
    VideoRenderer.Callbacks remoteRender;

    String username;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_view);

        // Make sure our username was passed in (via Intent)
        Bundle extras = getIntent().getExtras();
        if(extras == null || !extras.containsKey(Constants.USER_NAME))
        {
            finish();
            return;
        }
        this.username = extras.getString(Constants.USER_NAME, "");
        logText("My Username:: " + this.username);

        mTextLog = (TextView)findViewById(R.id.textLog);
        mGLView  = (GLSurfaceView)findViewById(R.id.glview);
        initWebRTC(extras);

    }

    void initWebRTC(Bundle extras)
    {
        try
        {
            // Setup variables
            final Context c               = this;
            final boolean initAudio       = true;
            final boolean initVideo       = true;
            final boolean vidCodecHWAccel = true;
            final Object renderEGLContext = null;

            boolean success = PeerConnectionFactory.initializeAndroidGlobals(c, initAudio, initVideo, vidCodecHWAccel, renderEGLContext);

            if(!success)
            {
                finish();
                throw new java.lang.Exception("PeerConnectionFactory - Failed to Initialize Android Globals.");
            }
            logText("[PeerConnectionFactory.initializeAndroidGlobals(...)]");

            PeerConnectionFactory peerConnectionFactory = new PeerConnectionFactory();
            logText("[new PeerConnectionFactory()]");

            int deviceCount = VideoCapturerAndroid.getDeviceCount();
            logText("Capture Device Count: " + deviceCount);
            //String frontFaceDeviceString       = VideoCapturerAndroid.getNameOfFrontFacingDevice();
            //String backFaceDeviceString        = VideoCapturerAndroid.getNameOfBackFacingDevice();
            VideoCapturerAndroid videoCapturer = VideoCapturerAndroid.create(VideoCapturerAndroid.getDeviceName(deviceCount-1));

            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth",  "428"));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", "240"));
            localVideoSource           = peerConnectionFactory.createVideoSource(videoCapturer, videoConstraints);
            VideoTrack localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource);
            logText("[peerConnectionFactory.createVideoTrack(...)]");

            MediaConstraints audioConstraints = new MediaConstraints();
            AudioSource audioSource    = peerConnectionFactory.createAudioSource(audioConstraints);
            AudioTrack localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
            logText("[peerConnectionFactory.createAudioTrack(...)]");

            // Then we set that view, and pass a Runnable to run once the surface is ready
            VideoRendererGui.setView(mGLView, null);
            logText("[VideoRendererGui.setView(...)]");

            // Now that VideoRendererGui is ready, we can get our VideoRenderer. IN THIS ORDER. Effects which is on top or bottom
            remoteRender = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
            localRender  = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);

            // We start out with an empty MediaStream object, created with help from our PeerConnectionFactory
            MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID);
            mediaStream.addTrack(localVideoTrack);
            mediaStream.addTrack(localAudioTrack);

            connectPubNubWebRTCClient(extras, mediaStream);
        }
        catch(java.lang.Exception e)
        {
            logText("ERROR - [FAILED TO INITIALIZE!] ");
            e.printStackTrace();
        }
    }

    void connectPubNubWebRTCClient(Bundle extras, MediaStream mediaStream)
    {
        this.pnRTCClient = new PnRTCClient(Constants.PUB_KEY, Constants.SUB_KEY, this.username);
        // First attach the RTC Listener so that callback events will be triggered

        List<PeerConnection.IceServer> servers = getXirSysIceServers();
        if (!servers.isEmpty()){
            this.pnRTCClient.setSignalParams(new PnSignalingParams(servers));

        }
        this.pnRTCClient.attachRTCListener(new MyRTCListener());
        this.pnRTCClient.attachLocalMediaStream(mediaStream);
        // Listen on a channel. This is your "phone number," also set the max chat users.
        this.pnRTCClient.listenOn(this.username);
        this.pnRTCClient.setMaxConnections(1);

        // If Constants.CALL_USER is in the intent extras, auto-connect them.
        if(extras.containsKey(Constants.CALL_USER))
        {
            String callUser = extras.getString(Constants.CALL_USER, "");
            pnRTCClient.connect(callUser);
        }
    }

    private class MyRTCListener extends PnRTCListener
    {
        @Override
        public void onLocalStream(final MediaStream localStream)
        {
            ViewActivity.this.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    logText("MyRTCListenet - onLocalStream.run()");
                    if(localStream.videoTracks.size() == 0){ return; }
                    localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
                }
            });
        }

        @Override
        public void onAddRemoteStream(final MediaStream remoteStream, final PnPeer peer)
        {
            ViewActivity.this.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    logText("MyRTCListenet - onAddRemoteStream.run()");
                    Toast.makeText(ViewActivity.this, "Connected to " + peer.getId(), Toast.LENGTH_SHORT).show();
                    try
                    {
                        if(remoteStream.videoTracks.size() == 0){ return; }
                        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
                        VideoRendererGui.update(remoteRender, 0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
                        VideoRendererGui.update(localRender, 72, 72, 25, 25, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
                    }
                    catch (Exception e){ e.printStackTrace(); }
                }
            });
        }

        @Override
        public void onPeerConnectionClosed(PnPeer peer)
        {
            logText("MyRTCListenet - onPeerConnectionClosed.run()");
            finish();
        }
    }

    @Override
    public void onBackPressed()
    {
        hangUp(null);
        super.onBackPressed();
    }

    public void hangUp(View view)
    {
        this.pnRTCClient.closeAllConnections();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        this.mGLView.onPause();
        this.localVideoSource.stop();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        this.mGLView.onResume();
        this.localVideoSource.restart();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if(this.localVideoSource != null){ this.localVideoSource.stop(); }
        if(this.pnRTCClient != null){ this.pnRTCClient.onDestroy(); }
    }

    void logText(String s)
    {
        Log.d(TAG, s);
        if(showLogTextOnScreen)
        {
            Message m = new Message();
            m.obj = s;
            logHandler.sendMessage(m);
        }
   }

    private final Handler logHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            mTextLog.setText(String.format("%s\n>>%s", mTextLog.getText(), msg.obj));
        }
    };

    public List<PeerConnection.IceServer> getXirSysIceServers(){
        List<PeerConnection.IceServer> servers = new ArrayList<PeerConnection.IceServer>();
        try {
            servers = new XirSysRequest().execute().get();
        } catch (InterruptedException e){
            e.printStackTrace();
        }catch (ExecutionException e){
            e.printStackTrace();
        }
        return servers;
    }
}
