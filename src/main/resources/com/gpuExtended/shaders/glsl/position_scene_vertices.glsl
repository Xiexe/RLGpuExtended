
#include "VERSION_HEADER"
#include "WORK_GROUP_SIZE_X"
#include "WORK_GROUP_SIZE_Y"
#include "WORK_GROUP_SIZE_Z"

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/comp_structs.glsl"
#include "shaders/glsl/comp_common.glsl"

#include "shaders/glsl/common.glsl"

layout(binding = 2) uniform sampler2DArray tileHeightSampler;
vec4 hillskew_vertexf(vec4 v, int hillskew, int y, int plane) {
    #define ESCENE_OFFSET 40.0

    vec2 halfTexel = vec2(0.5 / EXTENDED_SCENE_SIZE, 0.5 / EXTENDED_SCENE_SIZE);
    vec2 normalizedXY = vec2(
        (v.x / 128.0 + ESCENE_OFFSET) / EXTENDED_SCENE_SIZE,
        (v.z / 128.0 + ESCENE_OFFSET) / EXTENDED_SCENE_SIZE
    );
    vec3 texCoord = vec3(normalizedXY + halfTexel, float(plane));
    float h = textureLod(tileHeightSampler, texCoord, 0.0).r;
    return vec4(v.x, v.y + (h - y)*hillskew, v.z, v.w);
}

