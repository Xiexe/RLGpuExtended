package com.gpuExtended.util;

public class ProfileTime {
    // TODO: Remove this for release? Probably no one has Intrinsic.dll on their computer though
    private static boolean NATIVE_LOADED = false;
    static {
        try {
            // Intrinsic.dll just defines a wrapper around __rdtsc for more precise profiling
            System.loadLibrary("intrinsic");
            NATIVE_LOADED = true;
            System.out.println("Native library 'intrinsic' loaded successfully. rdtsc() will use the native implementation.");
        } catch (UnsatisfiedLinkError e) {
            NATIVE_LOADED = false;
            System.err.println("WARNING: Failed to load native library 'intrinsic'. Falling back to System.nanoTime().");
            System.err.println("  Reason: " + e.getMessage());
        }
    }

    private static double timestampUnit = 1.0/1000;
    public static boolean calculatedTimestampUnit = false;

    public static double cachedTscFrequency = 0;

    public static double GetTimestampUnit() {
        if (!NATIVE_LOADED) return 1.0/1000;
        if (cachedTscFrequency > 0) return cachedTscFrequency;

        cachedTscFrequency = 1000000.0/getTscFrequency();
        return cachedTscFrequency;
    }

    public static native long rdtsc();
    public static native long getTscFrequency();

    public static long GetTime() {
        if (NATIVE_LOADED) {
            return rdtsc();
        } else {
            return System.nanoTime();
        }
    }
}
