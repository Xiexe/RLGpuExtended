package com.gpuExtended.regions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.annotation.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Area {
    String name;
    @Nullable
    Region[] regions;
    @Nullable
    SubArea[] subAreas;
}
