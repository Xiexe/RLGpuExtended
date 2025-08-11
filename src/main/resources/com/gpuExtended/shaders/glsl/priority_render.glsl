// Calculate adjusted priority for a face with a given priority, distance, and
// model global min10 and face distance averages. This allows positioning faces
// with priorities 10/11 into the correct 'slots' resulting in 18 possible
// adjusted priorities
uint priority_map(uint p, int distance, int _min10, int avg1, int avg2, int avg3) {
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
uint count_prio_offset(uint priority) {
  // this shouldn't ever be outside of (0, 17) because it is the return value from priority_map
  priority = clamp(priority, 0u, 17u);
  uint total = 0;
  for (uint i = 0; i < priority; i++) {
    total += totalMappedNum[i];
  }
  return total;
}

void get_face(uint localId, modelinfo minfo, float cameraYaw, float cameraPitch, out int dis, out Vertex o1, out Vertex o2, out Vertex o3) {
  uint size = minfo.size;
  uint offset = minfo.offset;
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
  if (flags < 0) {
    thisA = vb[offset + ssboOffset * 3];
    thisB = vb[offset + ssboOffset * 3 + 1];
    thisC = vb[offset + ssboOffset * 3 + 2];
  } else {
    thisA = tempvb[offset + ssboOffset * 3];
    thisB = tempvb[offset + ssboOffset * 3 + 1];
    thisC = tempvb[offset + ssboOffset * 3 + 2];
  }

  if (localId < size) {
    int orientation = flags & 0x7ff;

    vec4 thisrvA = rotate_vertex(vec4(thisA.pos, 0), orientation);
    vec4 thisrvB = rotate_vertex(vec4(thisB.pos, 0), orientation);
    vec4 thisrvC = rotate_vertex(vec4(thisC.pos, 0), orientation);

    int thisDistance = face_distance(thisrvA.xyz, thisrvB.xyz, thisrvC.xyz, cameraYaw, cameraPitch);

    o1.pos = thisrvA.xyz;
    o1.ahsl = thisA.ahsl;

    o2.pos = thisrvB.xyz;
    o2.ahsl = thisB.ahsl;

    o3.pos = thisrvC.xyz;
    o3.ahsl = thisC.ahsl;

    dis = thisDistance;
  } else {
    o1.pos = vec3(0);
    o1.ahsl = 0;

    o2.pos = vec3(0);
    o2.ahsl = 0;

    o3.pos = vec3(0);
    o3.ahsl = 0;
    dis = 0;
  }
}

void add_face_prio_distance(uint localId, modelinfo minfo, Vertex thisrvA, Vertex thisrvB, Vertex thisrvC, int thisDistance, ivec4 pos) {
  if (localId < minfo.size) {
    uint thisPriority = uint((thisrvA.ahsl >> 16) & 0xff);// all vertices on the face have the same priority
    // if the face is not culled, it is calculated into priority distance averages
    if (face_visible(thisrvA.pos, thisrvB.pos, thisrvC.pos, pos)) {
      if (thisPriority == 1 || thisPriority == 2) {
        atomicAdd(totalNum12, 1);
        atomicAdd(totalDistance12, thisDistance);
      }
      else if (thisPriority == 3 || thisPriority == 4) {
        atomicAdd(totalNum34, 1);
        atomicAdd(totalDistance34, thisDistance);
      }
      else if (thisPriority == 6 || thisPriority == 8) {
        atomicAdd(totalNum68, 1);
        atomicAdd(totalDistance68, thisDistance);
      }
      //atomicAdd(totalNum[thisPriority], 1);
      //atomicAdd(totalDistance[thisPriority], thisDistance);

      // calculate minimum distance to any face of priority 10 for positioning the 11 faces later
      if (thisPriority == 10) {
        atomicMin(min10, thisDistance);
      }
    }
  }
}

uint map_face_priority(uint localId, modelinfo minfo, int thisDistance, Vertex thisrvA, out uint prio) {
  uint size = minfo.size;

  // Compute average distances for 0/2, 3/4, and 6/8

  if (localId < size) {
    uint thisPriority = uint((thisrvA.ahsl >> 16) & 0xff);// all vertices on the face have the same priority
    float avg1 = float(totalDistance12) / float(totalNum12);
    float avg2 = float(totalDistance34) / float(totalNum34);
    float avg3 = float(totalDistance68) / float(totalNum68);
    avg1 *= float(totalNum12 > 0);
    avg2 *= float(totalNum34 > 0);
    avg3 *= float(totalNum68 > 0);

    uint adjPrio = priority_map(thisPriority, thisDistance, min10, int(avg1), int(avg2), int(avg3));
    uint prioIdx = atomicAdd(totalMappedNum[adjPrio], 1);

    prio = adjPrio;
    return prioIdx;
  }

  prio = 0;
  return 0;
}

uint calculate_priority(int distance, uint localId) {
  distance = clamp(distance, -32768, 32767);
  return uint((distance + 32768)) << 16 | (~localId & 0xffffu);
}

void insert_face(uint localId, modelinfo minfo, uint adjPrio, int distance, uint prioIdx) {
  uint size = minfo.size;

  if (localId < size) {
    // calculate base offset into renderPris based on number of faces with a lower priority
    uint baseOff = count_prio_offset(adjPrio);
    // the furthest faces draw first, and have the highest value
    // if two faces have the same distance, the one with the
    // lower id draws first
    renderPris[baseOff + prioIdx] = calculate_priority(distance, localId);
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
  unrotatedNormal = normalize(unrotatedNormal);
  // A precomputed lookup table for: pow(saturation / 7., 0.05)
  const float[8] POW_LOOKUP = float[8](
  0.0f, 0.908f, 0.938f, 0.958f, 0.972f, 0.983f, 0.992f, 1.0f
  );

  const vec3 LIGHT_DIR_MODEL = vec3(0.57735026, 0.57735026, 0.57735026);
  const int IGNORE_LOW_LIGHTNESS = 3;
  const float LIGHTNESS_MULTIPLIER = 4.f;
  const int BASE_LIGHTEN = 10;

  int saturation = (hsl >> 7) & 0x7;
  int lightness = hsl & 0x7F;

  // Calculate the dot product. Assumes unrotatedNormal is normalized.
  float vanillaLightDotNormals = dot(LIGHT_DIR_MODEL, unrotatedNormal);

  // Branchless version of the "if(vanillaLightDotNormals > 0)" check
  float positiveNdl = max(0.0, vanillaLightDotNormals);

  // The entire calculation is now performed by every thread,
  // but it results in adding 0 if the surface is not lit.
  float lighten = max(0, lightness - IGNORE_LOW_LIGHTNESS);
  lightness += int((lighten * LIGHTNESS_MULTIPLIER + BASE_LIGHTEN - lightness) * positiveNdl);

  // Use the lookup table instead of the expensive pow() function
  int maxLightness = int(127.f - 72.f * POW_LOOKUP[saturation]);

  lightness = min(lightness, maxLightness);

  hsl = (hsl & ~0x7F) | lightness;
}

void calculate_output_offsets(uint localId, modelinfo minfo, uint thisPriority, int thisDistance,
                              Vertex thisrvA, Vertex thisrvB, Vertex thisrvC,
                              out uint myOffset) {
  uint size = minfo.size;

  if (localId < size) {
    uint outOffset = minfo.idx;
    int toffset = minfo.toffset;
    int flags = minfo.flags;
    uint offset = minfo.offset;

    // we only have to order faces against others of the same priority
    const uint priorityOffset = count_prio_offset(thisPriority);
    const uint numOfPriority = totalMappedNum[thisPriority];
    const uint start = priorityOffset;                // index of first face with this priority
    const uint end = priorityOffset + numOfPriority;  // index of last face with this priority
    uint renderPriority = calculate_priority(thisDistance, localId);
    myOffset = priorityOffset;
    int orientation = flags & 0x7ff;
    int plane = (flags >> BIT_ZHEIGHT) & 3;
    int hillskew = (flags >> BIT_HILLSKEW) & 1;

    // calculate position this face will be in
    for (uint i = start; i < end; ++i) {
      if (renderPriority < renderPris[i]) {
        ++myOffset;
      }
    }
  }
}

void output_vertices_and_flags(uint localId, modelinfo minfo, Vertex thisrvA, Vertex thisrvB, Vertex thisrvC) {
  uint size = minfo.size;
  vec4 pos = vec4(minfo.x, minfo.y, minfo.z, 0);
  if (localId < size) {
    int toffset = minfo.toffset;
    int flags = minfo.flags;
    int orientation = flags & 0x7ff;
    int plane = (flags >> BIT_ZHEIGHT) & 3;
    int hillskew = (flags >> BIT_HILLSKEW) & 1;
    vec4 vertA = vec4(thisrvA.pos, 0) + pos;
    vec4 vertB = vec4(thisrvB.pos, 0) + pos;
    vec4 vertC = vec4(thisrvC.pos, 0) + pos;


    vertA = hillskew_vertexf(vertA, hillskew, minfo.y, plane);
    vertB = hillskew_vertexf(vertB, hillskew, minfo.y, plane);
    vertC = hillskew_vertexf(vertC, hillskew, minfo.y, plane);

    uint myOffset = localId;
    uint outOffset = minfo.idx;
    vout[outOffset + myOffset * 3]     = Vertex(vertA.xyz, thisrvA.ahsl);
    vout[outOffset + myOffset * 3 + 1] = Vertex(vertB.xyz, thisrvB.ahsl);
    vout[outOffset + myOffset * 3 + 2] = Vertex(vertC.xyz, thisrvC.ahsl);

    flagsout[outOffset + myOffset * 3]     = minfo.exFlags;
    flagsout[outOffset + myOffset * 3 + 1] = minfo.exFlags;
    flagsout[outOffset + myOffset * 3 + 2] = minfo.exFlags;
  }
}

void gather_texture_attribute(uint localId, modelinfo minfo, uint vertexIndex, out vec4 tex) {
  uint size = minfo.size;

  if (localId < size) {
    int toffset = minfo.toffset;
    int flags = minfo.flags;
    int orientation = flags & 0x7ff;
    int plane = (flags >> BIT_ZHEIGHT) & 3;
    int hillskew = (flags >> BIT_HILLSKEW) & 1;
    vec4 pos = vec4(minfo.x, minfo.y, minfo.z, 0);

    tex = vec4(0);
    if (toffset >= 0)
    {
      if (flags >= 0) {
        tex = temptexb[toffset + localId * 3 + vertexIndex];
      } else {
        tex = texb[toffset + localId * 3 + vertexIndex];
      }

      // swizzle from (tex,x,y,z) to (x,y,z,tex) for rotate and hillskew
      tex = tex.yzwx;
      // rotate
      tex = rotate_vertex(tex, orientation);
      // position
      tex += pos;
      // hillskew
      tex = hillskew_vertexf(tex, hillskew, minfo.y, plane);
      tex = tex.wxyz; // back to (tex,x,y,z)
    }
  }
  else {
    tex = vec4(0);
  }
}

void output_uv(uint localId, modelinfo minfo, uint vertexIndex, vec4 tex) {
  uint size = minfo.size;
  vec4 pos = vec4(minfo.x, minfo.y, minfo.z, 0);
  if (localId < size) {
    uint myOffset = localId;
    uint outOffset = minfo.idx;

    uvout[outOffset + myOffset * 3 + vertexIndex] = tex;
  }
}

void gather_normal_attribute(uint localId, modelinfo minfo, uint vertexIndex, out vec4 norm) {
  uint size = minfo.size;

  if (localId < size) {
    uint offset = minfo.offset;
    int flags = minfo.flags;
    int orientation = flags & 0x7ff;
    int plane = (flags >> BIT_ZHEIGHT) & 3;
    int hillskew = (flags >> BIT_HILLSKEW) & 1;

    if (flags < 0)
    {
      norm = normal[offset + localId * 3 + vertexIndex];
    }
    else
    {
      norm = tempnormal[offset + localId * 3 + vertexIndex];
    }

    norm = rotate_vertex(norm, orientation);
    norm = hillskew_vertexf(norm, hillskew, minfo.y, plane);
  }
  else {
    norm = vec4(0);
  }
}

void output_normal(uint localId, modelinfo minfo, uint vertexIndex, vec4 norm) {
  uint size = minfo.size;
  vec4 pos = vec4(minfo.x, minfo.y, minfo.z, 0);
  if (localId < size) {
    uint myOffset = localId;
    uint outOffset = minfo.idx;

    normalout[outOffset + myOffset * 3 + vertexIndex] = norm;
  }
}