#define OUT_OF_BOUNDS 0xFFFFFFFF
// Returns 0xFFFFFFFF for globalThreadIndex which are out of bounds
uint binary_search_for_model_index(uint globalThreadIndex) {
    uint low = 0;
    uint high = modelInfos.length() - 1;
    while (low <= high) {
        uint mid = low + (high - low) / 2;

        modelinfo info = modelInfos[mid];
        uint triangleStartIndex = uint(info.idx)/3;
        uint triangleEndIndex = uint(info.idx)/3 + info.size;

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

layout(std430, binding = PRIORITY_DATA_BUFFER_IN_BINDING_ID) buffer prioity_buffer {
    PriorityData priorityData[];
};

struct KeyValue {
    uint keyHigh;
    uint keyLow;
    uint value;
};
layout(std430, binding = RADIX_KEY_VALUE_BUFFER_ID) buffer radix_key_value_buffer {
    KeyValue keyValues[];
};

#define MAX_MAPPED_PRIORITY 6
int map_priority_to_sums_index(int priority) {
    // The avg1/2/3 sums only care about priority [1],[2], [3],[4], and [6],[8]
    switch (priority) {
        case 1: return 0;
        case 2: return 1;
        case 3: return 2;
        case 4: return 3;
        case 6: return 4;
        case 8: return 5;
        default: return 0xFFFFFFFF;
    }
}

layout(local_size_x = WORK_GROUP_SIZE_X, local_size_y = WORK_GROUP_SIZE_Y, local_size_z = WORK_GROUP_SIZE_Z) in;
void main() {
    uint modelIndex = binary_search_for_model_index(gl_GlobalInvocationID.x);
    if (modelIndex < modelInfos.length()) {
        modelinfo myModelInfo = modelInfos[modelIndex];
        ivec3 modelPosition = ivec3(myModelInfo.x, myModelInfo.y, myModelInfo.z);
        bool isStatic = myModelInfo.flags < 0;
        uint localFaceIndex = gl_GlobalInvocationID.x - uint(myModelInfo.idx)/3;
        uint outOffset = uint(myModelInfo.idx);

        Vertex vertA, vertB, vertC;
        vec4 normA, normB, normC;
        vec4 texA, texB, texC;

        if (isStatic) {
            vertA = staticVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 0];
            vertB = staticVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            vertC = staticVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 2];

            normA = staticNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 0];
            normB = staticNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            normC = staticNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 2];

            texA = statixUvBufferIn[myModelInfo.toffset + localFaceIndex * 3 + 0];
            texB = statixUvBufferIn[myModelInfo.toffset + localFaceIndex * 3 + 1];
            texC = statixUvBufferIn[myModelInfo.toffset + localFaceIndex * 3 + 2];
        } else {
            vertA = dynamicVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 0];
            vertB = dynamicVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            vertC = dynamicVertexBufferIn[myModelInfo.offset + localFaceIndex * 3 + 2];

            normA = dynamicNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 0];
            normB = dynamicNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 1];
            normC = dynamicNormalBufferIn[myModelInfo.offset + localFaceIndex * 3 + 2];

            texA = dynamicUvBufferIn[myModelInfo.toffset + localFaceIndex * 3];
            texB = dynamicUvBufferIn[myModelInfo.toffset + localFaceIndex * 3 + 1];
            texC = dynamicUvBufferIn[myModelInfo.toffset + localFaceIndex * 3 + 2];
        }

        int orientation = myModelInfo.flags & 0x7ff;
        int plane = (myModelInfo.flags >> BIT_ZHEIGHT) & 3;
        int hillskew = (myModelInfo.flags >> BIT_HILLSKEW) & 1;
        int thisPriority = (vertA.ahsl >> 16) & 0xff; // all vertices on the face have the same priority

        vertA.pos = rotate_vertex(vec4(vertA.pos, 0), orientation).xyz;
        vertB.pos = rotate_vertex(vec4(vertB.pos, 0), orientation).xyz;
        vertC.pos = rotate_vertex(vec4(vertC.pos, 0), orientation).xyz;

        normA = rotate_vertex(normA, orientation);
        normB = rotate_vertex(normB, orientation);
        normC = rotate_vertex(normC, orientation);

        vertA.pos += modelPosition.xyz;
        vertB.pos += modelPosition.xyz;
        vertC.pos += modelPosition.xyz;

        vertA.pos = hillskew_vertexf(vec4(vertA.pos, 0), hillskew, myModelInfo.y, plane).xyz;
        vertB.pos = hillskew_vertexf(vec4(vertB.pos, 0), hillskew, myModelInfo.y, plane).xyz;
        vertC.pos = hillskew_vertexf(vec4(vertC.pos, 0), hillskew, myModelInfo.y, plane).xyz;

        normA = hillskew_vertexf(normA, hillskew, myModelInfo.y, plane);
        normB = hillskew_vertexf(normB, hillskew, myModelInfo.y, plane);
        normC = hillskew_vertexf(normC, hillskew, myModelInfo.y, plane);

        vertexBufferOut[outOffset + localFaceIndex * 3 + 0] = vertA;
        vertexBufferOut[outOffset + localFaceIndex * 3 + 1] = vertB;
        vertexBufferOut[outOffset + localFaceIndex * 3 + 2] = vertC;

        normalBufferOut[outOffset + localFaceIndex * 3 + 0] = normA;
        normalBufferOut[outOffset + localFaceIndex * 3 + 1] = normB;
        normalBufferOut[outOffset + localFaceIndex * 3 + 2] = normC;

        // swizzle from (tex,x,y,z) to (x,y,z,tex) for rotate and hillskew
        texA = texA.yzwx;
        texB = texB.yzwx;
        texC = texC.yzwx;
        // rotate
        texA = rotate_vertex(texA, orientation);
        texB = rotate_vertex(texB, orientation);
        texC = rotate_vertex(texC, orientation);
        // position
        texA += ivec4(modelPosition.xyz, 0);
        texB += ivec4(modelPosition.xyz, 0);
        texC += ivec4(modelPosition.xyz, 0);
        // hillskew
        texA = hillskew_vertexf(texA, hillskew, myModelInfo.y, plane);
        texB = hillskew_vertexf(texB, hillskew, myModelInfo.y, plane);
        texC = hillskew_vertexf(texC, hillskew, myModelInfo.y, plane);

        if (myModelInfo.toffset < 0) {
            uvBufferOut[outOffset + localFaceIndex * 3 + 0] = vec4(0);
            uvBufferOut[outOffset + localFaceIndex * 3 + 1] = vec4(0);
            uvBufferOut[outOffset + localFaceIndex * 3 + 2] = vec4(0);
        } else {
            uvBufferOut[outOffset + localFaceIndex * 3 + 0] = texA.wxyz;
            uvBufferOut[outOffset + localFaceIndex * 3 + 1] = texB.wxyz;
            uvBufferOut[outOffset + localFaceIndex * 3 + 2] = texC.wxyz;
        }

        flagsOut[outOffset + localFaceIndex * 3 + 0] = myModelInfo.exFlags;
        flagsOut[outOffset + localFaceIndex * 3 + 1] = myModelInfo.exFlags;
        flagsOut[outOffset + localFaceIndex * 3 + 2] = myModelInfo.exFlags;

        int thisDistance = face_distance(vertA.pos, vertB.pos, vertC.pos, cameraYaw, cameraPitch);

        bool isUnordered = bool(myModelInfo.exFlags.y & 1); // Currently this is also isTerrain
        if (!isUnordered && face_visible(vertA.pos, vertB.pos, vertC.pos, ivec4(modelPosition, 0))) {
            int mapped_priority = map_priority_to_sums_index(thisPriority);
            if (mapped_priority < MAX_MAPPED_PRIORITY) {
                atomicAdd(priorityData[modelIndex].totalNum[mapped_priority], 1);
                atomicAdd(priorityData[modelIndex].totalDistance[mapped_priority], thisDistance);
            }
            if (thisPriority == 10 && thisDistance != 6000) { // NOTE: Intentionally left unmapped as 10 priority, since this is min10
                atomicMin(priorityData[modelIndex].min10, thisDistance);
            }
        }

        KeyValue keyValue;


        #define MASK_BITS(n) ((1u << (n)) - 1u)
        #define MODEL_ID_BITS 20
        #define PRIORITY_BITS 5
        #define DISTANCE_BITS 16
        #define FACE_ID_BITS  13

        /*
        NSight structured view
        struct KeyValue {
            uint thisPriorityHigh:2;
            uint modelId: 20;
            hide uint pad:10;
            uint localFaceId:13;
            uint mappedDistance:16;
            uint thisPriorityLow:3;
            uint value;
        };
        */

        // We XOR with 0x8000u (which is 2^15) to flip the sign bit.
        // This maps [-32768, 32767] to a sortable [0, 65535] uint range.
        uint mappedDistance = (uint(thisDistance) & MASK_BITS(DISTANCE_BITS)) ^ 0x8000u;

        // 13 bits for faceId, then 16 bits for distnace, then 3 bits of priority
        keyValue.keyLow = (localFaceIndex & MASK_BITS(FACE_ID_BITS)) |
        ((mappedDistance & MASK_BITS(DISTANCE_BITS)) << FACE_ID_BITS) |
        ((thisPriority & MASK_BITS(3)) << (FACE_ID_BITS + DISTANCE_BITS));

        // 2 bits of priority, then 20 bits of modelId
        keyValue.keyHigh = ((thisPriority >> 3) & MASK_BITS(2)) |
        ((modelIndex & MASK_BITS(MODEL_ID_BITS)) << 2);
        keyValue.value = gl_GlobalInvocationID.x;

        keyValues[gl_GlobalInvocationID.x] = keyValue;
    }
}