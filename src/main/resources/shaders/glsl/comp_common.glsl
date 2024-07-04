

#define PI 3.1415926535897932384626433832795f
#define UNIT PI / 1024.0f

layout(std140) uniform uniforms {
  float cameraYaw;
  float cameraPitch;
  int centerX;
  int centerY;
  int zoom;
  float cameraX;
  float cameraY;
  float cameraZ;
  ivec2 sinCosTable[2048];
};

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

struct vert {
  vec3 pos;
  int ahsl;
};

layout(std430, binding = 0) readonly buffer modelbuffer_in {
  modelinfo ol[];
};

// position data
layout(std430, binding = 1) writeonly buffer vertex_out {
  vert vout[];
};

layout(std430, binding = 2) readonly buffer vertexbuffer_in {
  vert vb[];
};

layout(std430, binding = 3) readonly buffer tempvertexbuffer_in {
  vert tempvb[];
};

// uv data
layout(std430, binding = 4) writeonly buffer uv_out {
  vec4 uvout[];
};

layout(std430, binding = 5) readonly buffer texturebuffer_in {
  vec4 texb[];
};

layout(std430, binding = 6) readonly buffer temptexturebuffer_in {
  vec4 temptexb[];
};

// normal data
layout(std430, binding = 7) writeonly buffer normal_out {
  vec4 normalout[];
};

layout(std430, binding = 8) readonly buffer normalbuffer_in {
  vec4 normal[];
};

layout(std430, binding = 9) readonly buffer tempnormalbuffer_in {
  vec4 tempnormal[];
};