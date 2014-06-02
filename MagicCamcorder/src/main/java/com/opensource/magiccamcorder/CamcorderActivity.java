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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The Camcorder activity.
 */
public class CamcorderActivity extends Activity
        implements View.OnClickListener, SurfaceHolder.Callback {

    private static final String TAG = "CamcorderActivity";

    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private static final int ENABLE_SHUTTER_BUTTON = 6;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    // The brightness settings used when it is set to automatic in the system.
    // The reason why it is set to 0.7 is just because 1.0 is too bright.
    private static final float DEFAULT_CAMERA_BRIGHTNESS = 0.7f;

    private static final long NO_STORAGE_ERROR = -1L;
    private static final long CANNOT_STAT_ERROR = -2L;
    private static final long LOW_STORAGE_THRESHOLD = 512L * 1024L;

    private static final int STORAGE_STATUS_OK = 0;
    private static final int STORAGE_STATUS_LOW = 1;
    private static final int STORAGE_STATUS_NONE = 2;
    private static final int STORAGE_STATUS_FAIL = 3;


    private android.hardware.Camera mCameraDevice;
    /**
     * An unpublished intent flag requesting to start recording straight away
     * and return as soon as recording is stopped.
     * TODO: consider publishing by moving into MediaStore.
     */
    private SurfaceView mVideoPreview;
    private SurfaceHolder mSurfaceHolder = null;

    private boolean mStartPreviewFail = false;

    private int mStorageStatus = STORAGE_STATUS_OK;

    private MediaRecorder mMediaRecorder;
    private boolean mMediaRecorderRecording = false;
    private long mRecordingStartTime;
    // The video file that the hardware camera is about to record into
    // (or is recording into.)
    private String mVideoFilename;
    private ParcelFileDescriptor mVideoFileDescriptor;

    // The video file that has already been recorded, and that is being
    // examined by the user.
    private String mCurrentVideoFilename;
    private Uri mCurrentVideoUri;
    private ContentValues mCurrentVideoValues;

    private CamcorderProfile mProfile;

    // The video duration limit. 0 menas no limit.
    private int mMaxVideoDurationInMs;

    boolean mPausing = false;
    boolean mPreviewing = false; // True if preview is started.

    //    private Switcher mSwitcher;
    private boolean mRecordingTimeCountsDown = false;

    private final ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();

    private final Handler mHandler = new MainHandler();
    private Parameters mParameters;

    // multiple cameras support
    private int mNumberOfCameras;
    private int mCameraId = 0;

    private MainOrientationEventListener mOrientationListener;
    // The device orientation in degrees. Default is unknown.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    // The orientation compensation for icons and thumbnails. Degrees are in
    // counter-clockwise
    private int mOrientationCompensation = 0;
    private int mOrientationHint; // the orientation hint for video playback

    private BroadcastReceiver mReceiver = null;

    // This Handler is used to post message back onto the main thread of the
    // application
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case ENABLE_SHUTTER_BUTTON: //Enable shutter button
                    break;

                case CLEAR_SCREEN_DELAY: {
                    getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case UPDATE_RECORD_TIME: {
                    updateRecordingTime();
                    break;
                }

                default:
                    Log.v(TAG, "Unhandled message: " + msg.what);
                    break;
            }
        }
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                updateAndShowStorageHint(false);
                stopVideoRecording();
            } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                updateAndShowStorageHint(true);
//                updateThumbnailButton();
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                // SD card unavailable
                // handled in ACTION_MEDIA_EJECT
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
//                Toast.makeText(CamcorderActivity.this,
//                        getResources().getString(R.string.wait), Toast.LENGTH_LONG);
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                updateAndShowStorageHint(true);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window win = getWindow();

        // Overright the brightness settings if it is automatic
        int mode = Settings.System.getInt(
                getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.screenBrightness = DEFAULT_CAMERA_BRIGHTNESS;
            win.setAttributes(winParams);
        }

        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();

        readVideoPreferences();

        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        Thread startPreviewThread = new Thread(new Runnable() {
            public void run() {
                try {
                    mStartPreviewFail = false;
                    startPreview();
                } catch (CameraHardwareException e) {
                    // In eng build, we throw the exception so that test tool
                    // can detect it and report it
                    if ("eng".equals(Build.TYPE)) {
                        throw new RuntimeException(e);
                    }
                    mStartPreviewFail = true;
                }
            }
        });
        startPreviewThread.start();

        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_camcorder);

        resizeForPreviewAspectRatio();

        mVideoPreview = (SurfaceView) findViewById(R.id.sv_camcorder_preview);

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceCreated / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mVideoPreview.getHolder();
        holder.addCallback(this);
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            //This constant was deprecated in API level 11.
            //this is ignored, this value is set automatically when needed.
            //so only when API level bellow 11 need to be set.
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

