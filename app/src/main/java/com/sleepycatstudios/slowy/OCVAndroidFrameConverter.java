package com.sleepycatstudios.slowy;

import android.graphics.Bitmap;
import android.hardware.Camera;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2RGBA;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_GRAY2RGBA;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;

import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.merge;
import static org.bytedeco.javacpp.opencv_core.split;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2RGBA;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGRA2RGBA;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
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
import static org.bytedeco.javacpp.opencv_core.split;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2RGBA;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGRA2RGBA;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;

/**
 * Created by Александр on 11.09.2016.
 */
public class OCVAndroidFrameConverter extends AndroidFrameConverter {
    OpenCVFrameConverter.ToMat oCVConverter = new OpenCVFrameConverter.ToMat();
    Bitmap bitmap;
    Mat mat;

    /**
     * Convert YUV 4:2:0 SP (NV21) data to BGR, as received, for example,
     * via {@link Camera.PreviewCallback#onPreviewFrame(byte[],Camera)}.
     */
    public Frame convert(byte[] data, int width, int height) {
        if (frame == null || frame.imageWidth != width
                || frame.imageHeight != height || frame.imageChannels != 3) {
            frame = new Frame(width, height, Frame.DEPTH_UBYTE, 3);
        }
        ByteBuffer out = (ByteBuffer)frame.image[0];
        int stride = frame.imageStride;

        // ported from https://android.googlesource.com/platform/development/+/master/tools/yuv420sp2rgb/yuv420sp2rgb.c
        int offset = height * width;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int Y = data[i * width + j] & 0xFF;
                int V = data[offset + (i/2) * width + 2 * (j/2)    ] & 0xFF;
                int U = data[offset + (i/2) * width + 2 * (j/2) + 1] & 0xFF;

                // Yuv Convert
                Y -= 16;
                U -= 128;
                V -= 128;

                if (Y < 0)
                    Y = 0;

                // R = (int)(1.164 * Y + 2.018 * U);
                // G = (int)(1.164 * Y - 0.813 * V - 0.391 * U);
                // B = (int)(1.164 * Y + 1.596 * V);

                int B = (int)(1192 * Y + 2066 * U);
                int G = (int)(1192 * Y - 833 * V - 400 * U);
                int R = (int)(1192 * Y + 1634 * V);

                R = Math.min(262143, Math.max(0, R));
                G = Math.min(262143, Math.max(0, G));
                B = Math.min(262143, Math.max(0, B));

                R >>= 10; R &= 0xff;
                G >>= 10; G &= 0xff;
                B >>= 10; B &= 0xff;

                out.put(i * stride + 3 * j,     (byte)B);
                out.put(i * stride + 3 * j + 1, (byte)G);
                out.put(i * stride + 3 * j + 2, (byte)R);
            }
        }
        return frame;
    }

    @Override public Frame convert(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        int channels = 0;
        switch (bitmap.getConfig()) {
            case ALPHA_8:   channels = 1; break;
            case RGB_565:
            case ARGB_4444: channels = 2; break;
            case ARGB_8888: channels = 4; break;
            default: assert false;
        }

        if (frame == null || frame.imageWidth != bitmap.getWidth()
                || frame.imageHeight != bitmap.getHeight() || frame.imageChannels != channels) {
            frame = new Frame(bitmap.getWidth(), bitmap.getHeight(), Frame.DEPTH_UBYTE, channels);
        }

        // assume matching strides
        bitmap.copyPixelsToBuffer(frame.image[0].position(0));

        return frame;
    }

    @Override public Bitmap convert(Frame frame) {
        if (frame == null || frame.image == null) {
            return null;
        }

        Bitmap.Config config = null;
        switch (frame.imageChannels) {
            case 2: config = Bitmap.Config.RGB_565; break;
            case 1:
            case 3:
            case 4: config = Bitmap.Config.ARGB_8888; break;
            default: assert false;
        }

        if (bitmap == null
                || bitmap.getWidth() != frame.imageWidth
                || bitmap.getHeight() != frame.imageHeight
                || bitmap.getConfig() != config)
        {
            bitmap = Bitmap.createBitmap(frame.imageWidth, frame.imageHeight, config);
        }

        ByteBuffer in = (ByteBuffer)frame.image[0];
        if (frame.imageChannels == 1) {
            mat = oCVConverter.convert(frame);
            cvtColor(mat, mat, COLOR_GRAY2RGBA);
            bitmap.copyPixelsFromBuffer(mat.getByteBuffer().position(0));
            mat.release();
        } else if (frame.imageChannels == 3) {
            mat = oCVConverter.convert(frame);
            cvtColor(mat, mat, COLOR_BGR2RGBA);
            bitmap.copyPixelsFromBuffer(mat.getByteBuffer().position(0));
            mat.release();
        } else {
            // assume matching strides
            bitmap.copyPixelsFromBuffer(in.position(0));
        }
        return bitmap;
    }
}
