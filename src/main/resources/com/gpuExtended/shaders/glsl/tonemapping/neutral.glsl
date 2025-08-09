vec3 neutral(vec3 hdr_linear, float whitePoint) {

    vec3 c = hdr_linear;

    const vec3 W = vec3(0.2126, 0.7152, 0.0722);
    float Y  = max(1e-6, dot(c, W));

    float w2 = whitePoint * whitePoint;
    float Yt = (Y * (1.0 + Y / w2)) / (1.0 + Y);

    float s = Yt / Y;
    vec3  mapped = c * s;

    return mapped;
}