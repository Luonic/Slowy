package com.sleepycatstudios.slowy;

import android.graphics.Color;
import android.net.Uri;
import android.util.Log;

import org.bytedeco.javacpp.indexer.FloatBufferIndexer;
import org.bytedeco.javacpp.indexer.UByteBufferIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.bytedeco.javacpp.helper.opencv_core.CV_RGB;
import static org.bytedeco.javacpp.helper.opencv_core.RGB;
import static org.bytedeco.javacpp.opencv_core.CV_32FC1;
import static org.bytedeco.javacpp.opencv_core.FILLED;
import static org.bytedeco.javacpp.opencv_core.LINE_8;
import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.cartToPolar;
import static org.bytedeco.javacpp.opencv_core.normalize;
import static org.bytedeco.javacpp.opencv_core.theRNG;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_HSV2BGR;
import static org.bytedeco.javacpp.opencv_imgproc.circle;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.line;
import static org.bytedeco.javacpp.opencv_imgproc.threshold;
import static org.bytedeco.javacpp.opencv_video.calcOpticalFlowFarneback;
import static org.bytedeco.javacpp.opencv_video.cvKalmanUpdateByTime;

public class VideoProcessor {

    private static Uri inputVideoUri;
    private static String outputVideoPath;

    private static int width;
    private static int height;
    private static float fps;
    private static float timeScaleMultiplier = 0.5f;
    private static int framesToCalculate;

    private static FrameGrabber grabber;
    private static FrameRecorder recorder;

    private static FrameCalcThread[] frameCalcThreads = new FrameCalcThread[Runtime.getRuntime().availableProcessors()];

    public void onCreate() {

    }

    private static void GetFramesCountForCalculation() {
        if (timeScaleMultiplier > 1)
            try {
                throw new Exception("Time scale more than 1");
            } catch (Exception e) {
                e.printStackTrace();
            }
        framesToCalculate = Math.round((1 / timeScaleMultiplier) - 1);

    }

    public static void loadVideo(Uri videoUri) {
        inputVideoUri = videoUri;
        String directory = "";// = "file://";
        directory += FileHelper.getRealPathFromURI(inputVideoUri);
        File file = new File(directory);
        String path = file.getParent();
        String filename = new SimpleDateFormat("'Slowy_'yyyyMMddhhmm'.mp4'", Locale.getDefault()).format(new Date());
        outputVideoPath = path + "/" + filename;
    }

    public static Uri getSourceUri() {
        return inputVideoUri;
    }

    public static void setTimeScale(float timeScaleMultiplier) {
        VideoProcessor.timeScaleMultiplier = timeScaleMultiplier;
    }

    private static void initDecoder() {
        // The available FrameGrabber classes include OpenCVFrameGrabber (opencv_videoio),
        // DC1394FrameGrabber, FlyCaptureFrameGrabber, OpenKinectFrameGrabber,
        // PS3EyeFrameGrabber, VideoInputFrameGrabber, and FFmpegFrameGrabber.
        FFmpegLogCallback.set();
        try {
            String path = FileHelper.getRealPathFromURI(inputVideoUri);
            grabber = new FFmpegFrameGrabber(path);
            grabber.start();
            width = grabber.getImageWidth();
            height = grabber.getImageHeight();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("javacv", "Failed to start grabber" + e);
        }
    }

    private static void initEncoder() {
        try {

            recorder = new FFmpegFrameRecorder(outputVideoPath, width, height, 1);
            recorder.setFormat("mp4");
            recorder.setSampleRate(grabber.getSampleRate());
            recorder.setVideoBitrate(grabber.getVideoBitrate());
            recorder.setVideoQuality(0);
            //re-set in the surface changed method as well
            //recorder.setFrameRate(grabber.getFrameRate());
            recorder.start();
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        }

    }

