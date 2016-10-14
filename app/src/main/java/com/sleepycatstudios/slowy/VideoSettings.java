package com.sleepycatstudios.slowy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.VideoView;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;

import com.github.channguyen.rsv.RangeSliderView;


public class VideoSettings extends AppCompatActivity {
    private RangeSliderView speedSeekbar;
    double fps = 30;
    float speedMultiplier = 0.5f;
    SurfaceView videoSurfaceView = null;
    SeekBar videoProgerssSeekbar = null;
    FloatingActionButton slowmoButton = null;
    Button clearSlowmoButton = null;
    IProgressListener progressListener;
    VideoSlowmoEditor editor = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_settings);
        speedSeekbar = (RangeSliderView) findViewById(R.id.rsvSpeedSeekbar);
        videoSurfaceView = (SurfaceView) findViewById(R.id.videoSurfaceView);
        videoProgerssSeekbar = (SeekBar) findViewById(R.id.videoProgerssSeekbar);
        slowmoButton = (FloatingActionButton) findViewById(R.id.slowmoButton);
        clearSlowmoButton = (Button) findViewById(R.id.clearSlowmoButton);

        progressListener = new IProgressListener() {
            @Override
            public void onMediaStart() {

            }

            @Override
            public void onMediaProgress(final float progress) {
                final float mediaProgress = progress;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        videoProgerssSeekbar.setProgress(Math.round(mediaProgress * 100));
                    }
                });
            }

            @Override
            public void onMediaDone() {

            }

            @Override
            public void onMediaPause() {

            }

            @Override
            public void onMediaStop() {

            }

            @Override
            public void onError(Exception exception) {

            }
        };

        editor = new VideoSlowmoEditor(this, videoSurfaceView);
        editor.setProgressListener(progressListener);

        slowmoButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        if (editor != null)
                            editor.slowerSpeed();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (editor != null)
                            editor.normalSpeed();
                        break;
                }
                return false;
            }
        });

        clearSlowmoButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        break;
                    case MotionEvent.ACTION_UP:
                        if (editor != null)
                            editor.clearSlowmo();
                        break;
                }
                return false;
            }
        });

        videoProgerssSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //if (fromUser)
                    //editor.seekTo((float) progress / 100);

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                editor.pause();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                editor.seekTo((float) seekBar.getProgress() / 100);
                editor.start();
            }
        });


        speedSeekbar.setOnSlideListener(new RangeSliderView.OnSlideListener() {
            public void onSlide(int index) {
                if (index != 0) {
                    speedMultiplier = 0.5f / (2 * index);
                } else {
                    speedMultiplier = 0.5f;
                }
                editor.setSlowmoSpeed(speedMultiplier);
            }
        });
    }

    class PlaybackControllerTask extends AsyncTask<Void, Integer, Void> {

        private boolean isPlaying = true;

        protected void stop() {
            isPlaying = false;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            while (isPlaying) {
                try {
                    int timeToNextFrame = (int) (1000 / fps * (1 / speedMultiplier));//TODO
                    publishProgress(1);
                    Thread.sleep(15);
                    publishProgress(0);
                    Thread.sleep(timeToNextFrame);//change this to affect playback speed, default is 15
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (values[0] == 0) {
                //videoView.pause();
            } else {
                //videoView.start();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        final MenuItem menuItem = menu.add(Menu.NONE, 1000, Menu.NONE, "Next");
        MenuItemCompat.setShowAsAction(menuItem, MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1000)
        {
            VideoProcessor.setTimeScale(speedMultiplier);
            //playbackControllerTask.stop();
            //videoView.stopPlayback();
            navigateToRender();
        }
        return true;
    }

    private void navigateToRender()
    {
        VideoProcessor.setSlowFrames(editor.getSlowFrames());
        editor.pause();
        Intent navIntent = new Intent(this, Render.class);
        startActivity(navIntent);
    }
}
