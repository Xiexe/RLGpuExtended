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
    int sceneOffsetX;                   // 4 bytes
    int sceneOffsetZ;                   // 4 bytes
};                                      // 24 bytes

layout(std140, binding = ENVIRONMENT_BUFFER_BINDING_ID) uniform EnvironmentBlock {
    vec4 ambientColor;                  // 16 bytes
    vec4 skyColor;                      // 16 bytes
    int envType;                        // 4 bytes
    int fogDepth;                       // 4 bytes
    int padEnv0;                        // 4 bytes
    int padEnv1;                        // 4 bytes
    Light mainLight;                    // 128 bytes (due to padding inside Light struct)
    Light additiveLights[LIGHT_COUNT];  // 128 * LIGHT_COUNT = 12,800 bytes (due to padding inside Light struct)
};                                      // 12,976 bytes

layout(std140, binding = TILEMARKER_BUFFER_BINDING_ID) uniform TileMarkerBlock {
    vec4 currentTile;                   // 16 bytes
    vec4 currentTileFillColor;
    vec4 currentTileOutlineColor;
    vec4 targetTile;                    // 16 bytes
    vec4 targetTileFillColor;
    vec4 targetTileOutlineColor;
    vec4 hoveredTile;                   // 16 bytes
    vec4 hoveredTileFillColor;
    vec4 hoveredTileOutlineColor;
};                                      // Total: 9 * 16 = 144 bytes

layout(std140, binding = SYSTEMINFO_BUFFER_BINDING_ID) uniform SystemInfoBlock {
    int tick;                           // 4 bytes
    int screenWidth;                    // 4 bytes
    int screenHeight;                   // 4 bytes
    float deltaTime;                    // 4 bytes
    float time;                         // 4 bytes
};                                      // 24 bytes

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
uniform sampler2D tileFillColorMap; // holds colors of tiles
uniform sampler2D tileBorderColorMap; // holds textures of tiles
uniform sampler2D tileSettingsMap; // holds settings of tiles
uniform sampler2D tileMaskTexture; // holds mask of tiles
uniform vec2 textureAnimations[128];
