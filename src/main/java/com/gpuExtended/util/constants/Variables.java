package com.gpuExtended.util.constants;

import net.runelite.api.Constants;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;

public class Variables {
    public static final int BIT_ZHEIGHT = 24;
    public static final int BIT_HILLSKEW = 26;

    public static final int BIT_PLANE = 29;
    public static final int BIT_XPOS = 21;
    public static final int BIT_YPOS = 13;
    public static final int BIT_ISBRIDGE = 12;
    public static final int BIT_ISTERRAIN = 11;
    public static final int BIT_ISDYNAMICMODEL = 10;
    public static final int BIT_ISONBRIDGE = 9;

    public static final int MAX_LIGHTS = 500;
    public static final int MAX_LIGHTS_PER_TILE = 16;
    public static final int MAX_LIGHT_RENDER_DISTANCE = 50;

    // This is the maximum number of triangles the compute shaders support
    public static final int MAX_TRIANGLE = 6144;
    public static final int SMALL_TRIANGLE_COUNT = 512;
    public static final int FLAG_SCENE_BUFFER = Integer.MIN_VALUE;
    public static final int DEFAULT_DISTANCE = 25;
    public static final int MAX_DISTANCE = 184;
    public static final int MAX_FOG_DEPTH = 100;
    public static final int SCENE_OFFSET = (EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2; // offset for sxy -> msxy
    public static final int GROUND_MIN_Y = 350; // how far below the ground models extend

    // overlap is ok here because this is vtx attrib, and the other is uniform buffers
    public static final int VPOS_BINDING_ID = 0;
    public static final int VHSL_BINDING_ID = 1;
    public static final int VUV_BINDING_ID = 2;
    public static final int VNORM_BINDING_ID = 3;
    public static final int VFLAGS_BINDING_ID = 4;

    public static final int CAMERA_BUFFER_BINDING_ID = 0;
    public static final int PLAYER_BUFFER_BINDING_ID = 1;
    public static final int ENVIRONMENT_BUFFER_BINDING_ID = 2;
    public static final int TILEMARKER_BUFFER_BINDING_ID = 3;
    public static final int SYSTEMINFO_BUFFER_BINDING_ID = 4;
    public static final int CONFIG_BUFFER_BINDING_ID = 5;
    public static final int LIGHT_BINNING_BUFFER_BINDING_ID = 6;

    public static final int MODEL_BUFFER_IN_BINDING_ID = 1;
    public static final int VERTEX_BUFFER_OUT_BINDING_ID = 2;
    public static final int VERTEX_BUFFER_IN_BINDING_ID = 3;
    public static final int TEMP_VERTEX_BUFFER_IN_BINDING_ID = 4;
    public static final int TEXTURE_BUFFER_OUT_BINDING_ID = 5;
    public static final int TEXTURE_BUFFER_IN_BINDING_ID = 6;
    public static final int TEMP_TEXTURE_BUFFER_IN_BINDING_ID = 7;
    public static final int NORMAL_BUFFER_OUT_BINDING_ID = 8;
    public static final int NORMAL_BUFFER_IN_BINDING_ID = 9;
    public static final int TEMP_NORMAL_BUFFER_IN_BINDING_ID = 10;

    public static final int FLAGS_BUFFER_OUT_BINDING_ID = 11;
    public static final int FLAGS_BUFFER_IN_BINDING_ID = 12;
    public static final int TEMP_FLAGS_BUFFER_IN_BINDING_ID = 13;
}
