#include "VERSION_HEADER"
#include "THREAD_COUNT"
#include "FACES_PER_THREAD"

shared uint totalNum12;
shared int totalDistance12;
shared uint totalNum34;
shared int totalDistance34;
shared uint totalNum68;
shared int totalDistance68;
shared int min10;                                         // minimum distance to a face of priority 10
shared uint renderPris[THREAD_COUNT * FACES_PER_THREAD];  // packed distance and face

#if SHARED_MEMORY_SIZE < 36864 // renderPris + radixBitmasks = 36864
#define BITS_PER_PASS 3
#else
#define BITS_PER_PASS 4
#endif
// TODO: can reuse renderPris in the radix sort
#define RADIX_PASS_COUNT ((32 + BITS_PER_PASS - 1) / BITS_PER_PASS)
#define NUM_BUCKETS (1 << BITS_PER_PASS)
#define NUM_BITFIELDS ((THREAD_COUNT*FACES_PER_THREAD)/32)

shared uint radixDigitStartIndices[RADIX_PASS_COUNT][NUM_BUCKETS];
shared uint radixBitmasks[NUM_BUCKETS][NUM_BITFIELDS];

uint get_bitfield_index(uint n) {
  return n >> 5; // n/32
}

uint get_bitfield_bit(uint n) {
  uint bit = n & 31;
  return (1 << bit);
}


#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/comp_common.glsl"

layout(local_size_x = THREAD_COUNT) in;

#include "shaders/glsl/common.glsl"
#include "shaders/glsl/priority_render.glsl"

void shuffle_vertex(uint localId, inout Vertex v[FACES_PER_THREAD], in uint whoSendsMeVertices[FACES_PER_THREAD]) {
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToUint(v[i].pos.x);
  }
  barrier();
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    v[i].pos.x = uintBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  barrier();

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToUint(v[i].pos.y);
  }
  barrier();
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    v[i].pos.y = uintBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  barrier();

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToUint(v[i].pos.z);
  }
  barrier();
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    v[i].pos.z = uintBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  barrier();

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = uint(v[i].ahsl);
  }
  barrier();
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    v[i].ahsl = int(renderPris[whoSendsMeVertices[i]]);
  }
  barrier();
}

void shuffle_vec4(uint localId, inout vec4 v[FACES_PER_THREAD], in uint whoSendsMeVertices[FACES_PER_THREAD]) {
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToUint(v[i].x);
  }
  barrier();
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    v[i].x = uintBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  barrier();

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToUint(v[i].y);
  }
  barrier();
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    v[i].y = uintBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  barrier();

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToUint(v[i].z);
  }
  barrier();
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    v[i].z = uintBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  barrier();

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToUint(v[i].w);
  }
  barrier();
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    v[i].w = uintBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  barrier();
}

