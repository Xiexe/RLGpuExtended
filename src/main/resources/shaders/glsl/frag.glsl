/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#version 330

//#define FRAG_UVS

uniform sampler2DArray textures;
uniform float brightness;
uniform float smoothBanding;
uniform vec4 fogColor;
uniform int colorBlindMode;
uniform float textureLightMode;
//uniform vec3 lightDirection;
//uniform vec4 ambientColor;

in vec4 fColor;
smooth in vec3 fNormal;
noperspective centroid in float fHsl;
flat in int fTextureId; //41 = fire cape, 60 = infernal cape
in vec2 fUv;
in float fFogAmount;

out vec4 FragColor;

#include "/shaders/glsl/hsl_to_rgb.glsl"
#include "/shaders/glsl/colorblind.glsl"
#include "/shaders/glsl/structs.glsl"
#include "/shaders/glsl/helpers.glsl"

void main() {
  Surface s;
  PopulateSurfaceColor(s);
  PopulateSurfaceNormal(s, fNormal);

  vec3 lightDirection = vec3(0.5, 1.0, 0.5);
  vec3 lightColor = vec3(1, 1, 1);
  vec3 ambientColor = fogColor.rgb;

  float ndl = max(dot(s.normal, lightDirection), 0);
  vec3 litFragment = s.albedo.rgb * (ndl * lightColor + ambientColor);

  vec3 finalColor = mix(litFragment, fogColor.rgb, fFogAmount);

  if(fTextureId == 60)
    finalColor = vec3(1);

  PostProcessImage(finalColor, colorBlindMode);
  FragColor = vec4(finalColor, s.albedo.a);
}
