layout(std140, binding = CAMERA_BUFFER_BINDING_ID) uniform CameraBlock {
    mat4 cameraProjectionMatrix;        // 64 bytes
    vec4 cameraPosition;                // 16 bytes
    vec4 cameraFocalPoint;              // 16 bytes
    float cameraPitch;                  // 4 bytes
    float cameraYaw;                    // 4 bytes
    int zoom;                           // 4 bytes
    int centerX;                        // 4 bytes
    int centerY;                        // 4 bytes
};                                      // 128 bytes

layout(std140, binding = PLAYER_BUFFER_BINDING_ID) uniform PlayerBlock {
    vec4 playerPosition;                // 16 bytes
};                                      // 16 bytes

layout(std140, binding = ENVIRONMENT_BUFFER_BINDING_ID) uniform EnvironmentBlock {
    vec4 ambientColor;                  // 16 bytes
    vec4 fogColor;                      // 16 bytes
    int fogDepth;                       // 4 bytes
    int sceneOffsetX;                   // 4 bytes
    int sceneOffsetZ;                   // 4 bytes
    float envPadding;                   // 4 bytes
    Light mainLight;                    // 112 bytes (due to padding inside Light struct)
    Light additiveLights[LIGHT_COUNT];  // 112 * LIGHT_COUNT = 11,200 bytes (due to padding inside Light struct)
};                                      // 11,360 bytes

layout(std140, binding = TILEMARKER_BUFFER_BINDING_ID) uniform TileMarkerBlock {
    vec4 currentTile;                   // 16 bytes
    vec4 targetTile;                    // 16 bytes
    vec4 hoveredTile;                   // 16 bytes
    vec4 markedTiles[256];              // 256 * 16 bytes = 4096 bytes
};                                      // Total: 4128 bytes

layout(std140, binding = SYSTEMINFO_BUFFER_BINDING_ID) uniform SystemInfoBlock {
    int tick;                           // 4 bytes
    int screenWidth;                    // 4 bytes
    int screenHeight;                   // 4 bytes
    float deltaTime;                    // 4 bytes
};                                      // 16 bytes

layout(std140, binding = CONFIG_BUFFER_BINDING_ID) uniform ConfigBlock {
    float brightness;                   // 4 bytes
    float smoothBanding;                // 4 bytes
    int expandedMapLoadingChunks;       // 4 bytes
    int drawDistance;                   // 4 bytes
    int colorBlindMode;                 // 4 bytes
    int roofFading;                     // 4 bytes
    int roofFadeDistance;               // 4 bytes
};

uniform sampler2DArray textures;
uniform sampler2D shadowMap;
uniform sampler2D depthMap;
uniform vec2 textureAnimations[128];
