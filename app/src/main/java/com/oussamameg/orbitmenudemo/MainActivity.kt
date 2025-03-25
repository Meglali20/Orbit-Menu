package com.oussamameg.orbitmenudemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.oussamameg.orbitmenu.core.OrbitMenu
import com.oussamameg.orbitmenudemo.ui.theme.OrbitMenuTheme
import com.oussamameg.orbitmenu.core.OrbitMenuSurfaceView
import com.oussamameg.orbitmenu.model.OrbitMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // val orbitMenuSurfaceView = OrbitMenuSurfaceView(this)
        val imageInfoList = (0 until 16).map { index ->
            ImageInfo(
                imageUrl = "https://picsum.photos/600/400?random=${index + 1}",
                backgroundImageUrl = "https://picsum.photos/400/600?random=${index + 1}&grayscale&blur=1",
                glowingColor = when (index) {
                    1 -> Color(0xB7FF7979).toArgb()
                    4 -> Color(0xB779FFAA).toArgb()
                    6 -> Color(0xB78279FF).toArgb()
                    8 -> Color.LightGray.toArgb()
                    10 -> OrbitMenuSurfaceView.DEFAULT_GLOW_COLOR
                    else -> OrbitMenuSurfaceView.NO_GLOW
                }
            )
        }
        setContent {
            OrbitMenuTheme {
                val context = LocalContext.current
                var activeIndex by remember { mutableIntStateOf(-1) }
                var progressSlider by remember { mutableFloatStateOf(0f) }
                val sheetState = rememberModalBottomSheetState()
                var showBottomSheet by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(true) }
                var isMoving by remember { mutableStateOf(true) }
                val coroutineScope = rememberCoroutineScope()
                var imageSkewing by remember { mutableStateOf(true) }

                val imageRect by remember { mutableStateOf(Rect()) }

                var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var orbitMenuItems by remember { mutableStateOf<List<OrbitMenuItem>>(emptyList()) }

                val animationDuration = 200
                val buttonScale by animateFloatAsState(
                    targetValue = if (isMoving) 0.6f else 1f,
                    label = "buttonScale",
                    animationSpec = tween(durationMillis = animationDuration)
                )
                val buttonAlpha by animateFloatAsState(
                    targetValue = if (isMoving) 0f else 1f,
                    label = "buttonAlpha",
                    animationSpec = tween(durationMillis = animationDuration)
                )

                val textOffsetY by animateDpAsState(
                    targetValue = if (isMoving) 20.dp else 0.dp,
                    label = "textOffsetY",
                    animationSpec = tween(durationMillis = animationDuration)
                )
                val textAlpha by animateFloatAsState(
                    targetValue = if (isMoving) 0f else 1f,
                    label = "textAlpha",
                    animationSpec = tween(durationMillis = animationDuration)
                )

                val configuration = LocalConfiguration.current
                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        orbitMenuItems = loadOrbitMenuItems(
                            context = context,
                            imageInfoList = imageInfoList,
                            width = 512,
                            height = 512
                        )
                    }
                    /*val orbitMenuItems = loadOrbitMenuItems(
                        context = context,
                        imageInfoList = imageInfoList,
                        width = 512,
                        height = 512
                    )
                    Log.d("Test", "Image has been loaded ${orbitMenuItems.size}")
                    orbitMenuSurfaceView.setOrbitMenuItems(orbitMenuItems)
                    //delay(5000)
                    //orbitMenuSurfaceView.snapToImage(5)
                    orbitMenuSurfaceView.orbitSelectionListener = object : OrbitSelectionListener {
                        override fun onSnapComplete(itemIndex: Int) {
                            activeIndex = itemIndex
                            progressSlider = itemIndex.toFloat()
                            coroutineScope.launch {
                                val backgroundImage = loadBitmapWithGlide(
                                    context,
                                    imageInfoList[itemIndex].backgroundImageUrl,
                                    400,
                                    600
                                )
                                orbitMenuSurfaceView.setBackgroundImage(backgroundImage)
                            }
                            Log.d("onSnapComplete", "-Snap complete for item: $itemIndex")
                            val width = context.resources.displayMetrics.widthPixels
                            val height = context.resources.displayMetrics.heightPixels
                            val positionInScreen =
                                orbitMenuSurfaceView.getImageScreenPosition(width, height)
                            imageRect.set(positionInScreen)
                            Log.e(
                                "Position",
                                "getImageScreenPosition screen size $width/$height " +
                                        "left: ${positionInScreen.left} top: ${positionInScreen.top} right: ${positionInScreen.right} bottom: ${positionInScreen.bottom}}"
                            )
                        }

                        override fun onSnapTargetReached(reached: Boolean) {
                            Log.d("Update", "-REACHED? $reached")
                        }

                        override fun onMovementChange(moving: Boolean) {
                            Log.d("onMovementChange", "-onMovementChange: $isMoving")
                            isMoving = moving
                        }
                    }*/
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        /*AndroidView(
                            modifier = Modifier
                                .padding(0.dp)
                                .fillMaxSize(),
                            factory = {
                                orbitMenuSurfaceView
                            }
                        )*/
                        OrbitMenu(
                            orbitMenuItems = orbitMenuItems,
                            backgroundBitmap = backgroundBitmap,
                            skewImage = imageSkewing,
                            activeIndex = activeIndex,
                            onSnapComplete = { index ->
                                activeIndex = index
                                Log.d("Update", "-Selected index:? $activeIndex")
                                progressSlider = index.toFloat()

                                coroutineScope.launch {
                                    val backgroundImage = loadBitmapWithGlide(
                                        context,
                                        imageInfoList[index].backgroundImageUrl,
                                        400,
                                        600
                                    )
                                    backgroundBitmap = backgroundImage
                                }
                            },
                            onSnapTargetReached = { reached ->
                                Log.d("Update", "-Reached? $reached")
                            },
                            onMovementChange = { moving ->
                                Log.d("onMovementChange", "-onMovementChange: $isMoving")
                                isMoving = moving
                            },
                            onImagePositioned = { rect ->
                                imageRect.set(rect)
                            }
                        )
                        /*Box(
                            modifier = Modifier
                                .offset(
                                    x = imageRect.left.toDp(),
                                    y = imageRect.top.toDp()
                                )
                                .size(
                                    width = (imageRect.width()).toDp(),
                                    height = (imageRect.height()).toDp()
                                )
                                .border(
                                    width = 4.dp,
                                    color = Color.Red,
                                    shape = RectangleShape
                                )
                        )*/
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (showSettings) {
                                IconButton(
                                    modifier = Modifier
                                        .padding(horizontal = 0.dp, vertical = 10.dp)
                                        .align(Alignment.BottomEnd)
                                        .background(
                                            color = Color(0x833D3B3B),
                                            shape = RoundedCornerShape(
                                                topStart = 50.dp,
                                                bottomStart = 50.dp,
                                                topEnd = 0.dp,
                                                bottomEnd = 0.dp
                                            )
                                        ), onClick = {
                                        showBottomSheet = !showBottomSheet
                                    }) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = -configuration.screenHeightDp.dp * 0.15f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                OutlinedButton(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .scale(buttonScale)
                                        .alpha(buttonAlpha)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        ),
                                    shape = CircleShape,
                                    border = BorderStroke(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.background
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                                    onClick = {
                                        Toast.makeText(
                                            context,
                                            "Current active index: $activeIndex",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Icon(
                                        modifier = Modifier
                                            .rotate(-45f),
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null
                                    )
                                }

                                Spacer(modifier = Modifier.size(ButtonDefaults.IconSize * 1.5f))

                                Text(
                                    text = "Explore",
                                    modifier = Modifier
                                        .offset(y = textOffsetY)
                                        .alpha(textAlpha)
                                )
                            }

                        }

                        if (showBottomSheet) {
                            ModalBottomSheet(
                                onDismissRequest = {
                                    showBottomSheet = false
                                },
                                sheetState = sheetState
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    SwitchToggle("Image Skewing", imageSkewing) {
                                        imageSkewing = it
                                    }
                                    Text(text = if (isMoving) "Moving to target item..." else "Current Active item $activeIndex")
                                    Slider(
                                        modifier = Modifier.padding(20.dp),
                                        value = progressSlider,
                                        valueRange = 0f..imageInfoList.size.toFloat() - 1,
                                        steps = imageInfoList.size,
                                        onValueChange = {
                                            progressSlider = it
                                            activeIndex = it.toInt()
                                        },
                                        enabled = true
                                    )
                                }

                            }
                        }


                    }
                }
            }
        }
    }


    @Composable
    fun SwitchToggle(text: String, checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
        Row(
            modifier = Modifier
                .padding(horizontal = 5.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }

}


data class ImageInfo(
    val imageUrl: String,
    val backgroundImageUrl: String,
    val glowingColor: Int
)


suspend fun loadOrbitMenuItems(
    context: Context,
    imageInfoList: List<ImageInfo>,
    width: Int = 512,
    height: Int = 512
): List<OrbitMenuItem> = withContext(Dispatchers.IO) {
    val deferredBitmaps = imageInfoList.mapIndexed { index, imageInfo ->
        async {
            val bitmap = try {
                Log.d("ImageLoader", "Before Starting to load image: ${imageInfo.imageUrl}")
                loadBitmapWithGlide(context, imageInfo.imageUrl, width, height)
            } catch (e: Exception) {
                Log.e("ImageLoader", "Failed to load image ${imageInfo.imageUrl}: ${e.message}")
                createErrorBitmap(width, height, "Error ${e.message}")
            }

            Triple(index, bitmap, imageInfo.glowingColor)
        }
    }

    val results = deferredBitmaps.awaitAll()

    return@withContext results.map { (index, bitmap, glowingColor) ->
        OrbitMenuItem(index, bitmap, glowingColor)
    }
}


private suspend fun loadBitmapWithGlide(
    context: Context,
    url: String,
    width: Int,
    height: Int
): Bitmap = suspendCoroutine { continuation ->
    Log.d("ImageLoader", "Starting to load image: $url")

    val target = object : CustomTarget<Bitmap>(width, height) {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            Log.d("ImageLoader", "Image loaded successfully: $url ${resource.byteCount}")
            continuation.resume(resource)
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            Log.e("ImageLoader", "Failed to load image: $url")
            val errorBitmap = createErrorBitmap(width, height, "Error")
            continuation.resume(errorBitmap)
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            Log.d("ImageLoader", "Load cleared: $url")
        }
    }


    val uniqueUrl = if (url.contains("?")) {
        "$url&cacheBust=${System.currentTimeMillis()}"
    } else {
        "$url?cacheBust=${System.currentTimeMillis()}"
    }

    Glide.with(context)
        .asBitmap()
        .load(uniqueUrl)
        .apply(
            RequestOptions()
                .override(width, height)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
        )
        .into(target)
}

private fun createErrorBitmap(
    width: Int,
    height: Int,
    errorMessage: String
): Bitmap {
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    canvas.drawColor(Color.LightGray.toArgb())

    val paint = Paint().apply {
        color = Color.Red.toArgb()
        textSize = 48f
        textAlign = Paint.Align.CENTER
    }

    canvas.drawText(errorMessage, width / 2f, height / 2f, paint)

    return bitmap
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OrbitMenuTheme {
        Greeting("Android")
    }
}