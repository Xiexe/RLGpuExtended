

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

layout(std430, binding = 0) readonly buffer modelbuffer_in {
  modelinfo ol[];
};

layout(std430, binding = 1) readonly buffer vertexbuffer_in {
  ivec4 vb[];
};

layout(std430, binding = 2) readonly buffer tempvertexbuffer_in {
  ivec4 tempvb[];
};

layout(std430, binding = 3) writeonly buffer vertex_out {
  ivec4 vout[];
};

layout(std430, binding = 4) writeonly buffer uv_out {
  vec4 uvout[];
};

layout(std430, binding = 5) readonly buffer texturebuffer_in {
  vec4 texb[];
};

layout(std430, binding = 6) readonly buffer temptexturebuffer_in {
  vec4 temptexb[];
};