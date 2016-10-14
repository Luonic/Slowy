package com.sleepycatstudios.slowy;

import android.net.Uri;
import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;

import org.bytedeco.javacpp.indexer.FloatBufferIndexer;
import org.bytedeco.javacpp.indexer.UByteBufferIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.bytedeco.javacpp.helper.opencv_core.RGB;
import static org.bytedeco.javacpp.opencv_core.CV_32F;
import static org.bytedeco.javacpp.opencv_core.CV_32FC2;
import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.addWeighted;
import static org.bytedeco.javacpp.opencv_core.magnitude;
import static org.bytedeco.javacpp.opencv_core.multiply;
import static org.bytedeco.javacpp.opencv_core.split;
import static org.bytedeco.javacpp.opencv_core.subtract;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_RGB2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.CV_INTER_NN;
import static org.bytedeco.javacpp.opencv_imgproc.THRESH_TRUNC;
import static org.bytedeco.javacpp.opencv_imgproc.blendLinear;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.line;
import static org.bytedeco.javacpp.opencv_imgproc.remap;
import static org.bytedeco.javacpp.opencv_imgproc.threshold;
import static org.bytedeco.javacpp.opencv_video.calcOpticalFlowFarneback;

public class VideoProcessor {

    private static IProgressListener progressListener;

    private static Thread mainThread;
    private static boolean isProcessing = false;

    private static Uri inputVideoUri;
    private static String outputVideoPath;

    private static boolean[] slowFrames = null;

    private static int width;
    private static int height;
    private static float fps;
    private static float timeScaleMultiplier = 0.5f;
    private static int framesToCalculate;

    private static FrameGrabber grabber;
    private static FrameRecorder recorder;

    private static Mat coordMat;
    private static int pyramidsCount;

    private static int flowReduceFactor = 1;

    private static FrameCalcThread[] frameCalcThreads = new FrameCalcThread[Runtime.getRuntime().availableProcessors()];

    public void onCreate() {

    }

    public static void setProgressListner(IProgressListener progressListener)
    {
        VideoProcessor.progressListener = progressListener;
    }

