package com.gpuExtended.shader;

import com.gpuExtended.GpuExtendedPlugin;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

import static org.lwjgl.opengl.GL43C.*;

@Slf4j
public class Uniforms
{ // A.K.A. Global Shader Variables
    public static class ShaderVariables
    {
        public int ColorBlindMode;
        public int UiColorBlindMode;
        public int InterfaceTexture;
        public int MainTexture;
        public int BloomTexture;
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
        public int GodNoiseTextureMap;
        public int TileHeightMap;
        
        public int SourceTexture;
        public int DestinationTexture;
        public int SourceResolution;
        public int DestinationResolution;
        public int MipmapLevel;
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
        shaderVariables.TileHeightMap = glGetUniformLocation(shader, "tileHeightMap");
        shaderVariables.GodNoiseTextureMap = glGetUniformLocation(shader, "godNoiseMap");
        
        shaderVariables.SourceTexture = glGetUniformLocation(shader, "srcTexture");
        shaderVariables.DestinationTexture = glGetUniformLocation(shader, "dstTexture");
        shaderVariables.SourceResolution = glGetUniformLocation(shader, "srcResolution");
        shaderVariables.DestinationResolution = glGetUniformLocation(shader, "dstResolution");
        shaderVariables.MipmapLevel = glGetUniformLocation(shader, "mipMapLevel");

        shaderVariables.ColorBlindMode = glGetUniformLocation(shader, "colorBlindMode");
        shaderVariables.Textures = glGetUniformLocation(shader, "textures");
        shaderVariables.TextureAnimations = glGetUniformLocation(shader, "textureAnimations");

        shaderVariables.InterfaceTexture = glGetUniformLocation(shader, "interfaceTexture");
        shaderVariables.MainTexture = glGetUniformLocation(shader, "mainTexture");
        shaderVariables.BloomTexture = glGetUniformLocation(shader, "bloomTexture");
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
        if(map != null) {
            map.clear();
        }
    }

    public ShaderVariables GetUniforms(int shader)
    {
        if(map == null)
        {
            return null;
        }

        if(!map.containsKey(shader))
        {
            log.info("Shader uniforms not found");
            return null;
        }

        return map.get(shader);
    }
}