//        mIsVideoCaptureIntent = isVideoCaptureIntent();
        mOrientationListener = new MainOrientationEventListener(CamcorderActivity.this);

        // Make sure preview is started.
        try {
            startPreviewThread.join();
            if (mStartPreviewFail) {
                showCameraErrorAndFinish();
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }

        mBtnRecord = (Button) findViewById(R.id.btn_recorder);
        mBtnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mIsRecording) {
//                    mRecorderHolder.stopRecord();
                    mIsRecording = false;
                    mBtnRecord.setText("Start");
                    mRecordingStartTime = new Date().getTime();
                } else {
//                    mRecorderHolder.initRecorder();
//                    mRecorderHolder.startRecord();
                    mIsRecording = true;
                    mBtnRecord.setText("Stop");
                }

            }
        });

    }

    private Button mBtnRecord;
    private boolean mIsRecording =  false;

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
//        SimpleDateFormat dateFormat = new SimpleDateFormat(
//                getString(R.string.video_file_name_format));
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss");

        return dateFormat.format(date);
    }

    private void showCameraErrorAndFinish() {
        Resources ress = getResources();
//        Util.showFatalErrorAndFinish(CamcorderActivity.this,
//                ress.getString(R.string.camera_error_title),
//                ress.getString(R.string.cannot_connect_camera));
    }

    public static int roundOrientation(int orientation) {
        return ((orientation + 45) / 90 * 90) % 360;
    }

    private class MainOrientationEventListener
            extends OrientationEventListener {
        public MainOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (mMediaRecorderRecording) return;
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = roundOrientation(orientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util.getDisplayRotation(CamcorderActivity.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
//                if (!mIsVideoCaptureIntent) {
//                    setOrientationIndicator(mOrientationCompensation);
//                }
//                mHeadUpDisplay.setOrientation(mOrientationCompensation);
            }
        }
    }

    private RecorderHolder mRecorderHolder = null;

    private void setOrientationIndicator(int degree) {
//        ((RotateImageView) findViewById(
//                R.id.review_thumbnail)).setDegree(degree);
//        ((RotateImageView) findViewById(
//                R.id.camera_switch_icon)).setDegree(degree);
//        ((RotateImageView) findViewById(
//                R.id.video_switch_icon)).setDegree(degree);
    }

    @Override
    protected void onStart() {
        super.onStart();
//        if (!mIsVideoCaptureIntent) {
//            mSwitcher.setSwitch(SWITCH_VIDEO);
//        }
    }

    private void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW, mCurrentVideoUri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
        }
    }


    private void onStopVideoRecording(boolean valid) {
//        if (mIsVideoCaptureIntent) {
//            if (mQuickCapture) {
//                stopVideoRecordingAndReturn(valid);
//            } else {
//                stopVideoRecordingAndShowAlert();
//            }
//        } else {
//            stopVideoRecordingAndGetThumbnail();
//        }
    }


//    private OnScreenHint mStorageHint;

    private void updateAndShowStorageHint(boolean mayHaveSd) {
        mStorageStatus = getStorageStatus(mayHaveSd);
        showStorageHint();
    }

    private void showStorageHint() {
        String errorMessage = null;
        switch (mStorageStatus) {
            case STORAGE_STATUS_NONE:
//                errorMessage = getString(R.string.no_storage);
                break;
            case STORAGE_STATUS_LOW:
//                errorMessage = getString(R.string.spaceIsLow_content);
                break;
            case STORAGE_STATUS_FAIL:
//                errorMessage = getString(R.string.access_sd_fail);
                break;
        }
    }

    private int getStorageStatus(boolean mayHaveSd) {
        long remaining = mayHaveSd ? getAvailableStorage() : NO_STORAGE_ERROR;
        if (remaining == NO_STORAGE_ERROR) {
            return STORAGE_STATUS_NONE;
        } else if (remaining == CANNOT_STAT_ERROR) {
            return STORAGE_STATUS_FAIL;
        }
        return remaining < LOW_STORAGE_THRESHOLD
                ? STORAGE_STATUS_LOW
                : STORAGE_STATUS_OK;
    }

    private void readVideoPreferences() {
        mProfile = CamcorderProfile.get(mCameraId, CamcorderProfile.QUALITY_HIGH);
    }

    private void resizeForPreviewAspectRatio() {
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPausing = false;

        // Start orientation listener as soon as possible because it takes
        // some time to get first orientation.
        mOrientationListener.enable();
        mVideoPreview.setVisibility(View.VISIBLE);
//        readVideoPreferences();
        resizeForPreviewAspectRatio();
        if (!mPreviewing && !mStartPreviewFail) {
            if (!restartPreview()) return;
        }
        keepScreenOnAwhile();

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        mReceiver = new MyBroadcastReceiver();
        registerReceiver(mReceiver, intentFilter);
        mStorageStatus = getStorageStatus(true);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                showStorageHint();
            }
        }, 200);