    /*private static Mat calculateFrame(Mat sourceFrameMat, Mat opticalFlow, int frameIndex, int totalFrames) {
        Log.d("VideoProcessor", "Calulating frame " + (frameIndex + 1) + " of " + totalFrames);
        float vecMultiplier = frameIndex / totalFrames;

        int dstCoordX, dstCoordY;

        int flowVecX, flowVecY;

        //Creating indexer for calculated optical flow
        UByteBufferIndexer opticalFlowIndexer = opticalFlow.createIndexer();

        //Creatring indexer for source matrix
        UByteBufferIndexer sourceFrameMatIndexer = sourceFrameMat.createIndexer();

        //Matrix and indexer for destination matrix
        Mat dstFrameMat = sourceFrameMat.clone();
        UByteBufferIndexer dstFrameMatIndexer = dstFrameMat.createIndexer();

        int matWidth = sourceFrameMat.arrayWidth();
        int matHeight = sourceFrameMat.arrayHeight();

        for (int y = 0; y < matHeight; y++) {
            for (int x = 0; x < matWidth; x++) {
                flowVecX = opticalFlowIndexer.get(y, x, 0);
                flowVecY = opticalFlowIndexer.get(y, x, 1);

                dstCoordX = x + Math.round(flowVecX * vecMultiplier);
                dstCoordY = y + Math.round(flowVecY * vecMultiplier);

                //If index not out of bounds
                if (dstCoordX < matWidth && y + dstCoordY < matHeight) {
                    //Moving pixels by channel
                    for (int c = 0; c < sourceFrameMat.channels(); c++) {
                        //Getting value of channel "c"
                        int val = sourceFrameMatIndexer.get(y, x, c);
                        //Setting value of channel "c"
                        dstFrameMatIndexer.put(dstCoordY, dstCoordX, c, val);
                    }
                }
            }
        }
        sourceFrameMatIndexer.release();
        dstFrameMatIndexer.release();
        return dstFrameMat;
    }*/

    private static Mat calculateFrame(Mat sourceFrameMat, Mat opticalFlow, int frameIndex, int totalFrames) {
        Log.d("VideoProcessor", "Calulating frame " + (frameIndex + 1) + " of " + totalFrames);
        float vecMultiplier = frameIndex / totalFrames;

        int dstCoordX, dstCoordY;

        int flowVecX, flowVecY;

        int pixelVal;
        //Creating indexer for calculated optical flow
        FloatBufferIndexer opticalFlowIndexer = opticalFlow.createIndexer();

        //Creatring indexer for source matrix
        UByteBufferIndexer sourceFrameMatIndexer = sourceFrameMat.createIndexer();

        //Matrix and indexer for destination matrix
        Mat dstFrameMat = sourceFrameMat.clone();
        UByteBufferIndexer dstFrameMatIndexer = dstFrameMat.createIndexer();

        int matWidth = sourceFrameMat.arrayWidth();
        int matHeight = sourceFrameMat.arrayHeight();

        for (int y = 0; y < matHeight; y++) {
            for (int x = 0; x < matWidth; x++) {
                flowVecX = Math.round(opticalFlowIndexer.get(y, x, 0));
                flowVecY = Math.round(opticalFlowIndexer.get(y, x, 1));

                dstCoordX = x + Math.round(flowVecX * vecMultiplier);
                dstCoordY = y + Math.round(flowVecY * vecMultiplier);

                //If index not out of bounds
                if (dstCoordX < matWidth && dstCoordY < matHeight) {
                    float[] hsv = new float[3];
                    hsv[0] = (int)Math.round(Math.atan2(255f / opticalFlowIndexer.get(y, x, 0), 255f / opticalFlowIndexer.get(y, x, 1)) / Math.PI * 180);
                    hsv[1] = 1;
                    hsv[2] = 1;
                    int rgb = Color.HSVToColor(hsv);



                    //Moving pixels by channel
                    pixelVal = sourceFrameMatIndexer.get(y, x, 0);
                    //dstFrameMatIndexer.put(dstCoordY, dstCoordX, 0, pixelVal);
                    dstFrameMatIndexer.put(y, x, 0, Color.blue(rgb));

                    pixelVal = sourceFrameMatIndexer.get(y, x, 1);
                    //dstFrameMatIndexer.put(dstCoordY, dstCoordX, 1, pixelVal);
                    dstFrameMatIndexer.put(y, x, 1, Color.green(rgb));

                    pixelVal = sourceFrameMatIndexer.get(y, x, 2);
                    //dstFrameMatIndexer.put(dstCoordY, dstCoordX, 2, pixelVal);
                    dstFrameMatIndexer.put(y, x, 2, Color.red(rgb));
                }
            }
        }
        //opticalFlowIndexer.release();
        //sourceFrameMatIndexer.release();
        //dstFrameMatIndexer.release();
        return drawOpticalFlowMap(dstFrameMat, opticalFlow, 64);
        //return dstFrameMat;
    }

