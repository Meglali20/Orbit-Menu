package com.oussamameg.orbitmenu.core

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

@SuppressLint("ClickableViewAccessibility")
internal class ArcBallControl(
    private val view: View,
    private val targetReachedCallback: (targetReached: Boolean) -> Unit = {},
    private val updateCallback: (deltaTime: Float) -> Unit = {}
) : View.OnTouchListener {
    var isTouchDown = false
    val currentOrientation = Quaternionf().identity()
    val currentTouchRotation = Quaternionf().identity()
    var currentRotationVelocity = 0f
    val currentRotationAxis = Vector3f(1f, 0f, 0f)

    val defaultSnapDirection = Vector3f(0f, 0f, -1f)
    var targetSnapDirection: Vector3f? = null

    private val currentTouchPosition = Vector2f()
    private val previousTouchPosition = Vector2f()
    private var smoothedRotationVelocity = 0f
    private val smoothedCombinedQuaternion = Quaternionf().identity()

    private val touchDeltaThreshold = 0.1f
    private val identityQuaternion = Quaternionf().identity()
    var targetReached = false
    private var previousTargetReached = false
    private var forceSnapRequested = false
    private var forceSnapInProgress = false

    var skewing = true

    init {
        view.setOnTouchListener(this)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentTouchPosition.set(event.x, event.y)
                previousTouchPosition.set(currentTouchPosition)
                isTouchDown = true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouchDown = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTouchDown) {
                    currentTouchPosition.set(event.x, event.y)
                }
            }
        }
        return true
    }

    fun updateControlState(deltaTime: Float, targetFrameDuration: Float = 16f) {
        val timeFactor = deltaTime / (targetFrameDuration + 0.00001f)
        var rotationAngleFactor = timeFactor
        val snapQuat = Quaternionf().identity()

        if (isTouchDown) {
            targetReached = false
            if (previousTargetReached) {
                targetReachedCallback(false)
                previousTargetReached = false
            }
            val dragIntensity = 0.3f * timeFactor
            val rotationAmplification = 5f / timeFactor

            val interpolatedTouchPos = Vector2f(currentTouchPosition)
            interpolatedTouchPos.sub(previousTouchPosition)
            interpolatedTouchPos.mul(dragIntensity)

            if (interpolatedTouchPos.lengthSquared() > touchDeltaThreshold) {
                interpolatedTouchPos.add(previousTouchPosition)

                val currentProjectedPos = projectToSphere(interpolatedTouchPos)
                val previousProjectedPos = projectToSphere(previousTouchPosition)
                val normalizedCurrentVector = Vector3f(currentProjectedPos).normalize()
                val normalizedPreviousVector = Vector3f(previousProjectedPos).normalize()

                previousTouchPosition.set(interpolatedTouchPos)
                rotationAngleFactor *= rotationAmplification

                calculateQuaternionFromVectors(
                    normalizedCurrentVector,
                    normalizedPreviousVector,
                    currentTouchRotation,
                    rotationAngleFactor
                )
            } else {
                currentTouchRotation.slerp(identityQuaternion, dragIntensity)
            }
        } else {
            if (forceSnapRequested) {
                targetReached = false
                if (previousTargetReached) {
                    targetReachedCallback(false)
                    previousTargetReached = false
                }
            }
            val releaseIntensity = 0.1f * timeFactor
            currentTouchRotation.slerp(identityQuaternion, releaseIntensity)

            targetSnapDirection?.let { target ->
                val snapIntensity = if (forceSnapRequested) 0.6f else 0.2f
                val squaredDistance = target.distanceSquared(defaultSnapDirection)
                val snapDistanceFactor = max(0.1f, 1f - squaredDistance * 10f)
                rotationAngleFactor *= snapIntensity * snapDistanceFactor
                calculateQuaternionFromVectors(
                    target,
                    defaultSnapDirection,
                    snapQuat,
                    rotationAngleFactor
                )
                if (squaredDistance < 0.0001f && currentRotationVelocity < 0.001f) {
                    if (forceSnapRequested) {
                        forceSnapInProgress = false
                        forceSnapRequested = false
                    }
                    targetReached = true
                    if (!previousTargetReached) {
                        targetReachedCallback(true)
                        targetSnapDirection = null
                    }
                }
            }
        }

        // Correct quaternion combination order (snap * touch)
        val combinedRotationQuat = Quaternionf(snapQuat).mul(currentTouchRotation)

        val previousOrientationQuat = Quaternionf(currentOrientation)
        currentOrientation.set(combinedRotationQuat).mul(previousOrientationQuat).normalize()

        val skewIntensity = if(skewing) 0.8f * timeFactor else 0f

        smoothedCombinedQuaternion.slerp(combinedRotationQuat, skewIntensity).normalize()

        val rotationAngleRadians = acos(smoothedCombinedQuaternion.w) * 2.0f
        val sineHalfAngle = sin(rotationAngleRadians / 2.0f)
        var instantRotationVelocity = 0f
        if (sineHalfAngle > 0.000001f) {
            instantRotationVelocity = rotationAngleRadians / (2f * Math.PI.toFloat())
            currentRotationAxis.set(
                smoothedCombinedQuaternion.x / sineHalfAngle,
                smoothedCombinedQuaternion.y / sineHalfAngle,
                smoothedCombinedQuaternion.z / sineHalfAngle
            )
        }

        val velocitySmoothingFactor = 0.5f * timeFactor
        smoothedRotationVelocity += (instantRotationVelocity - smoothedRotationVelocity) * velocitySmoothingFactor
        currentRotationVelocity = smoothedRotationVelocity / timeFactor

        previousTargetReached = targetReached
        updateCallback(deltaTime)
    }

    fun forceSnapTo(target: Vector3f, immediate: Boolean = false) {
        targetSnapDirection = target
        forceSnapRequested = true
        forceSnapInProgress = true
        previousTargetReached = false  // reset to ensure callback fires

        if (immediate) {
            // Immediately apply the rotation without animation
            val snapQuat = Quaternionf().identity()
            calculateQuaternionFromVectors(
                target,
                defaultSnapDirection,
                snapQuat,
                1.0f  // Use full rotation factor for immediate effect
            )

            // Apply the snap rotation to current orientation directly
            currentOrientation.set(snapQuat).normalize()

            smoothedRotationVelocity = 0f
            currentRotationVelocity = 0f
            targetReached = true
            forceSnapInProgress = false
            forceSnapRequested = false

            targetReachedCallback(true)
            targetSnapDirection = null
        }
    }

    fun setImmediateOrientation(orientation: Quaternionf) {
        //set orientation without any animations or transitions
        currentOrientation.set(orientation).normalize()
        currentTouchRotation.identity()
        smoothedCombinedQuaternion.identity()
        smoothedRotationVelocity = 0f
        currentRotationVelocity = 0f
        targetReached = true
        targetSnapDirection = null
        forceSnapRequested = false
        forceSnapInProgress = false

        updateCallback(0f)
    }

    fun isForceSnapInProgress(): Boolean = forceSnapInProgress

    private fun calculateQuaternionFromVectors(
        a: Vector3f,
        b: Vector3f,
        out: Quaternionf,
        angleFactor: Float = 1f
    ): Triple<Quaternionf, Vector3f, Float> {
        val axis = Vector3f()
        a.cross(b, axis)

        if (axis.lengthSquared() < 1e-12f) {
            return when {
                a.dot(b) > 0.9999f -> { // Same direction
                    out.identity()
                    Triple(out, Vector3f(1f, 0f, 0f), 0f)
                }

                else -> { // Opposite directions (180° rotation)
                    val tmp = Vector3f(0f, 1f, 0f)
                    if (abs(a.x) < 0.9f) tmp.set(1f, 0f, 0f)
                    axis.cross(a, tmp).normalize()
                    out.rotationAxis(Math.PI.toFloat() * angleFactor, axis)
                    Triple(out, axis, Math.PI.toFloat() * angleFactor)
                }
            }
        }

        axis.normalize()
        val d = a.dot(b).coerceIn(-1f, 1f)
        val angle = acos(d) * angleFactor
        out.rotationAxis(angle, axis)
        return Triple(out, axis, angle)
    }

    private fun projectToSphere(pos: Vector2f): Vector3f {
        val radius = 2f
        val width = view.measuredWidth.toFloat()
        val height = view.measuredHeight.toFloat()
        val scale = max(width, height) - 1f

        val x = (2f * pos.x - width - 1f) / scale
        val y = (2f * pos.y - height - 1f) / scale

        val xySq = x * x + y * y
        val rSq = radius * radius

        val z = when {
            xySq <= rSq / 2f -> sqrt(rSq - xySq)
            else -> rSq / sqrt(xySq)
        }

        return Vector3f(-x, y, z)
    }
}