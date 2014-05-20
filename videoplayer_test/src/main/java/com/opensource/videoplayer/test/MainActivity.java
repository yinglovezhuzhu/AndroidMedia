package com.opensource.videoplayer.test;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.opensource.movieplayer.test.R;
import com.opensource.videoplayer.VideoActivity;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, VideoActivity.class);
//                i.setData(Uri.parse("http://192.168.1.121/family_guy_penis_car.3gp"));
                i.setData(Uri.parse("/storage/extSdCard/DCIM/Camera/VID_20140502_085802.mp4"));
                startActivity(i);
            }
        });
    }
}