    private static Mat drawOpticalFlowMap (Mat frame, Mat opticalFlow, int step) {
        FloatBufferIndexer flowIndexer = opticalFlow.createIndexer();
        opencv_core.Point dstPoint = new opencv_core.Point(0, 0);
        opencv_core.Point srcPoint = new opencv_core.Point(0, 0);
        for(int y = 0; y < opticalFlow.arrayHeight(); y += step)
            for(int x = 0; x < opticalFlow.arrayWidth(); x += step)
            {

                dstPoint.x(Math.round(flowIndexer.get(y, x, 0) + x));
                dstPoint.y(Math.round(flowIndexer.get(y, x, 1) + y));
                srcPoint.x(x);
                srcPoint.y(y);
                line(frame, srcPoint, dstPoint, RGB(0, 255, 0));
                circle(frame, srcPoint, 1, RGB(0, 255, 0), 2, FILLED, 0);
            }
        return frame;
    }


    private static Frame[] calculateFrames(Frame framePrev, Frame frameNext, int framesForCalc) {

        // FAQ about IplImage and Mat objects from OpenCV:
        // - For custom raw processing of data, createBuffer() returns an NIO direct
        //   buffer wrapped around the memory pointed by imageData, and under Android we can
        //   also use that Buffer with Bitmap.copyPixelsFromBuffer() and copyPixelsToBuffer().
        // - To get a BufferedImage from an IplImage, or vice versa, we can chain calls to
        //   Java2DFrameConverter and OpenCVFrameConverter, one after the other.
        // - Java2DFrameConverter also has static copy() methods that we can use to transfer
        //   data more directly between BufferedImage and IplImage or Mat via Frame objects.

        OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
        //AndroidFrameConverter converterToBitmap = new AndroidFrameConverter();


        Frame[] calculatedFrames = new Frame[framesForCalc];

        Mat opticalFlow = new Mat();
        Mat framePrevMat = converterToMat.convert(framePrev);
        Mat frameNextMat = converterToMat.convert(frameNext);

        Mat framePrevMatGray = new Mat();
        Mat frameNextMatGray = new Mat();

        cvtColor(framePrevMat, framePrevMatGray, COLOR_BGR2GRAY);
        cvtColor(frameNextMat, frameNextMatGray, COLOR_BGR2GRAY);


        //frameNextMat.release();

        //final DenseOpticalFlow optFlowDualTVL1 = createOptFlow_DualTVL1();

        //optFlowDualTVL1.calc(framePrevMatGray, frameNextMatGray, opticalFlow);
        calcOpticalFlowFarneback(framePrevMatGray, frameNextMatGray, opticalFlow, 0.5, 1, 12, 2, 7, 1.5, 0);
        //calcOpticalFlowFarneback(framePrevMatGray, frameNextMatGray, opticalFlow, 0.5, 3, 15, 3, 5, 1.2, 0);
        //calcOpticalFlowFarneback(framePrevMat, frameNextMat, opticalFlow, 0.5, 1, 12, 2, 7, 1.5, 0);

        Mat frameMat = null;
        for (int interpolatedFrameIndex = 0; interpolatedFrameIndex < framesForCalc; interpolatedFrameIndex++) {
            //Calculating time-interpolated frame
            frameMat = calculateFrame(framePrevMat, opticalFlow, interpolatedFrameIndex, framesToCalculate);

            // Convert processedMat back to a Frame
            Frame frame = converterToMat.convert(frameMat);

            // Copy the data to a Bitmap for display or something
            //Bitmap bitmap = converterToBitmap.convert(frame);

            //Add frame to array of calculated frames
            calculatedFrames[interpolatedFrameIndex] = frame;
        }
        //framePrevMat.release();
        //frameNextMatGray.release();
        //frameNextMatGray.release();
        //if (frameMat != null)
            //frameMat.release();
        return calculatedFrames;
    }

