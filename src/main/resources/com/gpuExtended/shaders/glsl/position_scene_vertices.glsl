
#include "VERSION_HEADER"
#include "WORK_GROUP_SIZE_X"
#include "WORK_GROUP_SIZE_Y"
#include "WORK_GROUP_SIZE_Z"

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/comp_structs.glsl"
#include "shaders/glsl/comp_common.glsl"

layout(local_size_x = WORK_GROUP_SIZE_X, local_size_y = WORK_GROUP_SIZE_Y, local_size_z = WORK_GROUP_SIZE_Z) in;

#include "shaders/glsl/common.glsl"

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

void main() {
    uint modelIndex = binary_search_for_model_index(gl_GlobalInvocationID.x);
    if (modelIndex < modelInfos.length()) {
        modelinfo myModelInfo = modelInfos[modelIndex];
        ivec3 modelPosition = ivec3(myModelInfo.x, myModelInfo.y, myModelInfo.z);
        bool isStatic = myModelInfo.flags < 0;
        uint localFaceIndex = gl_GlobalInvocationID.x - myModelInfo.idx;
        uint outOffset = uint(myModelInfo.idx);

        Vertex vertA, vertB, vertC;
        vec4 normA, normB, normC;
        ivec4 flagsA, flagsB, flagsC;

        if (isStatic) {
            vertA = staticVertexBufferIn[myModelInfo.offset + localFaceIndex * 3];
            vertB = staticVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            vertC = staticVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 2];

            normA = staticNormalBufferIn[myModelInfo.offset + localFaceIndex * 3    ];
            normB = staticNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            normC = staticNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 2];

            flagsA = staticFlagsIn[myModelInfo.offset + localFaceIndex * 3    ];
            flagsB = staticFlagsIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            flagsC = staticFlagsIn[myModelInfo.offset + localFaceIndex * 3 + 2];
        } else {
            vertA = dynamicVertexBufferIn[myModelInfo.offset + localFaceIndex * 3];
            vertB = dynamicVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            vertC = dynamicVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 2];

            normA = dynamicNormalBufferIn[myModelInfo.offset + localFaceIndex * 3    ];
            normB = dynamicNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            normC = dynamicNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 2];

            flagsA = dynamicFlagsIn[myModelInfo.offset + localFaceIndex * 3    ];
            flagsB = dynamicFlagsIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            flagsC = dynamicFlagsIn[myModelInfo.offset + localFaceIndex * 3 + 2];
        }

        int orientation = myModelInfo.flags & 0x7ff;
        int plane = (myModelInfo.flags >> BIT_ZHEIGHT) & 3;
        int hillskew = (myModelInfo.flags >> BIT_HILLSKEW) & 1;

        vertA.pos = rotate_vertex(vec4(vertA.pos, 0), orientation);
        vertB.pos = rotate_vertex(vec4(vertB.pos, 0), orientation);
        vertC.pos = rotate_vertex(vec4(vertC.pos, 0), orientation);

        normA = rotate_vertex(normA, orientation);
        normB = rotate_vertex(normB, orientation);
        normC = rotate_vertex(normC, orientation);

        vertA.pos += modelPosition.xyz;
        vertB.pos += modelPosition.xyz;
        vertC.pos += modelPosition.xyz;

        vertA.pos = hillskew_vertexf(vertA, hillskew, minfo.y, plane).xyz;
        vertB.pos = hillskew_vertexf(vertB, hillskew, minfo.y, plane).xyz;
        vertC.pos = hillskew_vertexf(vertC, hillskew, minfo.y, plane).xyz;

        normA = hillskew_vertexf(normA, hillskew, minfo.y, plane);
        normB = hillskew_vertexf(normB, hillskew, minfo.y, plane);
        normC = hillskew_vertexf(normC, hillskew, minfo.y, plane);

        vertexBufferOut[outOffset + localFaceIndex * 3]     = vertA;
        vertexBufferOut[outOffset + localFaceIndex * 3 + 1] = vertB;
        vertexBufferOut[outOffset + localFaceIndex * 3 + 2] = vertC;

        normalBufferOut[outOffset + localFaceIndex * 3]     = normA;
        normalBufferOut[outOffset + localFaceIndex * 3 + 1] = normB;
        normalBufferOut[outOffset + localFaceIndex * 3 + 2] = normC;
    }
}