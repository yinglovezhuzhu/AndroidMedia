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

package com.opensource.videoplayer.test;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.opensource.videoplayer.PlayerActivity;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_play).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
//				Intent i = new Intent(MainActivity.this, MyVideoPlay.class);
//				i.putExtra("netUrl", "http://download.cloud.189.cn/v5/downloadFile.action?downloadRequest=1_79D25A703382CFEA132FE87D2B127C3B01019106D228B136040420F0B46692450A910255CAB91388E859A89B3B9F10BBF985543E2D2BCD84817780930F20F671F581EBB3B67BFBB477C33BD0D039868FF1CEB90C2BA0611116AB3C2F0044FD11DEAFF731C99DDEF71DA7E4B48421941B");
//				startActivity(i);
                Intent i = new Intent(MainActivity.this, PlayerActivity.class);
                startActivity(i);
            }
        });
    }
}
