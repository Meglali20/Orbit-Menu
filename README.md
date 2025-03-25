# OrbitMenu: 3D Circular Navigation for Android

OrbitMenu is a cutting-edge Android library that transforms menu navigation with an immersive 3D orbital interface. Create engaging, interactive user experiences with smooth circular menu interactions.

## Features

- Smooth 3D orbital rotation for menu items
- Interactive item selection with callbacks
- Customizable item glow effects
- Custom background support
- Works with both Jetpack Compose and traditional Android Views
- Smooth animations with physics-based movement

## Installation

Add the dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation ("io.github.meglali20:orbit-menu:1.0.0")
}
```

## Usage

### Jetpack Compose Example

```kotlin
@Composable
fun OrbitMenuExample() {
    var activeIndex by remember { mutableStateOf(0) }
    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val orbitMenuItems = remember {
        listOf(
            OrbitMenuItem(0, loadBitmap(context, R.drawable.item1), Color.Red.toArgb()),
            OrbitMenuItem(1, loadBitmap(context, R.drawable.item2), Color.Blue.toArgb()),
            OrbitMenuItem(2, loadBitmap(context, R.drawable.item3), Color.Green.toArgb())
        )
    }

    OrbitMenu(
        orbitMenuItems = orbitMenuItems,
        backgroundBitmap = backgroundBitmap,
        skewImage = true,
        activeIndex = activeIndex,
        onSnapComplete = { index ->
            activeIndex = index
            Log.d("OrbitMenu", "Selected index: $activeIndex")

            coroutineScope.launch {
                val newBackgroundImage = loadBitmapWithGlide(
                    context,
                    imageInfoList[index].backgroundImageUrl,
                    400,
                    600
                )
                backgroundBitmap = newBackgroundImage
            }
        },
        onMovementChange = { isMoving ->
            Log.d("OrbitMenu", "Menu moving: $isMoving")
        }
    )
}
```

### Traditional Android Views

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var orbitMenu: OrbitMenuSurfaceView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        orbitMenu = findViewById(R.id.orbit_menu)
        
        val menuItems = listOf(
            OrbitMenuItem(0, loadBitmap(R.drawable.item1), Color.RED),
            OrbitMenuItem(1, loadBitmap(R.drawable.item2), Color.BLUE)
        )
        
        orbitMenu.setOrbitMenuItems(menuItems)
    }
}
```

## Important Notes

### Jetpack Compose Wrapper

The `OrbitMenu` Composable is a thin wrapper around the `OrbitMenuSurfaceView`. It provides a declarative API for integrating the orbit menu into Compose UI hierarchies. Key components include:

- Declarative item management
- Reactive state handling
- Lifecycle-aware configuration
- Callback support for menu interactions


### Item Snap
You can select a desired item like this: 
```kotlin
orbitMenu.snapTo(itemIndex)
```

## License

```
Copyright 2024 [Oussama Meglali]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.