//        updateThumbnailButton();
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            mCameraDevice.setPreviewDisplay(holder);
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void startPreview() throws CameraHardwareException {
        Log.v(TAG, "startPreview");
        if (mCameraDevice == null) {
            // If the activity is paused and resumed, camera device has been
            // released and we need to open the camera.
            mCameraDevice = CameraHolder.instance().open(mCameraId);
        }

        if (mPreviewing == true) {
            mCameraDevice.stopPreview();
            mPreviewing = false;
        }
        setPreviewDisplay(mSurfaceHolder);
        Util.setCameraDisplayOrientation(this, mCameraId, mCameraDevice);
        setCameraParameters();

//        mRecorderHolder = new RecorderHolder(mCameraDevice, mVideoPreview);

        initRecorder();
        mCameraDevice.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (yuvIplimage != null && mIsRecording) {
                    yuvIplimage.getByteBuffer().put(data);
                    //yuvIplimage = rotateImage(yuvIplimage.asCvMat(), 90).asIplImage();
                    Log.v(TAG, "Writing Frame");
                    try {
                        System.out.println(System.currentTimeMillis() - mRecordingStartTime);
//                        if (during < 6000) {
                            recorder.setTimestamp(new Date().getTime() - mRecordingStartTime);
                            recorder.record(yuvIplimage);
//                        }
                    } catch (FFmpegFrameRecorder.Exception e) {
                        Log.v(TAG, e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        });

        try {
            mCameraDevice.startPreview();
            mPreviewing = true;
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
    }


    private opencv_core.IplImage yuvIplimage = null;
    private FFmpegFrameRecorder recorder = null;

    public void initRecorder() {
        String folder = Environment.getExternalStorageDirectory()
                .getAbsolutePath() + "/magiccamcorder/video/temp";
        String ffmpeg_link = folder + "/" + "video.mp4";
        Log.w(TAG, "init recorder");

        Camera.Size previewSize = mCameraDevice.getParameters().getPreviewSize();
        if (yuvIplimage == null) {
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
        // Set in the surface changed method
        recorder.setFrameRate(24);

        Log.i(TAG, "recorder initialize success");

        try {
            recorder.start();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private boolean restartPreview() {
        try {
            startPreview();
        } catch (CameraHardwareException e) {
            showCameraErrorAndFinish();
            return false;
        }
        return true;
    }

    private void closeCamera() {
        Log.v(TAG, "closeCamera");
        if (mCameraDevice == null) {
            Log.d(TAG, "already stopped.");
            return;
        }
        // If we don't lock the camera, release() will fail.
        mCameraDevice.lock();
        CameraHolder.instance().release();
        mCameraDevice = null;
        mPreviewing = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPausing = true;

        // Hide the preview now. Otherwise, the preview may be rotated during
        // onPause and it is annoying to users.
        mVideoPreview.setVisibility(View.INVISIBLE);

        // This is similar to what mShutterButton.performClick() does,
        // but not quite the same.
        if (mMediaRecorderRecording) {
//            if (mIsVideoCaptureIntent) {
//                stopVideoRecording();
//                showAlert();
//            } else {
//                stopVideoRecordingAndGetThumbnail();
//            }
        } else {
            stopVideoRecording();
        }
        closeCamera();

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        resetScreenOn();

//        if (!mIsVideoCaptureIntent) {
//            mThumbController.storeData(ImageManager.getLastVideoThumbPath());
//        }
//
//        if (mStorageHint != null) {
//            mStorageHint.cancel();
//            mStorageHint = null;
//        }

        mOrientationListener.disable();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (!mMediaRecorderRecording) keepScreenOnAwhile();
    }

    @Override
    public void onBackPressed() {
        if (mPausing) {
            return;
        }
        if (mMediaRecorderRecording) {
            onStopVideoRecording(false);
        } else {
            super.onBackPressed();
        }
    }

    /*@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Do not handle any key if the activity is paused.
        if (mPausing) {
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                if (event.getRepeatCount() == 0) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MENU:
                if (mMediaRecorderRecording) {
                    onStopVideoRecording(true);
                    return true;
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }*/

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            Log.d(TAG, "holder.getSurface() == null");
            return;
        }

        mSurfaceHolder = holder;

        if (mPausing) {
            // We're pausing, the screen is off and we already stopped
            // video recording. We don't want to start the camera again
            // in this case in order to conserve power.
            // The fact that surfaceChanged is called _after_ an onPause appears
            // to be legitimate since in that case the lockscreen always returns
            // to portrait orientation possibly triggering the notification.
            return;
        }

        // The mCameraDevice will be null if it is fail to connect to the
        // camera hardware. In this case we will show a dialog and then
        // finish the activity, so it's OK to ignore it.
        if (mCameraDevice == null) {
            return;
        }

        // Set preview display if the surface is being created. Preview was
        // already started.
        if (holder.isCreating()) {
            setPreviewDisplay(holder);
        } else {
            stopVideoRecording();
            restartPreview();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        initRecorder();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
    }

    /*private void gotoGallery() {
        MenuHelper.gotoCameraVideoGallery(this);
    }*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    private boolean isVideoCaptureIntent() {
        String action = getIntent().getAction();
        return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
    }

    private void doReturnToCaller(boolean valid) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = RESULT_OK;
            resultIntent.setData(mCurrentVideoUri);
        } else {
            resultCode = RESULT_CANCELED;
        }
        setResult(resultCode, resultIntent);
        finish();
    }

    /**
     * Returns
     *
     * @return number of bytes available, or an ERROR code.
     */
    private static long getAvailableStorage() {
//        try {
//            if (!ImageManager.hasStorage()) {
//                return NO_STORAGE_ERROR;
//            } else {
//                String storageDirectory =
//                        Environment.getExternalStorageDirectory().toString();
//                StatFs stat = new StatFs(storageDirectory);
//                return (long) stat.getAvailableBlocks()
//                        * (long) stat.getBlockSize();
//            }
//        } catch (Exception ex) {
//            // if we can't stat the filesystem then we don't know how many
//            // free bytes exist. It might be zero but just leave it
//            // blank since we really don't know.
//            Log.e(TAG, "Fail to access sdcard", ex);
//            return CANNOT_STAT_ERROR;
//        }
        return 10000L;
    }

    private void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
                Log.v(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }


    // Prepares media recorder.
    private void initializeRecorder() {
        Log.v(TAG, "initializeRecorder");
        // If the mCameraDevice is null, then this activity is going to finish
        if (mCameraDevice == null) return;

        if (mSurfaceHolder == null) {
            Log.v(TAG, "Surface holder is null. Wait for surface changed.");
            return;
        }

        Intent intent = getIntent();
        Bundle myExtras = intent.getExtras();

        long requestedSizeLimit = 0;
//        if (mIsVideoCaptureIntent && myExtras != null) {
//            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
//            if (saveUri != null) {
//                try {
//                    mVideoFileDescriptor =
//                            mContentResolver.openFileDescriptor(saveUri, "rw");
//                    mCurrentVideoUri = saveUri;
//                } catch (java.io.FileNotFoundException ex) {
//                    // invalid uri
//                    Log.e(TAG, ex.toString());
//                }
//            }
//            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
//        }
        mMediaRecorder = new MediaRecorder();

        // Unlock the camera object before passing it to media recorder.
        mCameraDevice.unlock();
        mMediaRecorder.setCamera(mCameraDevice);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setProfile(mProfile);
//        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);

        // Set output file.
        if (mStorageStatus != STORAGE_STATUS_OK) {
            mMediaRecorder.setOutputFile("/dev/null");
        } else {
            // Try Uri in the intent first. If it doesn't exist, use our own
            // instead.
            if (mVideoFileDescriptor != null) {
                mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
                try {
                    mVideoFileDescriptor.close();
                } catch (IOException e) {
                    Log.e(TAG, "Fail to close fd", e);
                }
            } else {
                createVideoPath();
                mMediaRecorder.setOutputFile(mVideoFilename);
            }
        }

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        // Set maximum file size.
        // remaining >= LOW_STORAGE_THRESHOLD at this point, reserve a quarter
        // of that to make it more likely that recording can complete
        // successfully.
        long maxFileSize = getAvailableStorage() - LOW_STORAGE_THRESHOLD / 4;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }

        try {
            mMediaRecorder.setMaxFileSize(maxFileSize);
        } catch (RuntimeException exception) {
            // We are going to ignore failure of setMaxFileSize here, as
            // a) The composer selected may simply not support it, or
            // b) The underlying media framework may not handle 64-bit range
            // on the size restriction.
        }

        // See android.hardware.Camera.Parameters.setRotation for
        // documentation.
        int rotation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - mOrientation + 360) % 360;
            } else {  // back-facing camera
                rotation = (info.orientation + mOrientation) % 360;
            }
        }
        mMediaRecorder.setOrientationHint(rotation);
        mOrientationHint = rotation;

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare failed for " + mVideoFilename, e);
            releaseMediaRecorder();
            throw new RuntimeException(e);
        }

