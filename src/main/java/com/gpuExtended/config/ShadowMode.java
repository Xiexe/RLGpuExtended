package com.gpuExtended.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShadowMode
{
    MODE_OFF("Off", 0),
    MODE_PCF("PCF", 1),
    MODE_PCSS("PCSS", 2);

    private final String name;
    private final int value;

    @Override
    public String toString()
    {
        return name;
    }
}
