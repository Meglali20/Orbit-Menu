package com.oussamameg.orbitmenu.model

import org.joml.Matrix4f
import org.joml.Vector3f

data class Camera(
    val position: Vector3f = Vector3f(0f, 0f, 3f),
    val up: Vector3f = Vector3f(0f, 1f, 0f),
    val matrix: Matrix4f = Matrix4f(),
    val matrices: CameraMatrices = CameraMatrices(),
    var aspect: Float = 1f,
    var fov: Float = (Math.PI / 4).toFloat(),
    var near: Float = 0.1f,
    var far: Float = 40f
)

data class CameraMatrices(
    val view: Matrix4f = Matrix4f(),
    val projection: Matrix4f = Matrix4f(),
    val inverseProjection: Matrix4f = Matrix4f()
)