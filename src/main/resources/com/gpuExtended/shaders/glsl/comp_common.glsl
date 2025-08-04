layout(std140, binding = CAMERA_BUFFER_BINDING_ID) uniform CameraBlock {
  mat4 cameraProjectionMatrix;        // 64 bytes
  vec4 cameraPosition;                // 16 bytes
  vec4 cameraFocalPoint;              // 16 bytes
  float cameraPitch;                  // 4 bytes
  float cameraYaw;                    // 4 bytes
  int zoom;                           // 4 bytes
  int centerX;                        // 4 bytes
  int centerY;                        // 4 bytes
};                                    // 128 bytes

layout(std430, binding = MODEL_BUFFER_IN_BINDING_ID) readonly buffer modelbuffer_in {
  modelinfo modelInfos[];
};

// position data
layout(std430, binding = VERTEX_BUFFER_OUT_BINDING_ID) writeonly buffer vertex_out {
  Vertex vertexBufferOut[];
};

layout(std430, binding = VERTEX_BUFFER_IN_BINDING_ID) readonly buffer vertexbuffer_in {
  Vertex staticVertexBufferIn[];
};

layout(std430, binding = TEMP_VERTEX_BUFFER_IN_BINDING_ID) readonly buffer tempvertexbuffer_in {
  Vertex dynamicVertexBufferIn[];
};

// uv data
layout(std430, binding = TEXTURE_BUFFER_OUT_BINDING_ID) writeonly buffer uv_out {
  vec4 uvBufferOut[];
};

layout(std430, binding = TEXTURE_BUFFER_IN_BINDING_ID) readonly buffer texturebuffer_in {
  vec4 statixUvBufferIn[];
};

layout(std430, binding = TEMP_TEXTURE_BUFFER_IN_BINDING_ID) readonly buffer temptexturebuffer_in {
  vec4 dynamicUvBufferIn[];
};

// normal data
layout(std430, binding = NORMAL_BUFFER_OUT_BINDING_ID) writeonly buffer normal_out {
  vec4 normalBufferOut[];
};

layout(std430, binding = NORMAL_BUFFER_IN_BINDING_ID) readonly buffer normalbuffer_in {
  vec4 staticNormalBufferIn[];
};

layout(std430, binding = TEMP_NORMAL_BUFFER_IN_BINDING_ID) readonly buffer tempnormalbuffer_in {
  vec4 dynamicNormalBufferIn[];
};

// flags data
layout(std430, binding = FLAGS_BUFFER_OUT_BINDING_ID) writeonly buffer flags_out {
  ivec4 flagsOut[];
};

layout(std430, binding = FLAGS_BUFFER_IN_BINDING_ID) readonly buffer flagsbuffer_in {
  ivec4 staticFlagsIn[];
};

layout(std430, binding = TEMP_FLAGS_BUFFER_IN_BINDING_ID) readonly buffer tempflagsbuffer_in {
  ivec4 tempFlagsIn[];
};