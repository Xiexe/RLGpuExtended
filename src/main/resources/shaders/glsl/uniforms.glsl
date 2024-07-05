layout(std140, binding = CAMERA_BUFFER_BINDING_ID) uniform cameraUniforms {
    vec3 cameraPosition;
    float cameraPitch;
    float cameraYaw;
    int zoom;
    int centerX;
    int centerY;
};

layout(std140, binding = LIGHT_BUFFER_BINDING_ID) uniform lightUniforms {
    Light LightsArray[LIGHT_COUNT];
};

uniform sampler2DArray textures;
uniform float brightness;
uniform float smoothBanding;
uniform int fogDepth;
uniform int colorBlindMode;
uniform int expandedMapLoadingChunks;
uniform float textureLightMode;
uniform vec3 lightColor;
uniform vec3 lightDirection;
uniform vec3 ambientColor;
uniform vec3 fogColor;
uniform int drawDistance;
uniform int sceneOffsetX;
uniform int sceneOffsetZ;
uniform vec2 textureAnimations[128];
uniform int tick;
uniform mat4 projectionMatrix;