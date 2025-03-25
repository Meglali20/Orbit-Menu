package com.oussamameg.orbitmenu.model

import android.graphics.Bitmap
import androidx.annotation.Keep

/**
 * Data class representing an item in the orbit menu.
 *
 * @property index The unique index of this item in the menu
 * @property image The bitmap image to display for this menu item (the image mustn't be with huge size to avoid OOM also the image isn't recycled later on
 * @property glowingColor The color to use when this item is glowing, DEFAULT_GLOW_COLOR or NO_GLOW (-1) for no glow
 */
@Keep
data class OrbitMenuItem(
    val index: Int,
    val image: Bitmap,
    val glowingColor: Int
)