package com.oussamameg.orbitmenu.geometry

class SphereGeometry(
    radius: Float = 1.0f,
    widthSegments: Int = 32,
    heightSegments: Int = 16,
    phiStart: Float = 0f,
    phiLength: Float = PI2,
    thetaStart: Float = 0f,
    thetaLength: Float = PI
) : Geometry() {

    companion object {
        private const val PI = Math.PI.toFloat()
        private const val PI2 = (2 * Math.PI).toFloat()
    }

    init {
        val thetaEnd = thetaStart + thetaLength

        var index = 0
        val grid = ArrayList<ArrayList<Int>>()

        // Generate vertices
        for (iy in 0..heightSegments) {
            val row = ArrayList<Int>()
            val v = iy.toFloat() / heightSegments

            val uOffset = if (iy == 0 && thetaStart == 0f) 0.5f / widthSegments else 0f

            for (ix in 0..widthSegments) {
                val u = ix.toFloat() / widthSegments

                // Vertex position
                val x = -radius * cos(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                val y = radius * cos(thetaStart + v * thetaLength)
                val z = radius * sin(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)

                addVertex(x, y, z)

                // Update vertex normal
                lastVertex.normal.set(x, y, z).normalize()

                // Update UV coordinates
                lastVertex.uv.set(u + uOffset, 1 - v)

                row.add(index++)
            }

            grid.add(row)
        }

        // Generate faces
        for (iy in 0 until heightSegments) {
            for (ix in 0 until widthSegments) {
                val a = grid[iy][ix + 1]
                val b = grid[iy][ix]
                val c = grid[iy + 1][ix]
                val d = grid[iy + 1][ix + 1]

                if (iy != 0 || thetaStart > 0) {
                    addFace(a, b, d)
                }

                if (iy != heightSegments - 1 || thetaEnd < PI) {
                    addFace(b, c, d)
                }
            }
        }
    }

    private fun cos(value: Float): Float = kotlin.math.cos(value)
    private fun sin(value: Float): Float = kotlin.math.sin(value)
}