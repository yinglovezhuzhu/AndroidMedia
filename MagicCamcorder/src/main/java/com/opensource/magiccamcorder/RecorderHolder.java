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

package com.opensource.magiccamcorder;

import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Use:
 * Created by yinglovezhuzhu@gmail.com on 2014-06-01.
 */
public class RecorderHolder {

    private static final String TAG = "RecorderHolder";
    public static final int MAX_TIME = 6000;

    private String mTempFolder = null;

    private List<String> videoTempFiles = new ArrayList<String>();
    private SurfaceView mySurfaceView = null;
    private boolean isMax = false;
    private long videoStartTime;
    private int totalTime = 0;
    private boolean isStart = false;

    private opencv_core.IplImage yuvIplimage = null;
    private volatile FFmpegFrameRecorder recorder;
    private int sampleAudioRateInHz = 44100;
    private int frameRate = 30;
    /* audio data getting thread */
    private AudioRecord mAudioRecorder;
    private AudioRecordRunnable audioRecordRunnable;
    private Thread mAudioRecordThread;
    volatile boolean runAudioThread = true;

    private boolean isFinished = false;

    Camera mCameraDevice;

    public RecorderHolder(Camera cameraDevice, SurfaceView mySurfaceView) {
        this.mCameraDevice = cameraDevice;
        this.mySurfaceView = mySurfaceView;
        mTempFolder = generateParentFolder();
        reset();
    }

//    private Camera getCamera() {
//        return cameraManager.getCamera();
//    }

    public boolean isStart() {
        return isStart;
    }

    public long getVideoStartTime() {
        return videoStartTime;
    }

    public int checkIfMax(long timeNow) {
        int during = 0;
        if (isStart) {
            during = (int) (totalTime + (timeNow - videoStartTime));
            System.out.println(during + ",T:" + totalTime + ",N:" + timeNow
                    + ",S:" + videoStartTime);
            if (during >= MAX_TIME) {
                stopRecord();
                during = MAX_TIME;
                isMax = true;
            }
        } else {
            during = totalTime;
            if (during >= MAX_TIME) {
                during = MAX_TIME;
            }
        }

        return during;
    }

