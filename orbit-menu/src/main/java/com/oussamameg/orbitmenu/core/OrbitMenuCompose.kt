package com.oussamameg.orbitmenu.core

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.oussamameg.orbitmenu.listener.OrbitSelectionListener
import com.oussamameg.orbitmenu.model.OrbitMenuItem

/**
 * IMPORTANT: this is Jetpack Compose wrapper for the OrbitMenuSurfaceView.
 *
 * This composable provides a declarative API for using the orbit menu within Compose UI hierarchies.
 *
 * @param orbitMenuItems List of items to display in the orbit menu (list of OrbitMenuItem)
 * @param modifier Modifier to apply to the composable holding the surface view
 * @param activeIndex Index of the initially active menu item (-1 for none)
 * @param skewImage Whether to enable skewing effect during rotation
 * @param backgroundColor The background color for the menu default is MaterialTheme background
 * @param backgroundBitmap Optional bitmap to use as a background image
 * @param onSnapComplete Callback invoked when snap animation completes, with the selected item index
 * @param onSnapTargetReached Callback invoked when snap target is reached
 * @param onMovementChange Callback invoked when movement state changes
 * @param onImagePositioned Callback invoked with the screen position of the centered image
 */
@Composable
fun OrbitMenu(
    orbitMenuItems: List<OrbitMenuItem>,
    modifier: Modifier = Modifier,
    activeIndex: Int = -1,
    skewImage: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    backgroundBitmap: Bitmap? = null,
    onSnapComplete: (itemIndex: Int) -> Unit = { _ -> },
    onSnapTargetReached: (reached: Boolean) -> Unit = { },
    onMovementChange: (isMoving: Boolean) -> Unit = { },
    onImagePositioned: (imageRect: Rect) -> Unit = { _ -> },
) {
    val context = LocalContext.current

    val orbitMenuSurfaceView = remember { OrbitMenuSurfaceView(context) }
    var previousActiveIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(backgroundColor) {
        orbitMenuSurfaceView.bgColor = backgroundColor.toArgb()
    }

    LaunchedEffect(backgroundBitmap) {
        backgroundBitmap?.let {
            orbitMenuSurfaceView.setBackgroundImage(it)
        }
    }
    LaunchedEffect(activeIndex) {
        if (activeIndex != previousActiveIndex && activeIndex >= 0 && activeIndex < orbitMenuItems.size) {
            previousActiveIndex = activeIndex
            orbitMenuSurfaceView.snapToImage(activeIndex)
        }
    }

    LaunchedEffect(skewImage) {
        orbitMenuSurfaceView.toggleImageSkewing(skewImage)
    }

    LaunchedEffect(orbitMenuItems) {
        orbitMenuSurfaceView.setOrbitMenuItems(orbitMenuItems)

        if (activeIndex >= 0 && activeIndex < orbitMenuItems.size) {
            previousActiveIndex = activeIndex
            orbitMenuSurfaceView.snapToImage(activeIndex)
        }
    }

    DisposableEffect(
        orbitMenuSurfaceView,
        onSnapComplete,
        onSnapTargetReached,
        onMovementChange
    ) {
        val listener = object : OrbitSelectionListener {
            override fun onSnapComplete(itemIndex: Int) {
                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels
                val positionInScreen =
                    orbitMenuSurfaceView.getImageScreenPosition(screenWidth, screenHeight)

                previousActiveIndex = itemIndex
                onSnapComplete(itemIndex)
                onImagePositioned(positionInScreen)
            }

            override fun onSnapTargetReached(reached: Boolean) {
                onSnapTargetReached(reached)
            }

            override fun onMovementChange(moving: Boolean) {
                onMovementChange(moving)
            }
        }

        orbitMenuSurfaceView.orbitSelectionListener = listener

        onDispose {
            orbitMenuSurfaceView.orbitSelectionListener = null
        }
    }
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { orbitMenuSurfaceView }
        )
    }
}