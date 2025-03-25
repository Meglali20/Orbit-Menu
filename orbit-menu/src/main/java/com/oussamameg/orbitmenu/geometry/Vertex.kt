package com.oussamameg.orbitmenu.geometry

import org.joml.Vector2f
import org.joml.Vector3f

class Vertex(x: Float, y: Float, z: Float) {
    val position: Vector3f = Vector3f(x, y, z)
    val normal: Vector3f = Vector3f()
    val uv: Vector2f = Vector2f()
}