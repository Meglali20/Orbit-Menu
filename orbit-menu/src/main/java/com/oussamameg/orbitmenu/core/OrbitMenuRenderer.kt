package com.oussamameg.orbitmenu.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.math.absoluteValue
import androidx.core.graphics.createBitmap
import com.oussamameg.orbitmenu.model.Camera
import com.oussamameg.orbitmenu.geometry.DiskGeometry
import com.oussamameg.orbitmenu.util.GLHelper
import com.oussamameg.orbitmenu.geometry.Geometry
import com.oussamameg.orbitmenu.geometry.IcosahedronGeometry
import com.oussamameg.orbitmenu.model.OrbitMenuItem
import com.oussamameg.orbitmenu.listener.OrbitSelectionListener
import com.oussamameg.orbitmenu.geometry.QuadGeometry
import com.oussamameg.orbitmenu.geometry.SphereGeometry
import kotlin.collections.iterator
import androidx.core.graphics.scale
import com.oussamameg.orbitmenu.core.OrbitMenuSurfaceView.Companion.DEFAULT_GLOW_COLOR
import com.oussamameg.orbitmenu.core.OrbitMenuSurfaceView.Companion.NO_GLOW
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

internal class OrbitMenuRenderer(
    private val surfaceView: OrbitMenuSurfaceView,
    private val context: Context
) : GLSurfaceView.Renderer {

    companion object {
        private const val TARGET_FRAME_DURATION = 1000f / 60f // 60 fps
        private const val SPHERE_RADIUS = 3f
    }

    var orbitSelectionListener: OrbitSelectionListener? = null
    val bgColorRGBAFloat = convertColorIntToFloatArray(Color(0xFF000000).toArgb())
    var clearColor = 0
        set(value) {
            convertColorIntToFloatArray(value).copyInto(bgColorRGBAFloat)
        }


    private var diskProgram: Int = 0
    private var backgroundProgram: Int = 0
    private var sphereProgram: Int = 0

    private var diskVAO: Int = 0
    private var backgroundVAO: Int = 0
    private var sphereVAO: Int = 0

    private var textureId: Int = 0
    private var glowMapTextureId: Int = 0
    private var backgroundTextureId: Int = 0
    private var sphereTextureId: Int = 0

    private lateinit var icosahedronGeometry: IcosahedronGeometry
    private lateinit var diskGeometry: DiskGeometry
    private lateinit var backgroundGeometry: QuadGeometry
    private lateinit var sphereGeometry: SphereGeometry

    lateinit var arcBallControl: ArcBallControl

    private val worldMatrix = Matrix4f()
    private val worldMatrixData = FloatArray(16)
    private val viewMatrixData = FloatArray(16)
    private val projectionMatrixData = FloatArray(16)

    private var aModelPosition: Int = -1
    private var aModelUvs: Int = -1
    private var aInstanceMatrix: Int = -1
    private var uWorldMatrix: Int = -1
    private var uViewMatrix: Int = -1
    private var uProjectionMatrix: Int = -1
    private var uRotationAxisVelocity: Int = -1
    private var uTex: Int = -1
    private var uItemCount: Int = -1
    private var uAtlasSize: Int = -1
    private var uTime: Int = -1
    private var uGlowMap: Int = -1

    private var aBackgroundPos: Int = -1
    private var aBackgroundUvs: Int = -1
    private var uBackgroundTex: Int = -1

    private var aSpherePosition: Int = -1
    private var aSphereUvs: Int = -1
    private var uSphereWorldMatrix: Int = -1
    private var uSphereViewMatrix: Int = -1
    private var uSphereProjectionMatrix: Int = -1
    private var uSphereTex: Int = -1

    private val viewportSize = Vector2f()

    data class DiskInstances(
        val matricesArray: FloatBuffer,
        val matrices: List<Matrix4f>,
        var buffer: Int
    )

    private lateinit var diskInstances: DiskInstances

    private var instancePositions: MutableList<Vector3f> = mutableListOf()
    private var diskInstanceCount = 0
    private var atlasSize = 1

    private var lastFrameTime = SystemClock.elapsedRealtime()
    private var elapsedTime: Float = 0f
    private var frameDelta: Float = 0f
    private var deltaFrames: Float = 0f
    private var frames: Int = 0

    private var movementActive = false

    private lateinit var diskBuffers: Geometry.GeometryData
    private lateinit var backgroundBuffers: Geometry.GeometryData
    private lateinit var sphereBuffers: Geometry.GeometryData

    var camera = Camera(near = 0.1f, far = 40f, fov = (Math.PI / 4).toFloat(), aspect = 1f)
    var rotationSpeed = 0f

    var isPaused = true
    var itemsCount = 0
    var activeIndex = -1
    var forceSnapTo = -1

    var drawBackground = false
    var drawSphere = false
    var diskInitializationDone = false

    var backgroundAlpha = 0.75f
    private var currentBackgroundAlpha = 0f
    private var isBackgroundTransitionInProgress = false
    private var pendingBackgroundBitmap: Bitmap? = null
    private var pendingBackgroundAlpha: Float = 0.75f

    var drawDebugText = false

    override fun onSurfaceCreated(
        p0: GL10?,
        p1: EGLConfig?
    ) {
        GLES30.glClearColor(
            bgColorRGBAFloat[0],
            bgColorRGBAFloat[1],
            bgColorRGBAFloat[2],
            bgColorRGBAFloat[3]
        )
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
    }

    override fun onSurfaceChanged(
        p0: GL10?,
        width: Int,
        height: Int
    ) {
        if (width == 0 || height == 0) return
        GLES30.glViewport(0, 0, width, height)

        viewportSize.set(width.toFloat(), height.toFloat())
        updateProjection()

        if (diskInitializationDone) return

        if (drawBackground)
            initBackgroundProgramAndTexture()
        initDiskProgramAndTexture()
        if (drawSphere)
            initSphereProgramAndTexture()

        isPaused = false
    }

    private fun initBackgroundProgramAndTexture() {
        backgroundProgram = GLHelper.Companion.createProgram(
            vertexShaderString = GLHelper.Companion.loadFromAsset(
                context,
                "shaders/background_vert_shader.glsl"
            ),
            fragmentShaderString = GLHelper.Companion.loadFromAsset(
                context,
                "shaders/background_frag_shader.glsl"
            )
        )

        aBackgroundPos = GLES30.glGetAttribLocation(backgroundProgram, "aPosition")
        aBackgroundUvs = GLES30.glGetAttribLocation(backgroundProgram, "aUvs")
        uBackgroundTex = GLES30.glGetUniformLocation(backgroundProgram, "uBackgroundTex")

        backgroundGeometry = QuadGeometry()
        backgroundBuffers = backgroundGeometry.data
        backgroundVAO = GLHelper.Companion.makeVertexArray(
            listOf(
                Triple(
                    GLHelper.Companion.makeBuffer(
                        backgroundBuffers.vertices,
                        GLES30.GL_STATIC_DRAW
                    ),
                    aBackgroundPos,
                    3
                ),
                Triple(
                    GLHelper.Companion.makeBuffer(backgroundBuffers.uvs, GLES30.GL_STATIC_DRAW),
                    aBackgroundUvs,
                    2
                )
            ),
            backgroundBuffers.indices
        )
        checkGLError("backgroundVAO Creation Stub!")
        initBackgroundTexture()
    }

    private fun initSphereProgramAndTexture() {
        sphereProgram = GLHelper.Companion.createProgram(
            vertexShaderString = GLHelper.Companion.loadFromAsset(
                context,
                "shaders/sphere_vert_shader.glsl"
            ),
            fragmentShaderString = GLHelper.Companion.loadFromAsset(
                context,
                "shaders/sphere_frag_shader.glsl"
            )
        )

        aSpherePosition = GLES30.glGetAttribLocation(sphereProgram, "aPosition")
        aSphereUvs = GLES30.glGetAttribLocation(sphereProgram, "aUvs")
        uSphereWorldMatrix = GLES30.glGetUniformLocation(sphereProgram, "uWorldMatrix")
        uSphereViewMatrix = GLES30.glGetUniformLocation(sphereProgram, "uViewMatrix")
        uSphereProjectionMatrix = GLES30.glGetUniformLocation(sphereProgram, "uProjectionMatrix")
        uSphereTex = GLES30.glGetUniformLocation(sphereProgram, "uTex")

        validateSphereShaderLocations()

        sphereGeometry =
            SphereGeometry(radius = SPHERE_RADIUS * 0.82f, widthSegments = 32, heightSegments = 32)
        sphereBuffers = sphereGeometry.data

        sphereVAO = GLHelper.Companion.makeVertexArray(
            listOf(
                Triple(
                    GLHelper.Companion.makeBuffer(sphereBuffers.vertices, GLES30.GL_STATIC_DRAW),
                    aSpherePosition,
                    3
                ),
                Triple(
                    GLHelper.Companion.makeBuffer(sphereBuffers.uvs, GLES30.GL_STATIC_DRAW),
                    aSphereUvs,
                    2
                )
            ),
            sphereBuffers.indices
        )
        checkGLError("sphereVAO Stub!")
        initSphereTexture(GLHelper.Companion.generateRandomBitmap(500, 500))
    }

    private fun initDiskProgramAndTexture() {
        diskProgram = GLHelper.Companion.createProgram(
            vertexShaderString = GLHelper.Companion.loadFromAsset(
                context,
                "shaders/disk_vert_shader.glsl"
            ),
            fragmentShaderString = GLHelper.Companion.loadFromAsset(
                context,
                "shaders/disk_frag_shader.glsl"
            )
        )

        aModelPosition = GLES30.glGetAttribLocation(diskProgram, "aModelPosition")
        aModelUvs = GLES30.glGetAttribLocation(diskProgram, "aModelUvs")
        aInstanceMatrix = GLES30.glGetAttribLocation(diskProgram, "aInstanceMatrix")

        uWorldMatrix = GLES30.glGetUniformLocation(diskProgram, "uWorldMatrix")
        uViewMatrix = GLES30.glGetUniformLocation(diskProgram, "uViewMatrix")
        uProjectionMatrix = GLES30.glGetUniformLocation(diskProgram, "uProjectionMatrix")
        uRotationAxisVelocity = GLES30.glGetUniformLocation(diskProgram, "uRotationAxisVelocity")
        uTex = GLES30.glGetUniformLocation(diskProgram, "uTex")
        uItemCount = GLES30.glGetUniformLocation(diskProgram, "uItemCount")
        uAtlasSize = GLES30.glGetUniformLocation(diskProgram, "uAtlasSize")
        uTime = GLES30.glGetUniformLocation(diskProgram, "uTime")
        uGlowMap = GLES30.glGetUniformLocation(diskProgram, "uGlowMap")

        validateDiskShaderLocations()

        diskGeometry = DiskGeometry(56, 2f)
        diskBuffers = diskGeometry.data
        diskVAO = GLHelper.Companion.makeVertexArray(
            listOf(
                Triple(
                    GLHelper.Companion.makeBuffer(diskBuffers.vertices, GLES30.GL_STATIC_DRAW),
                    aModelPosition,
                    3
                ),
                Triple(
                    GLHelper.Companion.makeBuffer(diskBuffers.uvs, GLES30.GL_STATIC_DRAW),
                    aModelUvs,
                    2
                )
            ),
            diskBuffers.indices
        )
        checkGLError("discVAO Creation Stub!")

        icosahedronGeometry = IcosahedronGeometry()
        icosahedronGeometry.subdivide(1).spherize(SPHERE_RADIUS)
        instancePositions = icosahedronGeometry.vertices.map {
            it.position
        }.toMutableList()
        diskInstanceCount = icosahedronGeometry.vertices.size

        initDiscInstances(diskInstanceCount)
        checkGLError("initDiscInstances Stub!")

        checkGLError("loadBackgroundTexture Stub!")

        /* val images = mutableListOf<OrbitMenuItem>()
         for (i in 0..16) {
             images.add(OrbitMenuItem(i, GLHelper.generateRandomBitmap(512, 512), 0))
         }
         itemsCount = images.size

        initAtlasTexture(images)
        initGlowMapTexture()
        setGlowingDisksWithColors(
            mapOf(
                2 to Color(0xFFCD2C45).toArgb(),
                5 to Color(0xFFF3D050).toArgb(),  // Red glow for disk 5
                3 to Color(0xFF2DE7C1).toArgb()  // Blue glow for disk 3
            )
        )*/
        //loadImagesAndInitAtlas()
        //checkGLError("initTexture Stub!")

        arcBallControl = ArcBallControl(surfaceView, targetReachedCallback = {
            orbitSelectionListener?.onSnapTargetReached(it)
            if (arcBallControl.isTouchDown) return@ArcBallControl
            val nearestVertexIndex = getNearestItemVertex()
            val itemIndex = nearestVertexIndex % maxOf(1, itemsCount)
            activeIndex = itemIndex
            orbitSelectionListener?.onSnapComplete(itemIndex)
            forceSnapTo = -1
        }, updateCallback = {
            handleControlStateUpdate(it)
        })

        updateCamera()
        updateProjection()
        diskInitializationDone = true
    }

    fun validateDiskShaderLocations() {
        val shaderLocations = mapOf(
            "uWorldMatrix" to uWorldMatrix,
            "uViewMatrix" to uViewMatrix,
            "uProjectionMatrix" to uProjectionMatrix,
            "uRotationAxisVelocity" to uRotationAxisVelocity,
            "uTex" to uTex,
            "uItemCount" to uItemCount,
            "uAtlasSize" to uAtlasSize,
            "uTime" to uTime,
            "aModelPosition" to aModelPosition,
            "aModelUvs" to aModelUvs,
            "aInstanceMatrix" to aInstanceMatrix
        )

        for ((name, location) in shaderLocations) {
            if (location == -1) {
                Log.e("ShaderError", "Error: $name not found in the shader program!")
            } else {
                Log.d("ShaderCheck", "$name location: $location")
            }
        }
    }

    private fun validateSphereShaderLocations() {
        val shaderLocations = mapOf(
            "uSphereWorldMatrix" to uSphereWorldMatrix,
            "uSphereViewMatrix" to uSphereViewMatrix,
            "uSphereProjectionMatrix" to uSphereProjectionMatrix,
            "uSphereTex" to uSphereTex,
            "aSpherePosition" to aSpherePosition,
            "aSphereUvs" to aSphereUvs
        )

        for ((name, location) in shaderLocations) {
            if (location == -1) {
                Log.e("ShaderError", "Error: $name not found in the sphere shader program!")
            } else {
                Log.d("ShaderCheck", "$name location: $location")
            }
        }
    }

    override fun onDrawFrame(p0: GL10?) {
        if (isPaused) return
        GLES30.glClearColor(
            bgColorRGBAFloat[0],
            bgColorRGBAFloat[1],
            bgColorRGBAFloat[2],
            bgColorRGBAFloat[3]
        )
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (itemsCount == 0) return
        val currentTime = SystemClock.elapsedRealtime()
        frameDelta = (currentTime - lastFrameTime).toFloat().coerceAtMost(32f)
        elapsedTime = currentTime.toFloat()
        deltaFrames = frameDelta / TARGET_FRAME_DURATION
        frames += deltaFrames.toInt()
        lastFrameTime = currentTime
        updateTransforms(frameDelta)

        if (drawBackground)
            renderBackground()
        if (drawSphere)
            renderSphere()
        renderDisks()
        checkGLError("DrawFrame")
    }

    private fun initDiscInstances(count: Int) {
        if (diskVAO == 0) {
            throw RuntimeException("discVAO not setup correctly")
        }

        // Allocate direct ByteBuffer for matrices
        val matricesBuffer = ByteBuffer.allocateDirect(count * 16 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        // Create matrices list
        val matrices = List(count) { index ->
            val matrix = Matrix4f()
            matrix.identity()
            // Get a view of the buffer for this matrix
            matricesBuffer.position(index * 16)
            val matrixBuffer = matricesBuffer.slice()
            matrixBuffer.limit(16)
            matrix.get(matrixBuffer)
            matrix
        }

        // Create instance buffer
        val buffers = IntArray(1)
        GLES30.glGenBuffers(1, buffers, 0)
        val instanceBuffer = buffers[0]

        diskInstances = DiskInstances(matricesBuffer, matrices, instanceBuffer)

        GLES30.glBindVertexArray(diskVAO)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, diskInstances.buffer)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            count * 16 * 4,  // size in bytes
            null,
            GLES30.GL_DYNAMIC_DRAW
        )

        // Set up matrix attribute slots
        val mat4AttribSlotCount = 4
        val bytesPerMatrix = 16 * 4 // 16 floats, 4 bytes each
        for (j in 0 until mat4AttribSlotCount) {
            val loc = aInstanceMatrix + j
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribPointer(
                loc,
                4,
                GLES30.GL_FLOAT,
                false,
                bytesPerMatrix,
                j * 4 * 4
            )
            GLES30.glVertexAttribDivisor(loc, 1)
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun loadImagesAndInitAtlas() {
        val imageCount = 16

        val placeholderBitmaps = List(imageCount) {
            OrbitMenuItem(0, createBitmap(512, 512), 0)
        }

        initAtlasTexture(placeholderBitmaps)
        itemsCount = imageCount

        CoroutineScope(Dispatchers.IO).launch {
            val orbitMenuItems = mutableListOf<OrbitMenuItem>()

            for (index in 0 until imageCount) {
                try {
                    val imageUrl = URL("https://picsum.photos/600/400?random=${index + 1}")
                    val connection = imageUrl.openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.connect()

                    val inputStream = connection.getInputStream()
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    val scaledBitmap = originalBitmap.scale(512, 512)
                    originalBitmap.recycle()

                    orbitMenuItems.add(
                        OrbitMenuItem(
                            index, scaledBitmap,
                            android.graphics.Color.rgb(
                                Random.Default.nextInt(256),
                                Random.Default.nextInt(256),
                                Random.Default.nextInt(256)
                            )
                        )
                    )
                    Log.d("ImageLoader", "Loaded image ${index + 1} successfully")
                } catch (e: Exception) {
                    Log.e("ImageLoader", "Failed to load image ${index + 1}: ${e.message}")
                    // Create error bitmap
                    val errorBitmap = createBitmap(512, 512)
                    val canvas = Canvas(errorBitmap)
                    canvas.drawColor(Color.Gray.toArgb())

                    val paint = Paint().apply {
                        color = Color.Red.toArgb()
                        textSize = 48f
                        textAlign = Paint.Align.CENTER
                    }

                    canvas.drawText("Error loading image", 256f, 256f, paint)
                    orbitMenuItems.add(OrbitMenuItem(index, errorBitmap, 0))
                }
            }

            surfaceView.queueEvent {
                try {
                    if (orbitMenuItems.isNotEmpty()) {
                        initAtlasTexture(orbitMenuItems)
                        Log.d(
                            "ImageLoader",
                            "Updated texture atlas with ${orbitMenuItems.size} images"
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ImageLoader", "Error updating texture atlas: ${e.message}")
                } finally {
                    orbitMenuItems.forEach { it.image.recycle() }
                }
            }

        }
    }

    fun setOrbitMenuItems(items: List<OrbitMenuItem>, initialSelectedIndex: Int = 0) {
        itemsCount = items.size
        //setInitialDiskFacingCamera(0)
        initAtlasTexture(items)
        initGlowMapTexture()
        updateGlowingDisks(items)
        snapToImage(initialSelectedIndex)
    }

    private fun setInitialDiskFacingCamera(diskIndex: Int) {
        if (!::arcBallControl.isInitialized) return

        val angle = (2 * Math.PI * diskIndex) / itemsCount
        val diskPosition = Vector3f(
            sin(angle.toFloat()),
            cos(angle.toFloat()),
            0f
        ).normalize()

        // Target direction (camera facing)
        val targetDirection = Vector3f(0f, 0f, -1f)

        // Create a quaternion that rotates from the disk's position to the target direction
        val initialOrientation = Quaternionf()
        initialOrientation.rotationTo(diskPosition, targetDirection)

        arcBallControl.setImmediateOrientation(initialOrientation)

        updateTransforms(0f)
        activeIndex = diskIndex
    }

    private fun initAtlasTexture(items: List<OrbitMenuItem>) {
        textureId = GLHelper.Companion.createAndSetupTexture(
            GLES30.GL_LINEAR,
            GLES30.GL_LINEAR,
            GLES30.GL_CLAMP_TO_EDGE,
            GLES30.GL_CLAMP_TO_EDGE
        )
        // Calculate atlas dimensions
        val itemCount = maxOf(1, items.size)
        atlasSize = ceil(sqrt(itemCount.toFloat())).toInt()
        val cellSize = 512

        val atlasBitmap = createBitmap(atlasSize * cellSize, atlasSize * cellSize)
        val canvas = Canvas(atlasBitmap)

        val textPaint = Paint().apply {
            textSize = 48f
            color = Color.Red.toArgb()
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
        }

        val strokePaint = Paint().apply {
            textSize = 48f
            color = Color.Black.toArgb()
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        items.forEachIndexed { index, orbitMenuItem ->
            Log.e("Test", "Drawing ATLAS $index")
            val x = (index % atlasSize) * cellSize
            val y = (index / atlasSize) * cellSize

            canvas.drawBitmap(
                orbitMenuItem.image,
                null,
                RectF(
                    x.toFloat(),
                    y.toFloat(),
                    (x + cellSize).toFloat(),
                    (y + cellSize).toFloat()
                ),
                null
            )
            if (!drawDebugText) return@forEachIndexed
            val text = "image $index"

            val textX = x + cellSize / 2f
            val textY = y + cellSize / 2f
            canvas.save()
            canvas.translate(textX, textY)
            canvas.scale(-1f, 1f)
            canvas.drawText(text, 0f, 0f, strokePaint)
            canvas.drawText(text, 0f, 0f, textPaint)
            canvas.restore()
        }


        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        val buffer = ByteBuffer.allocateDirect(atlasBitmap.width * atlasBitmap.height * 4)
            .order(ByteOrder.nativeOrder())
        atlasBitmap.copyPixelsToBuffer(buffer)
        buffer.position(0)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            atlasBitmap.width,
            atlasBitmap.height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )

        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        atlasBitmap.recycle()
    }

    private fun initGlowMapTexture() {
        val glowMapBuffer = IntArray(1)
        GLES30.glGenTextures(1, glowMapBuffer, 0)
        glowMapTextureId = glowMapBuffer[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, glowMapTextureId)
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_NEAREST
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_NEAREST
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )

        // Initialize with all disks not glowing
        val initialGlowData = ByteBuffer.allocateDirect(itemsCount * 4 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        for (i in 0 until itemsCount) {
            // R: glow factor (0 = no glow, 1 = glow)
            // G, B, A: glow color and intensity
            initialGlowData.put(0.0f) // R - glow factor
            initialGlowData.put(0.0f) // G - color.r
            initialGlowData.put(0.0f) // B - color.g
            initialGlowData.put(0.0f) // A - color.b
        }
        initialGlowData.position(0)

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA32F,
            itemsCount, 1,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_FLOAT,
            initialGlowData
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun updateGlowingDisks(menuItems: List<OrbitMenuItem>) {
        // Create a map of index to color for items that should glow (color != -1)
        val glowingItemsMap = menuItems
            .filter { it.glowingColor != NO_GLOW }
            .associate { it.index to it.glowingColor }
        setGlowingDisksWithColors(glowingItemsMap)
    }

    private fun initSphereTexture(bitmap: Bitmap) {
        sphereTextureId = GLHelper.Companion.createAndSetupTexture(
            GLES30.GL_LINEAR,
            GLES30.GL_LINEAR,
            GLES30.GL_CLAMP_TO_EDGE,
            GLES30.GL_CLAMP_TO_EDGE
        )

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sphereTextureId)

        val buffer = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
            .order(ByteOrder.nativeOrder())
        bitmap.copyPixelsToBuffer(buffer)
        buffer.position(0)

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            bitmap.width,
            bitmap.height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )

        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }

    private fun initBackgroundTexture() {
        backgroundTextureId = GLHelper.Companion.createAndSetupTexture(
            GLES30.GL_LINEAR,
            GLES30.GL_LINEAR,
            GLES30.GL_CLAMP_TO_EDGE,
            GLES30.GL_CLAMP_TO_EDGE
        )

        val backgroundBitmap = GLHelper.Companion.createDefaultBackgroundBitmap()


        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTextureId)

        val buffer = ByteBuffer.allocateDirect(backgroundBitmap.width * backgroundBitmap.height * 4)
            .order(ByteOrder.nativeOrder())
        backgroundBitmap.copyPixelsToBuffer(buffer)
        buffer.position(0)


        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            backgroundBitmap.width,
            backgroundBitmap.height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )

        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        backgroundBitmap.recycle()
    }

    fun setBackgroundImage(bitmap: Bitmap, backgroundAlpha: Float = 0.75f) {
        if (backgroundProgram <= 0) {
            initBackgroundProgramAndTexture()
            this.backgroundAlpha = backgroundAlpha
            setBackgroundImage(bitmap)
            return
        }

        if (backgroundTextureId <= 0) {
            initBackgroundTexture()
            this.backgroundAlpha = backgroundAlpha
            setBackgroundImage(bitmap)
            return
        }

        if (isBackgroundTransitionInProgress) {
            pendingBackgroundBitmap?.recycle() // Clean up any existing pending bitmap
            pendingBackgroundBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            pendingBackgroundAlpha = backgroundAlpha
        } else if (currentBackgroundAlpha > 0f) {
            //fade-out transition
            isBackgroundTransitionInProgress = true
            pendingBackgroundBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            pendingBackgroundAlpha = backgroundAlpha
        } else {
            // no transition needed, update immediately
            this.backgroundAlpha = backgroundAlpha
            updateBackgroundTexture(bitmap)
            drawBackground = true
        }
    }

    private fun updateBackgroundTexture(bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTextureId)
        val buffer = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
            .order(ByteOrder.nativeOrder())
        bitmap.copyPixelsToBuffer(buffer)
        buffer.position(0)

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            bitmap.width,
            bitmap.height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )

        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }

    fun setGlowingDisks(glowingIndices: List<Int>) {
        // Create a small 1D texture that stores whether each disk should glow
        val glowDataBuffer = ByteBuffer.allocateDirect(itemsCount * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        // Initialize all to 0f (no glow)
        for (i in 0 until itemsCount) {
            glowDataBuffer.put(i, 0.0f)
        }

        // Set glow values for specified indices
        for (index in glowingIndices) {
            if (index < itemsCount) {
                glowDataBuffer.put(index, 1.0f)
            }
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, glowMapTextureId)
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            0, 0,
            itemsCount, 1,
            GLES30.GL_RED,
            GLES30.GL_FLOAT,
            glowDataBuffer
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun setGlowingDisksWithColors(colorMap: Map<Int, Int>) {
        // Create buffer for RGBA data
        val glowDataBuffer = ByteBuffer.allocateDirect(itemsCount * 4 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        // Initialize all to 0f (no glow)
        for (i in 0 until itemsCount * 4) {
            glowDataBuffer.put(0.0f)
        }
        glowDataBuffer.position(0)

        // Set glow values and colors for specified indices
        for ((index, colorInt) in colorMap) {
            if (index < itemsCount) {
                if(colorInt == NO_GLOW) continue
                val color = if(colorInt == DEFAULT_GLOW_COLOR) Vector3f(0f,0f,0f) else GLHelper.intColorToVector3f(colorInt)
                val bufferIndex = index * 4
                glowDataBuffer.put(bufferIndex, 1.0f)         // R: glow enabled
                glowDataBuffer.put(bufferIndex + 1, color.x)  // G: red component
                glowDataBuffer.put(bufferIndex + 2, color.y)  // B: green component
                glowDataBuffer.put(bufferIndex + 3, color.z)  // A: blue component
            }
        }
        glowDataBuffer.position(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, glowMapTextureId)
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            0, 0,
            itemsCount, 1,
            GLES30.GL_RGBA,
            GLES30.GL_FLOAT,
            glowDataBuffer
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        checkGLError("setGlowingDisksWithColors Stub!")
    }

    private fun checkGLError(tag: String) {
        var error: Int
        while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
            Log.e(tag, "glError: $error")
        }
    }

    private fun updateTransforms(frameDelta: Float) {
        arcBallControl.updateControlState(frameDelta, TARGET_FRAME_DURATION)

        val tempQuaternionf = Quaternionf()
        arcBallControl.currentOrientation.get(tempQuaternionf)

        val positions = instancePositions.map { pos ->
            Vector3f(pos).rotate(tempQuaternionf)
        }

        val scale = 0.35f
        val scaleIntensity = 0.8f
        val tempVec = Vector3f()
        val tempDir = Vector3f()
        val tempRight = Vector3f()
        val tempUp = Vector3f(0f, 1f, 0f)
        val tempCorrectedUp = Vector3f()

        positions.forEachIndexed { ndx, pos ->
            val s = (pos.z.absoluteValue / SPHERE_RADIUS) * scaleIntensity + (1f - scaleIntensity)
            val finalScale = s * scale

            tempDir.set(pos).normalize()
            tempDir.cross(tempUp, tempRight).normalize()

            // Recompute corrected up vector
            tempRight.cross(tempDir, tempCorrectedUp).normalize()

            val rotationMatrix = Matrix4f().apply {
                m00(tempRight.x)
                m01(tempRight.y)
                m02(tempRight.z)
                m03(0f)
                m10(tempCorrectedUp.x)
                m11(tempCorrectedUp.y)
                m12(tempCorrectedUp.z)
                m13(0f)
                m20(-tempDir.x)
                m21(-tempDir.y)
                m22(-tempDir.z)
                m23(0f)
                m30(0f)
                m31(0f)
                m32(0f)
                m33(1f)
            }

            diskInstances.matrices[ndx].identity()
                .translate(pos.negate(tempVec))
                .mul(rotationMatrix)
                .scale(finalScale)
                .translate(0f, 0f, -SPHERE_RADIUS * 1.05f)
        }

        diskInstances.matricesArray.clear()
        diskInstances.matrices.forEach { matrix ->
            matrix.get(diskInstances.matricesArray)
            diskInstances.matricesArray.position(diskInstances.matricesArray.position() + 16)
        }
        diskInstances.matricesArray.flip()

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, diskInstances.buffer)
        GLES30.glBufferSubData(
            GLES30.GL_ARRAY_BUFFER,
            0,
            diskInstances.matricesArray.capacity() * 4,
            diskInstances.matricesArray
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        rotationSpeed = arcBallControl.currentRotationVelocity
    }

    private fun renderBackground() {
        GLES30.glUseProgram(backgroundProgram)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUniform1i(uBackgroundTex, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTextureId)

        val uAlphaLocation = GLES30.glGetUniformLocation(backgroundProgram, "uAlpha")
        val isMoving = arcBallControl.isTouchDown ||
                abs(rotationSpeed) > 0.01f || arcBallControl.isForceSnapInProgress()

        val alphaAnimationSpeed = 0.05f
        if (isBackgroundTransitionInProgress) {
            //changing background - fade out first
            currentBackgroundAlpha =
                0.0f.coerceAtLeast(currentBackgroundAlpha - alphaAnimationSpeed)
            // when fully transparent, apply new texture and start fade in
            if (currentBackgroundAlpha <= 0f) {
                pendingBackgroundBitmap?.let {
                    updateBackgroundTexture(it)
                    backgroundAlpha = pendingBackgroundAlpha
                    it.recycle()
                    pendingBackgroundBitmap = null
                }
                isBackgroundTransitionInProgress = false
                // force draw to be enabled and set a small initial alpha to start fade-in
                drawBackground = true
                currentBackgroundAlpha = 0.01f
            }
        } else if (isMoving) {
            // standard movement-based fade-out
            currentBackgroundAlpha =
                0.0f.coerceAtLeast(currentBackgroundAlpha - alphaAnimationSpeed)

            // only disable drawing if not in transition and fully faded out
            if (currentBackgroundAlpha <= 0f && !isBackgroundTransitionInProgress) {
                drawBackground = false
            }
        } else {
            // standard fade-in when not moving
            currentBackgroundAlpha =
                backgroundAlpha.coerceAtMost(currentBackgroundAlpha + alphaAnimationSpeed)
            // always enable drawing during fade-in
            drawBackground = true
        }
        GLES30.glUniform1f(uAlphaLocation, currentBackgroundAlpha)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glBindVertexArray(backgroundVAO)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            backgroundBuffers.indices.size,
            GLES30.GL_UNSIGNED_SHORT,
            0
        )
        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun renderSphere() {
        GLES30.glUseProgram(sphereProgram)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_FRONT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        worldMatrix.get(worldMatrixData)
        GLES30.glUniformMatrix4fv(uSphereWorldMatrix, 1, false, worldMatrixData, 0)

        camera.matrices.view.get(viewMatrixData)
        GLES30.glUniformMatrix4fv(uSphereViewMatrix, 1, false, viewMatrixData, 0)

        camera.matrices.projection.get(projectionMatrixData)
        GLES30.glUniformMatrix4fv(uSphereProjectionMatrix, 1, false, projectionMatrixData, 0)

        GLES30.glUniform1i(uSphereTex, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sphereTextureId)

        GLES30.glBindVertexArray(sphereVAO)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            sphereBuffers.indices.size,
            GLES30.GL_UNSIGNED_SHORT,
            0
        )
        GLES30.glBindVertexArray(0)

        GLES30.glCullFace(GLES30.GL_BACK)
    }

    private fun renderDisks() {
        if (diskProgram == 0) return

        GLES30.glUseProgram(diskProgram)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)


        worldMatrix.get(worldMatrixData)
        GLES30.glUniformMatrix4fv(uWorldMatrix, 1, false, worldMatrixData, 0)

        camera.matrices.view.get(viewMatrixData)
        GLES30.glUniformMatrix4fv(uViewMatrix, 1, false, viewMatrixData, 0)

        camera.matrices.projection.get(projectionMatrixData)
        GLES30.glUniformMatrix4fv(uProjectionMatrix, 1, false, projectionMatrixData, 0)

        GLES30.glUniform4f(
            uRotationAxisVelocity,
            arcBallControl.currentRotationAxis.x,
            arcBallControl.currentRotationAxis.y,
            arcBallControl.currentRotationAxis.z,
            rotationSpeed * 1.1f
        )

        GLES30.glUniform1i(uItemCount, itemsCount)
        GLES30.glUniform1i(uAtlasSize, atlasSize)
        GLES30.glUniform1f(uTime, ((System.currentTimeMillis() % 100_000L) / 1_000f))

        // Bind texture
        GLES30.glUniform1i(uTex, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)

        GLES30.glUniform1i(uGlowMap, 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, glowMapTextureId)

        // Draw instances
        GLES30.glBindVertexArray(diskVAO)
        GLES30.glDrawElementsInstanced(
            GLES30.GL_TRIANGLES,
            diskBuffers.indices.size,
            GLES30.GL_UNSIGNED_SHORT,
            0,
            diskInstanceCount
        )
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /**
    * @index index of the itm to glow set to -1 for none
    * */
    fun setActiveGlowingDisk(index: Int) {
        // -1 means no disk is glowing
        if (index == NO_GLOW) {
            setGlowingDisks(emptyList())
        } else {
            setGlowingDisks(listOf(index))
        }
    }

    private fun updateCamera() {
        camera.matrix.setLookAt(
            camera.position,          // Eye position
            Vector3f(0f, 0f, 0f),    // Center/target position
            camera.up                 // Up vector
        ).invert()
        camera.matrices.view.set(camera.matrix).invert()
    }

    private fun updateProjection() {
        val surfaceWidth = viewportSize.x
        val surfaceHeight = viewportSize.y
        camera.aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()

        val height = SPHERE_RADIUS * 0.4f
        val distance = camera.position.z()
        camera.fov = if (camera.aspect > 1f) {
            2f * atan(height / distance)
        } else {
            2f * atan((height / camera.aspect) / distance)
        }

        camera.matrices.projection.setPerspective(
            camera.fov,
            camera.aspect,
            camera.near,
            camera.far
        )
        camera.matrices.inverseProjection.set(camera.matrices.projection).invert()
    }

    fun handleControlStateUpdate(frameDelta: Float) {
        val timeScale = frameDelta / TARGET_FRAME_DURATION + 0.0001f
        var damping = 5f / timeScale
        var cameraTargetZ = 3f

        val isMoving = arcBallControl.isTouchDown ||
                abs(rotationSpeed) > 0.01f || arcBallControl.isForceSnapInProgress()

        if (isMoving != movementActive) {
            movementActive = isMoving
            orbitSelectionListener?.onMovementChange(isMoving)
        }

        // handle snapping to nearest item if not dragging
        if (!arcBallControl.isTouchDown && !arcBallControl.isForceSnapInProgress()) {
            val nearestVertexIndex = getNearestItemVertex()
            val snapDirection =
                calculateWorldPosition(nearestVertexIndex)
                    .normalize()
            arcBallControl.targetSnapDirection = snapDirection
        } else {

            if (arcBallControl.isForceSnapInProgress()) {
                val snapDirection = calculateWorldPosition(forceSnapTo).normalize()
                arcBallControl.targetSnapDirection = snapDirection
            }
            //TODO add option to specify how far camera gets away
            val additionalZ = 3.1f
            cameraTargetZ += arcBallControl.currentRotationVelocity * 80f + additionalZ
            damping = 7f / timeScale
        }
        // Update camera position with smooth damping
        camera.position.z += (cameraTargetZ - camera.position.z) / damping
        updateCamera()
    }

    fun getNearestItemVertex(): Int {
        if (!::arcBallControl.isInitialized) return 0
        val n = arcBallControl.defaultSnapDirection
        val inverseOrientation = Quaternionf(arcBallControl.currentOrientation).conjugate()
        val nt = Vector3f(n).rotate(inverseOrientation)

        var maxD = -1f
        var nearestVertexIndex = 0

        instancePositions.forEachIndexed { index, position ->
            val d = nt.dot(position)
            if (d > maxD) {
                maxD = d
                nearestVertexIndex = index
            }
        }

        return nearestVertexIndex
    }

    private fun calculateWorldPosition(index: Int): Vector3f {
        val nearestVertexPos = instancePositions[index]
        return Vector3f(nearestVertexPos).rotate(arcBallControl.currentOrientation)
    }

    private fun convertColorIntToFloatArray(colorInt: Int): FloatArray {
        val red = ((colorInt shr 16) and 0xFF) / 255.0f
        val green = ((colorInt shr 8) and 0xFF) / 255.0f
        val blue = (colorInt and 0xFF) / 255.0f
        val alpha = ((colorInt shr 24) and 0xFF) / 255.0f

        return floatArrayOf(red, green, blue, alpha)
    }

    fun snapToImage(index: Int) {
        forceSnapTo = index
        arcBallControl.forceSnapTo(calculateWorldPosition(index).normalize())
        //activeIndex = index
    }

    fun immediateSnapToImage(index: Int){
        forceSnapTo = index
        arcBallControl.forceSnapTo(calculateWorldPosition(index).normalize(), immediate = true)
    }

    /**
     * Calculates the screen-space rectangle containing the central disk image by properly
     * projecting the 3D position and size to screen coordinates, accounting for device density.
     *
     * @param screenWidth The width of the device screen in pixels
     * @param screenHeight The height of the device screen in pixels
     * @return A Rect object with the left, top, right, and bottom coordinates of the disk image
     */
    fun calculateDiskScreenRect(screenWidth: Int, screenHeight: Int): Rect {
        // Use the shader's diskRadius value
        val diskRadiusWorld = 0.25f * 2.0f // Shader diskRadius * 2 (diameter)

        // Create a position for the centered disk (at origin, slightly in front of camera)
        val diskPosition = Vector3f(0f, 0f, -SPHERE_RADIUS * 1.05f)

        // Convert world position to clip space
        val clipSpacePos = Vector4f(diskPosition.x, diskPosition.y, diskPosition.z, 1f)

        // Apply view and projection matrices
        clipSpacePos.mul(camera.matrices.view)
        clipSpacePos.mul(camera.matrices.projection)

        // Perspective division
        if (clipSpacePos.w != 0f) {
            clipSpacePos.x /= clipSpacePos.w
            clipSpacePos.y /= clipSpacePos.w
        }

        // Convert from clip space (-1 to 1) to screen space
        val screenCenterX = (clipSpacePos.x + 1f) * 0.5f * screenWidth
        val screenCenterY = (1f - clipSpacePos.y) * 0.5f * screenHeight // Invert Y for screen space

        // Calculate radius in screen space
        // The diskRadius needs to be projected using the same matrices
        val distanceToCamera = SPHERE_RADIUS * 1.05f
        val scale = 0.35f // From your code

        // Calculate FOV scaling factor based on distance
        val fovScaleFactor = tan(camera.fov * 0.5f) * distanceToCamera

        // Calculate the radius in screen pixels
        val diskRadiusScreen = (diskRadiusWorld * scale * screenHeight) / (2f * fovScaleFactor)

        // In the shader, disk images are scaled to fit within diskRadius
        // and there's a correction factor applied for the final display size
        val diskRadiusShaderCorrection = 5f // From shader (base size correction)

        // Calculate final screen radius
        val finalRadius = diskRadiusScreen * diskRadiusShaderCorrection

        // Calculate the bounds
        val left = (screenCenterX - finalRadius).toInt()
        val top = (screenCenterY - finalRadius).toInt()
        val right = (screenCenterX + finalRadius).toInt()
        val bottom = (screenCenterY + finalRadius).toInt()

        return Rect(left, top, right, bottom)
    }

    fun toggleImageSkewing(enable: Boolean) {
        if (!::arcBallControl.isInitialized) return
        arcBallControl.skewing = enable
    }

    /*fun calculateDiskScreenRect(screenWidth: Int, screenHeight: Int): Rect {
        // Constants from the original code
        val scale = 0.35f
        val diskRadiusWorld = 2.0f * scale // Disk base radius from DiskGeometry constructor * scale

        // Calculate the device density factor
        val displayMetrics = context.resources.displayMetrics
        val densityFactor = displayMetrics.density

        // Calculate the center of the disk on screen
        val screenCenterX = screenWidth / 2f
        val screenCenterY = screenHeight / 2f

        // Calculate distance factors based on camera parameters
        val distanceToCamera = camera.position.z + SPHERE_RADIUS * 1.05f
        val fovY = camera.fov

        // Convert world space sizes to screen space
        val halfScreenHeight = screenHeight / 2f
        val screenSpaceConversionFactor = halfScreenHeight / (distanceToCamera * tan(fovY / 2f))

        // Calculate the disk size in screen pixels
        val diskRadiusScreen = diskRadiusWorld * screenSpaceConversionFactor

        // (4f - disk radius) in fragment shader
        val baseSizeCorrection = 3.75f

        // Apply density-aware scaling
        val correctedRadius = diskRadiusScreen * baseSizeCorrection

        // Calculate the bounds
        val left = (screenCenterX - correctedRadius).toInt()
        val top = (screenCenterY - correctedRadius).toInt()
        val right = (screenCenterX + correctedRadius).toInt()
        val bottom = (screenCenterY + correctedRadius).toInt()

        *//* Log.d("DiskRect", "Screen: $screenWidth/$screenHeight, DPI: ${displayMetrics.densityDpi}, " +
                "Density: $densityFactor, Radius: $correctedRadius, " +
                "Rect: L:$left T:$top R:$right B:$bottom")*//*

        return Rect(left, top, right, bottom)
    }*/
}