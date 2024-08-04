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
        public int InterfaceTexture;
        public int MainTexture;
        public int TexSamplingMode;
        public int TexSourceDimensions;
        public int TexTargetDimensions;
        public int UiAlphaOverlay;
        public int Textures;
        public int TextureAnimations;
        public int BlockSmall;
        public int BlockLarge;
        public int CameraBlock;
        public int PlayerBlock;
        public int EnvironmentBlock;
        public int TileMarkerBlock;
        public int SystemInfoBlock;
        public int ConfigBlock;
        public int ShadowMap;
        public int DepthMap;
        public int TileMarkerBorderColorMap;
        public int TileMarkerFillColorMap;
        public int TileMarkerSettingsMap;
        public int RoofMaskTextureMap;
    }

    public HashMap<Integer, ShaderVariables> map;

    public void InitializeShaderUniformsForShader(int shader, GpuExtendedPlugin.ComputeMode computeMode)
    {
        if(map == null)
        {
            map = new HashMap<>();
        }

        ShaderVariables shaderVariables = new ShaderVariables();
        shaderVariables.ShadowMap = glGetUniformLocation(shader, "shadowMap");
        shaderVariables.DepthMap = glGetUniformLocation(shader, "depthMap");

        shaderVariables.TileMarkerFillColorMap = glGetUniformLocation(shader, "tileFillColorMap");
        shaderVariables.TileMarkerBorderColorMap = glGetUniformLocation(shader, "tileBorderColorMap");
        shaderVariables.TileMarkerSettingsMap = glGetUniformLocation(shader, "tileSettingsMap");
        shaderVariables.RoofMaskTextureMap = glGetUniformLocation(shader, "roofMaskMap");

        shaderVariables.ColorBlindMode = glGetUniformLocation(shader, "colorBlindMode");
        shaderVariables.Textures = glGetUniformLocation(shader, "textures");
        shaderVariables.TextureAnimations = glGetUniformLocation(shader, "textureAnimations");

        shaderVariables.InterfaceTexture = glGetUniformLocation(shader, "interfaceTexture");
        shaderVariables.MainTexture = glGetUniformLocation(shader, "mainTexture");
        shaderVariables.TexSamplingMode = glGetUniformLocation(shader, "samplingMode");
        shaderVariables.TexTargetDimensions = glGetUniformLocation(shader, "targetDimensions");
        shaderVariables.TexSourceDimensions = glGetUniformLocation(shader, "sourceDimensions");
        shaderVariables.UiColorBlindMode = glGetUniformLocation(shader, "colorBlindMode");
        shaderVariables.UiAlphaOverlay = glGetUniformLocation(shader, "alphaOverlay");

        shaderVariables.CameraBlock = glGetUniformBlockIndex(shader, "CameraBlock");
        shaderVariables.PlayerBlock = glGetUniformBlockIndex(shader, "PlayerBlock");
        shaderVariables.EnvironmentBlock = glGetUniformBlockIndex(shader, "EnvironmentBlock");
        shaderVariables.TileMarkerBlock = glGetUniformBlockIndex(shader, "TileMarkerBlock");
        shaderVariables.SystemInfoBlock = glGetUniformBlockIndex(shader, "SystemInfoBlock");
        shaderVariables.ConfigBlock = glGetUniformBlockIndex(shader, "ConfigBlock");

        if (computeMode == GpuExtendedPlugin.ComputeMode.OPENGL)
        {
            shaderVariables.BlockSmall = glGetUniformBlockIndex(shader, "CameraBlock");
            shaderVariables.BlockLarge = glGetUniformBlockIndex(shader, "CameraBlock");
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
