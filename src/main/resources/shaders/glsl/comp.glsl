#include "VERSION_HEADER"
#include "THREAD_COUNT"
#include "FACES_PER_THREAD"

shared int totalNum[12];       // number of faces with a given priority
shared int totalDistance[12];  // sum of distances to faces of a given priority

shared int totalMappedNum[18];  // number of faces with a given adjusted priority

shared int min10;                                         // minimum distance to a face of priority 10
shared int renderPris[THREAD_COUNT * FACES_PER_THREAD];  // packed distance and face id

#include "/shaders/glsl/constants.glsl"
#include "/shaders/glsl/comp_common.glsl"

layout(local_size_x = THREAD_COUNT) in;

#include "/shaders/glsl/common.glsl"
#include "/shaders/glsl/priority_render.glsl"

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

  for (int i = 0; i < FACES_PER_THREAD; i++) {
    sort_and_insert(localId + i, minfo, prioAdj[i], dis[i], vA[i], vB[i], vC[i]);
  }
}
