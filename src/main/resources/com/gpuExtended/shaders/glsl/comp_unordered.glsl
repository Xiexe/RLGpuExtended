#include "VERSION_HEADER"

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/comp_structs.glsl"
#include "shaders/glsl/comp_common.glsl"

layout(local_size_x = 6) in;

void main() {
  uint groupId = gl_WorkGroupID.x;
  uint localId = gl_LocalInvocationID.x;
  modelinfo minfo = modelInfos[groupId];

  int offset = minfo.offset;
  int size = minfo.size;
  int outOffset = minfo.idx;
  int toffset = minfo.toffset;
  int flags = minfo.flags;

  if (localId >= size) {
    return;
  }

  uint ssboOffset = localId;
  Vertex thisA, thisB, thisC;
  vec4 normA, normB, normC;
  vec4 texA, texB, texC;
  ivec4 flagsA, flagsB, flagsC;

  uint myOffset = localId;
  vec3 pos = vec3(minfo.x, minfo.y, minfo.z);
  ivec4 texPos = ivec4(0, pos);

  // Grab triangle vertices and normals from the correct buffer
  bool isStatic = flags < 0;
  if (isStatic) {
    thisA = staticVertexBufferIn[offset + ssboOffset * 3];
    thisB = staticVertexBufferIn[offset + ssboOffset * 3 + 1];
    thisC = staticVertexBufferIn[offset + ssboOffset * 3 + 2];

    normA = staticNormalBufferIn[offset + ssboOffset * 3];
    normB = staticNormalBufferIn[offset + ssboOffset * 3 + 1];
    normC = staticNormalBufferIn[offset + ssboOffset * 3 + 2];

    texA = texPos + statixUvBufferIn[toffset + localId * 3];
    texB = texPos + statixUvBufferIn[toffset + localId * 3 + 1];
    texC = texPos + statixUvBufferIn[toffset + localId * 3 + 2];
  } else {
    thisA = dynamicVertexBufferIn[offset + ssboOffset * 3];
    thisB = dynamicVertexBufferIn[offset + ssboOffset * 3 + 1];
    thisC = dynamicVertexBufferIn[offset + ssboOffset * 3 + 2];

    normA = dynamicNormalBufferIn[offset + ssboOffset * 3];
    normB = dynamicNormalBufferIn[offset + ssboOffset * 3 + 1];
    normC = dynamicNormalBufferIn[offset + ssboOffset * 3 + 2];

    texA = texPos + dynamicUvBufferIn[toffset + localId * 3];
    texB = texPos + dynamicUvBufferIn[toffset + localId * 3 + 1];
    texC = texPos + dynamicUvBufferIn[toffset + localId * 3 + 2];
  }

  vec3 vertA = thisA.pos + pos;
  vec3 vertB = thisB.pos + pos;
  vec3 vertC = thisC.pos + pos;

  // position vertices in scene and write to out buffer
  vertexBufferOut[outOffset + myOffset * 3]          = Vertex(vertA, thisA.ahsl);
  vertexBufferOut[outOffset + myOffset * 3 + 1]      = Vertex(vertB, thisB.ahsl);
  vertexBufferOut[outOffset + myOffset * 3 + 2]      = Vertex(vertC, thisC.ahsl);

  normalBufferOut[outOffset + myOffset * 3]     = normA;
  normalBufferOut[outOffset + myOffset * 3 + 1] = normB;
  normalBufferOut[outOffset + myOffset * 3 + 2] = normC;

  flagsOut[outOffset + myOffset * 3]     = minfo.exFlags;
  flagsOut[outOffset + myOffset * 3 + 1] = minfo.exFlags;
  flagsOut[outOffset + myOffset * 3 + 2] = minfo.exFlags;

  if(toffset < 0)
  {
    texA = vec4(0);
    texB = vec4(0);
    texC = vec4(0);
  }

  uvBufferOut[outOffset + myOffset * 3]       = texA;
  uvBufferOut[outOffset + myOffset * 3 + 1]   = texB;
  uvBufferOut[outOffset + myOffset * 3 + 2]   = texC;
}
