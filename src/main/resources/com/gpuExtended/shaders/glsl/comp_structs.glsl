struct modelinfo {
    int offset;   // offset into vertex buffer
    int toffset;  // offset into texture buffer
    int size;     // length in faces
    int idx;      // write idx in target buffer
    int flags;    // buffer, hillskew, plane, radius, orientation
    int x;        // scene position x
    int y;        // scene position y
    int z;        // scene position z
    ivec4 exFlags; //
};

struct Vertex {
    vec3 pos;
    int ahsl;
};

struct PriorityData {
    int totalNum[12];        // number of faces with a given priority
    int totalDistance[12];   // sum of distances to faces of a given priority
    int min10;               // minimum distance to a face of priority 10
    int avg1;
    int avg2;
    int avg3;
};