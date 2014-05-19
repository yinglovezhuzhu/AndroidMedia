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
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;

import com.android.test.libary.mymodule.app.R;

/**
 * Use：
 * 
 * @author yinglovezhuzhu@gmail.com
 */
public class PlayerActivity extends Activity {
	private SurfaceView surfaceView;
	private Button btnPause, btnPlayUrl, btnStop;
	private SeekBar skbProgress;
	private PlayerManager playerManager;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_test_videoplay);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		surfaceView = (SurfaceView) this.findViewById(R.id.sv_video_player);

		btnPlayUrl = (Button) this.findViewById(R.id.btnPlayUrl);
		btnPlayUrl.setOnClickListener(new ClickEvent());

		btnPause = (Button) this.findViewById(R.id.btnPause);
		btnPause.setOnClickListener(new ClickEvent());

		btnStop = (Button) this.findViewById(R.id.btnStop);
		btnStop.setOnClickListener(new ClickEvent());

		skbProgress = (SeekBar) this.findViewById(R.id.sb_video_player);
		skbProgress.setOnSeekBarChangeListener(new SeekBarChangeEvent());
		playerManager = new PlayerManager(surfaceView, skbProgress);

	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(playerManager != null) {
            playerManager.stop();
        }
    }

    class ClickEvent implements OnClickListener {

		@Override
		public void onClick(View arg0) {
			if (arg0 == btnPause) {
				playerManager.pause();
			} else if (arg0 == btnPlayUrl) {
                String url = "http://192.168.1.121/family_guy_penis_car.3gp";
//				String url="http://daily3gp.com/vids/family_guy_penis_car.3gp";
//				String url="http://download.cloud.189.cn/v5/downloadFile.action?downloadRequest=1_79D25A703382CFEA132FE87D2B127C3B01019106D228B136040420F0B46692450A910255CAB91388E859A89B3B9F10BBF985543E2D2BCD84817780930F20F671F581EBB3B67BFBB477C33BD0D039868FF1CEB90C2BA0611116AB3C2F0044FD11DEAFF731C99DDEF71DA7E4B48421941B";
//				String url="http://download.cloud.189.cn/v5/downloadFile.action?downloadRequest=1_9DE6071EE8DD75617D9330E2A2BD8BC7EE01AD6035D65D2143EEE816DE47005DCBFBEB511F241CDDFB384C23D356E96402EEB15F60C1FF96F00AF8419716D30E1F1956F9951E9D67256F8D31AE5DA1D3F1D7681FF9B2404707922F7F6496F675A0D605198B07496E1D291BEC4DE12C61";
//				String url="http://download.cloud.189.cn/v5/downloadFile.action?downloadRequest=1_4068F6291B716BD463B8B5946DF9E10CBCADB599115E640AA679F2BC6BF3E84E2C96FDDFF038FB783DBFA67A6A466E37F23B8F2683237807AA5DB19E981A9882BDC94DE93BA749FB59C6AEE4362B902538B582141C91128345779454B1FC28BAB839E57113996F91F0D723345F8FBCB9";
//				String url="http://download.cloud.189.cn/v5/downloadFile.action?downloadRequest=1_F4BCB69437F145E40AE26744D1D5A0C23E05F8700B8D77A408929DEFBEB84B4A95444372BD8E702FB90AFC33307E57E97A6B641166E7B3F5506F9A7056E2B9FC24B5D3B9387B1AC78DC96A2EAD85BB828C879A4B6C65DC2EE185ED945CA2285C0B74D13AB4543DE5A60CD8A7CFA943A4";
				playerManager.playUrl(url);
			} else if (arg0 == btnStop) {
				playerManager.stop();
			}

		}
	}

	class SeekBarChangeEvent implements SeekBar.OnSeekBarChangeListener {
		int progress;

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			// 原本是(progress/seekBar.getMax())*player.mediaPlayer.getDuration()
			this.progress = progress * playerManager.mMediaPlayer.getDuration()
					/ seekBar.getMax();
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			// seekTo()的参数是相对与影片时间的数字，而不是与seekBar.getMax()相对的数字
			playerManager.mMediaPlayer.seekTo(progress);
		}
	}

}
