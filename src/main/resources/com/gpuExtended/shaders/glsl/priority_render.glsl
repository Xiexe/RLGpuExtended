

// Calculate adjusted priority for a face with a given priority, distance, and
// model global min10 and face distance averages. This allows positioning faces
// with priorities 10/11 into the correct 'slots' resulting in 18 possible
// adjusted priorities
int priority_map(int p, int distance, int _min10, int avg1, int avg2, int avg3) {
  // (10, 11)  0  1  2  (10, 11)  3  4  (10, 11)  5  6  7  8  9  (10, 11)
  //   0   1   2  3  4    5   6   7  8    9  10  11 12 13 14 15   16  17
  switch (p) {
    case 0:
    return 2;
    case 1:
    return 3;
    case 2:
    return 4;
    case 3:
    return 7;
    case 4:
    return 8;
    case 5:
    return 11;
    case 6:
    return 12;
    case 7:
    return 13;
    case 8:
    return 14;
    case 9:
    return 15;
    case 10:
    if (distance > avg1) {
      return 0;
    } else if (distance > avg2) {
      return 5;
    } else if (distance > avg3) {
      return 9;
    } else {
      return 16;
    }
    case 11:
    if (distance > avg1 && _min10 > avg1) {
      return 1;
    } else if (distance > avg2 && (_min10 > avg1 || _min10 > avg2)) {
      return 6;
    } else if (distance > avg3 && (_min10 > avg1 || _min10 > avg2 || _min10 > avg3)) {
      return 10;
    } else {
      return 17;
    }
    default:
    // this can't happen unless an invalid priority is sent. just assume 0.
    return 0;
  }
}

// calculate the number of faces with a lower adjusted priority than
// the given adjusted priority
int count_prio_offset(int priority) {
  // this shouldn't ever be outside of (0, 17) because it is the return value from priority_map
  priority = clamp(priority, 0, 17);
  int total = 0;
  for (int i = 0; i < priority; i++) {
    total += totalMappedNum[i];
  }
  return total;
}

void get_face(uint localId, modelinfo minfo, float cameraYaw, float cameraPitch, out int prio, out int dis, out Vertex o1, out Vertex o2, out Vertex o3) {
  int size = minfo.size;
  int offset = minfo.offset;
  int flags = minfo.flags;
  uint ssboOffset;

  if (localId < size) {
    ssboOffset = localId;
  } else {
    ssboOffset = 0;
  }

  Vertex thisA;
  Vertex thisB;
  Vertex thisC;

  // Grab triangle vertices from the correct buffer
  bool isStatic = flags < 0;
  if (isStatic) {
    thisA = staticVertexBufferIn[offset + ssboOffset * 3];
    thisB = staticVertexBufferIn[offset + ssboOffset * 3 + 1];
    thisC = staticVertexBufferIn[offset + ssboOffset * 3 + 2];
  } else {
    thisA = dynamicVertexBufferIn[offset + ssboOffset * 3];
    thisB = dynamicVertexBufferIn[offset + ssboOffset * 3 + 1];
    thisC = dynamicVertexBufferIn[offset + ssboOffset * 3 + 2];
  }

  if (localId < size) {
    int orientation = flags & 0x7ff;

    vec4 thisrvA = rotate_vertex(vec4(thisA.pos, 0), orientation);
    vec4 thisrvB = rotate_vertex(vec4(thisB.pos, 0), orientation);
    vec4 thisrvC = rotate_vertex(vec4(thisC.pos, 0), orientation);

    int thisPriority = (thisA.ahsl >> 16) & 0xff;// all vertices on the face have the same priority
    int thisDistance = face_distance(thisrvA.xyz, thisrvB.xyz, thisrvC.xyz, cameraYaw, cameraPitch);

    o1.pos = thisrvA.xyz;
    o1.ahsl = thisA.ahsl;

    o2.pos = thisrvB.xyz;
    o2.ahsl = thisB.ahsl;

    o3.pos = thisrvC.xyz;
    o3.ahsl = thisC.ahsl;

    prio = thisPriority;
    dis = thisDistance;
  } else {
    o1.pos = vec3(0);
    o1.ahsl = 0;

    o2.pos = vec3(0);
    o2.ahsl = 0;

    o3.pos = vec3(0);
    o3.ahsl = 0;
    prio = 0;
    dis = 0;
  }
}

void add_face_prio_distance(uint localId, modelinfo minfo, Vertex thisrvA, Vertex thisrvB, Vertex thisrvC, int thisPriority, int thisDistance, ivec4 pos) {
  if (localId < minfo.size) {
    // if the face is not culled, it is calculated into priority distance averages
    if (face_visible(thisrvA.pos, thisrvB.pos, thisrvC.pos, pos)) {
      atomicAdd(totalNum[thisPriority], 1);
      atomicAdd(totalDistance[thisPriority], thisDistance);

      // calculate minimum distance to any face of priority 10 for positioning the 11 faces later
      if (thisPriority == 10) {
        atomicMin(min10, thisDistance);
      }
    }
  }
}

