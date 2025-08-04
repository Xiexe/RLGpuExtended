// line 1 is off limits
#include "VERSION_HEADER"
#include "WORK_GROUP_SIZE_X"
#include "WORK_GROUP_SIZE_Y"
#include "WORK_GROUP_SIZE_Z"

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/comp_structs.glsl"

layout(std430, binding = PRIORITY_DATA_BUFFER_IN_BINDING_ID) buffer priority_buffer {
    PriorityData priorityData[];
};

layout(local_size_x = WORK_GROUP_SIZE_X, local_size_y = WORK_GROUP_SIZE_Y, local_size_z = WORK_GROUP_SIZE_Z) in;
void main() {
    uint modelIndex = gl_GlobalInvocationID.x;
    if (modelIndex < priorityData.length()) {
        PriorityData myPriorityData = priorityData[modelIndex];

        if (myPriorityData.totalNum[0] > 0 || myPriorityData.totalNum[1] > 0) {
            myPriorityData.avg1 = (myPriorityData.totalDistance[0] + myPriorityData.totalDistance[1]) / (myPriorityData.totalNum[0] + myPriorityData.totalNum[1]);
        }

        if (myPriorityData.totalNum[2] > 0 || myPriorityData.totalNum[3] > 0) {
            myPriorityData.avg2 = (myPriorityData.totalDistance[2] + myPriorityData.totalDistance[3]) / (myPriorityData.totalNum[2] + myPriorityData.totalNum[3]);
        }

        if (myPriorityData.totalNum[4] > 0 || myPriorityData.totalNum[5] > 0) {
            myPriorityData.avg3 = (myPriorityData.totalDistance[4] + myPriorityData.totalDistance[5]) / (myPriorityData.totalNum[4] + myPriorityData.totalNum[5]);
        }

        priorityData[modelIndex] = myPriorityData;
    }
}