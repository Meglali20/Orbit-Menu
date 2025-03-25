package com.oussamameg.orbitmenu.geometry

import kotlin.math.cos
import kotlin.math.sin

class DiskGeometry(steps: Int = 4, radius: Float = 1f) : Geometry() {
    init {
        val safeSteps = maxOf(4, steps)
        val alpha = (2 * Math.PI) / safeSteps

        // Center vertex
        addVertex(0f, 0f, 0f)
        lastVertex.uv.set(0.5f, 0.5f)

        for (i in 0 until safeSteps) {
            val x = cos(alpha * i).toFloat()
            val y = sin(alpha * i).toFloat()
            addVertex(radius * x, radius * y, 0f)
            lastVertex.uv.set(x * 0.5f + 0.5f, y * 0.5f + 0.5f)

            if (i > 0) {
                addFace(0, i, i + 1)
            }
        }
        addFace(0, safeSteps, 1)
    }
}