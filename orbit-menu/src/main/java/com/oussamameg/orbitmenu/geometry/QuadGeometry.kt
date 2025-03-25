package com.oussamameg.orbitmenu.geometry

class QuadGeometry : Geometry() {
    init {
        addVertex(
            -1.0f, -1.0f, 0.0f,  // bottom-left
            1.0f, -1.0f, 0.0f,   // bottom-right
            1.0f, 1.0f, 0.0f,    // top-right
            -1.0f, 1.0f, 0.0f    // top-left
        )

        vertices[0].uv.set(0.0f, 0.0f)
        vertices[1].uv.set(1.0f, 0.0f)
        vertices[2].uv.set(1.0f, 1.0f)
        vertices[3].uv.set(0.0f, 1.0f)

        addFace(
            0, 1, 2,
            0, 2, 3
        )
    }
}