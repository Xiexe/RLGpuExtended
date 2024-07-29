struct Light
{
    vec4 pos; // 16 bytes // w = plane, xyz = position or direction for directional light
    vec4 offset; // 16 bytes
    vec4 color; // 16 bytes
    float intensity; // 4 bytes
    float radius; // 4 bytes
    int animation; // 4 bytes
    int type; // 4 bytes
}; // 64 bytes

struct MainLight
{
    vec4 pos; // 16 bytes // w = plane, xyz = position or direction for directional light
    vec4 offset; // 16 bytes
    vec4 color; // 16 bytes
    float intensity; // 4 bytes
    float radius; // 4 bytes
    int animation; // 4 bytes
    int type; // 4 bytes
    mat4 projectionMatrix; // 64 bytes
}; // 128 bytes

struct Surface
{
    vec4 albedo;
    vec4 normal;
};

struct VertexFlags
{
    int tileX;
    int tileY;
    int plane;
    bool isBridge;
    bool isTerrain;
    bool isDynamicModel;
    bool isOnBridge;
    bool isRoof;
};