package com.sleepycatstudios.slowy;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.RecoverySystem;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ProgressBar;

public class Render extends AppCompatActivity {
    ProgressDialog progressDialog;

    public IProgressListener progressListener = new IProgressListener() {
        @Override
        public void onMediaStart() {

            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setProgress(0);
                        progressDialog.show();
                    }
                });
            } catch (Exception e) {
            }
        }

        @Override
        public void onMediaProgress(float progress) {

            final float mediaProgress = progress;

            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setProgress((int) (progressDialog.getMax() * mediaProgress));
                    }
                });
            } catch (Exception e) {
            }
        }

        @Override
        public void onMediaDone() {
            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //progressDialog.hide();
                        navigateToFinish();
                    }
                });
            } catch (Exception e) {
            }
        }

        @Override
        public void onMediaPause() {
        }

        @Override
        public void onMediaStop() {
        }

        @Override
        public void onError(Exception exception) {
            try {
                final Exception e = exception;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String errorMessage = (e.getMessage() != null) ? e.getMessage() : e.toString();
                        AlertDialog.Builder alertDialog = new android.support.v7.app.AlertDialog.Builder(Render.this);
                        alertDialog.setMessage("Error. " + errorMessage);
                        alertDialog.create();
                        alertDialog.show();
                    }
                });
            } catch (Exception e) {
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_render);
        progressDialog = new ProgressDialog(Render.this);
        progressDialog.setTitle(R.string.rendering_progress_dialog_title);
        //progressDialog.setMessage(R.string.rendering_progress_dialog_message);
        progressDialog.setMessage("Please wait until complete...");
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setMax(100);
        VideoProcessor.setProgressListner(progressListener);
        VideoProcessor.start();
    }

    private void navigateToFinish()
    {
        Intent navIntent = new Intent(this, Finish.class);
        startActivity(navIntent);
    }
}
