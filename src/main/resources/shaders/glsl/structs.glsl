#define LIGHT_COUNT 100 // 100 lights maximum

struct Light
{
    vec4 pos;
    vec4 color;
    float intensity;
    float radius;
    float type;
    float animation;
};

struct Surface
{
    vec4 albedo;
    vec4 normal;
};

layout(std140) uniform lightUniforms
{
    Light LightsArray[LIGHT_COUNT];
};