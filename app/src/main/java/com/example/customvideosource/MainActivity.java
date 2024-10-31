package com.example.customvideosource;

import android.Manifest;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.ViewDataBinding;

import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

//import com.google.firebase.firestore.FirebaseFirestore;
import com.example.customvideosource.databinding.ActivityMainBinding;
import com.example.customvideosource.databinding.*;


import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CapturerObserver;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
//import org.webrtc.VideoRenderer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.YuvConverter;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.Executor;

import io.socket.client.IO;
import io.socket.client.Socket;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static io.socket.client.Socket.EVENT_CONNECT;
import static io.socket.client.Socket.EVENT_CONNECT_ERROR;
import static io.socket.client.Socket.EVENT_DISCONNECT;
import static org.webrtc.SessionDescription.Type.ANSWER;
import static org.webrtc.SessionDescription.Type.OFFER;

import com.example.customvideosource.R;
import com.example.customvideosource.SimpleSdpObserver;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "CompleteActivity";
    private static final int RC_CALL = 111;
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final int VIDEO_RESOLUTION_WIDTH = 1280;
    public static final int VIDEO_RESOLUTION_HEIGHT = 720;
    public static final int FPS = 30;

    private Socket socket;
    private boolean isInitiator;
    private boolean isChannelReady;
    private boolean isStarted;


    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    SurfaceTextureHelper surfaceTextureHelper;



    private PeerConnection peerConnection;
    private EglBase rootEglBase;
    private PeerConnectionFactory factory;
    private VideoTrack videoTrackFromCamera;
    private int rotation = 0;
    long timestamp = System.currentTimeMillis();
    private ActivityMainBinding binding;
    private DataChannel localDataChannel;
    private Executor executor;
    private Handler messageHandler;
    private static final int MESSAGE_INTERVAL = 5000;

    private static final int MAX_MESSAGE_SIZE = 65507;
    private HandlerThread sendThread;
    private Handler sendHandler;

    private SurfaceView renderFrameView;
    private SurfaceHolder renderSurfaceHolder;
    private Paint paint = new Paint();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        setSupportActionBar(binding.toolbar);
        start();
        renderFrameView = findViewById(R.id.surface_view);
        renderSurfaceHolder = renderFrameView.getHolder();
        renderSurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onDestroy() {
        if (socket != null) {
            sendMessage("bye");
            socket.disconnect();
        }
        if (sendThread != null) {
            sendThread.quitSafely();
            sendThread = null;
        }
        surfaceTextureHelper.dispose();
        super.onDestroy();
    }

    @AfterPermissionGranted(RC_CALL)
    private void start() {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        if (EasyPermissions.hasPermissions(this, perms)) {
            connectToSignallingServer();

            initializeSurfaceViews();

            initializePeerConnectionFactory();

            createVideoTrackFromCameraAndShowIt();

            initializePeerConnections();

            startStreamingVideo();

//            messageHandler = new Handler();
//            startSendingMessages();

        } else {
            EasyPermissions.requestPermissions(this, "Need some permissions", RC_CALL, perms);
        }
    }


    private static ByteBuffer stringToByteBuffer(String msg, Charset charset) {
        return ByteBuffer.wrap(msg.getBytes(charset));
    }

    private void connectToSignallingServer() {
        Log.d("method call", "connectToSignallingServer: ");
        try {
            String URL = "http://192.168.214.20:8000";
            Log.e(TAG, "REPLACE ME: IO Socket:" + URL);
            socket = IO.socket(URL);

            socket.on(EVENT_CONNECT, args -> {
                Log.d(TAG, "connectToSignallingServer: connect");
                socket.emit("create or join", "cuarto");
            }).on("ipaddr", args -> {
                Log.d(TAG, "connectToSignallingServer: ipaddr");
            }).on("created", args -> {
                Log.d(TAG, "connectToSignallingServer: created");
                isInitiator = true;
            }).on("full", args -> {
                Log.d(TAG, "connectToSignallingServer: full");
            }).on("join", args -> {
                Log.d(TAG, "connectToSignallingServer: join");
                Log.d(TAG, "connectToSignallingServer: Another peer made a request to join room");
                Log.d(TAG, "connectToSignallingServer: This peer is the initiator of room");
                isChannelReady = true;
            }).on("joined", args -> {
                Log.d(TAG, "connectToSignallingServer: joined");
                isChannelReady = true;
            }).on("log", args -> {
                for (Object arg : args) {
                    Log.d(TAG, "connectToSignallingServer: " + String.valueOf(arg));
                }
            }).on("message", args -> {
                Log.d(TAG, "connectToSignallingServer: got a message");
                try {
                    if (args[0] instanceof String) {
                        String message = (String) args[0];
                        if (message.equals("got user media")) {
                            Log.d("Signalling Server", "maybeStart wil be called");
                            maybeStart();
                        }
                    } else {
                        JSONObject message = (JSONObject) args[0];
                        Log.d(TAG, "connectToSignallingServer: got message " + message);
                        if (message.getString("type").equals("offer")) {
                            Log.d(TAG, "connectToSignallingServer: received an offer " + isInitiator + " " + isStarted);
                            if (!isInitiator && !isStarted) {
                                maybeStart();
                            }
                            peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(OFFER, message.getString("sdp")));
                            doAnswer();
                        } else if (message.getString("type").equals("answer") && isStarted) {
                            Log.d("SignallingMessage", message.toString());
                            peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(ANSWER, message.getString("sdp")));
                        } else if (message.getString("type").equals("candidate") && isStarted) {
                            Log.d(TAG, "connectToSignallingServer: receiving candidates");
                            IceCandidate candidate = new IceCandidate(message.getString("id"), message.getInt("label"), message.getString("candidate"));
                            peerConnection.addIceCandidate(candidate);
                        }
                        /*else if (message === 'bye' && isStarted) {
                        handleRemoteHangup();
                    }*/
                    }
                } catch (JSONException e) {
                    Log.d(TAG, "connectToSignallingServer: JSONException");
                    e.printStackTrace();
                }
            }).on(EVENT_DISCONNECT, args -> {
                Log.d(TAG, "connectToSignallingServer: disconnect");
            }).on(EVENT_CONNECT_ERROR, args -> {
                Log.e(TAG, " Signal Connection error: " + args[0]);
            });
            socket.connect();
        } catch (URISyntaxException e) {
            Log.d("URISyntaxException", "UriSyntaxException");
            e.printStackTrace();
        }
    }
    //MirtDPM4
    private void doAnswer() {
        Log.d(TAG, "doAnswer: ");
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "answer");
                    message.put("sdp", sessionDescription.description);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new MediaConstraints());
    }

    private void maybeStart() {
        Log.d(TAG, "maybeStart: " + isStarted + " " + isChannelReady);
        if (!isStarted && isChannelReady) {
            isStarted = true;
            if (isInitiator) {
                doCall();
            }
        }
    }

    private void doCall() {
        Log.d("method call", "doCall: ");
        MediaConstraints sdpMediaConstraints = new MediaConstraints();

        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "onCreateSuccess: ");
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "offer");
                    message.put("sdp", sessionDescription.description);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, sdpMediaConstraints);
    }

    private void sendMessage(Object message) {
        socket.emit("message", message);
    }

    private void initializeSurfaceViews() {
        Log.d("method call", " initializeSurfaceViews");
        rootEglBase = EglBase.create();
        binding.surfaceView.init(rootEglBase.getEglBaseContext(), null);
        binding.surfaceView.setEnableHardwareScaler(true);
        binding.surfaceView.setMirror(true);


        binding.surfaceView2.init(rootEglBase.getEglBaseContext(), null);
        binding.surfaceView2.setEnableHardwareScaler(true);
        binding.surfaceView2.setMirror(true);

        //add one more
    }

    private void initializePeerConnectionFactory() {
        Log.d("method call", "initializePeerConnectionFactory");
        PeerConnectionFactory.InitializationOptions initOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();

        PeerConnectionFactory.initialize(initOptions);

        VideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),
                /* enableIntelVp8Encoder */ true,
                /* enableH264HighProfile */ true
        );
        VideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableEncryption = false;
        options.disableNetworkMonitor = false;

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .setOptions(options)
                .createPeerConnectionFactory();

        Log.d("method call", "PeerConnectionFactory initialized successfully");
    }


    private void createVideoTrackFromCameraAndShowIt() {
        Log.d("method call", "createVideoTrackFromCameraAndShowIt");
        VideoCapturer videoCapturer = createVideoCapturer();
        if (videoCapturer == null) {
            Log.e("Video Capture", "Failed to create video capturer.");
            return;
        }

        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        VideoSource videoSource = factory.createVideoSource(videoCapturer.isScreencast());

        // Initialize the HandlerThread
        sendThread = new HandlerThread("SendThread");
        sendThread.start();
        sendHandler = new Handler(sendThread.getLooper());

        CapturerObserver capturerObserver = new CapturerObserver() {
            @Override
            public void onFrameCaptured(VideoFrame frame) {
                if (frame == null) {
                    Log.d("Frame", "Captured frame is null");
                    return;
                }

                VideoFrame.I420Buffer buffer = frame.getBuffer().toI420();
                ByteBuffer byteBuffer = convertI420BufferToByteBuffer(buffer);
                if (byteBuffer != null) {
                    sendHandler.post(() -> sendVideoFrame(byteBuffer));
                }
                videoSource.getCapturerObserver().onFrameCaptured(frame);
            }

            @Override
            public void onCapturerStarted(boolean success) {
                Log.d("Capturer", "Video capturer started: " + success);
            }

            @Override
            public void onCapturerStopped() {
                Log.d("Capturer", "Video capturer stopped.");
            }
        };

        videoCapturer.initialize(surfaceTextureHelper, this, capturerObserver);
        videoTrackFromCamera = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        videoTrackFromCamera.setEnabled(true);

        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("101", audioSource);

        try {
            videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);
        } catch (Exception e) {
            Log.e("Video Capture", "Failed to start capture: " + e.getMessage());
            return;
        }

        binding.surfaceView.setVisibility(View.VISIBLE);
        videoTrackFromCamera.addSink(binding.surfaceView);
    }

    private void sendVideoFrame(ByteBuffer byteBuffer) {
        if (localDataChannel.state() == DataChannel.State.OPEN) {
            Log.d("DataChannel", "DataChannel is open, sending video frame.");

            while (byteBuffer.remaining() > 0) {
                int sizeToSend = Math.min(MAX_MESSAGE_SIZE, byteBuffer.remaining());
                ByteBuffer chunk = byteBuffer.slice();
                chunk.limit(sizeToSend);
                boolean sent = localDataChannel.send(new DataChannel.Buffer(chunk, false));
                if (sent) {
                    Log.d("DataChannel", "Successfully sent a chunk of video frame.");
                } else {
                    Log.d("DataChannel", "Failed to send a chunk of video frame.");
                    int retries = 0;
                    while (retries < 3) {
                        boolean reSend = localDataChannel.send(new DataChannel.Buffer(chunk, false));
                        if (reSend) {
                            Log.d("DataChannel", "Successfully sent a chunk of video frame.");
                            break;
                        }
                        retries++;
                    }
                }
                byteBuffer.position(byteBuffer.position() + sizeToSend);
            }
        } else {
            Log.d("DataChannel", "DataChannel not open, unable to send video frame: " + localDataChannel.state());
        }
    }


    public static ByteBuffer convertI420BufferToByteBuffer(VideoFrame.I420Buffer i420Buffer) {
        final int width = i420Buffer.getWidth();
        final int height = i420Buffer.getHeight();
        final int ySize = width * height;
        final int chromaWidth = (width + 1) / 2;
        final int chromaHeight = (height + 1) / 2;
        final int uSize = chromaWidth * chromaHeight;
        final int vSize = uSize;

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(ySize + uSize + vSize);

        ByteBuffer yData = i420Buffer.getDataY();
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                byteBuffer.put(yData.get(y * i420Buffer.getStrideY() + x));
            }
        }

        ByteBuffer uData = i420Buffer.getDataU();
        for (int y = 0; y < chromaHeight; ++y) {
            for (int x = 0; x < chromaWidth; ++x) {
                byteBuffer.put(uData.get(y * i420Buffer.getStrideU() + x));
            }
        }

        ByteBuffer vData = i420Buffer.getDataV();
        for (int y = 0; y < chromaHeight; ++y) {
            for (int x = 0; x < chromaWidth; ++x) {
                byteBuffer.put(vData.get(y * i420Buffer.getStrideV() + x));
            }
        }

        byteBuffer.rewind();
        return byteBuffer;
    }


    private void initializePeerConnections() {
        Log.d("method call", " initializePeerConnections");
        peerConnection = createPeerConnection(factory);
        localDataChannel = peerConnection.createDataChannel("sendDataChannel", new DataChannel.Init());
        localDataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {

            }

            @Override
            public void onStateChange() {
                Log.d(TAG, "onStateChange: " + localDataChannel.state().toString());

                    if (localDataChannel.state() == DataChannel.State.OPEN) {
                       Log.d("LocalDataChannelState", "DataChannel is open");
                    } else {
                        Log.d("LocalDataChannelState", "DataChannel is closed");
                    }

            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                Log.d("DataChannelInitialize", "onMessage: got message");
            }
        });

    }

    private void startStreamingVideo() {
        Log.d("method call", " startStreamingVideo");
        MediaStream mediaStream = factory.createLocalMediaStream("ARDAMS");
        mediaStream.addTrack(videoTrackFromCamera);
        mediaStream.addTrack(localAudioTrack);
        peerConnection.addStream(mediaStream);

        sendMessage("got user media");
    }

    private PeerConnection createPeerConnection(PeerConnectionFactory factory) {
        Log.d("PeerConnection function ", factory == null ? "null" : "not null");
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        String URL = "stun:stun.l.google.com:19302";
        iceServers.add(new PeerConnection.IceServer(URL));

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;
        MediaConstraints pcConstraints = new MediaConstraints();
        PeerConnection.Observer pcObserver = new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: ");
            }
            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: ");
            }
            @Override
            public void onIceConnectionReceivingChange(boolean b) {
                Log.d(TAG, "onIceConnectionReceivingChange: ");
            }
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: ");
            }
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d(TAG, "onIceCandidate: ");
                JSONObject message = new JSONObject();

                try {
                    message.put("type", "candidate");
                    message.put("label", iceCandidate.sdpMLineIndex);
                    message.put("id", iceCandidate.sdpMid);
                    message.put("candidate", iceCandidate.sdp);

                    Log.d(TAG, "onIceCandidate: sending candidate " + message);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "onIceCandidatesRemoved: ");
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size());
                VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                AudioTrack remoteAudioTrack = mediaStream.audioTracks.get(0);
                remoteAudioTrack.setEnabled(true);
                remoteVideoTrack.setEnabled(true);
                remoteVideoTrack.addSink(binding.surfaceView2);
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "onRemoveStream: ");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannelstate: " + dataChannel.state());
                dataChannel.registerObserver(new DataChannel.Observer() {
                    @Override
                    public void onBufferedAmountChange(long l) {
                        Log.d("DataChannelReceived", "Buffered amount change: " + l + " bytes");
                    }

                    @Override
                    public void onStateChange() {
                        Log.d("DataChannelReceived", "onStateChange: remote data channel state: " + dataChannel.state().toString());
                    }

                    @Override
                    public void onMessage(DataChannel.Buffer buffer) {
                        Log.d("DataChannelReceived", "onMessage: got message");
                        processReceivedBuffer(buffer, VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT);
                    }
                });
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ");
            }
        };

        //noinspection deprecation
        return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);
    }


    private void processReceivedBuffer(DataChannel.Buffer buffer, int width, int height) {
        if (buffer != null && buffer.data.remaining() > 0) {
            ByteBuffer receivedBuffer = buffer.data;

            int ySize = width * height;
            int uSize = (width / 2) * (height / 2);
            int vSize = uSize;

            if (receivedBuffer.remaining() >= (ySize + uSize + vSize)) {
                ByteBuffer yBuffer = ByteBuffer.allocateDirect(ySize);
                ByteBuffer uBuffer = ByteBuffer.allocateDirect(uSize);
                ByteBuffer vBuffer = ByteBuffer.allocateDirect(vSize);

                receivedBuffer.get(yBuffer.array());
                receivedBuffer.get(uBuffer.array());
                receivedBuffer.get(vBuffer.array());


                VideoFrame.I420Buffer i420Buffer = new VideoFrame.I420Buffer() {
                    @Override
                    public ByteBuffer getDataY() {
                        return yBuffer;
                    }

                    @Override
                    public ByteBuffer getDataU() {
                        return uBuffer;
                    }

                    @Override
                    public ByteBuffer getDataV() {
                        return vBuffer;
                    }

                    @Override
                    public int getStrideY() {
                        return 0;
                    }

                    @Override
                    public int getStrideU() {
                        return 0;
                    }

                    @Override
                    public int getStrideV() {
                        return 0;
                    }

                    @Override
                    public int getWidth() {
                        return 0;
                    }

                    @Override
                    public int getHeight() {
                        return 0;
                    }

                    @Nullable
                    @Override
                    public VideoFrame.I420Buffer toI420() {
                        return null;
                    }

                    @Override
                    public void retain() {

                    }

                    @Override
                    public void release() {
                        // Handle release logic if needed
                    }

                    @Override
                    public VideoFrame.Buffer cropAndScale(int i, int i1, int i2, int i3, int i4, int i5) {
                        return null;
                    }
                };

                VideoFrame videoFrame = new VideoFrame(i420Buffer, 0, System.nanoTime());
                processReceivedVideoFrame(videoFrame);
            } else {
                Log.e("DataChannelReceived", "Received buffer does not contain enough data.");
            }
        } else {
            Log.d("DataChannelReceived", "Received buffer is null or empty.");
        }
    }

    private void processReceivedVideoFrame(VideoFrame videoFrame) {
        if (videoFrame != null) {
            renderFrame(videoFrame);
            videoFrame.release();
        } else {
            Log.e("VideoFrameProcessing", "Received null video frame.");
        }
    }

    private void renderFrame(VideoFrame frame) {
        VideoFrame.I420Buffer i420Buffer = frame.getBuffer().toI420();
        ByteBuffer yBuffer = i420Buffer.getDataY();
        ByteBuffer uBuffer = i420Buffer.getDataU();
        ByteBuffer vBuffer = i420Buffer.getDataV();
        int width = i420Buffer.getWidth();
        int height = i420Buffer.getHeight();

        Canvas canvas = null;
        try {
            canvas = renderSurfaceHolder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                drawYUVToRGB(canvas, yBuffer, uBuffer, vBuffer, width, height);
            }
        } finally {
            if (canvas != null) {
                renderSurfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void drawYUVToRGB(Canvas canvas, ByteBuffer yBuffer, ByteBuffer uBuffer, ByteBuffer vBuffer, int width, int height) {
        paint.setColor(Color.BLUE);
        canvas.drawRect(0, 0, width, height, paint);
    }




    private VideoCapturer createVideoCapturer() {

        VideoCapturer videoCapturer;
        if (useCamera2()) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        Log.d("Video Caputure", String.valueOf(videoCapturer));
        return videoCapturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        for (String deviceName : deviceNames) {
            Log.d("Device name", deviceName);
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }
    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this);
    }
}