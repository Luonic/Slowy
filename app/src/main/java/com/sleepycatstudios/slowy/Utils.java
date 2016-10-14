package com.sleepycatstudios.slowy;

import android.os.Debug;

public class Utils {
    public static long getUsedMemorySize() {

        long freeSize = 0L;
        long totalSize = 0L;
        long usedSize = -1L;
        try {
            Runtime info = Runtime.getRuntime();
            freeSize = info.freeMemory() / 1024 / 1024;
            totalSize = info.totalMemory() / 1024 / 1024;
            usedSize = totalSize - freeSize;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return usedSize;
    }

    public static long getFreeMemorySize() {

        long freeSize = 0L;
        long totalSize = 0L;
        long usedSize = -1L;
        try {
            Runtime info = Runtime.getRuntime();
            freeSize = info.freeMemory() / 1024 / 1024;
            totalSize = info.totalMemory() / 1024 / 1024;
            usedSize = totalSize - freeSize;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return freeSize;
    }

    public static long getFreeNativeMemory() {
        return Debug.getNativeHeapFreeSize() / 1024 / 1024;
    }
}
