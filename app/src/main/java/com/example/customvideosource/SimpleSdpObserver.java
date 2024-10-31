package com.example.customvideosource;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class SimpleSdpObserver implements SdpObserver {

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d("Simple Sdp Observer", "On Create Success : " + sessionDescription.description  );
    }

    @Override
    public void onSetSuccess() {
        Log.d("Simple Sdp Observer", "On Set Success");
    }

    @Override
    public void onCreateFailure(String s) {
        Log.d("Simple Sdp Observer", "On Create Failure");
    }

    @Override
    public void onSetFailure(String s) {
        Log.d("Simple Sdp Observer", "On Set Failure");
    }

}