    public static void startProcessing() {
        // Preload the opencv_objdetect module to work around a known bug.
        //Loader.load(opencv_objdetect.class);
        initDecoder();
        initEncoder();
        GetFramesCountForCalculation();

        // CanvasFrame, FrameGrabber, and FrameRecorder use Frame objects to communicate image data.
        // We need a FrameConverter to interface with other APIs (Android, Java 2D, or OpenCV).
        //OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();


        // CanvasFrame is a JFrame containing a Canvas component, which is hardware accelerated.
        // It can also switch into full-screen mode when called with a screenNumber.
        // We should also specify the relative monitor/camera response for proper gamma correction.

        try {
            //Processing video frames
            Frame framePrev;
            do {
                framePrev = grabber.grab().clone();
            } while (framePrev.image == null);

            Log.d("VideoProcessor", "Video length is " + grabber.getLengthInFrames() + "frames");

            while (grabber.getFrameNumber() < 40) {//grabber.getLengthInFrames()) {
                for (int i = 0; i < frameCalcThreads.length; i++) {
                    frameCalcThreads[i] = null;
                    if (grabber.getFrameNumber() < grabber.getLengthInFrames()) {
                        Frame frameNext;
                        do {
                            frameNext = grabber.grab();
                        } while (frameNext.image == null);
                        frameCalcThreads[i] = new FrameCalcThread(framePrev, frameNext);
                        Log.d("VideoProcessor", "Created new thread with id " + frameCalcThreads[i].getId());
                        framePrev = frameNext.clone();
                        frameCalcThreads[i].start();
                    }
                }

                //Telling threads to wait other threads
                for (int i = 0; i < frameCalcThreads.length; i++) {
                    try {
                        if (frameCalcThreads[i] != null)
                            frameCalcThreads[i].join();
                        Log.d("VideoProcessor", "Thread " + frameCalcThreads[i].getId() + " joined");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.e("VideoProcessor", "Failed to join thread");
                    }
                }

                //Recording original and generated frames for every thread
                for (FrameCalcThread thread : frameCalcThreads){
                    if (thread != null) {
                        recorder.record(thread.framePrev);
                        for (Frame frame : thread.result) {
                            recorder.record(frame);
                        }
                    }
                }
            }
            recorder.record(framePrev);
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
        }

        //Stopping recording
        try {
            recorder.stop();
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        }

        try {
            grabber.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class FrameCalcThread extends Thread {
        Frame framePrev;
        Frame frameNext;

        Frame[] result;

        public FrameCalcThread(Frame framePrev, Frame frameNext) {
            this.framePrev = framePrev.clone();
            this.frameNext = frameNext.clone();
            this.setPriority(MAX_PRIORITY);
        }

        @Override
        public void run() {
            Log.d("VideoProcessor","Calculation in thread " + this.getId() + " between frame " + grabber.getFrameNumber() + " and frame " + (grabber.getFrameNumber() + 1));
            result = calculateFrames(framePrev, frameNext, framesToCalculate);
        }
    }
}


