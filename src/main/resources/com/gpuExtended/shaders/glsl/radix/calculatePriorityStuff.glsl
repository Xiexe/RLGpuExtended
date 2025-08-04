// line 1 is off limits
#include "VERSION_HEADER"
#include "WORK_GROUP_SIZE_X"
#include "WORK_GROUP_SIZE_Y"
#include "WORK_GROUP_SIZE_Z"

#include "shaders/glsl/comp_structs.glsl"

layout(std430, binding = 0) readonly buffer modelbuffer_in {
    modelinfo modelInfos[];
};

layout(std430, binding = 1) writeonly buffer modelbuffer_in {
    PriorityData priorityData[];
};

#define OUT_OF_BOUNDS 0xFFFFFFFF
// Returns 0xFFFFFFFF for globalThreadIndex which are out of bounds
uint binary_search_for_model_index(uint globalThreadIndex) {
    uint low = 0;
    uint high = modelInfos.length() - 1;
    while (low <= high) {
        uint mid = low + (high - low) / 2;

        modelinfo info = modelInfos[mid];
        uint triangleStartIndex = info.idx;
        uint triangleEndIndex = info.idx + info.size;

        if (globalThreadIndex >= triangleStartIndex && globalThreadIndex < triangleEndIndex) {
            return mid;
        }
        else if (globalThreadIndex >= triangleEndIndex) {
            low = mid + 1;
        }
        else {
            high = mid - 1;
        }
    }
    return OUT_OF_BOUNDS;
}

layout(local_size_x = WORK_GROUP_SIZE_X, local_size_y = WORK_GROUP_SIZE_Y, local_size_z = WORK_GROUP_SIZE_Z) in;
void main() {
    uint modelIndex = binary_search_for_model_index(gl_GlobalInvocationID.x);
    if (modelIndex < modelInfos.length()) {
        modelinfo myModelInfo = modelInfos[modelIndex];
        uint localFaceIndex = gl_GlobalInvocationID.x - myModelInfo.idx;
        ivec4 modelPosition = ivec4(myModelInfo.x, myModelInfo.y, myModelInfo.z, 0);

        // get_face

        int thisDistance = face_distance(vertexA.pos, vertexB.pos, vertexC.pos, cameraYaw, cameraPitch);

        if (face_visible(vertexA.pos, vertexB.pos, vertexC.pos, modelPosition)) {
            atomicAdd(priorityData[modelIndex].totalNum[thisPriority], 1);
            atomicAdd(priorityData[modelIndex].totalDistance[thisPriority], thisDistance);
            if (thisPriority == 10) {
                atomicMin(priorityData[modelIndex].min10, thisDistance);
            }
        }
        // TODO: calc prioriwtyData
    //    }
    //
    //    // TODO: Should we hillskew and stuff here?
    //
    //    // TODO: can check if the entire work group is sharing the same model, use shared memory to atomic add, then atomic add result. Probably doesn't matter much
        //    // TODO: Technically e only care about priority [1],[2], [3],[4], [6],[8] and min10. so we don't need space for all 12 priorities

    /*
// pos =
minfo = modelInfos[groupId];
ivec4 pos = ivec4(minfo.x, minfo.y, minfo.z, 0);
    if (face_visible(thisrvA.pos, thisrvB.pos, thisrvC.pos, pos)) {
      atomicAdd(totalNum[thisPriority], 1);
      atomicAdd(totalDistance[thisPriority], thisDistance);

      // calculate minimum distance to any face of priority 10 for positioning the 11 faces later
      if (thisPriority == 10) {
        atomicMin(min10, thisDistance);
      }
    }
    */

    /*if (totalNum[1] > 0 || totalNum[2] > 0) {
        avg1 = (totalDistance[1] + totalDistance[2]) / (totalNum[1] + totalNum[2]);
    }

    if (totalNum[3] > 0 || totalNum[4] > 0) {
        avg2 = (totalDistance[3] + totalDistance[4]) / (totalNum[3] + totalNum[4]);
    }

    if (totalNum[6] > 0 || totalNum[8] > 0) {
        avg3 = (totalDistance[6] + totalDistance[8]) / (totalNum[6] + totalNum[8]);
    }*/
}