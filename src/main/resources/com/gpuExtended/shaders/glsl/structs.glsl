struct Light
{
    vec4 pos; // 16 bytes // w = type, xyz = position or direction for directional light
    vec4 color; // 16 bytes
    float intensity; // 4 bytes
    float radius; // 4 bytes
    int animation; // 4 bytes
    float lightPad; // 4 bytes
    mat4 projectionMatrix; // 64 bytes
}; // 108 bytes

struct Surface
{
    vec4 albedo;
    vec4 normal;
};