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

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.test.libary.mymodule.app.R;
import com.opensource.videoplayer.utils.TimeUtils;

/**
 * Use：
 * 
 * @author yinglovezhuzhu@gmail.com
 */
public class PlayerActivity extends Activity implements OnClickListener{


    private Resources mResource;
    private String mPackageName;
	private SurfaceView mSurfaceView;
    private ImageButton mIbtnRewind;
    private ImageButton mIbtnPlayAndPause;
    private ImageButton mIbtnFastForward;
    private TextView mTvCurrentTime;
    private TextView mTvTotalTime;
	private SeekBar mProgressSeekBar;

	private PlayerManager mPlayerManager;


    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {

        @Override
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            mProgressSeekBar.setSecondaryProgress(percent);
            int currentProgress = mProgressSeekBar.getMax()
                    * mp.getCurrentPosition() / mp.getDuration();
            Log.e(currentProgress + "% play", percent + "% buffer");
        }
    };

    private MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            mTvCurrentTime.setText(mTvTotalTime.getText());
            mp.reset();
            mIbtnPlayAndPause.setImageResource(R.drawable.ic_media_play);
            mProgressSeekBar.setProgress(0);
        }
    };

    private MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {

        @Override
        public void onPrepared(MediaPlayer mp) {
            mPlayerManager.mVideoWidth = mp.getVideoWidth();
            mPlayerManager.mVideoHeight = mp.getVideoHeight();
            mTvTotalTime.setText(TimeUtils.formatTime(mp.getDuration()));
            if (mPlayerManager.mVideoHeight != 0 && mPlayerManager.mVideoWidth != 0) {
                mp.start();
                mProgressHandle.sendMessageDelayed(mProgressHandle.obtainMessage(0), 1000);
            }
            Log.e("mediaPlayer", "onPrepared");
        }
    };

    private Handler mProgressHandle = new Handler() {
        public void handleMessage(Message msg) {
            try {
                int position = mPlayerManager.mMediaPlayer.getCurrentPosition();
                int duration = mPlayerManager.mMediaPlayer.getDuration();

                if (duration > 0) {
                    long pos = mProgressSeekBar.getMax() * position / duration + 1;
                    mProgressSeekBar.setProgress((int) pos);

                }
                mTvCurrentTime.setText(TimeUtils.formatTime(position));
                mProgressHandle.sendMessageDelayed(mProgressHandle.obtainMessage(0), 1000);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        };
    };

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mResource = getResources();
        mPackageName = getPackageName();

		setContentView(R.layout.activity_video_player);
//		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mSurfaceView = (SurfaceView) this.findViewById(R.id.sv_video_player);

        mIbtnRewind = (ImageButton) this.findViewById(R.id.ibtn_video_player_rew);
        mIbtnRewind.setOnClickListener(this);

        mIbtnPlayAndPause = (ImageButton) this.findViewById(R.id.ibtn_video_player_play_and_pause);
        mIbtnPlayAndPause.setOnClickListener(this);

        mIbtnFastForward = (ImageButton) this.findViewById(R.id.ibtn_video_player_ff);
        mIbtnFastForward.setOnClickListener(this);

        mTvCurrentTime = (TextView) findViewById(R.id.tv_video_player_time_current);
        mTvTotalTime = (TextView) findViewById(R.id.tv_video_player_time_total);

        mProgressSeekBar = (SeekBar) this.findViewById(R.id.sb_video_player);
		mProgressSeekBar.setOnSeekBarChangeListener(new SeekBarChangeEvent());
//		mPlayerManager = new PlayerManager(mSurfaceView, mProgressSeekBar, mTvCurrentTime, mTvTotalTime);
		mPlayerManager = new PlayerManager(mSurfaceView, mBufferingUpdateListener, mCompletionListener, mPreparedListener);

	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        if(mPlayerManager != null) {
//            mPlayerManager.stop();
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pause();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == mResource.getIdentifier("ibtn_video_player_rew", "id", mPackageName)) {

        } else if(id == mResource.getIdentifier("ibtn_video_player_play_and_pause", "id", mPackageName)) {
            if(PlayerManager.STATE_STOPED == mPlayerManager.getState()) {
                String url = "http://192.168.1.121/family_guy_penis_car.3gp";
                mPlayerManager.playUrl(url);
                mIbtnPlayAndPause.setImageResource(R.drawable.ic_media_pause);
            } else if(PlayerManager.STATE_PLAYING == mPlayerManager.getState()) {
                pause();
            } else if(PlayerManager.STATE_PAUSED == mPlayerManager.getState()) {
                mPlayerManager.play();
                mIbtnPlayAndPause.setImageResource(R.drawable.ic_media_pause);
            }
        } else if(id == mResource.getIdentifier("ibtn_video_player_ff", "id", mPackageName)) {

        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(KeyEvent.KEYCODE_BACK == keyCode) {
            exit(RESULT_OK, null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void pause() {
        mPlayerManager.pause();
        mIbtnPlayAndPause.setImageResource(R.drawable.ic_media_play);

    }

    private void exit(int resultCode, Intent data) {
        setResult(resultCode, data);
        finish();
    }

	class SeekBarChangeEvent implements SeekBar.OnSeekBarChangeListener {
		int mmProgress;

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			this.mmProgress = progress * mPlayerManager.mMediaPlayer.getDuration() / seekBar.getMax();
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			mPlayerManager.mMediaPlayer.seekTo(mmProgress + 1);
		}
	}

}
