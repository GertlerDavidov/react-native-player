/*
* @Author: xeodou
* @Date:   2015
*/

package com.xeodou.rctplayer;


import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;

import java.io.IOException;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Callback;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.chunk.Format;


public class ReactAudio extends ReactContextBaseJavaModule implements ExoPlayer.Listener {

    public static final String REACT_CLASS = "ReactAudio";

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;
    private static final int SILENT_CHECK = 0;

    private ExoPlayer player = null;
    private PlayerControl playerControl = null;
    private ReactApplicationContext context;

    private static final String LOG_TAG = "AudioRecord";
    private static String mFileName = null;
    private MediaRecorder mRecorder = null;
    private MediaPlayer   mPlayer = null;

    RecordAmplitude recordAmplitude;
    boolean isRecording = false;
    public int recordingAmp = 0;
    public int recordingDur = 0;

    private class RecordAmplitude extends android.os.AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            while (isRecording) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                publishProgress(mRecorder.getMaxAmplitude());
            }
            return null;
        }
        protected void onProgressUpdate(Integer... progress) {
           int result;
            result = progress[0];
            recordingAmp = recordingAmp + result;
            recordingDur++;

        }
    }

    public ReactAudio(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }


    private void sendEvent(String eventName,
                           @Nullable WritableMap params) {
        this.context
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    @ReactMethod
    public void removeListener() {
        // this could be used on React component unmount
        player.removeListener(this);
    }

    private static String getDefaultUserAgent() {
        StringBuilder result = new StringBuilder(64);
        result.append("Dalvik/");
        result.append(System.getProperty("java.vm.version")); // such as 1.1.0
        result.append(" (Linux; U; Android ");

        String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
        result.append(version.length() > 0 ? version : "1.0");

        // add the model for the release build
        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }
        String id = Build.ID; // "MASTER" or "M4-rc20"
        if (id.length() > 0) {
            result.append(" Build/");
            result.append(id);
        }
        result.append(")");
        return result.toString();
    }

    private MediaCodecAudioTrackRenderer buildRender(String url, String agent, boolean auto) {
        Uri uri = Uri.parse(url);

        Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);

        DataSource dataSource = new DefaultUriDataSource(context, agent);
        ExtractorSampleSource sampleSource = new ExtractorSampleSource(uri, dataSource, allocator,
                BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);

        MediaCodecAudioTrackRenderer render = new MediaCodecAudioTrackRenderer(sampleSource);

        return render;
    }

    @ReactMethod
    public void prepare(String url, boolean auto) {
        if (player != null ) {
            player.release();
            player = null;
        }

        player = ExoPlayer.Factory.newInstance(1);
        playerControl = new PlayerControl(player);

        String agent = getDefaultUserAgent();
        MediaCodecAudioTrackRenderer render = this.buildRender(url, agent, auto);
        player.prepare(render);
        player.addListener(this);
        player.setPlayWhenReady(auto);
    }

    @ReactMethod
    public void start() {
        if (player != null ) {
            playerControl.start();
        }
    }

    @ReactMethod
    public void pause() {
        if (player != null ) {
            playerControl.pause();
        }
    }

    @ReactMethod
    public void resume() {
        if (player != null ) {
            playerControl.start();
        }
    }

    @ReactMethod
    public void isPlaying(Callback cb) {
        if (player != null ) {
            cb.invoke(playerControl.isPlaying());
        }
    }

    @ReactMethod
    public void getDuration(Callback cb) {
        if (player != null ) {
            cb.invoke(playerControl.getDuration());
        }
    }

    @ReactMethod
    public void getCurrentPosition(Callback cb) {
        if (player != null ) {
            cb.invoke(playerControl.getCurrentPosition());
        }
    }

    @ReactMethod
    public void getBufferPercentage(Callback cb) {
        if (player != null ) {
            cb.invoke(playerControl.getBufferPercentage());
        }
    }

    @ReactMethod
    public void stop() {
        if (player != null ) {
            player.release();
            player = null;
        }
    }

    @ReactMethod
    public void seekTo(int timeMillis) {
        if (player != null ) {
            playerControl.seekTo(timeMillis);
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        WritableMap params = Arguments.createMap();
        switch (playbackState) {
            // event list from official demo example
            case ExoPlayer.STATE_BUFFERING:
                sendEvent("buffering", params);
                break;
            case ExoPlayer.STATE_ENDED:
                player.release();
                player = null;
                sendEvent("end", params);
                break;
            case ExoPlayer.STATE_IDLE:
                sendEvent("idle", params);
                break;
            case ExoPlayer.STATE_PREPARING:
                sendEvent("preparing", params);
                break;
            case ExoPlayer.STATE_READY:
                sendEvent("ready", params);
                break;
        }
    }

    public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format,
           long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {
        // to make sure media is loaded
        WritableMap params = Arguments.createMap();
        sendEvent("loadCompleted", params);
    }

    @Override
    public void onPlayWhenReadyCommitted() {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        WritableMap params = Arguments.createMap();
        params.putString("msg", error.getMessage());
        sendEvent("error", params);
    }

    @ReactMethod
    public void startPlaying() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }
    }

    @ReactMethod
    public void stopPlaying() {
        if (mPlayer != null ) {
            mPlayer.release();
            mPlayer = null;
        }
    }



    @ReactMethod
    public void startRecording() {


         if (mRecorder != null ) {
            mRecorder.release();
            mRecorder = null;
        }
        try {
            mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
            mFileName += "/test.m4a";
        } catch (Exception e) {
            Log.e(LOG_TAG, "getExternalStorageDirectory() failed");
        }

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setAudioChannels(1);
        mRecorder.setAudioSamplingRate(22050);
        mRecorder.setAudioEncodingBitRate(31000);
        mRecorder.setOutputFile(mFileName);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.start();
        recordingAmp = 0;
        recordingDur = 0;
        isRecording = true;
        recordAmplitude = new RecordAmplitude();
        recordAmplitude.execute();
    }

    @ReactMethod
    public void stopRecording() {
        Log.i(LOG_TAG, "-> STOP vv RECORDING -> Total sec: " + recordingDur + " Sum Amp: " + recordingAmp);
        isRecording = false;
        recordAmplitude.cancel(true);
        Log.d("TEST", "Result " + recordingAmp );
        if (mRecorder != null ) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    @ReactMethod
    public void silentCheck( Callback cb) {
        int silent =  Math.round( recordingAmp / recordingDur );
        cb.invoke(silent);
    }


}
