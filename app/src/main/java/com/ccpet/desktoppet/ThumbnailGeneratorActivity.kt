package com.ccpet.desktoppet

import android.graphics.Bitmap
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.filament.Camera
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import java.io.File

/**
 * Debug helper to generate a PNG thumbnail from a glb in assets/models/.
 *
 * Start via adb:
 * adb shell am start -n com.ccpet.desktoppet/.ThumbnailGeneratorActivity --es assetPath "models/BabyDragon.glb"
 *
 * Output: Android/data/<package>/files/shop_thumbs/<safe>.png
 */
class ThumbnailGeneratorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDebuggable =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            finish()
            return
        }

        val assetPath = intent.getStringExtra(EXTRA_ASSET_PATH)
            ?: run {
                Toast.makeText(this, "missing assetPath", Toast.LENGTH_LONG).show()
                finish()
                return
            }

        val host = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(THUMB_SIZE_PX, THUMB_SIZE_PX)
        }
        setContentView(host)

        val sceneView = SceneView(this, isOpaque = true).apply {
            layoutParams = FrameLayout.LayoutParams(THUMB_SIZE_PX, THUMB_SIZE_PX)
            cameraManipulator = null
        }
        host.addView(sceneView)

        val profile = ModelDisplayProfiles.forAsset(assetPath)
        sceneView.modelLoader.loadModelInstanceAsync(assetPath) { instance ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (instance == null) {
                    Toast.makeText(this, "load failed: $assetPath", Toast.LENGTH_LONG).show()
                    finish()
                    return@runOnUiThread
                }

                val node = ModelNode(
                    modelInstance = instance,
                    autoAnimate = true,
                    scaleToUnits = profile.shopScaleToUnits,
                    centerOrigin = Position(x = 0f, y = 0f, z = 0f)
                )
                node.scale = Scale(profile.shopManualScale)
                node.position = Position(
                    x = profile.shopOffsetX,
                    y = profile.shopOffsetY,
                    z = profile.shopOffsetZ
                )
                node.rotation = Rotation(
                    x = profile.shopRotationX,
                    y = profile.shopRotationY,
                    z = profile.shopRotationZ
                )
                sceneView.addChildNode(node)

                sceneView.cameraNode.apply {
                    position = Position(x = 0f, y = profile.shopCameraY, z = profile.shopCameraZ)
                    setProjection(
                        fovInDegrees = profile.shopCameraFov.toDouble(),
                        near = 0.01f,
                        far = 2000f,
                        direction = Camera.Fov.VERTICAL
                    )
                }

                // Give Filament a short moment to render stable frames.
                sceneView.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    captureHostBitmap(host) { bitmap ->
                        if (bitmap == null) {
                            Toast.makeText(this, "capture failed", Toast.LENGTH_LONG).show()
                            finish()
                            return@captureHostBitmap
                        }
                        val outFile = outputFileFor(assetPath)
                        runCatching {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { os ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                            }
                        }.onFailure {
                            Toast.makeText(this, "write failed: ${it.message}", Toast.LENGTH_LONG).show()
                            finish()
                            return@captureHostBitmap
                        }
                        Toast.makeText(this, "saved: ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }, 900L)
            }
        }
    }

    private fun captureHostBitmap(host: FrameLayout, onResult: (Bitmap?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            onResult(null)
            return
        }
        val decor = window.decorView
        if (decor.width <= 0 || decor.height <= 0 || host.width <= 0 || host.height <= 0) {
            onResult(null)
            return
        }
        val full = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        host.getLocationInWindow(location)
        val srcRect = Rect(
            location[0],
            location[1],
            location[0] + host.width,
            location[1] + host.height
        )
        val thread = HandlerThread("thumb-pixel-copy").apply { start() }
        PixelCopy.request(window, full, { result ->
            val out = if (result == PixelCopy.SUCCESS) {
                Bitmap.createBitmap(
                    full,
                    srcRect.left.coerceAtLeast(0),
                    srcRect.top.coerceAtLeast(0),
                    srcRect.width().coerceAtMost(full.width - srcRect.left.coerceAtLeast(0)),
                    srcRect.height().coerceAtMost(full.height - srcRect.top.coerceAtLeast(0))
                )
            } else {
                null
            }
            thread.quitSafely()
            onResult(out)
        }, Handler(thread.looper))
    }

    private fun outputFileFor(assetPath: String): File {
        val safe = assetPath.replace("/", "_").replace("\\", "_").removeSuffix(".glb")
        val dir = getExternalFilesDir(null) ?: filesDir
        return File(dir, "shop_thumbs/$safe.png")
    }

    private companion object {
        const val EXTRA_ASSET_PATH = "assetPath"
        const val THUMB_SIZE_PX = 512
    }
}

