#version 420

in float fIsLocalPlayer;

void main() {
    gl_FragDepth = gl_FragCoord.z;
}