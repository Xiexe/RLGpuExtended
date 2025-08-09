package com.gpuExtended.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Tonemapper
{
    TONEMAPPER_NEUTRAL("Neutral", 0),
    TONEMAPPER_ACES("ACES", 1),
    TONEMAPPER_AGX("AGX", 2),
    TONEMAPPER_FILMIC("Filmic", 3),
    TONEMAPPER_REINHARD("Reinhard", 4),
    TONEMAPPER_LOTTES("Lottes", 5);

    private final String name;
    private final int value;

    @Override
    public String toString()
    {
        return name;
    }
}
