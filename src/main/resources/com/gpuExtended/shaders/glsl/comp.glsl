#include "VERSION_HEADER"
#include "THREAD_COUNT"
#include "FACES_PER_THREAD"

shared int totalNum[12];       // number of faces with a given priority
shared int totalDistance[12];  // sum of distances to faces of a given priority

shared int totalMappedNum[18];  // number of faces with a given adjusted priority

shared int min10;                                         // minimum distance to a face of priority 10
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
    for (int i = 0; i < 12; ++i) {
      totalNum[i] = 0;
      totalDistance[i] = 0;
    }
    for (int i = 0; i < 18; ++i) {
      totalMappedNum[i] = 0;
    }
  }

  // TODO: recalc distance/prio to reduce register usage?
  int prio[FACES_PER_THREAD];
  int dis[FACES_PER_THREAD];
  Vertex vA[FACES_PER_THREAD];
  Vertex vB[FACES_PER_THREAD];
  Vertex vC[FACES_PER_THREAD];

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    get_face(localId + i, minfo, cameraYaw, cameraPitch, prio[i], dis[i], vA[i], vB[i], vC[i]);
  }

  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    add_face_prio_distance(localId + i, minfo, vA[i], vB[i], vC[i], prio[i], dis[i], pos);
  }

  memoryBarrierShared();
  barrier();

  // TODO: recalc prioAdj/idx to reduce register usage?
  int prioAdj[FACES_PER_THREAD];
  int idx[FACES_PER_THREAD];
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    idx[i] = map_face_priority(localId + i, minfo, prio[i], dis[i], prioAdj[i]);
  }

  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    insert_face(localId + i, minfo, prioAdj[i], dis[i], idx[i]);
  }

  memoryBarrierShared();
  barrier();

  int outputOffsets[FACES_PER_THREAD];
  int thisRenderPriority[FACES_PER_THREAD];
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    calculate_output_offsets(localId + i, minfo, prioAdj[i], dis[i],
                             vA[i], vB[i], vC[i],
                             outputOffsets[i], thisRenderPriority[i]);

  }

  memoryBarrierShared();
  barrier();

  // Scatter localIds from renderPris to the thread they're relevant to
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    int size = minfo.size;

    if ((localId+i) < size) { // TODO: when moving this remove the + i
      //int localIdFromRenderPriority = (~(thisRenderPriority[i] & 0xffff)) & 0xffff;
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

    if ((localId+i) < size) { // TODO: when moving this remove the + i
      whoSendsMeVertices[i] = renderPris[localId+i]; // TODO: when moving this remove the + i
    } else {
      // For out of bounds vertices, we make them look at their own index to remove the if check for size each time
      // The index into shared memory is in bounds, it's just not going to have any valid data
      whoSendsMeVertices[i] = int(localId) + i; // TODO: when moving this remove the + i
    }
  }

  memoryBarrierShared();
  barrier();

  // TODO: gather and shuffle one vertex at a time to reduce register usage
  shuffle_vertex(int(localId), vA, whoSendsMeVertices);
  shuffle_vertex(int(localId), vB, whoSendsMeVertices);
  shuffle_vertex(int(localId), vC, whoSendsMeVertices);

  memoryBarrierShared();
  barrier();

  vec4 texA[FACES_PER_THREAD];
  vec4 texB[FACES_PER_THREAD];
  vec4 texC[FACES_PER_THREAD];
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_vertices_and_flags(localId + i, minfo, vA[i], vB[i], vC[i]);
    gather_texture_attribute(localId + i, minfo, texA[i], texB[i], texC[i]);
  }

  shuffle_vec4(int(localId), texA, whoSendsMeVertices);
  shuffle_vec4(int(localId), texB, whoSendsMeVertices);
  shuffle_vec4(int(localId), texC, whoSendsMeVertices);

  memoryBarrierShared();
  barrier();

  vec4 normalA[FACES_PER_THREAD];
  vec4 normalB[FACES_PER_THREAD];
  vec4 normalC[FACES_PER_THREAD];
  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_uvs(localId + i, minfo, texA[i], texB[i], texC[i]);
    gather_normal_attribute(localId + i, minfo, normalA[i], normalB[i], normalC[i]);
  }
  shuffle_vec4(int(localId), normalA, whoSendsMeVertices);
  shuffle_vec4(int(localId), normalB, whoSendsMeVertices);
  shuffle_vec4(int(localId), normalC, whoSendsMeVertices);
  memoryBarrierShared();
  barrier();

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    output_normals(localId + i, minfo, normalA[i], normalB[i], normalC[i]);
  }
}