void main() {
  uint groupId = gl_WorkGroupID.x;
  uint localId = gl_LocalInvocationID.x * FACES_PER_THREAD;
  modelinfo minfo = ol[groupId];

  if (localId == 0) {
    min10 = 6000;
    totalNum12 = 0;
    totalDistance12 = 0;
    totalNum34 = 0;
    totalDistance34 = 0;
    totalNum68 = 0;
    totalDistance68 = 0;
  }

  barrier(); // For zeroing shared memory

  Vertex vA[FACES_PER_THREAD];
  Vertex vB[FACES_PER_THREAD];
  Vertex vC[FACES_PER_THREAD];

  {
    int dis[FACES_PER_THREAD];

    uint _totalNum12 = 0;
    uint _totalNum34 = 0;
    uint _totalNum68 = 0;
    int _totalDistance12 = 0;
    int _totalDistance34 = 0;
    int _totalDistance68 = 0;
    int _min10 = 6000;

    for (uint i = 0; i < FACES_PER_THREAD; ++i) {
      get_face(localId + i, minfo, cameraYaw, cameraPitch, dis[i], vA[i], vB[i], vC[i]);

      if (localId + i < minfo.size) {
        ivec4 pos = ivec4(minfo.x, minfo.y, minfo.z, 0);
        uint thisPrio = uint((vA[i].ahsl >> 16) & 0xff);
        if (face_visible(vA[i].pos, vB[i].pos, vC[i].pos, pos)) {
          // buckets 1/2, 3/4, 6/8
          if (thisPrio == 1 || thisPrio == 2) { ++_totalNum12; totalDistance12 += dis[i]; }
          else if (thisPrio == 3 || thisPrio == 4) { ++_totalNum34; _totalDistance34 += dis[i]; }
          else if (thisPrio == 6 || thisPrio == 8) { ++_totalNum68; _totalDistance68 += dis[i]; }
          if (thisPrio == 10) { _min10 = min(_min10, dis[i]); }
        }
      }
    }

    if (_totalNum12 > 0) { atomicAdd(totalNum12, _totalNum12); atomicAdd(totalDistance12, _totalDistance12); }
    if (_totalNum34 > 0) { atomicAdd(totalNum34, _totalNum34); atomicAdd(totalDistance34, _totalDistance34); }
    if (_totalNum68 > 0) { atomicAdd(totalNum68, _totalNum68); atomicAdd(totalDistance68, _totalDistance68); }
    if (_min10 != 6000) { atomicMin(min10, _min10); }

    barrier(); // Wait for atomics

    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      uint prioAdj;
      map_face_priority(localId + i, minfo, dis[i], vA[i], prioAdj);
      insert_face(localId + i, minfo, prioAdj, dis[i]);
    }

    barrier(); // Wait for writes to renderPris
  }

  // Radix sort all renderPris
  const uint MAX_BITFIELD = min(NUM_BITFIELDS, get_bitfield_index(minfo.size)+1);
  for (uint passNumber = 0; passNumber < RADIX_PASS_COUNT; passNumber++) {
    if (gl_LocalInvocationID.x < NUM_BUCKETS) {
      radixDigitStartIndices[passNumber][gl_LocalInvocationID.x] = 0;
    }
    barrier();
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      uint digit = (renderPris[localId + i] >> (passNumber * BITS_PER_PASS)) & (NUM_BUCKETS - 1);
      atomicAdd(radixDigitStartIndices[passNumber][digit], 1);
    }
    barrier();
    // Exclusive prefix sum of digit counts gives us the digit start index
    if (gl_LocalInvocationID.x == 0) {
      uint sum = 0;
      for (int i = 0; i < NUM_BUCKETS; i++) {
        uint temp = radixDigitStartIndices[passNumber][i];
        radixDigitStartIndices[passNumber][i] = sum;
        sum += temp;
      }
    }
  }
  barrier();

  for (uint passNumber = 0; passNumber < RADIX_PASS_COUNT; passNumber++) {
    #define ITERATIONS ((NUM_BITFIELDS*NUM_BUCKETS + THREAD_COUNT - 1) / THREAD_COUNT)
    for (int i = 0; i < ITERATIONS; i++) {
      uint baseIndex = gl_LocalInvocationID.x * ITERATIONS;
      uint index = baseIndex + i;
      uint bucketIndex = index & (NUM_BUCKETS-1); // index % NUM_BUCKETS
      uint bitfieldIndex = index >> uint(log2(NUM_BUCKETS)); // index / NUM_BUCKETS
      if (bitfieldIndex < MAX_BITFIELD) {
        radixBitmasks[bucketIndex][bitfieldIndex] = 0;
      }
    }

    // We read the values now so we can do a read->barrier->write later, which gets rid of the need for a second temporary array
    uint value[FACES_PER_THREAD];
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      if ((localId + i) < minfo.size) {
        value[i] = renderPris[localId + i];
      }
    }

    barrier();

    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      if ((localId + i) < minfo.size) {
        uint digit = (value[i] >> (passNumber * BITS_PER_PASS)) & (NUM_BUCKETS - 1);
        uint bitfieldIndex = get_bitfield_index(localId + i);
        uint bit = get_bitfield_bit(localId + i);
        atomicOr(radixBitmasks[digit][bitfieldIndex], bit);
      }
    }

    barrier();

    // Read the masked bit counts for the last bitfield because we'll need it later and we're about to overwrite the bitfields with a prefix sum of bitcounts
    uint maskedBitcounts[FACES_PER_THREAD];
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      uint digit = (value[i] >> (passNumber * BITS_PER_PASS)) & (NUM_BUCKETS - 1);
      uint endBitfield = get_bitfield_index(localId + i);
      // Only count digits to the left of this one by masking out bits to the left
      uint bit = get_bitfield_bit(localId + i);
      uint mask = bit == 0 ? 0 : bit - 1;
      maskedBitcounts[i] = bitCount(radixBitmasks[digit][endBitfield] & mask);
    }

    barrier();

    // Exclusive prefix sum of bitcounts for the bitfields for each digit gives us the number of same digits that appear before a given digit
    if (gl_LocalInvocationID.x < NUM_BUCKETS) {
      uint bucketIndex = gl_LocalInvocationID.x;
      uint sum = 0;
      for (uint bitfieldIndex = 0; bitfieldIndex < MAX_BITFIELD; bitfieldIndex++) {
        uint temp = bitCount(radixBitmasks[bucketIndex][bitfieldIndex]);
        radixBitmasks[bucketIndex][bitfieldIndex] = sum;
        sum += temp;
      }
    }

    barrier();

    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      if ((localId + i) < minfo.size) {
        uint digit = (value[i] >> (passNumber * BITS_PER_PASS)) & (NUM_BUCKETS - 1);
        uint endBitfield = get_bitfield_index(localId + i);
        // digitRelativeIndex is the index of the digit relative to other values with the same digit
        // For example with [1,2,2,2,3], there are three 2s, and if we convert the digits to letters we get
        // [A, B, B, B, C]
        // Now to differentiate the same letters, we give them a number in the order they appear
        // [A0, B0, B1, B2, C0]
        // digitRelativeIndex is the number given to each letter in the example
        // So for the third 2 digit to appear in the array, digitRelativeIndex = 2
        // It's obtained by doing a prefix sum on a bitmask for each digit
        // the bitmask for digit 2 in the example is [0,1,1,1,0]
        uint digitRelativeIndex = radixBitmasks[digit][endBitfield] + maskedBitcounts[i];
        uint digitStartIndex = radixDigitStartIndices[passNumber][digit];
        uint outputIndex = digitStartIndex + digitRelativeIndex;
        renderPris[outputIndex] = value[i];
      }
    }

    barrier();
  }

  // Grab who to listen to for vertex shuffle by grabbing the localId from the sorted renderPris at this position
  uint whoSendsMeVertices[FACES_PER_THREAD];
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    uint output_index = localId + i;
    if (output_index < minfo.size) {
      uint sorted_key = renderPris[output_index];
      whoSendsMeVertices[i] = (~sorted_key) & LOCALID_MASK;
    } else {
      whoSendsMeVertices[i] = output_index;
    }
  }

  barrier();

  // Shuffle vertices from threads who've already read them to threads who share the same output index
  // This way the thread who read verts[i] writes to outverts[i] making the writes coalesced
  shuffle_vertex(localId, vA, whoSendsMeVertices);
  shuffle_vertex(localId, vB, whoSendsMeVertices);
  shuffle_vertex(localId, vC, whoSendsMeVertices);

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    output_vertices_and_flags(localId + i, minfo, vA[i], vB[i], vC[i]);
  }

  {
    // Same for textures/normals, but we do it one component at a time to reduce register usage
    // Unable to do the same for vertices because we need to read the 3 vertices at the start to calculate distance.
    vec4 tex[FACES_PER_THREAD];
    // a
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      gather_texture_attribute(localId + i, minfo, 0, tex[i]);
    }
    shuffle_vec4(localId, tex, whoSendsMeVertices);
    //b
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      output_uv(localId + i, minfo, 0, tex[i]);
      gather_texture_attribute(localId + i, minfo, 1, tex[i]);
    }
    shuffle_vec4(localId, tex, whoSendsMeVertices);
    //c
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      output_uv(localId + i, minfo, 1, tex[i]);
      gather_texture_attribute(localId + i, minfo, 2, tex[i]);
    }
    shuffle_vec4(localId, tex, whoSendsMeVertices);
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      output_uv(localId + i, minfo, 2, tex[i]);
    }
  }

  {
    vec4 normal[FACES_PER_THREAD];
    //a
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      gather_normal_attribute(localId + i, minfo, 0, normal[i]);
    }
    shuffle_vec4(localId, normal, whoSendsMeVertices);

    //b
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      output_normal(localId + i, minfo, 0, normal[i]);
      gather_normal_attribute(localId + i, minfo, 1, normal[i]);
    }
    shuffle_vec4(localId, normal, whoSendsMeVertices);

    //c
    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      output_normal(localId + i, minfo, 1, normal[i]);
      gather_normal_attribute(localId + i, minfo, 2, normal[i]);
    }
    shuffle_vec4(localId, normal, whoSendsMeVertices);

    for (uint i = 0; i < FACES_PER_THREAD; i++) {
      output_normal(localId + i, minfo, 2, normal[i]);
    }
  }
}
