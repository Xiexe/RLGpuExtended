package com.gpuExtended.scene;

import com.google.inject.Inject;
import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.shader.ShaderHandler;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

public class Skybox {

    @Inject
    private GpuExtendedPlugin plugin;

    @Inject
    private GpuExtendedConfig config;

    @Inject
    private ShaderHandler shaderHandler;

    private int vao;
    public void Initialize() {
        float[] skyboxVertices = {
                // Positions
                -1.0f,  1.0f, -1.0f,
                -1.0f, -1.0f, -1.0f,
                1.0f, -1.0f, -1.0f,
                1.0f, -1.0f, -1.0f,
                1.0f,  1.0f, -1.0f,
                -1.0f,  1.0f, -1.0f,

                -1.0f, -1.0f,  1.0f,
                -1.0f, -1.0f, -1.0f,
                -1.0f,  1.0f, -1.0f,
                -1.0f,  1.0f, -1.0f,
                -1.0f,  1.0f,  1.0f,
                -1.0f, -1.0f,  1.0f,

                1.0f, -1.0f, -1.0f,
                1.0f, -1.0f,  1.0f,
                1.0f,  1.0f,  1.0f,
                1.0f,  1.0f,  1.0f,
                1.0f,  1.0f, -1.0f,
                1.0f, -1.0f, -1.0f,

                -1.0f, -1.0f,  1.0f,
                -1.0f,  1.0f,  1.0f,
                1.0f,  1.0f,  1.0f,
                1.0f,  1.0f,  1.0f,
                1.0f, -1.0f,  1.0f,
                -1.0f, -1.0f,  1.0f,

                -1.0f,  1.0f, -1.0f,
                1.0f,  1.0f, -1.0f,
                1.0f,  1.0f,  1.0f,
                1.0f,  1.0f,  1.0f,
                -1.0f,  1.0f,  1.0f,
                -1.0f,  1.0f, -1.0f,

                -1.0f, -1.0f, -1.0f,
                -1.0f, -1.0f,  1.0f,
                1.0f, -1.0f, -1.0f,
                1.0f, -1.0f, -1.0f,
                -1.0f, -1.0f,  1.0f,
                1.0f, -1.0f,  1.0f
        };

        vao = GL30.glGenVertexArrays();
        int vbo = GL30.glGenBuffers();

        GL30.glBindVertexArray(vao);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(skyboxVertices.length);
        buffer.put(skyboxVertices).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * 4, 0);
        GL20.glEnableVertexAttribArray(0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public void renderSkybox(float[] projectionMatrix, float[] viewMatrix, float[] skyColor) {
//        GL20.glUseProgram(shaderHandler.getSkyboxShader());
//
//        int projectionLoc = GL20.glGetUniformLocation(shaderProgram, "projection");
//        GL20.glUniformMatrix4fv(projectionLoc, false, projectionMatrix);
//
//        int viewLoc = GL20.glGetUniformLocation(shaderProgram, "view");
//        GL20.glUniformMatrix4fv(viewLoc, false, viewMatrix);
//
//        int skyColorLoc = GL20.glGetUniformLocation(shaderProgram, "skyColor");
//        GL20.glUniform3f(skyColorLoc, skyColor[0], skyColor[1], skyColor[2]);
//
//        GL30.glBindVertexArray(vao);
//        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);
//        GL30.glBindVertexArray(0);
//
//        GL20.glUseProgram(0);
    }
}
