

#include version_header

#include "/shaders/glsl/comp_common.glsl"

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
  ivec4 thisA, thisB, thisC;
  vec4 normA, normB, normC;

  // Grab triangle vertices and normals from the correct buffer
  if (flags < 0) {
    thisA = vb[offset + ssboOffset * 3];
    thisB = vb[offset + ssboOffset * 3 + 1];
    thisC = vb[offset + ssboOffset * 3 + 2];

    normA = normal[offset + ssboOffset * 3];
    normB = normal[offset + ssboOffset * 3 + 1];
    normC = normal[offset + ssboOffset * 3 + 2];
  } else {
    thisA = tempvb[offset + ssboOffset * 3];
    thisB = tempvb[offset + ssboOffset * 3 + 1];
    thisC = tempvb[offset + ssboOffset * 3 + 2];

    normA = tempnormal[offset + ssboOffset * 3];
    normB = tempnormal[offset + ssboOffset * 3 + 1];
    normC = tempnormal[offset + ssboOffset * 3 + 2];
  }

  uint myOffset = localId;
  ivec4 pos = ivec4(minfo.x, minfo.y, minfo.z, 0);
  ivec4 texPos = pos.wxyz;

  // position vertices in scene and write to out buffer
  vout[outOffset + myOffset * 3]          = pos + thisA;
  vout[outOffset + myOffset * 3 + 1]      = pos + thisB;
  vout[outOffset + myOffset * 3 + 2]      = pos + thisC;

  normalout[outOffset + myOffset * 3]     = normA;
  normalout[outOffset + myOffset * 3 + 1] = normB;
  normalout[outOffset + myOffset * 3 + 2] = normC;

  if (toffset < 0) {
    uvout[outOffset + myOffset * 3]       = vec4(0);
    uvout[outOffset + myOffset * 3 + 1]   = vec4(0);
    uvout[outOffset + myOffset * 3 + 2]   = vec4(0);
  } else if (flags >= 0) {
    uvout[outOffset + myOffset * 3]       = texPos + temptexb[toffset + localId * 3];
    uvout[outOffset + myOffset * 3 + 1]   = texPos + temptexb[toffset + localId * 3 + 1];
    uvout[outOffset + myOffset * 3 + 2]   = texPos + temptexb[toffset + localId * 3 + 2];
  } else {
    uvout[outOffset + myOffset * 3]       = texPos + texb[toffset + localId * 3];
    uvout[outOffset + myOffset * 3 + 1]   = texPos + texb[toffset + localId * 3 + 1];
    uvout[outOffset + myOffset * 3 + 2]   = texPos + texb[toffset + localId * 3 + 2];
  }
}