int map_face_priority(uint localId, modelinfo minfo, int thisPriority, int thisDistance, out int prio) {
  int size = minfo.size;

  // Compute average distances for 0/2, 3/4, and 6/8

  if (localId < size) {
    int avg1 = 0;
    int avg2 = 0;
    int avg3 = 0;

    if (totalNum[1] > 0 || totalNum[2] > 0) {
      avg1 = (totalDistance[1] + totalDistance[2]) / (totalNum[1] + totalNum[2]);
    }

    if (totalNum[3] > 0 || totalNum[4] > 0) {
      avg2 = (totalDistance[3] + totalDistance[4]) / (totalNum[3] + totalNum[4]);
    }

    if (totalNum[6] > 0 || totalNum[8] > 0) {
      avg3 = (totalDistance[6] + totalDistance[8]) / (totalNum[6] + totalNum[8]);
    }

    int adjPrio = priority_map(thisPriority, thisDistance, min10, avg1, avg2, avg3);
    int prioIdx = atomicAdd(totalMappedNum[adjPrio], 1);

    prio = adjPrio;
    return prioIdx;
  }

  prio = 0;
  return 0;
}

void insert_face(uint localId, modelinfo minfo, int adjPrio, int distance, int prioIdx) {
  int size = minfo.size;

  if (localId < size) {
    // calculate base offset into renderPris based on number of faces with a lower priority
    int baseOff = count_prio_offset(adjPrio);
    // the furthest faces draw first, and have the highest value
    // if two faces have the same distance, the one with the
    // lower id draws first
    renderPris[baseOff + prioIdx] = distance << 16 | int(~localId & 0xffffu);
  }
}

layout(binding = 2) uniform sampler2DArray tileHeightSampler;
vec4 hillskew_vertexf(vec4 v, int hillskew, int y, int plane) {
  #define ESCENE_OFFSET 40.0

  vec2 halfTexel = vec2(0.5 / EXTENDED_SCENE_SIZE, 0.5 / EXTENDED_SCENE_SIZE);
  vec2 normalizedXY = vec2(
  (v.x / 128.0 + ESCENE_OFFSET) / EXTENDED_SCENE_SIZE,
  (v.z / 128.0 + ESCENE_OFFSET) / EXTENDED_SCENE_SIZE
  );
  vec3 texCoord = vec3(normalizedXY + halfTexel, float(plane));
  float h = textureLod(tileHeightSampler, texCoord, 0.0).r;
  return vec4(v.x, v.y + (h - y)*hillskew, v.z, v.w);
}

void undoVanillaShading(inout int hsl, vec3 unrotatedNormal) {
  const vec3 LIGHT_DIR_MODEL = vec3(0.57735026, 0.57735026, 0.57735026);
  // subtracts the X lowest lightness levels from the formula.
  // helps keep darker colors appropriately dark
  const int IGNORE_LOW_LIGHTNESS = 3;
  // multiplier applied to vertex' lightness value.
  // results in greater lightening of lighter colors
  const float LIGHTNESS_MULTIPLIER = 3.f;
  // the minimum amount by which each color will be lightened
  const int BASE_LIGHTEN = 10;

  int saturation = hsl >> 7 & 0x7;
  int lightness = hsl & 0x7F;
  float vanillaLightDotNormals = dot(LIGHT_DIR_MODEL, unrotatedNormal);
  if (vanillaLightDotNormals > 0) {
    vanillaLightDotNormals /= length(unrotatedNormal);
    float lighten = max(0, lightness - IGNORE_LOW_LIGHTNESS);
    lightness += int((lighten * LIGHTNESS_MULTIPLIER + BASE_LIGHTEN - lightness) * (vanillaLightDotNormals));
  }

  int maxLightness;
  maxLightness = int(127 - 72 * pow(saturation / 7., .05));
  lightness = min(lightness, maxLightness);
  hsl &= ~0x7F;
  hsl |= lightness;
}

