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
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.SeekBar;

import java.io.IOException;

/**
 * Use：Play Video
 * 
 * Create by yinglovezhuzhu@gmail.com on
 */

public class PlayerManager implements OnBufferingUpdateListener, OnCompletionListener,
		MediaPlayer.OnPreparedListener, SurfaceHolder.Callback {
	
	public MediaPlayer mMediaPlayer;
	private SurfaceHolder mSurfaceHolder;
	private SeekBar mSeekBar;
	private int mVideoWidth;
	private int mVideoHeight;

	public PlayerManager(SurfaceView surfaceView, SeekBar skbProgress) {
		this.mSeekBar = skbProgress;
		mSurfaceHolder = surfaceView.getHolder();
		mSurfaceHolder.addCallback(this);
		
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            try {
			mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            } catch (Exception e) {
                e.printStackTrace();
            }
		}
	}


	Handler handleProgress = new Handler() {
		public void handleMessage(Message msg) {

			int position = mMediaPlayer.getCurrentPosition();
			int duration = mMediaPlayer.getDuration();

			if (duration > 0) {
				long pos = mSeekBar.getMax() * position / duration;
				mSeekBar.setProgress((int) pos);
			}
            handleProgress.sendMessageDelayed(handleProgress.obtainMessage(0), 1000);
		};
	};

	// *****************************************************

	public void play() {
		mMediaPlayer.start();
	}

	
	public void playUrl(String videoUrl) {
		try {
			mMediaPlayer.reset();
			mMediaPlayer.setDataSource(videoUrl);
			mMediaPlayer.prepare();// prepare之后自动播放
			// mediaPlayer.start();
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
	}

	public void stop() {
		if (mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		Log.e("mediaPlayer", "surface changed");
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		try {
			mMediaPlayer = new MediaPlayer();
			mMediaPlayer.setDisplay(mSurfaceHolder);
			mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mMediaPlayer.setOnBufferingUpdateListener(this);
			mMediaPlayer.setOnPreparedListener(this);
		} catch (Exception e) {
			Log.e("mediaPlayer", "error", e);
		}
		Log.e("mediaPlayer", "surface created");
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		Log.e("mediaPlayer", "surface destroyed");
	}

	/**
	 * 通过onPrepared播放
	 */
	@Override
	public void onPrepared(MediaPlayer arg0) {
		mVideoWidth = mMediaPlayer.getVideoWidth();
		mVideoHeight = mMediaPlayer.getVideoHeight();
		if (mVideoHeight != 0 && mVideoWidth != 0) {
			arg0.start();
            handleProgress.sendMessageDelayed(handleProgress.obtainMessage(0), 1000);
		}
		Log.e("mediaPlayer", "onPrepared");
	}

	@Override
	public void onCompletion(MediaPlayer arg0) {
		// TODO Auto-generated method stub
        if(mMediaPlayer != null) {
            mMediaPlayer.release();
        }
	}

	@Override
	public void onBufferingUpdate(MediaPlayer arg0, int bufferingProgress) {
		mSeekBar.setSecondaryProgress(bufferingProgress);
		int currentProgress = mSeekBar.getMax()
				* mMediaPlayer.getCurrentPosition() / mMediaPlayer.getDuration();
		Log.e(currentProgress + "% play", bufferingProgress + "% buffer");

	}

}
