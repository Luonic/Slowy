package com.sleepycatstudios.slowy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.SeekBar;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;

import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.merge;
import static org.bytedeco.javacpp.opencv_core.solveLP;
import static org.bytedeco.javacpp.opencv_core.split;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2RGBA;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGRA2RGBA;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;

public class VideoSlowmoEditor extends SurfaceView implements SurfaceHolder.Callback {

    enum PlaybackMode {
        NORMAL,
        SLOW
    }

    String TAG = "VideoSlowmoEditor";
    SurfaceView surfaceView;
    SeekBar progressSeekBar;
    Button slowmoButton;
    Button clearSlowmoButton;
    float slowmoSpeed = 0.5f;
    DrawThread drawThread;
    FFmpegFrameGrabber grabber;
    boolean[] slowFrames;
    long frameStillTime;
    float slowmoTimeMultiplier = 1;
    PlaybackMode speed = PlaybackMode.NORMAL;
    IProgressListener progressListener;
    int seekFrameNumber = -1;



    public VideoSlowmoEditor(Context context, SurfaceView surfaceView) {
        super(context);
        if (surfaceView == null)
            throw new IllegalArgumentException("surfaceView can not be null");
        getHolder().addCallback(this);
        initDecoder();
        slowFrames = new boolean[grabber.getLengthInFrames()];
        setSurfaceView(surfaceView);
        drawThread = new DrawThread(this.surfaceView.getHolder());
        drawThread.startPlayback();
        drawThread.start();
    }

    private void initDecoder() {
        try {
            String path = FileHelper.getRealPathFromURI(VideoProcessor.getSourceUri());
            grabber = new FFmpegFrameGrabber(path);
            grabber.start();
            frameStillTime = Math.round(1000.0d / grabber.getFrameRate());
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
            Log.e("javacv", "Failed to start grabber in editor " + e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (grabber != null)
        {
            try {
                grabber.release();
            } catch (FrameGrabber.Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void seekTo(float position)
    {
        if (position < -1 || position > 1)
            throw new IllegalArgumentException("Position more 1 or less -1");

        if (grabber != null)
        {
            int frameNum = Math.round(grabber.getLengthInFrames() * position);
                seekFrameNumber = frameNum;
        }
    }

    private void updateSeekbar()
    {
        if (progressSeekBar != null)
        {
            int progress = (grabber.getFrameNumber()/grabber.getLengthInFrames()) * 100;
            if (progressListener != null)
                progressListener.onMediaProgress(progress);
        }
    }

    public void setSurfaceView(SurfaceView surfaceView)
    {
        this.surfaceView = surfaceView;
    }

    public void setProgressSeekBar(final SeekBar progressSeekBar)
    {
        this.progressSeekBar = progressSeekBar;
    }

    public void setSlowmoButton(Button button)
    {
        this.slowmoButton = button;
    }

    public void setProgressListener(IProgressListener progressListener)
    {
        this.progressListener = progressListener;
    }

    public void setSlowmoSpeed(float slowmoSpeed)
    {
        this.slowmoSpeed = slowmoSpeed;
    }

    public void start()
    {
        if (drawThread != null)
            drawThread.startPlayback();
    }

    public void stop()
    {
        if (drawThread != null)
            drawThread.stopPlayback();
    }

    public void pause()
    {
        if (drawThread != null)
            drawThread.pausePlayback();
    }

    public void slowerSpeed()
    {
        speed = PlaybackMode.SLOW;
    }

    public void normalSpeed()
    {
        speed = PlaybackMode.NORMAL;
    }

    public void clearSlowmo()
    {
        for (boolean isFrameSlow : slowFrames)
        {
            isFrameSlow = false;
        }
    }

    public boolean[] getSlowFrames()
    {
        return slowFrames;
    }

    class DrawThread extends Thread {
        private boolean runFlag = false;
        private boolean isPaused = false;
        private SurfaceHolder surfaceHolder;
        private long prevTime;
        Canvas canvas;
        Frame frame = null;
        Bitmap bitmapFrame = null;
        OCVAndroidFrameConverter converter = new OCVAndroidFrameConverter();

        public DrawThread(SurfaceHolder surfaceHolder){
            this.setPriority(MAX_PRIORITY);
            this.surfaceHolder = surfaceHolder;


            //init here

            // сохраняем текущее время
            prevTime = System.currentTimeMillis();
        }

        public void startPlayback()
        {
            runFlag = true;
            isPaused = false;
        }

        public void pausePlayback()
        {
            isPaused = true;
        }

        public void stopPlayback()
        {
            runFlag = false;
        }

        @Override
        public void run() {
            try {
                do {
                    frame = grabber.grab();
                } while (frame.image == null);
            } catch (FrameGrabber.Exception e) {
                e.printStackTrace();
            }

            bitmapFrame = converter.convert(frame);

            while (runFlag && grabber.getFrameNumber() < grabber.getLengthInFrames()) {//TODO: refact stop function to not finish thread but to reset file's position
                if (!isPaused) {
                    long now = System.currentTimeMillis();
                    long elapsedTime = now - prevTime;
                    if (elapsedTime > (float)frameStillTime / slowmoTimeMultiplier) {
                        prevTime = now;

                        canvas = null;
                        try {
                            // получаем объект Canvas и выполняем отрисовку
                            canvas = surfaceHolder.lockCanvas(null);
                            synchronized (surfaceHolder) {
                                if (bitmapFrame != null && canvas != null) {
                                    Rect sfcRect = new Rect(0, 0, surfaceView.getWidth(), surfaceView.getHeight());
                                    canvas.drawBitmap(bitmapFrame, null, sfcRect, null);
                                }
                            }
                        } finally {
                            if (canvas != null) {
                                // отрисовка выполнена. выводим результат на экран
                                surfaceHolder.unlockCanvasAndPost(canvas);
                            }
                        }

                        try {
                            if (grabber.getFrameNumber() == grabber.getLengthInFrames() - 1)
                                grabber.setFrameNumber(1);
                            do {
                                frame = grabber.grabImage();
                            } while (frame.image == null);
                            bitmapFrame = converter.convert(frame);


                        } catch (FrameGrabber.Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (seekFrameNumber < 0)
                        progressListener.onMediaProgress((float) grabber.getFrameNumber() / grabber.getLengthInFrames());

                }

                try {
                    if (seekFrameNumber >= 0) {
                        grabber.setFrameNumber(seekFrameNumber);
                        seekFrameNumber = -1;
                    }
                } catch (FrameGrabber.Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to seek to time");
                }

                if (speed == PlaybackMode.SLOW || slowFrames[grabber.getFrameNumber()])
                {
                    slowFrames[grabber.getFrameNumber()] = true;
                    slowmoTimeMultiplier = slowmoSpeed;
                } else {
                    slowmoTimeMultiplier = 1;
                }
            }
        }
    }
}
