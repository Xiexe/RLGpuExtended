#include "VERSION_HEADER"
#include "THREAD_COUNT"
#include "FACES_PER_THREAD"

shared int totalNum12;
shared int totalDistance12;
shared int totalNum34;
shared int totalDistance34;
shared int totalNum68;
shared int totalDistance68;
shared int min10;                                         // minimum distance to a face of priority 10

shared int totalMappedNum[18];  // number of faces with a given adjusted priority
shared int renderPris[THREAD_COUNT * FACES_PER_THREAD];  // packed distance and face id

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/comp_common.glsl"

layout(local_size_x = THREAD_COUNT) in;

#include "shaders/glsl/common.glsl"
#include "shaders/glsl/priority_render.glsl"

void shuffle_vertex(int localId, inout Vertex v[FACES_PER_THREAD], in int whoSendsMeVertices[FACES_PER_THREAD]) {
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToInt(v[i].pos.x);
  }
  memoryBarrierShared();
  barrier();
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    v[i].pos.x = intBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToInt(v[i].pos.y);
  }
  memoryBarrierShared();
  barrier();
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    v[i].pos.y = intBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToInt(v[i].pos.z);
  }
  memoryBarrierShared();
  barrier();
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    v[i].pos.z = intBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = v[i].ahsl;
  }
  memoryBarrierShared();
  barrier();
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    v[i].ahsl = renderPris[whoSendsMeVertices[i]];
  }
  memoryBarrierShared();
  barrier();
}

void shuffle_vec4(int localId, inout vec4 v[FACES_PER_THREAD], in int whoSendsMeVertices[FACES_PER_THREAD]) {
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToInt(v[i].x);
  }
  memoryBarrierShared();
  barrier();
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    v[i].x = intBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToInt(v[i].y);
  }
  memoryBarrierShared();
  barrier();
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    v[i].y = intBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToInt(v[i].z);
  }
  memoryBarrierShared();
  barrier();
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    v[i].z = intBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    renderPris[localId + i] = floatBitsToInt(v[i].w);
  }
  memoryBarrierShared();
  barrier();
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    v[i].w = intBitsToFloat(renderPris[whoSendsMeVertices[i]]);
  }
  memoryBarrierShared();
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
    for (int i = 0; i < 18; ++i) {
      totalMappedNum[i] = 0;
    }
  }

  int dis[FACES_PER_THREAD];
  Vertex vA[FACES_PER_THREAD];
  Vertex vB[FACES_PER_THREAD];
  Vertex vC[FACES_PER_THREAD];

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    get_face(localId + i, minfo, cameraYaw, cameraPitch, dis[i], vA[i], vB[i], vC[i]);
  }

  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    add_face_prio_distance(localId + i, minfo, vA[i], vB[i], vC[i], dis[i], pos);
  }

  memoryBarrierShared();
  barrier();

  int prioAdj[FACES_PER_THREAD];
  int idx[FACES_PER_THREAD];
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    idx[i] = map_face_priority(localId + i, minfo, dis[i], vA[i], prioAdj[i]);
  }

  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    insert_face(localId + i, minfo, prioAdj[i], dis[i], idx[i]);
  }

  memoryBarrierShared();
  barrier();

  int outputOffsets[FACES_PER_THREAD];
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    calculate_output_offsets(localId + i, minfo, prioAdj[i], dis[i],
                             vA[i], vB[i], vC[i],
                             outputOffsets[i]);
  }

  memoryBarrierShared();
  barrier();

  // Scatter localIds from renderPris to the thread they're relevant to
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    int size = minfo.size;

    if ((localId+i) < size) {
      renderPris[outputOffsets[i]] = int(localId+i);
    }
  }

  memoryBarrierShared();
  barrier();
  // Now each thread knows which localId to look up in shared memory to get info about a vertex
  // For example if thread0 has renderPri[0] == 5, this means that thread 5 tells thread 0 what vertex to write out. IE. whoSendsMeVertices[0] = 5
  int whoSendsMeVertices[FACES_PER_THREAD];
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    int size = minfo.size;

    if ((localId+i) < size) {
      whoSendsMeVertices[i] = renderPris[localId+i];
    } else {
      // For out of bounds vertices, we make them look at their own index to remove the if check for size each time
      // The index into shared memory is in bounds, it's just not going to have any valid data
      whoSendsMeVertices[i] = int(localId) + i;
    }
  }

  memoryBarrierShared();
  barrier();

  // Shuffle vertices from threads who've already read them to threads who share the same output index
  // This way the thread who read verts[i] writes to outverts[i] making the writes coalesced
  shuffle_vertex(int(localId), vA, whoSendsMeVertices);
  shuffle_vertex(int(localId), vB, whoSendsMeVertices);
  shuffle_vertex(int(localId), vC, whoSendsMeVertices);

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_vertices_and_flags(localId + i, minfo, vA[i], vB[i], vC[i]);
  }

  // Same for textures/normals, but we do it one component at a time to reduce register usage
  // Unable to do the same for vertices because we need to read the 3 vertices at the start to calculate distance.
  vec4 tex[FACES_PER_THREAD];
  // a
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    gather_texture_attribute(localId + i, minfo, 0, tex[i]);
  }
  shuffle_vec4(int(localId), tex, whoSendsMeVertices);
  //b
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_uv(localId + i, minfo, 0, tex[i]);
    gather_texture_attribute(localId + i, minfo, 1, tex[i]);
  }
  shuffle_vec4(int(localId), tex, whoSendsMeVertices);
  //c
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_uv(localId + i, minfo, 1, tex[i]);
    gather_texture_attribute(localId + i, minfo, 2, tex[i]);
  }
  shuffle_vec4(int(localId), tex, whoSendsMeVertices);
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_uv(localId + i, minfo, 2, tex[i]);
  }


  vec4 normal[FACES_PER_THREAD];
  //a
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    gather_normal_attribute(localId + i, minfo, 0, normal[i]);
  }
  shuffle_vec4(int(localId), normal, whoSendsMeVertices);

  //b
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_normal(localId + i, minfo, 0, normal[i]);
    gather_normal_attribute(localId + i, minfo, 1, normal[i]);
  }
  shuffle_vec4(int(localId), normal, whoSendsMeVertices);

  //c
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_normal(localId + i, minfo, 1, normal[i]);
    gather_normal_attribute(localId + i, minfo, 2, normal[i]);
  }
  shuffle_vec4(int(localId), normal, whoSendsMeVertices);

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_normal(localId + i, minfo, 2, normal[i]);
  }
}
