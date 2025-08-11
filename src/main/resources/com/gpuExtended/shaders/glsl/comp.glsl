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

shared uint totalMappedNum[18];  // number of faces with a given adjusted priority
shared uint renderPris[THREAD_COUNT * FACES_PER_THREAD];  // packed distance and face id

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
  ivec4 pos = ivec4(minfo.x, minfo.y, minfo.z, 0);

  if (localId == 0) {
    min10 = 6000;
    totalNum12 = 0;
    totalDistance12 = 0;
    totalNum34 = 0;
    totalDistance34 = 0;
    totalNum68 = 0;
    totalDistance68 = 0;
    for (uint i = 0; i < 18; ++i) {
      totalMappedNum[i] = 0;
    }
  }

  int dis[FACES_PER_THREAD];
  Vertex vA[FACES_PER_THREAD];
  Vertex vB[FACES_PER_THREAD];
  Vertex vC[FACES_PER_THREAD];

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    get_face(localId + i, minfo, cameraYaw, cameraPitch, dis[i], vA[i], vB[i], vC[i]);
  }

  barrier();

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    add_face_prio_distance(localId + i, minfo, vA[i], vB[i], vC[i], dis[i], pos);
  }

  barrier();

  uint prioAdj[FACES_PER_THREAD];
  uint idx[FACES_PER_THREAD];
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    idx[i] = map_face_priority(localId + i, minfo, dis[i], vA[i], prioAdj[i]);
  }

  barrier();

  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    insert_face(localId + i, minfo, prioAdj[i], dis[i], idx[i]);
  }

  barrier();

  uint outputOffsets[FACES_PER_THREAD];
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    calculate_output_offsets(localId + i, minfo, prioAdj[i], dis[i],
                             vA[i], vB[i], vC[i],
                             outputOffsets[i]);
  }

  /*
outputOffsets = o // outArray[o]
renderPris[o] = i
whoSendsMeVerts = renderPris[i]

If i sort i get
o = renderPris[i] & localIdMask
renderPris[o] = i
whoSendsMeVerts = renderPris[i]

*/

  barrier();

  // Scatter localIds from renderPris to the thread they're relevant to
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    uint size = minfo.size;

    if ((localId+i) < size) {
      renderPris[outputOffsets[i]] = localId+i;
    }
  }

  barrier();
  // Now each thread knows which localId to look up in shared memory to get info about a vertex
  // For example if thread0 has renderPri[0] == 5, this means that thread 5 tells thread 0 what vertex to write out. IE. whoSendsMeVertices[0] = 5
  uint whoSendsMeVertices[FACES_PER_THREAD];
  for (uint i = 0; i < FACES_PER_THREAD; i++) {
    uint size = minfo.size;

    if ((localId+i) < size) {
      whoSendsMeVertices[i] = renderPris[localId+i];
    } else {
      // For out of bounds vertices, we make them look at their own index to remove the if check for size each time
      // The index into shared memory is in bounds, it's just not going to have any valid data
      whoSendsMeVertices[i] = localId + i;
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
