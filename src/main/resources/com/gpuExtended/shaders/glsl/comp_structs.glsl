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

    // Info required for mapping priority [0..11] to [0..17]
    // NOTE: This is set to 0 by CPU and filled out later in a compute pass
    int min10;
    int avg1;
    int avg2;
    int avg3;
};

struct Vertex {
    vec3 pos;
    int ahsl;
};