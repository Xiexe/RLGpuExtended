// line 1 is off limits
#include "VERSION_HEADER"
#include "WORK_GROUP_SIZE_X"
#include "WORK_GROUP_SIZE_Y"
#include "WORK_GROUP_SIZE_Z"

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/comp_structs.glsl"

layout(std430, binding = PRIORITY_DATA_BUFFER_IN_BINDING_ID) writeonly buffer prioity_buffer_out {
    PriorityData priorityData[];
};
layout(local_size_x = WORK_GROUP_SIZE_X, local_size_y = WORK_GROUP_SIZE_Y, local_size_z = WORK_GROUP_SIZE_Z) in;
void main() {
    uint modelIndex = gl_GlobalInvocationID.x; // 0 to model count
    if (modelIndex < priorityData.length()) {
        PriorityData myPriorityData;
        myPriorityData.min10 = 6000;
        for (int i = 0; i < 6; i++) {
            myPriorityData.totalNum[i] = 0;
            myPriorityData.totalDistance[i] = 0;
        }
        myPriorityData.avg1 = 0;
        myPriorityData.avg2 = 0;
        myPriorityData.avg3 = 0;
        priorityData[modelIndex] = myPriorityData;
    }
}