package com.sleepycatstudios.slowy;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.VideoView;

import com.sleepycatstudios.slowy.VideoProcessor;

public class LoadVideo extends AppCompatActivity {

    VideoView videoView;
    private static final int CHOOSE_VIDEO_REQUEST_CODE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_load_video);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        videoView = (VideoView) findViewById(R.id.videoView);
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                videoView.start();
            }
        });
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                int videoWidth = mp.getVideoWidth();
                int videoHeight = mp.getVideoHeight();

                //Get the width of the screen
                int screenWidth = getWindowManager().getDefaultDisplay().getWidth();
                int screenHeight = getWindowManager().getDefaultDisplay().getHeight();

                //Get the SurfaceView layout parameters
                android.view.ViewGroup.LayoutParams lp = videoView.getLayoutParams();

                //Set the width of the SurfaceView to the width of the screen
                //lp.width = screenWidth;
                lp.height = screenHeight;

                //Set the height of the SurfaceView to match the aspect ratio of the video
                //be sure to cast these as floats otherwise the calculation will likely be 0
                //lp.height = (int) (((float)videoHeight / (float)videoWidth) * (float)screenWidth);
                lp.width = (int) (((float)videoWidth / (float) videoHeight) * (float) screenHeight);
                //Commit the layout parameters
                videoView.setLayoutParams(lp);
            }
        });
        videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.slowmobackground));
        videoView.start();


    }

    @Override
    protected void onResume() {
        videoView.start();
        super.onResume();
    }

    public void pickFile(View v) {
        Intent pickVideoIntent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickVideoIntent, CHOOSE_VIDEO_REQUEST_CODE);
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.
        if (requestCode == CHOOSE_VIDEO_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                VideoProcessor.loadVideo(uri);
                navigateToSettings();
            }
        }
    }

    private void navigateToSettings()
    {
        videoView.pause();
        Intent navIntent = new Intent(this, VideoSettings.class);
        startActivity(navIntent);
    }
}
