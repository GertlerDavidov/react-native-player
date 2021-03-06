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
import com.facebook.infer.annotation.Assertions;
import java.io.*;

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

import com.xeodou.rctplayer.JavaScriptTimer.TaskHandle;

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
    private TaskHandle handle = null;
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

        if (auto == true) {
            onUpdatePosition();
        }
    }

    @ReactMethod
    public void start() {
        Assertions.assertNotNull(player);
        playerControl.start();

        // Send event for current position to JS
        onUpdatePosition();
    }

    @ReactMethod
    public void pause() {
        Assertions.assertNotNull(player);
        playerControl.pause();

        // clear setInterval
        handle.invalidate();
    }

    @ReactMethod
    public void resume() {
        Assertions.assertNotNull(player);
        playerControl.start();

        // Send event for current position to JS
        onUpdatePosition();
    }

    @ReactMethod
    public void isPlaying(Callback cb) {
        Assertions.assertNotNull(player);
        cb.invoke(playerControl.isPlaying());
    }

    @ReactMethod
    public void getDuration(Callback cb) {
        Assertions.assertNotNull(player);
        cb.invoke(playerControl.getDuration());
    }

    @ReactMethod
    public void getCurrentPosition(Callback cb) {
        Assertions.assertNotNull(player);
        cb.invoke(playerControl.getCurrentPosition());
    }

    @ReactMethod
    public void getBufferPercentage(Callback cb) {
        Assertions.assertNotNull(player);
        cb.invoke(playerControl.getBufferPercentage());
    }

    @ReactMethod
    public void stop() {
        Assertions.assertNotNull(player);
        player.stop();
        playerControl.seekTo(0);

        // clear setInterval
        handle.invalidate();
    }

    @ReactMethod
    public void release() {
        Assertions.assertNotNull(player);
        player.release();
        player = null;

        // clear setInterval
        handle.invalidate();
    }

    @ReactMethod
    public void seekTo(int timeMillis) {
        Assertions.assertNotNull(player);
        playerControl.seekTo(timeMillis);
    }

    @ReactMethod
    public void canPause(Callback cb) {
        Assertions.assertNotNull(player);
        cb.invoke(playerControl.canPause());
    }

    @ReactMethod
    public void canSeekBackward(Callback cb) {
        Assertions.assertNotNull(player);
        cb.invoke(playerControl.canSeekBackward());
    }

    @ReactMethod
    public void canSeekForward(Callback cb) {
        Assertions.assertNotNull(player);
        cb.invoke(playerControl.canSeekForward());
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        WritableMap params = Arguments.createMap();
        params.putBoolean("playWhenReady", playWhenReady);
        params.putInt("playbackState", playbackState);

        /*
            STATE_IDLE          : 1
            STATE_PREPARING     : 2
            STATE_BUFFERING     : 3
            STATE_READY         : 4
            STATE_ENDED         : 5
        */

        if(playbackState == ExoPlayer.STATE_ENDED) {
            player.stop();
            playerControl.seekTo(0);
        }

        sendEvent("onPlayerStateChanged", params);
    }

    public void onUpdatePosition() {

        // Remove previous timer
        if (handle != null) {
            handle.invalidate();
        }

        handle = JavaScriptTimer.setInterval(new Runnable() {
            public void run() {
                int currentPosition = playerControl.getCurrentPosition();

                WritableMap params = Arguments.createMap();
                params.putInt("currentPosition", currentPosition);

                sendEvent("onUpdatePosition", params);
            }
        }, 100);
    }

    /**
     *  Invoked when the current load operation completes.
    */
    public void onLoadCompleted(
        int sourceId,
        long bytesLoaded,
        int type,
        int trigger,
        Format format,
        long mediaStartTimeMs,
        long mediaEndTimeMs,
        long elapsedRealtimeMs,
        long loadDurationMs) {

        // Please refer to the link below about how to use WritableMap
        // https://github.com/facebook/react-native/blob/master/ReactAndroid/src/main/java/com/facebook/react/bridge/WritableMap.java
        WritableMap params = Arguments.createMap();
        params.putInt("sourceId", sourceId);
        params.putDouble("bytesLoaded", bytesLoaded);
        params.putInt("type", type);
        params.putInt("trigger", trigger);
        params.putDouble("mediaStartTimeMs", mediaStartTimeMs);
        params.putDouble("mediaEndTimeMs", mediaEndTimeMs);
        params.putDouble("elapsedRealtimeMs", elapsedRealtimeMs);
        params.putDouble("loadDurationMs", loadDurationMs);

        WritableMap subParams = Arguments.createMap();
        subParams.putInt("audioChannels", format.audioChannels);
        subParams.putInt("audioSamplingRate", format.audioSamplingRate);
        subParams.putInt("bitrate", format.bitrate);
        subParams.putString("codecs", format.codecs);
        subParams.putDouble("frameRate", format.frameRate);
        subParams.putInt("height", format.height);
        subParams.putString("id", format.id);
        subParams.putString("language", format.language);
        subParams.putString("mimeType", format.mimeType);
        subParams.putInt("width", format.width);

        params.putMap("format", subParams);

        sendEvent("onLoadCompleted", params);
    }

    @Override
    public void onPlayWhenReadyCommitted() {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        WritableMap params = Arguments.createMap();
        params.putString("msg", error.getMessage());
        sendEvent("onPlayerError", params);
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
            
            File existFile = new File(mFileName);
            if(existFile.exists() && !existFile.isDirectory()) { 
                existFile.delete();
            }
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
            try {
                mRecorder.stop();
                mRecorder.release();
                mRecorder = null;
            }  catch (Exception e) {
                Log.d(LOG_TAG, "stop() failed");
            }

        }
    }

    @ReactMethod
    public void silentCheck( Callback cb) {
        int silent =  Math.round( recordingAmp / recordingDur );
        cb.invoke(silent);
    }


}