//        mMediaRecorder.setOnErrorListener(this);
//        mMediaRecorder.setOnInfoListener(this);
    }

    private void releaseMediaRecorder() {
        Log.v(TAG, "Releasing media recorder.");
        if (mMediaRecorder != null) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        // Take back the camera object control from media recorder. Camera
        // device may be null if the activity is paused.
        if (mCameraDevice != null) mCameraDevice.lock();
    }

    private void createVideoPath() {
//        long dateTaken = System.currentTimeMillis();
//        String title = createName(dateTaken);
//        String filename = title + ".3gp"; // Used when emailing.
//        String cameraDirPath = ImageManager.CAMERA_IMAGE_BUCKET_NAME;
//        String filePath = cameraDirPath + "/" + filename;
//        File cameraDir = new File(cameraDirPath);
//        cameraDir.mkdirs();
//        ContentValues values = new ContentValues(7);
//        values.put(Video.Media.TITLE, title);
//        values.put(Video.Media.DISPLAY_NAME, filename);
//        values.put(Video.Media.DATE_TAKEN, dateTaken);
//        values.put(Video.Media.MIME_TYPE, "video/3gpp");
//        values.put(Video.Media.DATA, filePath);
//        mVideoFilename = filePath;
//        Log.v(TAG, "Current camera video filename: " + mVideoFilename);
//        mCurrentVideoValues = values;
    }

    private void registerVideo() {
        if (mVideoFileDescriptor == null) {
            Uri videoTable = Uri.parse("content://media/external/video/media");
            mCurrentVideoValues.put(Video.Media.SIZE,
                    new File(mCurrentVideoFilename).length());
//            try {
//                mCurrentVideoUri = mContentResolver.insert(videoTable,
//                        mCurrentVideoValues);
//            } catch (Exception e) {
//                // We failed to insert into the database. This can happen if
//                // the SD card is unmounted.
//                mCurrentVideoUri = null;
//                mCurrentVideoFilename = null;
//            } finally {
//                Log.v(TAG, "Current video URI: " + mCurrentVideoUri);
//            }
        }
        mCurrentVideoValues = null;
    }

    private void deleteCurrentVideo() {
        if (mCurrentVideoFilename != null) {
            deleteVideoFile(mCurrentVideoFilename);
            mCurrentVideoFilename = null;
        }
        if (mCurrentVideoUri != null) {
//            mContentResolver.delete(mCurrentVideoUri, null, null);
            mCurrentVideoUri = null;
        }
        updateAndShowStorageHint(true);
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    private void switchCameraId(int cameraId) {
        if (mPausing) return;
        mCameraId = cameraId;
//        CameraSettings.writePreferredCameraId(mPreferences, cameraId);

        // This is similar to what mShutterButton.performClick() does,
        // but not quite the same.
        if (mMediaRecorderRecording) {
//            if (mIsVideoCaptureIntent) {
//                stopVideoRecording();
//                showAlert();
//            } else {
//                stopVideoRecordingAndGetThumbnail();
//            }
        } else {
            stopVideoRecording();
        }
        closeCamera();

        // Reload the preferences.
//        mPreferences.setLocalId(this, mCameraId);
//        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        // Read media profile again because camera id is changed.
        readVideoPreferences();
        resizeForPreviewAspectRatio();
        restartPreview();

        // Reload the UI.
//        initializeHeadUpDisplay();
    }

    // from MediaRecorder.OnErrorListener
    public void onError(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            stopVideoRecording();
            updateAndShowStorageHint(true);
        }
    }

    /*
     * Make sure we're not recording music playing in the background, ask the
     * MediaPlaybackService to pause playback.
     */
    private void pauseAudioPlayback() {
        // Shamelessly copied from MediaPlaybackService.java, which
        // should be public, but isn't.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");

        sendBroadcast(i);
    }

    private void startVideoRecording() {
        Log.v(TAG, "startVideoRecording");
        if (mStorageStatus != STORAGE_STATUS_OK) {
            Log.v(TAG, "Storage issue, ignore the start request");
            return;
        }

        initializeRecorder();
        if (mMediaRecorder == null) {
            Log.e(TAG, "Fail to initialize media recorder");
            return;
        }

        pauseAudioPlayback();

        try {
            mMediaRecorder.start(); // Recording is now started
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not start media recorder. ", e);
            releaseMediaRecorder();
            return;
        }
//        mHeadUpDisplay.setEnabled(false);

        mMediaRecorderRecording = true;
        mRecordingStartTime = SystemClock.uptimeMillis();
        updateRecordingIndicator(false);
        // Rotate the recording time.
        updateRecordingTime();
        keepScreenOn();
    }

    private void updateRecordingIndicator(boolean showRecording) {
//        int drawableId =
//                showRecording ? R.drawable.btn_ic_video_record
//                        : R.drawable.btn_ic_video_record_stop;
//        Drawable drawable = getResources().getDrawable(drawableId);
//        mShutterButton.setImageDrawable(drawable);
    }

    private void showAlert() {
//        fadeOut(findViewById(R.id.shutter_button));
        if (mCurrentVideoFilename != null) {
            Bitmap src = ThumbnailUtils.createVideoThumbnail(
                    mCurrentVideoFilename, Video.Thumbnails.MINI_KIND);
            // MetadataRetriever already rotates the thumbnail. We should rotate
            // it back (and mirror if it is front-facing camera).
            CameraInfo[] info = CameraHolder.instance().getCameraInfo();
            if (info[mCameraId].facing == CameraInfo.CAMERA_FACING_BACK) {
                src = Util.rotateAndMirror(src, -mOrientationHint, false);
            } else {
                src = Util.rotateAndMirror(src, -mOrientationHint, true);
            }
        }
//        int[] pickIds = {R.id.btn_retake, R.id.btn_done, R.id.btn_play};
//        for (int id : pickIds) {
//            View button = findViewById(id);
//            fadeIn(((View) button.getParent()));
//        }
    }


    private static void fadeIn(View view) {
        view.setVisibility(View.VISIBLE);
        Animation animation = new AlphaAnimation(0F, 1F);
        animation.setDuration(500);
        view.startAnimation(animation);
    }

    private static void fadeOut(View view) {
        view.setVisibility(View.INVISIBLE);
        Animation animation = new AlphaAnimation(1F, 0F);
        animation.setDuration(500);
        view.startAnimation(animation);
    }

    private void stopVideoRecording() {
        Log.v(TAG, "stopVideoRecording");
        if (mMediaRecorderRecording) {
            boolean needToRegisterRecording = false;
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            try {
                mMediaRecorder.stop();
                mCurrentVideoFilename = mVideoFilename;
                Log.v(TAG, "Setting current video filename: "
                        + mCurrentVideoFilename);
                needToRegisterRecording = true;
            } catch (RuntimeException e) {
                Log.e(TAG, "stop fail: " + e.getMessage());
                deleteVideoFile(mVideoFilename);
            }
            mMediaRecorderRecording = false;
//            mHeadUpDisplay.setEnabled(true);
            updateRecordingIndicator(true);
            keepScreenOnAwhile();
            if (needToRegisterRecording && mStorageStatus == STORAGE_STATUS_OK) {
                registerVideo();
            }
            mVideoFilename = null;
            mVideoFileDescriptor = null;
        }
        releaseMediaRecorder();  // always release media recorder
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private void keepScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void acquireVideoThumb() {
        Bitmap videoFrame = ThumbnailUtils.createVideoThumbnail(
                mCurrentVideoFilename, Video.Thumbnails.MINI_KIND);
//        mThumbController.setData(mCurrentVideoUri, videoFrame);
//        mThumbController.updateDisplayIfNeeded();
    }

    /**
     * Update the time show in when recording.
     */
    private void updateRecordingTime() {
        if (!mMediaRecorderRecording) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime;

        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0
                && delta >= mMaxVideoDurationInMs - 60000);

        long next_update_delay = 1000 - (delta % 1000);
        long seconds;
        if (countdownRemainingTime) {
            delta = Math.max(0, mMaxVideoDurationInMs - delta);
            seconds = (delta + 999) / 1000;
        } else {
            seconds = delta / 1000; // round to nearest
        }

        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        String secondsString = Long.toString(remainderSeconds);
        if (secondsString.length() < 2) {
            secondsString = "0" + secondsString;
        }
        String minutesString = Long.toString(remainderMinutes);
        if (minutesString.length() < 2) {
            minutesString = "0" + minutesString;
        }
        String text = minutesString + ":" + secondsString;
        if (hours > 0) {
            String hoursString = Long.toString(hours);
            if (hoursString.length() < 2) {
                hoursString = "0" + hoursString;
            }
            text = hoursString + ":" + text;
        }

        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

//            int color = getResources().getColor(countdownRemainingTime
//                    ? R.color.recording_time_remaining_text
//                    : R.color.recording_time_elapsed_text);
//
//            mRecordingTimeView.setTextColor(color);
        }

        mHandler.sendEmptyMessageDelayed(
                UPDATE_RECORD_TIME, next_update_delay);
    }

    /**
     * Whether the setting value is supported.
     * @param value setting value.
     * @param supported the camera supported values.
     * @return
     */
    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    /**
     * Set camera setting parameters.
     */
    private void setCameraParameters() {
        mParameters = mCameraDevice.getParameters();

        mParameters.setPreviewSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mParameters.setPreviewFrameRate(mProfile.videoFrameRate);

        // Set flash mode.
//        String flashMode = mPreferences.getString(
//                CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
//                getString(R.string.pref_camera_video_flashmode_default));
        List<String> supportedFlash = mParameters.getSupportedFlashModes();
        //FIXME make flash mode can be set.
        String flashMode = Parameters.FLASH_MODE_OFF;
        if (isSupported(flashMode, supportedFlash)) {
            mParameters.setFlashMode(flashMode);
        } else {
            flashMode = mParameters.getFlashMode();
            if (flashMode == null) {
//                flashMode = getString(
//                        R.string.camera_flashmode_no_flash);
            }
        }

        // Set white balance parameter.
//        String whiteBalance = mPreferences.getString(
//                CameraSettings.KEY_WHITE_BALANCE,
//                getString(R.string.pref_camera_whitebalance_default));
//        if (isSupported(whiteBalance,
//                mParameters.getSupportedWhiteBalance())) {
//            mParameters.setWhiteBalance(whiteBalance);
//        } else {
//            whiteBalance = mParameters.getWhiteBalance();
//            if (whiteBalance == null) {
//                whiteBalance = Parameters.WHITE_BALANCE_AUTO;
//            }
//        }

        // Set color effect parameter.
//        String colorEffect = mPreferences.getString(
//                CameraSettings.KEY_COLOR_EFFECT,
//                getString(R.string.pref_camera_coloreffect_default));
//        if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
//            mParameters.setColorEffect(colorEffect);
//        }

        mCameraDevice.setParameters(mParameters);
        // Keep preview size up to date.
        mParameters = mCameraDevice.getParameters();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);

        // If the camera resumes behind the lock screen, the orientation
        // will be portrait. That causes OOM when we try to allocation GPU
        // memory for the GLSurfaceView again when the orientation changes. So,
        // we delayed initialization of HeadUpDisplay until the orientation
        // becomes landscape.
//        changeHeadUpDisplayState();
    }

    private void resetCameraParameters() {
        // We need to restart the preview if preview size is changed.
        Size size = mParameters.getPreviewSize();
        if (size.width != mProfile.videoFrameWidth
                || size.height != mProfile.videoFrameHeight) {
            // It is assumed media recorder is released before
            // onSharedPreferenceChanged, so we can close the camera here.
            closeCamera();
            resizeForPreviewAspectRatio();
            restartPreview(); // Parameters will be set in startPreview().
        } else {
            setCameraParameters();
        }
    }
}
