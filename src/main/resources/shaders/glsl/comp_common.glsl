struct modelinfo {
  int offset;   // offset into vertex buffer
  int toffset;  // offset into texture buffer
  int size;     // length in faces
  int idx;      // write idx in target buffer
  int flags;    // buffer, hillskew, plane, radius, orientation
  int x;        // scene position x
  int y;        // scene position y
  int z;        // scene position z
};

struct Vertex {
  vec3 pos;
  int ahsl;
};

layout(std140, binding = CAMERA_BUFFER_BINDING_ID) uniform cameraUniforms {
  vec3 cameraPosition;
  float cameraPitch;
  float cameraYaw;
  int zoom;
  int centerX;
  int centerY;
};

layout(std430, binding = MODEL_BUFFER_IN_BINDING_ID) readonly buffer modelbuffer_in {
  modelinfo ol[];
};

// position data
layout(std430, binding = VERTEX_BUFFER_OUT_BINDING_ID) writeonly buffer vertex_out {
  Vertex vout[];
};

layout(std430, binding = VERTEX_BUFFER_IN_BINDING_ID) readonly buffer vertexbuffer_in {
  Vertex vb[];
};

layout(std430, binding = TEMP_VERTEX_BUFFER_IN_BINDING_ID) readonly buffer tempvertexbuffer_in {
  Vertex tempvb[];
};

// uv data
layout(std430, binding = TEXTURE_BUFFER_OUT_BINDING_ID) writeonly buffer uv_out {
  vec4 uvout[];
};

layout(std430, binding = TEXTURE_BUFFER_IN_BINDING_ID) readonly buffer texturebuffer_in {
  vec4 texb[];
};

layout(std430, binding = TEMP_TEXTURE_BUFFER_IN_BINDING_ID) readonly buffer temptexturebuffer_in {
  vec4 temptexb[];
};

// normal data
layout(std430, binding = NORMAL_BUFFER_OUT_BINDING_ID) writeonly buffer normal_out {
  vec4 normalout[];
};

layout(std430, binding = NORMAL_BUFFER_IN_BINDING_ID) readonly buffer normalbuffer_in {
  vec4 normal[];
};

layout(std430, binding = TEMP_NORMAL_BUFFER_IN_BINDING_ID) readonly buffer tempnormalbuffer_in {
  vec4 tempnormal[];
};