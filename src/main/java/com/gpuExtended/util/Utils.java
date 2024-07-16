package com.gpuExtended.util;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Utils {
    public static Color colorLerp(Color color1, Color color2, float t) {
        // Ensure t is within the range [0, 1]
        t = Math.max(0, Math.min(1, t));

        int r1 = color1.getRed();
        int g1 = color1.getGreen();
        int b1 = color1.getBlue();
        int a1 = color1.getAlpha();

        int r2 = color2.getRed();
        int g2 = color2.getGreen();
        int b2 = color2.getBlue();
        int a2 = color2.getAlpha();

        int r = (int) (r1 + t * (r2 - r1));
        int g = (int) (g1 + t * (g2 - g1));
        int b = (int) (b1 + t * (b2 - b1));
        int a = (int) (a1 + t * (a2 - a1));

        return new Color(r, g, b, a);
    }

    public static float InverseLerp(float a, float b, float value) {
        return (value - a) / (b - a);
    }

    public static int GenerateTileHash(int[] worldPosition) {
        if (worldPosition == null || worldPosition.length != 3) {
            throw new IllegalArgumentException("World position must be an array of 3 integers");
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(12);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(worldPosition);

        return murmurHash3(byteBuffer.array(), 0, byteBuffer.array().length, 0);
    }

    public static int GenerateHashFromPosition(int x, int y, int z, int orientation, int hillskew, boolean isDynamic)
    {
        ByteBuffer byteBuffer = ByteBuffer.allocate(24);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(x);
        intBuffer.put(y);
        intBuffer.put(z);
        intBuffer.put(orientation);
        intBuffer.put(hillskew);
        intBuffer.put(isDynamic ? 1 : 0);

        return murmurHash3(byteBuffer.array(), 0, byteBuffer.array().length, 0);
    }

    public static int murmurHash3(byte[] data, int offset, int len, int seed) {
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;
        final int r1 = 15;
        final int r2 = 13;
        final int m = 5;
        final int n = 0xe6546b64;

        int hash = seed;

        int i = 0;
        while (i + 4 <= len) {
            int k = (data[offset + i] & 0xff) | ((data[offset + i + 1] & 0xff) << 8) |
                    ((data[offset + i + 2] & 0xff) << 16) | ((data[offset + i + 3] & 0xff) << 24);

            k *= c1;
            k = (k << r1) | (k >>> (32 - r1));
            k *= c2;

            hash ^= k;
            hash = (hash << r2) | (hash >>> (32 - r2));
            hash = hash * m + n;

            i += 4;
        }

        int k = 0;

        switch (len - i) {
            case 3:
                k ^= (data[offset + i + 2] & 0xff) << 16;
            case 2:
                k ^= (data[offset + i + 1] & 0xff) << 8;
            case 1:
                k ^= (data[offset + i] & 0xff);
                k *= c1;
                k = (k << r1) | (k >>> (32 - r1));
                k *= c2;
                hash ^= k;
        }

        hash ^= len;
        hash ^= (hash >>> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >>> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >>> 16);

        return hash;
    }
}
