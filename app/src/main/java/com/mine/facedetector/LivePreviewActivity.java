/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mine.facedetector;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.annotation.KeepName;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Live preview demo for ML Kit APIs.
 */
@KeepName
public final class LivePreviewActivity extends AppCompatActivity {

    Handler handler = new Handler();
    OutputStream outputStream;
    Runnable runnable;

    MediaRecorder mediaRecorder;

    private static final int REQUEST_CODE=1000;
    private static final int REQUEST_PERMISSION=1000;
    private static final SparseIntArray ORIENTATIONS=new SparseIntArray();
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    //private MediaProjectionCallback mediaProjectionCallback;

    private int mScreenDensity;
    private static final int DISPLAY_HEIGHT=1200;
    private static final int DISPLAY_WIDTH=720;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    private ToggleButton toggleButton;
    private VideoView videoView;
    private String videoUri="";
    SurfaceHolder holder;
    boolean recording = false;
    private boolean isPrepared = false;
    int videoNumber = 0;
    //GestureDetector gestureDetector;
    int delay=0001;

    private static final String TAG = "LivePreviewActivity";

    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private Boolean isFrontFacing = true;
    private ImageView imgCameraCapture;
    private ImageView imgCapture;
    private ImageView imgDone;
    private boolean isPhotoDetected = false;
    public static boolean isPhotoClicked = false;
    Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_vision_live_preview);

        DisplayMetrics metrics=new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity=metrics.densityDpi;
        mediaRecorder = new MediaRecorder();
        mediaProjectionManager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        videoView=findViewById(R.id.videoView);

        preview = findViewById(R.id.preview_view);
        if (preview == null) {
            Log.d(TAG, "Preview is null");
        }
        graphicOverlay = findViewById(R.id.graphic_overlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }
        //FACE_DETECTION

        ImageView facingSwitch = findViewById(R.id.facing_switch);
        imgCameraCapture = findViewById(R.id.imgCameraCapture);
        imgCapture = findViewById(R.id.imgCapture);
        imgDone = findViewById(R.id.imgDone);

        facingSwitch.setOnClickListener(v -> {
          isFrontFacing = !isFrontFacing;
          toggleCamera();
        });

        imgCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(bitmap != null){
                    Intent intent = new Intent(LivePreviewActivity.this, FullImageView.class);
                    startActivity(intent);
                }
            }
        });
        imgDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(bitmap != null){
                    Intent intent = new Intent(LivePreviewActivity.this, FullImageView.class);
                    startActivity(intent);
                }
            }
        });


        startrepeating();


        createCameraSource();
        toggleCamera();
    }



    public void startrepeating()
    {
        mRunnable.run();

    }
    private Runnable mRunnable= new Runnable() {
        @Override
        public void run() {
            if(isPhotoDetected){
                imgCameraCapture.setVisibility(View.VISIBLE);
                imgCameraCapture.setOnClickListener(v -> {

                    isPhotoClicked = true;
                    bitmap = loadBitmapFromView(graphicOverlay);
                    imgCapture.setImageBitmap(bitmap);
                    imgCapture.setVisibility(View.VISIBLE);
                    imgDone.setVisibility(View.VISIBLE);
                    createImageFromBitmap(bitmap);
                    saveImage(UUID.randomUUID().toString(),bitmap);
                    handler.removeCallbacks(mRunnable);
            /*else{
                Toast.makeText(this, "Image capture only when face detected!", Toast.LENGTH_SHORT).show();
            }*/

                });
                imgCameraCapture.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        longclick();
                       /* Snackbar.make(preview,"Permissions",Snackbar.LENGTH_INDEFINITE)
                                .setAction("ENABLE", new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        ActivityCompat.requestPermissions(LivePreviewActivity.this,new String[]{
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                Manifest.permission.RECORD_AUDIO
                                        },REQUEST_PERMISSION);
                                    }
                                }).show();
                        initRecorder();
                        recordScreen();*/
                        Toast.makeText(LivePreviewActivity.this, "Video saved", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
            }
            else{
                imgCameraCapture.setVisibility(View.INVISIBLE);
            }
            handler.postDelayed(this,0001);
        }

    };
    /*private void recordScreen(){

        if(mediaProjection==null){
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),REQUEST_CODE);
            return;
        }
        virtualDisplay=createVirtualDisplay();
        mediaRecorder.start();
    }


    private VirtualDisplay createVirtualDisplay() {
        return mediaProjection.createVirtualDisplay("LivePreviewActivity",DISPLAY_WIDTH,DISPLAY_HEIGHT,mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(),null,null);
    }
    private void initRecorder() {
        try{
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

            videoUri=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    +new StringBuilder("/").append(new SimpleDateFormat("EDMT_RECORD_dd-MM-yyyy-hh_mm_ss")
                    .format(new Date())).append(".mp4").toString();

            mediaRecorder.setOutputFile(videoUri);
            mediaRecorder.setVideoSize(DISPLAY_WIDTH,DISPLAY_HEIGHT);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setVideoEncodingBitRate(512*1000);
            mediaRecorder.setVideoFrameRate(30);

            int rotation=getWindowManager().getDefaultDisplay().getRotation();
            int orientation=ORIENTATIONS.get(rotation*90);
            mediaRecorder.setOrientationHint(orientation);
            mediaRecorder.prepare();

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }*/



    public void longclick(){

       /* File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CameraSample");

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        CamcorderProfile cpHigh = CamcorderProfile
                .get(CamcorderProfile.QUALITY_HIGH);
        mediaRecorder.setProfile(cpHigh);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        //CGlobal.VIDEO_RECORD_PATH = CGlobal.VIDEO_HOME_PATH + "VID_" + timeStamp;
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "VID_"+ timeStamp + ".mp4");

        mediaRecorder.setOutputFile(mediaFile+".mp4");
        mediaRecorder.setMaxDuration(50000); // 50 seconds
        mediaRecorder.setMaxFileSize(5000000); */
       Intent intent=new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT,10);
        startActivityForResult(intent,1);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        VideoView videoView = new VideoView(this);
        videoView.setVideoURI(data.getData());
        videoView.start();
        builder.setView(videoView).show();

    }




    public boolean saveImage(String imgName, Bitmap bmap){
        Uri ImageCollection=null;
        ContentResolver resolver= getContentResolver();
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
            ImageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }
        else{
            ImageCollection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        ContentValues contentValues= new ContentValues();
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME,imgName+".jpg");
        contentValues.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        Uri imageUri= resolver.insert(ImageCollection,contentValues);

        try {
            outputStream=resolver.openOutputStream(Objects.requireNonNull(imageUri));
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
            Objects.requireNonNull(outputStream);
            Toast.makeText(getApplicationContext(), "Image saved successfully", Toast.LENGTH_SHORT).show();
            return true;
        }
        catch (Exception e){
            Toast.makeText(getApplicationContext(), "Image not saved", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
        return false;
    }
    public String createImageFromBitmap(Bitmap bitmap) {
        String fileName = "myImage";//no .png or .jpg needed
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
            FileOutputStream fo = openFileOutput(fileName, Context.MODE_PRIVATE);
            fo.write(bytes.toByteArray());
            // remember close file output
            fo.close();
        } catch (Exception e) {
            e.printStackTrace();
            fileName = null;
        }
        return fileName;
    }

    private void toggleCamera(){
      Log.d(TAG, "Set facing");
      if (cameraSource != null) {
        if (isFrontFacing) {
          cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
        } else {
          cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
        }
      }
      preview.stop();
      startCameraSource();
    }


    private void createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }
        cameraSource.setMachineLearningFrameProcessor(new FaceDetectorProcessor(this, new OnFaceDetectedListener() {
            @Override
            public void onFaceDetected(Boolean isDetected) {
                isPhotoDetected = isDetected;
                if(isDetected){
                    imgCameraCapture.setImageResource(R.drawable.ic_camera_capture);
                }else{
                    imgCameraCapture.setImageResource(R.drawable.ic_baseline_camera_grey);
                }
            }

            @Override
            public void onMultipleFaceDetected() {

            }
        }));

    }

    public static Bitmap loadBitmapFromView(View v) {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.draw(c);

        return b;
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        createCameraSource();
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

   /* private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            mediaRecorder.stop();
            mediaRecorder.reset();

            mediaProjection=null;
            stopRecordScreen();
            super.onStop();

        }

    }

    private void stopRecordScreen() {
        if(virtualDisplay==null)
            return;
        virtualDisplay.release();
        destroyMediaProjection();
    }

    private void destroyMediaProjection() {
        if(mediaProjection!=null){
            mediaProjection.unregisterCallback(mediaProjectionCallback);
            mediaProjection.stop();
            mediaProjection=null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }*/
}