    // ---------------------------------------
    // initialize ffmpeg_recorder
    // ---------------------------------------
    public void initRecorder() {
        String ffmpeg_link = mTempFolder + "/" + "video.mp4";
        Log.w(TAG, "init recorder");

        Camera.Size previewSize = mCameraDevice.getParameters().getPreviewSize();
        if (yuvIplimage == null) {
//            yuvIplimage = opencv_core.IplImage.create(cameraManager.getDefaultSize().width,
//                    cameraManager.getDefaultSize().height, opencv_core.IPL_DEPTH_8U, 2);
            yuvIplimage = opencv_core.IplImage.create(previewSize.width,
                    previewSize.height, opencv_core.IPL_DEPTH_8U, 2);
            Log.i(TAG, "create yuvIplimage");
        }

        Log.i(TAG, "ffmpeg_url: " + ffmpeg_link);

//        recorder = new FFmpegFrameRecorder(ffmpeg_link,
//                cameraManager.getDefaultSize().width,
//                cameraManager.getDefaultSize().height, 1);
        recorder = new FFmpegFrameRecorder(ffmpeg_link,
                previewSize.width,
                previewSize.height, 1);
        recorder.setFormat("mp4");
        recorder.setSampleRate(sampleAudioRateInHz);
        // Set in the surface changed method
        recorder.setFrameRate(frameRate);

        Log.i(TAG, "recorder initialize success");

        audioRecordRunnable = new AudioRecordRunnable();
        mAudioRecordThread = new Thread(audioRecordRunnable);
        try {
            recorder.start();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mAudioRecordThread.start();
    }

    // ---------------------------------------------
    // audio thread, gets and encodes audio data
    // ---------------------------------------------
    class AudioRecordRunnable implements Runnable {

        @Override
        public void run() {
            android.os.Process
                    .setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            int bufferSize;
            short[] audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleAudioRateInHz,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = new short[bufferSize];

            Log.d(TAG, "mAudioRecorder.startRecording()");
            mAudioRecorder.startRecording();

			/* ffmpeg_audio encoding loop */
            while (!isFinished) {
                // Log.v(TAG,"recording? " + recording);
                bufferReadResult = mAudioRecorder.read(audioData, 0,
                        audioData.length);
                if (bufferReadResult > 0) {
                    // Log.v(TAG, "bufferReadResult: " + bufferReadResult);
                    // If "recording" isn't true when start this thread, it
                    // never get's set according to this if statement...!!!
                    // Why? Good question...
                    if (isStart) {
//                        try {
//                            Buffer[] barray = new Buffer[1];
//                            barray[0] = ShortBuffer.wrap(audioData, 0,
//                                    bufferReadResult);
//                            recorder.record(barray);
//                            // Log.v(TAG,"recording " + 1024*i + " to " +
//                            // 1024*i+1024);
//                        } catch (FFmpegFrameRecorder.Exception e) {
//                            Log.v(TAG, e.getMessage());
//                            e.printStackTrace();
//                        }
                    }
                }
            }
            Log.v(TAG, "AudioThread Finished, release mAudioRecorder");

			/* encoding finish, release recorder */
            if (mAudioRecorder != null) {
                mAudioRecorder.stop();
                mAudioRecorder.release();
                mAudioRecorder = null;
                Log.v(TAG, "mAudioRecorder released");
            }
        }
    }

    public void reset() {
        for (String file : videoTempFiles) {
            File tempFile = new File(file);
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
        videoTempFiles = new ArrayList<String>();
        isStart = false;
        totalTime = 0;
        isMax = false;
    }

    public List<String> getVideoTempFiles() {
        return videoTempFiles;
    }

    public String getVideoParentpath() {
        return mTempFolder;
    }

    public void startRecord() {
//        if (isMax) {
//            return;
//        }
        isStart = true;
        videoStartTime = new Date().getTime();
    }

    public void stopRecord() {
        if (recorder != null && isStart) {
            runAudioThread = false;
            if (!isMax) {
                totalTime += new Date().getTime() - videoStartTime;
                videoStartTime = 0;
            }
            isStart = false;

        }
    }

    public void releaseRecord() {
        isFinished = true;
        Log.v(TAG,
                "Finishing recording, calling stop and release on recorder");
        try {
            recorder.stop();
            recorder.release();
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
        recorder = null;
    }

    public void onPreviewFrame(byte[] data, Camera camera) {
        int during = checkIfMax(new Date().getTime());
		/* get video data */
        if (yuvIplimage != null && isStart) {
            yuvIplimage.getByteBuffer().put(data);
            //yuvIplimage = rotateImage(yuvIplimage.asCvMat(), 90).asIplImage();
            Log.v(TAG, "Writing Frame");
            try {
                System.out.println(System.currentTimeMillis() - videoStartTime);
                if (during < 6000) {
                    recorder.setTimestamp(1000 * during);
                    recorder.record(yuvIplimage);
                }
            } catch (FFmpegFrameRecorder.Exception e) {
                Log.v(TAG, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /*public opencv_core.CvMat rotateImage(opencv_core.CvMat input, int angle) {

        opencv_core.CvPoint2D32f center = new opencv_core.CvPoint2D32f(input.cols() / 2.0F,
                input.rows() / 2.0F);

        CvMat rotMat = cvCreateMat(2, 3, CV_32FC1);
        cv2DRotationMatrix(center, angle, 1, rotMat);
        CvMat dst = cvCreateMat(input.rows(), input.cols(), input.type());
        cvWarpAffine(input, dst, rotMat);
        return dst;

    }*/

    public String generateParentFolder() {
        String parentPath = Environment.getExternalStorageDirectory()
                .getAbsolutePath() + "/magiccamcorder/video/temp";
        File tempFile = new File(parentPath);
        if (!tempFile.exists()) {
            tempFile.mkdirs();
        }
        return parentPath;

    }
}
