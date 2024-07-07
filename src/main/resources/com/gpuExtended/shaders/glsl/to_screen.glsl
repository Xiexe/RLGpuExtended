

/*
 * Convert a vertex to screen space
 */
vec3 toScreen(vec3 vertex, float cameraYaw, float cameraPitch, int centerX, int centerY, int zoom) {
  float yawSin = sin(cameraYaw);
  float yawCos = cos(cameraYaw);

  float pitchSin = sin(cameraPitch);
  float pitchCos = cos(cameraPitch);

  float rotatedX = (vertex.z * yawSin) + (vertex.x * yawCos);
  float rotatedZ = (vertex.z * yawCos) - (vertex.x * yawSin);

  float var13 = (vertex.y * pitchCos) - (rotatedZ * pitchSin);
  float var12 = (vertex.y * pitchSin) + (rotatedZ * pitchCos);

  float x = rotatedX * zoom / var12 + centerX;
  float y = var13 * zoom / var12 + centerY;
  float z = -var12;  // in OpenGL depth is negative

  return vec3(x, y, z);
}
