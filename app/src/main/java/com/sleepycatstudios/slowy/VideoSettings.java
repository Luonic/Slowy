package com.sleepycatstudios.slowy;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.VideoView;

import com.github.channguyen.rsv.RangeSliderView;


public class VideoSettings extends AppCompatActivity {
    private RangeSliderView speedSeekbar;
    private PlaybackControllerTask playbackControllerTask;

    double fps = 30;
    float speedMultiplier = 0.5f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_settings);
        speedSeekbar = (RangeSliderView) findViewById(R.id.rsvSpeedSeekbar);
        speedSeekbar.setOnSlideListener(new RangeSliderView.OnSlideListener() {
            public void onSlide(int index) {
                if (index != 0) {
                    speedMultiplier = 0.5f / (2 * index);
                } else {
                    speedMultiplier = 0.5f;
                }
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
        Intent navIntent = new Intent(this, Render.class);
        startActivity(navIntent);
    }
}
