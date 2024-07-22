#include "VERSION_HEADER"

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/comp_common.glsl"

layout(local_size_x = 6) in;

void main() {
  uint groupId = gl_WorkGroupID.x;
  uint localId = gl_LocalInvocationID.x;
  modelinfo minfo = ol[groupId];

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

  uint myOffset = localId;
  vec3 pos = vec3(minfo.x, minfo.y, minfo.z);
  ivec4 texPos = ivec4(0, pos);

  // Grab triangle vertices and normals from the correct buffer
  if (flags < 0) {
    thisA = vb[offset + ssboOffset * 3];
    thisB = vb[offset + ssboOffset * 3 + 1];
    thisC = vb[offset + ssboOffset * 3 + 2];

    normA = normal[offset + ssboOffset * 3];
    normB = normal[offset + ssboOffset * 3 + 1];
    normC = normal[offset + ssboOffset * 3 + 2];

    texA = texPos + texb[toffset + localId * 3];
    texB = texPos + texb[toffset + localId * 3 + 1];
    texC = texPos + texb[toffset + localId * 3 + 2];
  } else {
    thisA = tempvb[offset + ssboOffset * 3];
    thisB = tempvb[offset + ssboOffset * 3 + 1];
    thisC = tempvb[offset + ssboOffset * 3 + 2];

    normA = tempnormal[offset + ssboOffset * 3];
    normB = tempnormal[offset + ssboOffset * 3 + 1];
    normC = tempnormal[offset + ssboOffset * 3 + 2];

    texA = texPos + temptexb[toffset + localId * 3];
    texB = texPos + temptexb[toffset + localId * 3 + 1];
    texC = texPos + temptexb[toffset + localId * 3 + 2];
  }

  vec3 vertA = thisA.pos + pos;
  vec3 vertB = thisB.pos + pos;
  vec3 vertC = thisC.pos + pos;

  // position vertices in scene and write to out buffer
  vout[outOffset + myOffset * 3]          = Vertex(vertA, thisA.ahsl);
  vout[outOffset + myOffset * 3 + 1]      = Vertex(vertB, thisB.ahsl);
  vout[outOffset + myOffset * 3 + 2]      = Vertex(vertC, thisC.ahsl);

  normalout[outOffset + myOffset * 3]     = normA;
  normalout[outOffset + myOffset * 3 + 1] = normB;
  normalout[outOffset + myOffset * 3 + 2] = normC;

  flagsout[outOffset + myOffset * 3]     = minfo.exFlags;
  flagsout[outOffset + myOffset * 3 + 1] = minfo.exFlags;
  flagsout[outOffset + myOffset * 3 + 2] = minfo.exFlags;

  if(toffset < 0)
  {
    texA = vec4(0);
    texB = vec4(0);
    texC = vec4(0);
  }

  uvout[outOffset + myOffset * 3]       = texA;
  uvout[outOffset + myOffset * 3 + 1]   = texB;
  uvout[outOffset + myOffset * 3 + 2]   = texC;
}
