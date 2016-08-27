package com.sleepycatstudios.slowy;

import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import org.bytedeco.javacpp.indexer.FloatBufferIndexer;
import org.bytedeco.javacpp.indexer.UByteBufferIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_video;
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

import static org.bytedeco.javacpp.helper.opencv_core.RGB;
import static org.bytedeco.javacpp.opencv_core.BORDER_CONSTANT;
import static org.bytedeco.javacpp.opencv_core.CV_16SC2;
import static org.bytedeco.javacpp.opencv_core.CV_32F;
import static org.bytedeco.javacpp.opencv_core.CV_32FC1;
import static org.bytedeco.javacpp.opencv_core.CV_32FC2;
import static org.bytedeco.javacpp.opencv_core.FILLED;
import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.add;
import static org.bytedeco.javacpp.opencv_core.addWeighted;
import static org.bytedeco.javacpp.opencv_core.cartToPolar;
import static org.bytedeco.javacpp.opencv_core.merge;
import static org.bytedeco.javacpp.opencv_core.multiply;
import static org.bytedeco.javacpp.opencv_core.normalize;
import static org.bytedeco.javacpp.opencv_core.split;
import static org.bytedeco.javacpp.opencv_core.subtract;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_RGB2BGR;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_RGB2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.CV_INTER_CUBIC;
import static org.bytedeco.javacpp.opencv_imgproc.CV_INTER_LINEAR;
import static org.bytedeco.javacpp.opencv_imgproc.CV_INTER_NN;
import static org.bytedeco.javacpp.opencv_imgproc.blendLinear;
import static org.bytedeco.javacpp.opencv_imgproc.circle;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.line;
import static org.bytedeco.javacpp.opencv_imgproc.remap;
import static org.bytedeco.javacpp.opencv_imgproc.resize;
import static org.bytedeco.javacpp.opencv_video.OPTFLOW_FARNEBACK_GAUSSIAN;
import static org.bytedeco.javacpp.opencv_video.calcOpticalFlowFarneback;

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

    private static Mat coordMat;
    private static int pyramidsCount;

    private static int flowReduceFactor = 1;

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

    private static void calcPyramidsCount()
    {
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

    private static void generateCoordMat()
    {
        coordMat = new Mat(height, width, CV_32FC2);
        FloatBufferIndexer coordMatIndexer = coordMat.createIndexer();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                coordMatIndexer.put(y, x, 0, x);
                coordMatIndexer.put(y, x, 1, y);
            }
        }
    }

    private static Mat calculateFrame(Mat sourceFrameMat, Mat blendedFramesMat,  Mat opticalFlow, int frameIndex, int totalFrames) {
        Log.d("VideoProcessor", "Calulating frame " + (frameIndex + 1) + " of " + totalFrames);
        double vecMultiplier = (double) (frameIndex + 1) / (totalFrames + 1);

        //Matrix and indexer for destination matrix
        Mat dstFrameMat = sourceFrameMat.clone();

        Mat coordinatedOpticalFlow = subtract(coordMat, multiply(vecMultiplier, opticalFlow)).asMat();

        opencv_core.MatVector opticalFlowSplitted = new opencv_core.MatVector(3);
        split(coordinatedOpticalFlow, opticalFlowSplitted);

        remap(sourceFrameMat,
                dstFrameMat,
                opticalFlowSplitted.get(0),
                opticalFlowSplitted.get(1),
                CV_INTER_NN);

        FloatBufferIndexer opticalFlowIndexer = opticalFlow.createIndexer();
        UByteBufferIndexer dstFrameIndexer = dstFrameMat.createIndexer();

        Mat weights1 = new Mat(width, height, CV_32F);
        FloatBufferIndexer weights1Indexer = weights1.createIndexer();
        float weight;
        float flowX, flowY;
        for (int y = 0; y < coordinatedOpticalFlow.arrayHeight(); y++) {
            for (int x = 0; x < coordinatedOpticalFlow.arrayWidth(); x++) {
                flowX = opticalFlowIndexer.get(y, x, 0);
                flowY = opticalFlowIndexer.get(y, x, 1);
                weight = Math.round(Math.sqrt(flowX * flowX + flowY * flowY));
                weights1Indexer.put(y, x, Math.round(255 - weight));
                //TODO: complete this algo, calculate weights and later blend blended image with calculated image via blendlinear()
            }
        }

        //sourceFrameMat.release();
        //coordinatedOpticalFlow.release();
        //opticalFlowSplitted.deallocate();

        //opticalFlowIndexer.release();
        //sourceFrameMatIndexer.release();
        //dstFrameMatIndexer.release();
        //return drawOpticalFlowVectors(dstFrameMat, opticalFlow, 32);
        //return  drawOpticalFlowPoints(sourceFrameMat, opticalFlow);
        return dstFrameMat;
    }

    private static Mat drawOpticalFlowVectors(Mat frame, Mat opticalFlow, int step) {
        FloatBufferIndexer flowIndexer = opticalFlow.createIndexer();
        opencv_core.Point dstPoint = new opencv_core.Point(0, 0);
        opencv_core.Point srcPoint = new opencv_core.Point(0, 0);
        float flowX;
        float flowY;

        for(int y = step / 2; y < opticalFlow.arrayHeight(); y += step)
            for(int x = step / 2; x < opticalFlow.arrayWidth(); x += step)
            {
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
        for(int y = 0; y < opticalFlow.arrayHeight(); y++)
            for(int x = 0; x < opticalFlow.arrayWidth(); x++)
            {
                flowX = flowIndexer.get(y, x, 0);
                flowY = flowIndexer.get(y, x, 1);
                pixelVal = (int)Math.round(Math.sqrt(flowX * flowX + flowY * flowY));
                frameIndexer.put(y, x, 0, pixelVal);
                frameIndexer.put(y, x, 1, pixelVal);
                frameIndexer.put(y, x, 2, pixelVal);
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

        cvtColor(framePrevMat, framePrevMatGray, COLOR_RGB2GRAY);
        cvtColor(frameNextMat, frameNextMatGray, COLOR_RGB2GRAY);


        frameNextMat.release();

        Mat framePrevMatGrayReduced = new Mat();
        Mat frameNextMatGrayReduced = new Mat();

        resize(framePrevMatGray,
                framePrevMatGrayReduced,
                new opencv_core.Size(
                        framePrevMatGray.arrayWidth() / flowReduceFactor,
                        framePrevMatGray.arrayHeight() / flowReduceFactor));
        resize(frameNextMatGray,
                frameNextMatGrayReduced,
                new opencv_core.Size(
                        frameNextMatGray.arrayWidth() / flowReduceFactor,
                        frameNextMatGray.arrayHeight() / flowReduceFactor));

        Mat opticalFlowReduced = new Mat();

        Log.d("VideoProcessor", "PyramidsCount=" + pyramidsCount);

        long optFCalcStartTime = SystemClock.currentThreadTimeMillis();
        //optFlowDualTVL1.calc(framePrevMatGray, frameNextMatGray, opticalFlow);
        calcOpticalFlowFarneback(framePrevMatGrayReduced,//first 8-bit single-channel input image
                frameNextMatGrayReduced,//second input image of the same size and the same type as prev
                opticalFlowReduced,//computed flow image that has the same size as prev and type CV_32FC2
                0.5, //parameter, specifying the image scale (<1) to build pyramids for each image;
                // pyr_scale=0.5 means a classical pyramid, where each next layer is twice smaller
                // than the previous one
                pyramidsCount, //number of pyramid layers including the initial image; levels=1
                // means that no extra layers are created and only the original images are used
                80, //averaging window size; larger values increase the algorithm robustness to
                // image noise and give more chances for fast motion detection, but yield more
                // blurred motion field
                24, //number of iterations the algorithm does at each pyramid level
                5, //size of the pixel neighborhood used to find polynomial expansion in each pixel;
                // larger values mean that the image will be approximated with smoother surfaces,
                // yielding more robust algorithm and more blurred motion field,
                // typically poly_n =5 or 7
                1.1, //standard deviation of the Gaussian that is used to smooth derivatives used
                // as a basis for the polynomial expansion; for poly_n=5,
                // you can set poly_sigma=1.1, for poly_n=7, a good value would be poly_sigma=1.5
                0);//Flags: OPTFLOW_USE_INITIAL_FLOW, OPTFLOW_FARNEBACK_GAUSSIAN

        Log.d("VideoProcessor", "Opt flow calculated in " + (SystemClock.currentThreadTimeMillis() - optFCalcStartTime) / 1000 + " sec");

        resize(opticalFlowReduced, opticalFlow, new opencv_core.Size(width, height), 0 ,0, CV_INTER_CUBIC);

        //opticalFlowReduced.release();
        //framePrevMatGrayReduced.release();
        //frameNextMatGrayReduced.release();
        //framePrevMatGray.release();
        //frameNextMatGray.release();

        Mat framesBlended = new Mat();
        addWeighted(framePrevMat, 0.5, frameNextMat, 0.5, 0.0, framesBlended);

        Mat frameMat = null;
        for (int interpolatedFrameIndex = 0; interpolatedFrameIndex < framesForCalc; interpolatedFrameIndex++) {
            //Calculating time-interpolated frame
            frameMat = calculateFrame(framePrevMat, framesBlended, opticalFlow, interpolatedFrameIndex, framesToCalculate);

            // Convert processedMat back to a Frame
            Frame frame = converterToMat.convert(frameMat);

            // Copy the data to a Bitmap for display or something
            //Bitmap bitmap = converterToBitmap.convert(frame);

            //Add frame to array of calculated frames
            calculatedFrames[interpolatedFrameIndex] = frame;
        }
        //framePrevMat.release();
        //frameNextMat.release();
        //frameMat.release();
        return calculatedFrames;
    }

    public static void startProcessing() {
        long startTime = SystemClock.currentThreadTimeMillis();
        // Preload the opencv_objdetect module to work around a known bug.
        //Loader.load(opencv_objdetect.class);
        initDecoder();
        initEncoder();
        GetFramesCountForCalculation();
        calcPyramidsCount();
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
            //while (grabber.getFrameNumber() < grabber.getLengthInFrames()) {
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
        long finishTime = SystemClock.currentThreadTimeMillis();
        Log.d("VideoProcessor", "Finished in " + finishTime + " millis");
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


