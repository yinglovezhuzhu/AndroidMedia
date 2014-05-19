/*
 * Copyright (C) 2014 The Android Open Source Project.
 *
 *        yinglovezhuzhu@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.opensource.videoplayer;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * Use：Play Video
 * 
 * Create by yinglovezhuzhu@gmail.com on
 */

public class PlayerManager implements SurfaceHolder.Callback {
        //OnBufferingUpdateListener, OnCompletionListener,
		//MediaPlayer.OnPreparedListener, SurfaceHolder.Callback {

    public static final int STATE_STOPED = 0x1;
	public static final int STATE_PLAYING = 0x2;
    public static final int STATE_PAUSED = 0x3;

	public MediaPlayer mMediaPlayer;
	private SurfaceHolder mSurfaceHolder;
//	private SeekBar mSeekBar;
//    private TextView mTvCurrentTime;
//    private TextView mTvTotalTime;
	public int mVideoWidth;
	public int mVideoHeight;

    private int mState = STATE_STOPED;

    private OnBufferingUpdateListener mOnBufferingUpdateListener;

    private OnCompletionListener mOnCompletionListener;

    private MediaPlayer.OnPreparedListener mOnPreParedListener;


//    public PlayerManager(SurfaceView surfaceView) {
//        this(surfaceView, null, null, null);
//    }

    public PlayerManager(SurfaceView surfaceView, OnBufferingUpdateListener bufferingUpdateListener,
                         OnCompletionListener completionListener, MediaPlayer.OnPreparedListener preparedListener) {
        this.mOnBufferingUpdateListener = bufferingUpdateListener;
        this.mOnCompletionListener = completionListener;
        this.mOnPreParedListener = preparedListener;
        this.mSurfaceHolder = surfaceView.getHolder();
        this.mSurfaceHolder.addCallback(this);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            try {
                mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

	public void play() {
		mMediaPlayer.start();
        mState = STATE_PLAYING;
	}

	
	public void playUrl(String videoUrl) {
		try {
			mMediaPlayer.reset();
			mMediaPlayer.setDataSource(videoUrl);
			mMediaPlayer.prepare();// auto play when MediaPlayer is prepared
            mState = STATE_PLAYING;
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void pause() {
		mMediaPlayer.pause();
        mState = STATE_PAUSED;
	}

	public void stop() {
		if (mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer.release();
            mState = STATE_STOPED;
		}
	}

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.e("mediaPlayer", "surface changed");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
            mMediaPlayer.setOnPreparedListener(mOnPreParedListener);
            mMediaPlayer.setOnCompletionListener(mOnCompletionListener);
        } catch (Exception e) {
            Log.e("mediaPlayer", "error", e);
        }
        Log.e("mediaPlayer", "surface created");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e("mediaPlayer", "surface destroyed");
    }

    public boolean isPlaying() {
        if(mMediaPlayer == null) {
            return false;
        }
        return mMediaPlayer.isPlaying();
    }

    public int getState() {
        return mState;
    }
}
