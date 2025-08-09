vec3 neutral(vec3 color) {
    // This is the threshold where the compression of highlights begins.
    // Colors brighter than this value will be smoothly compressed.
    // The value 0.76 is derived from (0.8 - 0.04).
    const float startCompression = 0.76;

    // A factor controlling how much highlights are desaturated as they are
    // compressed. This helps to prevent overly saturated bright spots and
    // gives a more natural, filmic look.
    const float desaturation = 0.15;

    // Find the minimum component of the color. This is used to create an
    // offset that lifts the black levels slightly, mimicking the behavior
    // of film and preventing details from being lost in the darkest areas.
    float x = min(color.r, min(color.g, color.b));
    float offset = x < 0.08 ? x - 6.25 * x * x : 0.04;
    color -= offset; // [2]

    // Find the maximum component of the color after the black level adjustment.
    float peak = max(color.r, max(color.g, color.b));

    // If the brightest part of the color is below the compression threshold,
    // no highlight compression is needed, so we can return the color as is.
    // This preserves the mid-tones and shadows without alteration. [2]
    if (peak < startCompression) {
        return color;
    }

    // This is the core of the highlight compression. It uses a hyperbolic
    // function to smoothly map the bright values into the displayable range. [2]
    const float d = 1.0 - startCompression; // The range of values to be compressed.
    float newPeak = 1.0 - d * d / (peak + d - startCompression);

    // The color is scaled down proportionally to how much the peak was compressed.
    // This maintains the original hue and saturation of the color.
    color *= newPeak / peak;

    // As the highlights are compressed, they can be gently desaturated.
    // The amount of desaturation is proportional to how much the color was
    // compressed, creating a smooth transition.
    float g = 1.0 - 1.0 / (desaturation * (peak - newPeak) + 1.0);
    return mix(color, vec3(newPeak), g); // [2]
}