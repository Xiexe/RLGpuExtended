package com.gpuExtended.shader;

import com.gpuExtended.GpuExtendedPlugin;
import static org.lwjgl.opengl.GL43C.*;

public class Uniforms
{ // A.K.A. Global Shader Variables
    public int ColorBlindMode;
    public int UiColorBlindMode;
    public int FogColor;
    public int FogDepth;
    public int DrawDistance;
    public int ExpandedMapLoadingChunks;
    public int ProjectionMatrix;
    public int Brightness;
    public int Tex;
    public int TexSamplingMode;
    public int TexSourceDimensions;
    public int TexTargetDimensions;
    public int UiAlphaOverlay;
    public int Textures;
    public int TextureAnimations;
    public int BlockSmall;
    public int BlockLarge;
    public int BlockMain;
    public int SmoothBanding;
    public int TextureLightMode;
    public int Tick;
    public int LightDirection;
    public int LightColor;
    public int AmbientColor;
    public int BlockLights;
    public int SceneOffsetX;
    public int SceneOffsetZ;
    public int Time;
    public int DeltaTime;

    public void Initialize(GpuExtendedPlugin.ComputeMode computeMode, int shader, int uiShader, int computeShader, int smallComputeShader)
    {
        ProjectionMatrix = glGetUniformLocation(shader, "projectionMatrix");
        Brightness = glGetUniformLocation(shader, "brightness");
        SmoothBanding = glGetUniformLocation(shader, "smoothBanding");
        FogColor = glGetUniformLocation(shader, "fogColor");
        FogDepth = glGetUniformLocation(shader, "fogDepth");
        DrawDistance = glGetUniformLocation(shader, "drawDistance");
        ExpandedMapLoadingChunks = glGetUniformLocation(shader, "expandedMapLoadingChunks");
        ColorBlindMode = glGetUniformLocation(shader, "colorBlindMode");
        TextureLightMode = glGetUniformLocation(shader, "textureLightMode");
        Tick = glGetUniformLocation(shader, "tick");
        Textures = glGetUniformLocation(shader, "textures");
        TextureAnimations = glGetUniformLocation(shader, "textureAnimations");

        LightDirection = glGetUniformLocation(shader, "lightDirection");
        LightColor = glGetUniformLocation(shader, "lightColor");
        AmbientColor = glGetUniformLocation(shader, "ambientColor");
        SceneOffsetX = glGetUniformLocation(shader, "sceneOffsetX");
        SceneOffsetZ = glGetUniformLocation(shader, "sceneOffsetZ");
        Time = glGetUniformLocation(shader, "time");
        DeltaTime = glGetUniformLocation(shader, "deltaTime");

        BlockMain = glGetUniformBlockIndex(shader, "uniforms");
        BlockLights = glGetUniformBlockIndex(shader, "lightUniforms");

        Tex = glGetUniformLocation(uiShader, "tex");
        TexSamplingMode = glGetUniformLocation(uiShader, "samplingMode");
        TexTargetDimensions = glGetUniformLocation(uiShader, "targetDimensions");
        TexSourceDimensions = glGetUniformLocation(uiShader, "sourceDimensions");
        UiColorBlindMode = glGetUniformLocation(uiShader, "colorBlindMode");
        UiAlphaOverlay = glGetUniformLocation(uiShader, "alphaOverlay");

        if (computeMode == GpuExtendedPlugin.ComputeMode.OPENGL)
        {
            BlockSmall = glGetUniformBlockIndex(smallComputeShader, "uniforms");
            BlockLarge = glGetUniformBlockIndex(computeShader, "uniforms");
        }
    }
}
