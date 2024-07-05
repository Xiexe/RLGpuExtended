package com.gpuExtended.rendering;

public class Color
{
    public float r;
    public float g;
    public float b;
    public float a;

    public Color(float r, float g, float b, float a)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public Color(float r, float g, float b)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = 1f;
    }

    public Color(float rgb)
    {
        this.r = rgb;
        this.g = rgb;
        this.b = rgb;
        this.a = 1f;
    }

    public Color(int hsla)
    {
        int[] rgba = HslToRgba(hsla);
        this.r = (float)rgba[0];
        this.g = (float)rgba[1];
        this.b = (float)rgba[2];
        this.a = (float)rgba[3];
    }

    public int[] HslToRgba(int hsla)
    {
        int[] unpackedHSLA = UnpackIntHSLA(hsla);
        int hue = unpackedHSLA[0];
        int saturation = unpackedHSLA[1];
        int lightness = unpackedHSLA[2];
        int alpha = unpackedHSLA[3];

        float c = (1 - Math.abs(2 * lightness - 1)) * saturation;
        float x = c * (1 - Math.abs((hue / 60) % 2 - 1));
        float m = lightness - c / 2;
        float rPrime, gPrime, bPrime;

        if (hue >= 0 && hue < 60) {
            rPrime = c;
            gPrime = x;
            bPrime = 0;
        } else if (hue >= 60 && hue < 120) {
            rPrime = x;
            gPrime = c;
            bPrime = 0;
        } else if (hue >= 120 && hue < 180) {
            rPrime = 0;
            gPrime = c;
            bPrime = x;
        } else if (hue >= 180 && hue < 240) {
            rPrime = 0;
            gPrime = x;
            bPrime = c;
        } else if (hue >= 240 && hue < 300) {
            rPrime = x;
            gPrime = 0;
            bPrime = c;
        } else {
            rPrime = c;
            gPrime = 0;
            bPrime = x;
        }

        int red = (int) ((rPrime + m) * 255);
        int green = (int) ((gPrime + m) * 255);
        int blue = (int) ((bPrime + m) * 255);

        return new int[] {
                Math.max(0, Math.min(255, red)),
                Math.max(0, Math.min(255, green)),
                Math.max(0, Math.min(255, blue)),
                Math.max(0, Math.min(255, alpha))
        };
    }

    public int[] UnpackIntHSLA(int hsl)
    {
        int A = (hsl >> 24 & 0xff);
        int H = ((hsl & 0xff0000) >> 16);
        int S = ((hsl & 0xff00) >> 8);
        int L = (hsl & 0xff);

        return new int[] { H, S, L, A };
    }
}
