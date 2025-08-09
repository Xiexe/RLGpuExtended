
#define L_WHITE 4
vec3 reinhard2(vec3 x) {
    return (x * (1.0 + x / (L_WHITE * L_WHITE))) / (1.0 + x);
}

float reinhard2(float x) {
    return (x * (1.0 + x / (L_WHITE * L_WHITE))) / (1.0 + x);
}

vec3 reinhard(vec3 x) {
    return x / (1.0 + x);
}

float reinhard(float x) {
    return x / (1.0 + x);
}