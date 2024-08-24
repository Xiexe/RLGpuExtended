

#include "shaders/glsl/to_screen.glsl"

/*
 * Rotate a vertex by a given orientation in JAU
 */
vec4 rotate_vertex(vec4 vertex, int orientation) {
  float rad = orientation * UNIT;
  float s = sin(rad);
  float c = cos(rad);
  // clang-format off
  mat4 m = mat4(
  c, 0, s, 0,
  0, 1, 0, 0,
  -s, 0, c, 0,
  0, 0, 0, 1
  );
  // clang-format on
  return vertex * m;
}

vec4 rotate2(vec4 vertex, int orientation) {
  vec4 iVertex = vec4(vertex * 1000);
  vertex = rotate_vertex(iVertex, orientation) / 1000.0;
  return vertex;
}

/*
 * Calculate the distance to a vertex given the camera angle
 */
float distance(vec3 vertex, float cameraYaw, float cameraPitch) {
  float yawSin = sin(cameraYaw);
  float yawCos = cos(cameraYaw);
  float pitchSin = sin(cameraPitch);
  float pitchCos = cos(cameraPitch);
  float j = vertex.z * yawCos - vertex.x * yawSin;
  float l = vertex.y * pitchSin + j * pitchCos;
  return l;
}

/*
 * Calculate the distance to a face
 */
int face_distance(vec3 vA, vec3 vB, vec3 vC, float cameraYaw, float cameraPitch) {
  float dvA = distance(vA, cameraYaw, cameraPitch);
  float dvB = distance(vB, cameraYaw, cameraPitch);
  float dvC = distance(vC, cameraYaw, cameraPitch);
  float faceDistance = (dvA + dvB + dvC) / 3;
  return int(faceDistance);
}

/*
 * Test if a face is visible (not backward facing)
 */
bool face_visible(vec3 vA, vec3 vB, vec3 vC, ivec4 position) {
  vec3 lA = vA + position.xyz - cameraPosition.xyz;
  vec3 lB = vB + position.xyz - cameraPosition.xyz;
  vec3 lC = vC + position.xyz - cameraPosition.xyz;

  vec3 sA = toScreen(lA, cameraYaw, cameraPitch, centerX, centerY, zoom);
  vec3 sB = toScreen(lB, cameraYaw, cameraPitch, centerX, centerY, zoom);
  vec3 sC = toScreen(lC, cameraYaw, cameraPitch, centerX, centerY, zoom);

  return (sA.x - sB.x) * (sC.y - sB.y) - (sC.x - sB.x) * (sA.y - sB.y) > 0;
}