    public static void setSlowFrames(boolean[] slowFrames)
    {
        VideoProcessor.slowFrames = slowFrames;
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

    private static void calcPyramidsCount() {
        pyramidsCount = 1;
        int biggerSide = Math.max(height, width) / flowReduceFactor;
        do {
            biggerSide = biggerSide / 2;
            pyramidsCount++;
        } while (biggerSide > 2);
        pyramidsCount -= 2;
        if (pyramidsCount < 1)
            pyramidsCount = 1;
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
        try {
            String path = FileHelper.getRealPathFromURI(inputVideoUri);
            grabber = new FFmpegFrameGrabber(path);
            grabber.start();
            width = grabber.getImageWidth();
            height = grabber.getImageHeight();
            generateCoordMat();
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

    private static void generateCoordMat() {
        coordMat = new Mat(height, width, CV_32FC2);
        FloatBufferIndexer coordMatIndexer = coordMat.createIndexer();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                coordMatIndexer.put(y, x, 0, x);
                coordMatIndexer.put(y, x, 1, y);
            }
        }
    }

    private static Mat calculateFrame(Mat sourceFrameMat, Mat nextFrameMat, Mat opticalFlow, int frameIndex, int totalFrames) {
        Log.d("VideoProcessor", "Calulating frame " + (frameIndex + 1) + " of " + (totalFrames));
        double vecMultiplier = (double) (frameIndex + 1) / (totalFrames + 1);
        Mat dstFrameMat = new Mat(sourceFrameMat.arrayHeight(), sourceFrameMat.arrayWidth(), sourceFrameMat.type());//sourceFrameMat.clone();
        Mat coordinatedOpticalFlow = subtract(coordMat, multiply(vecMultiplier, opticalFlow)).asMat();
        opencv_core.MatVector coordinatedOpticalFlowSplitted = new opencv_core.MatVector(3);//TODO: change to 2
        split(coordinatedOpticalFlow, coordinatedOpticalFlowSplitted);
        remap(sourceFrameMat,
                dstFrameMat,
                coordinatedOpticalFlowSplitted.get(0),
                coordinatedOpticalFlowSplitted.get(1),
                CV_INTER_NN);
        Mat blendedFramesMat = new Mat();
        addWeighted(sourceFrameMat, 1.0f - vecMultiplier, nextFrameMat, vecMultiplier, 0.0f, blendedFramesMat);

        /*UByteBufferIndexer fbIndexer = blendedFramesMat.createIndexer();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                fbIndexer.put(y, x, 0, 255); fbIndexer.put(y, x, 1, 0); fbIndexer.put(y, x, 2, 255);
            }
        }*/

        Mat weights1 = new Mat(height, width, CV_32F);
        Mat weights2 = new Mat(height, width, CV_32F);
        opencv_core.MatVector opticalFlowSplitted = new opencv_core.MatVector(3);//TODO: change to 2
        split(opticalFlow, opticalFlowSplitted);
        magnitude(opticalFlowSplitted.get(0), opticalFlowSplitted.get(1), weights2);
        opticalFlowSplitted.deallocate();
        threshold(weights2, weights1, 1, 1, THRESH_TRUNC);
        weights2 = subtract(new opencv_core.Scalar(1.0d), weights1).asMat();//TODO: here leaks
        Mat resultMat = new Mat();
        Log.d("VP", " sizes " + dstFrameMat.size().get() + ", " + blendedFramesMat.size().get() + ", " + weights1.size().get() + ", " + weights2.size().get());
        blendLinear(dstFrameMat, blendedFramesMat, weights1, weights2, resultMat);
        coordinatedOpticalFlowSplitted.deallocate();
        coordinatedOpticalFlow.release();
        dstFrameMat.release();
        blendedFramesMat.release();
        weights1.release();
        weights2.release();
        return resultMat;
    }

    private static void debugFloatMat (Mat mat, String matName)
    {
        FloatBufferIndexer indexer = mat.createIndexer();
        for (int i = 0; i < mat.arrayHeight() * mat.arrayWidth() * mat.arrayChannels(); i++) {
            Log.d("DebugMat", matName + " = " + indexer.get(i));
        }
    }

    private static Mat drawOpticalFlowVectors(Mat frame, Mat opticalFlow, int step) {
        FloatBufferIndexer flowIndexer = opticalFlow.createIndexer();
        opencv_core.Point dstPoint = new opencv_core.Point(0, 0);
        opencv_core.Point srcPoint = new opencv_core.Point(0, 0);
        float flowX;
        float flowY;

        for (int y = step / 2; y < opticalFlow.arrayHeight(); y += step)
            for (int x = step / 2; x < opticalFlow.arrayWidth(); x += step) {
                flowX = flowIndexer.get(y, x, 0);
                flowY = flowIndexer.get(y, x, 1);
                dstPoint.x(Math.round(flowX * 3 + x));
                dstPoint.y(Math.round(flowY * 3 + y));
                srcPoint.x(x);
                srcPoint.y(y);
                line(frame, srcPoint, dstPoint, RGB(0, 255, 0));
                //circle(frame, srcPoint, 1, RGB(0, 255, 0), 2, FILLED, 0);
            }
        return frame;
    }

    private static Mat drawOpticalFlowPoints(Mat frame, Mat opticalFlow) {
        FloatBufferIndexer flowIndexer = opticalFlow.createIndexer();
        UByteBufferIndexer frameIndexer = frame.createIndexer();
        float flowX;
        float flowY;
        int pixelVal;
        for (int y = 0; y < opticalFlow.arrayHeight(); y++)
            for (int x = 0; x < opticalFlow.arrayWidth(); x++) {
                flowX = flowIndexer.get(y, x, 0);
                flowY = flowIndexer.get(y, x, 1);
                pixelVal = (int) Math.round(Math.sqrt(flowX * flowX + flowY * flowY));
                frameIndexer.put(y, x, 0, pixelVal);
                frameIndexer.put(y, x, 1, pixelVal);
                frameIndexer.put(y, x, 2, pixelVal);
            }
        return frame;
    }


    private static Mat[] calculateFrames(Mat framePrevMat, Mat frameNextMat, int framesForCalc) {
        Mat[] calculatedFrames = new Mat[framesForCalc + 1];
        Mat opticalFlow = new Mat();
        Mat framePrevMatGray = new Mat();
        Mat frameNextMatGray = new Mat();
        cvtColor(framePrevMat, framePrevMatGray, COLOR_RGB2GRAY);
        cvtColor(frameNextMat, frameNextMatGray, COLOR_RGB2GRAY);

        long optFCalcStartTime = SystemClock.currentThreadTimeMillis();
        calcOpticalFlowFarneback(framePrevMatGray,//first 8-bit single-channel input image
                frameNextMatGray,//second input image of the same size and the same type as prev
                opticalFlow,//computed flow image that has the same size as prev and type CV_32FC2
                0.5, //parameter, specifying the image scale (<1) to build pyramids for each image;
                // pyr_scale=0.5 means a classical pyramid, where each next layer is twice smaller
                // than the previous one
                pyramidsCount, //number of pyramid layers including the initial image; levels=1
                // means that no extra layers are created and only the original images are used
                120, //averaging window size; larger values increase the algorithm robustness to
                // image noise and give more chances for fast motion detection, but yield more
                // blurred motion field
                3, //number of iterations the algorithm does at each pyramid level
                5, //size of the pixel neighborhood used to find polynomial expansion in each pixel;
                // larger values mean that the image will be approximated with smoother surfaces,
                // yielding more robust algorithm and more blurred motion field,
                // typically poly_n =5 or 7
                1.1, //standard deviation of the Gaussian that is used to smooth derivatives used
                // as a basis for the polynomial expansion; for poly_n=5,
                // you can set poly_sigma=1.1, for poly_n=7, a good value would be poly_sigma=1.5
                0);//Flags: OPTFLOW_USE_INITIAL_FLOW, OPTFLOW_FARNEBACK_GAUSSIAN
        Log.d("VideoProcessor", "Opt flow calculated in " +
                (SystemClock.currentThreadTimeMillis() - optFCalcStartTime) / 1000 + " sec");
        framePrevMatGray.release();
        frameNextMatGray.release();
        Mat frameMat;
        calculatedFrames[0] = framePrevMat.clone();
        for (int interpolatedFrameIndex = 1; interpolatedFrameIndex < framesForCalc + 1; interpolatedFrameIndex++) {
            //Calculating time-interpolated frame
            frameMat = calculateFrame(framePrevMat, frameNextMat, opticalFlow, interpolatedFrameIndex - 1, framesToCalculate);
            //Add frame to array of calculated frames
            calculatedFrames[interpolatedFrameIndex] = frameMat.clone();
            frameMat.release();
        }

        opticalFlow.release();
        return calculatedFrames;
    }

    public static void start() {
        mainThread = new Thread(new Runnable() {
            @Override
            public void run() {
                startProcessing();
            }
        });
        mainThread.start();
    }

    public static void stop() {
        isProcessing = false;
    }

    private static void startProcessing() {
        OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
        isProcessing = true;

        if (progressListener != null)
            progressListener.onMediaStart();

        long startTime = SystemClock.currentThreadTimeMillis();
        initDecoder();
        initEncoder();
        GetFramesCountForCalculation();
        calcPyramidsCount();
        Log.d("VideoProcessor", "PyramidsCount=" + pyramidsCount);
        Frame tmpFrame;
        Mat framePrevMat;
        Mat frameNextMat;
        try {
            //Processing video frames
            do {
                tmpFrame = grabber.grab();
            } while (tmpFrame.image == null);
            framePrevMat = converterToMat.convert(tmpFrame).clone();

            Log.d("VideoProcessor", "Video length is " + grabber.getLengthInFrames() + "frames");

            //while (grabber.getFrameNumber() < 20 && isProcessing) {
            while (grabber.getFrameNumber() < grabber.getLengthInFrames() && isProcessing) {

                Log.d("VideoProcessor", "Creating new threads");
                Log.d("VideoProcessor", "Current frame number is " + grabber.getFrameNumber());
                for (int i = 0; i < frameCalcThreads.length; i++) {
                    Runtime.getRuntime().gc();

                    Log.d("VP", "Memory used: " + Long.toString(Utils.getUsedMemorySize()) + " Mb");
                    Log.d("VP", "Memory free: " + Long.toString(Utils.getFreeMemorySize()) + " Mb");
                    Log.d("VP", "Native Heap Free Size = " + Long.toString(Utils.getFreeNativeMemory()) + " Mb");

                    if (grabber.getFrameNumber() < grabber.getLengthInFrames()) {
                        while (!slowFrames[grabber.getFrameNumber()]) {
                            recorder.record(tmpFrame);

                            if (progressListener != null) {
                                float progress = (float) grabber.getFrameNumber() / grabber.getLengthInFrames();
                                Log.d("VideoProcessor", "Progress = " + progress);
                                progressListener.onMediaProgress(progress);
                            }

                            Log.d("VP", "Memory used: " + Long.toString(Utils.getUsedMemorySize()) + "Mb");
                            Log.d("VP", "Memory free: " + Long.toString(Utils.getFreeMemorySize()) + "Mb");
                            Log.d("VP", "Native Heap Free Size = " + Long.toString(Utils.getFreeNativeMemory()) + " Mb");

                            if (grabber.getFrameNumber() < grabber.getLengthInFrames()) {
                                do {
                                    tmpFrame = grabber.grab();//TODO: Replace with grabImage();
                                } while (tmpFrame.image == null);
                            }
                        }

                        if (grabber.getFrameNumber() < grabber.getLengthInFrames()) {
                            do {
                                tmpFrame = grabber.grab();
                            } while (tmpFrame.image == null);
                        }

                        frameNextMat = converterToMat.convert(tmpFrame);
                        frameCalcThreads[i] = new FrameCalcThread(framePrevMat, frameNextMat);
                        Log.d("VideoProcessor", "Created thread with id " + frameCalcThreads[i].getId());
                        frameCalcThreads[i].start();
                        framePrevMat.release();
                        framePrevMat = frameNextMat.clone();
                        frameNextMat.release();
                    }
                }

                //Telling threads to wait other threads
                for (int i = 0; i < frameCalcThreads.length; i++) {
                    try {
                        if (frameCalcThreads[i] != null) {
                            frameCalcThreads[i].join();
                            Log.d("VideoProcessor", "Thread " + frameCalcThreads[i].getId() + " joined");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.e("VideoProcessor", "Failed to join thread");
                    }
                }

                //Recording original and generated frames for every thread
                for (int i = 0; i < frameCalcThreads.length; i++) {
                    if (frameCalcThreads[i] != null) {
                        for (int j = 0; j < frameCalcThreads[i].result.length; j++) {
                            Log.d("VP", "Recording frame " + frameCalcThreads[i].result[j] + ", " + frameCalcThreads[i].getId());
                            recorder.record(converterToMat.convert(frameCalcThreads[i].result[j]));
                            frameCalcThreads[i].result[j].release();
                        }
                    }
                    frameCalcThreads[i] = null;
                }
                if (progressListener != null) {
                    float progress = (float) grabber.getFrameNumber() / grabber.getLengthInFrames();
                    Log.d("VideoProcessor", "Progress = " + progress);
                    progressListener.onMediaProgress(progress);
                }
                Log.d("VP", "Memory used: " + Long.toString(Utils.getUsedMemorySize()) + "Mb");
                Log.d("VP", "Memory free: " + Long.toString(Utils.getFreeMemorySize()) + "Mb");
                Log.d("VP", "Native Heap Free Size = " + Long.toString(Utils.getFreeNativeMemory()) + "Mb");
            }
            recorder.record(tmpFrame);//TODO: this may cause still frames in the end
            framePrevMat.release();
            //frameNextMat.release();
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
        }

        //Stopping recording
        try {
            recorder.stop();
            Log.d("VideoProcessor", "Recorder stopped");
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
            Log.d("VideoProcessor", "Failed to stop recorder");
        }

        try {
            grabber.stop();
            Log.d("VideoProcessor", "Grabber stopped");
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("VideoProcessor", "Failed to stop grabber");
        }
        long finishTime = SystemClock.currentThreadTimeMillis();
        Log.d("VideoProcessor", "Finished in " + (finishTime - startTime) / 1000 + " sec");
        if (progressListener != null && isProcessing)
            progressListener.onMediaDone();
        isProcessing = false;
    }

    static class FrameCalcThread extends Thread {
        Mat framePrev;
        Mat frameNext;

        Mat[] result;

        public FrameCalcThread(Mat framePrev, Mat frameNext) {
            this.framePrev = framePrev.clone();
            this.frameNext = frameNext.clone();
            this.setPriority(NORM_PRIORITY);
        }

        @Override
        public void run() {
            Log.d("VideoProcessor", "Calculation in thread " + this.getId() + " between frame " + grabber.getFrameNumber() + " and frame " + (grabber.getFrameNumber() + 1));
            result = calculateFrames(framePrev, frameNext, framesToCalculate);
            this.framePrev.release();
            this.frameNext.release();
        }
    }
}