void sort_and_insert(uint localId, modelinfo minfo, int thisPriority, int thisDistance, Vertex thisrvA, Vertex thisrvB, Vertex thisrvC) {
  int size = minfo.size;

  if (localId < size) {
    int outOffset = minfo.idx;
    int toffset = minfo.toffset;
    int flags = minfo.flags;
    int offset = minfo.offset;

    // we only have to order faces against others of the same priority
    const int priorityOffset = count_prio_offset(thisPriority);
    const int numOfPriority = totalMappedNum[thisPriority];
    const int start = priorityOffset;                // index of first face with this priority
    const int end = priorityOffset + numOfPriority;  // index of last face with this priority
    const int renderPriority = thisDistance << 16 | int(~localId & 0xffffu);
    int myOffset = priorityOffset;
    int orientation = flags & 0x7ff;
    int plane = (flags >> BIT_ZHEIGHT) & 3;
    int hillskew = (flags >> BIT_HILLSKEW) & 1;

    // calculate position this face will be in
    for (int i = start; i < end; ++i) {
      if (renderPriority < renderPris[i]) {
        ++myOffset;
      }
    }

    vec4 pos = vec4(minfo.x, minfo.y, minfo.z, 0);
    vec4 vertA = vec4(thisrvA.pos, 0) + pos;
    vec4 vertB = vec4(thisrvB.pos, 0) + pos;
    vec4 vertC = vec4(thisrvC.pos, 0) + pos;
    vec4 normA, normB, normC;

    bool isStatic = flags < 0;
    if (isStatic)
    {
        normA = staticNormalBufferIn[offset + localId * 3    ];
        normB = staticNormalBufferIn[offset + localId * 3 + 1];
        normC = staticNormalBufferIn[offset + localId * 3 + 2];
    }
    else
    {
        normA = dynamicNormalBufferIn[offset + localId * 3    ];
        normB = dynamicNormalBufferIn[offset + localId * 3 + 1];
        normC = dynamicNormalBufferIn[offset + localId * 3 + 2];
    }

    normA = rotate_vertex(normA, orientation);
    normB = rotate_vertex(normB, orientation);
    normC = rotate_vertex(normC, orientation);

    // apply hillskew
    vertA = hillskew_vertexf(vertA, hillskew, minfo.y, plane);
    vertB = hillskew_vertexf(vertB, hillskew, minfo.y, plane);
    vertC = hillskew_vertexf(vertC, hillskew, minfo.y, plane);

    normA = hillskew_vertexf(normA, hillskew, minfo.y, plane);
    normB = hillskew_vertexf(normB, hillskew, minfo.y, plane);
    normC = hillskew_vertexf(normC, hillskew, minfo.y, plane);

    normalBufferOut[outOffset + myOffset * 3]     = normA;
    normalBufferOut[outOffset + myOffset * 3 + 1] = normB;
    normalBufferOut[outOffset + myOffset * 3 + 2] = normC;

    flagsOut[outOffset + myOffset * 3]     = minfo.exFlags;
    flagsOut[outOffset + myOffset * 3 + 1] = minfo.exFlags;
    flagsOut[outOffset + myOffset * 3 + 2] = minfo.exFlags;

    // write to out buffer
    vertexBufferOut[outOffset + myOffset * 3]     = Vertex(vertA.xyz, thisrvA.ahsl);
    vertexBufferOut[outOffset + myOffset * 3 + 1] = Vertex(vertB.xyz, thisrvB.ahsl);
    vertexBufferOut[outOffset + myOffset * 3 + 2] = Vertex(vertC.xyz, thisrvC.ahsl);

    if (toffset < 0)
    {
      uvBufferOut[outOffset + myOffset * 3] = vec4(0);
      uvBufferOut[outOffset + myOffset * 3 + 1] = vec4(0);
      uvBufferOut[outOffset + myOffset * 3 + 2] = vec4(0);
    }
    else
    {
      vec4 texA, texB, texC;

      if (isStatic) {
        texA = statixUvBufferIn[toffset + localId * 3];
        texB = statixUvBufferIn[toffset + localId * 3 + 1];
        texC = statixUvBufferIn[toffset + localId * 3 + 2];
      } else {
        texA = dynamicUvBufferIn[toffset + localId * 3];
        texB = dynamicUvBufferIn[toffset + localId * 3 + 1];
        texC = dynamicUvBufferIn[toffset + localId * 3 + 2];
      }

      // swizzle from (tex,x,y,z) to (x,y,z,tex) for rotate and hillskew
      texA = texA.yzwx;
      texB = texB.yzwx;
      texC = texC.yzwx;
      // rotate
      texA = rotate_vertex(texA, orientation);
      texB = rotate_vertex(texB, orientation);
      texC = rotate_vertex(texC, orientation);
      // position
      texA += pos;
      texB += pos;
      texC += pos;
      // hillskew
      texA = hillskew_vertexf(texA, hillskew, minfo.y, plane);
      texB = hillskew_vertexf(texB, hillskew, minfo.y, plane);
      texC = hillskew_vertexf(texC, hillskew, minfo.y, plane);
      uvBufferOut[outOffset + myOffset * 3] = texA.wxyz;
      uvBufferOut[outOffset + myOffset * 3 + 1] = texB.wxyz;
      uvBufferOut[outOffset + myOffset * 3 + 2] = texC.wxyz;
    }
  }
}
