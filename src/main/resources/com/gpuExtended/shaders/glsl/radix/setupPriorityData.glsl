// line 1 is off limits
#include "VERSION_HEADER"
#include "WORK_GROUP_SIZE_X"
#include "WORK_GROUP_SIZE_Y"
#include "WORK_GROUP_SIZE_Z"

#include "shaders/glsl/comp_structs.glsl"

layout(std430, binding = 0) writeonly buffer modelbuffer_in {
    PriorityData priorityData[];
};

layout(local_size_x = WORK_GROUP_SIZE_X, local_size_y = WORK_GROUP_SIZE_Y, local_size_z = WORK_GROUP_SIZE_Z) in;
void main() {
    /*
      if (localId == 0) {
        min10 = 6000;
        for (int i = 0; i < 12; ++i) {
          totalNum[i] = 0;
          totalDistance[i] = 0;
        }
      }
    */
    uint modelIndex = gl_GlobalInvocationID.x; // 0 to model count
    uint loopIndex = gl_GlobalInvocationID.y; // 0 to 18
    if (modelIndex < priorityData.length()) {
        PriorityData myPriorityData;

        if (loopIndex == 0)
            myPriorityData.min10 = 6000;
        if (loopIndex < 12)
            myPriorityData.totalNum[loopIndex] = 0;
        if (loopIndex < 12)
            myPriorityData.totalDistance[loopIndex] = 0;
        priorityData[modelIndex] = myPriorityData;
    }
}