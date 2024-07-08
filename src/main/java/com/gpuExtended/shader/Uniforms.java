package com.gpuExtended.shader;

import com.google.common.collect.ArrayListMultimap;
import com.gpuExtended.GpuExtendedPlugin;

import java.util.HashMap;

import static org.lwjgl.opengl.GL43C.*;

public class Uniforms
{ // A.K.A. Global Shader Variables
    public static class ShaderVariables
    {
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
        //public int BlockMain;
        public int SmoothBanding;
        public int TextureLightMode;
        public int Tick;
        public int LightDirection;
        public int LightColor;
        public int AmbientColor;

        public int SceneOffsetX;
        public int SceneOffsetZ;
        public int Time;
        public int DeltaTime;

        public int ScreenWidth;
        public int ScreenHeight;

        public int LightUniformsBlock;
        public int CameraUniformsBlock;

        public int LightProjectionMatrix;
        public int ShadowMap;
        public int DepthMap;
        public int PlayerPosition;
        public int CameraFocalPoint;
    }

    public HashMap<Integer, ShaderVariables> map;

    public void InitializeShaderUniformsForShader(int shader, GpuExtendedPlugin.ComputeMode computeMode)
    {
        if(map == null)
        {
            map = new HashMap<>();
        }

        ShaderVariables shaderVariables = new ShaderVariables();

        shaderVariables.ProjectionMatrix = glGetUniformLocation(shader, "projectionMatrix");
        shaderVariables.Brightness = glGetUniformLocation(shader, "brightness");
        shaderVariables.SmoothBanding = glGetUniformLocation(shader, "smoothBanding");
        shaderVariables.FogColor = glGetUniformLocation(shader, "fogColor");
        shaderVariables.FogDepth = glGetUniformLocation(shader, "fogDepth");
        shaderVariables.DrawDistance = glGetUniformLocation(shader, "drawDistance");
        shaderVariables.ExpandedMapLoadingChunks = glGetUniformLocation(shader, "expandedMapLoadingChunks");
        shaderVariables.ColorBlindMode = glGetUniformLocation(shader, "colorBlindMode");
        shaderVariables.TextureLightMode = glGetUniformLocation(shader, "textureLightMode");
        shaderVariables.Tick = glGetUniformLocation(shader, "tick");
        shaderVariables.Textures = glGetUniformLocation(shader, "textures");
        shaderVariables.TextureAnimations = glGetUniformLocation(shader, "textureAnimations");

        shaderVariables.LightDirection = glGetUniformLocation(shader, "lightDirection");
        shaderVariables.LightColor = glGetUniformLocation(shader, "lightColor");
        shaderVariables.AmbientColor = glGetUniformLocation(shader, "ambientColor");
        shaderVariables.SceneOffsetX = glGetUniformLocation(shader, "sceneOffsetX");
        shaderVariables.SceneOffsetZ = glGetUniformLocation(shader, "sceneOffsetZ");
        shaderVariables.Time = glGetUniformLocation(shader, "time");
        shaderVariables.DeltaTime = glGetUniformLocation(shader, "deltaTime");

        shaderVariables.ScreenWidth = glGetUniformLocation(shader, "screenWidth");
        shaderVariables.ScreenHeight = glGetUniformLocation(shader, "screenHeight");

        shaderVariables.PlayerPosition = glGetUniformLocation(shader, "playerPosition");
        shaderVariables.CameraFocalPoint = glGetUniformLocation(shader, "cameraFocalPoint");

        shaderVariables.LightProjectionMatrix = glGetUniformLocation(shader, "lightProjectionMatrix");
        shaderVariables.ShadowMap = glGetUniformLocation(shader, "shadowMap");
        shaderVariables.DepthMap = glGetUniformLocation(shader, "depthMap");

        shaderVariables.CameraUniformsBlock = glGetUniformBlockIndex(shader, "cameraUniforms");
        shaderVariables.LightUniformsBlock = glGetUniformBlockIndex(shader, "lightUniforms");

        shaderVariables.Tex = glGetUniformLocation(shader, "tex");
        shaderVariables.TexSamplingMode = glGetUniformLocation(shader, "samplingMode");
        shaderVariables.TexTargetDimensions = glGetUniformLocation(shader, "targetDimensions");
        shaderVariables.TexSourceDimensions = glGetUniformLocation(shader, "sourceDimensions");
        shaderVariables.UiColorBlindMode = glGetUniformLocation(shader, "colorBlindMode");
        shaderVariables.UiAlphaOverlay = glGetUniformLocation(shader, "alphaOverlay");

        if (computeMode == GpuExtendedPlugin.ComputeMode.OPENGL)
        {
            shaderVariables.BlockSmall = glGetUniformBlockIndex(shader, "cameraUniforms");
            shaderVariables.BlockLarge = glGetUniformBlockIndex(shader, "cameraUniforms");
        }

        map.put(shader, shaderVariables);
    }

    public void ClearUniforms()
    {
        map.clear();
    }

    public ShaderVariables GetUniforms(int shader)
    {
        return map.get(shader);
    }
}
