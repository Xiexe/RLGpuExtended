package com.gpuExtended.util.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShadowResolution
{
    RES_OFF("Off", 0),
    RES_1024("Low (1K)", 1024),
    RES_2048("Medium (2K)", 2048),
    RES_4096("High (4K)", 4096),
    RES_8192("Ultra (8K)", 8192),
    RES_16384("Extreme (16K)", 16384);

    private final String name;
    private final int value;

    @Override
    public String toString()
    {
        return name;
    }
}
