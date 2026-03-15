package AppFrontend.Interface.Insights.ModelTraining

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class TrainingWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private val wavePath = Path()
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var animTime = 0f
    private var lastFrameTime = System.nanoTime()

    // --- STATE CONTROL ---
    private var isActive = false
    private var currentAmpMultiplier = 0f
    private var isStateInitialized = false

    fun setActive(active: Boolean) {
        if (!isStateInitialized) {
            this.isActive = active
            this.currentAmpMultiplier = if (active) 1f else 0f
            this.isStateInitialized = true
            postInvalidateOnAnimation()
        } else if (this.isActive != active) {
            this.isActive = active
            postInvalidateOnAnimation()
        }
    }

    // --- CONFIGURATION ---
    private val topColors = intArrayOf(
        Color.parseColor("#909090"), Color.parseColor("#606060"),
        Color.parseColor("#303030"), Color.parseColor("#121212")
    )
    private val bottomColors = intArrayOf(
        Color.parseColor("#8A181818"), Color.parseColor("#A6181818"),
        Color.parseColor("#96181818"), Color.parseColor("#FF181818")
    )

    // FIXED: Changed hardcoded pixel heights (200f) to relative percentages.
    // 1.0f = 100% of the max available height.
    private val layers = listOf(
        WaveLayer(0.8f, 0.7f, 0.95f, 0f),   // Layer 0: 95% height
        WaveLayer(1.3f, 0.9f, 0.75f, 15f),  // Layer 1: 75% height
        WaveLayer(1.5f, 1.1f, 0.55f, 35f),  // Layer 2: 55% height
        WaveLayer(1.7f, 1.3f, 0.35f, 50f)   // Layer 3: 35% height
    )

    // Notice 'amp' is now renamed to 'ampPercentage' for clarity
    private data class WaveLayer(val freq: Float, val speed: Float, val ampPercentage: Float, val offset: Float)

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
        lastFrameTime = currentTime

        // --- SMOOTH FLATLINE MATH ---
        val targetAmp = if (isActive) 1f else 0f
        currentAmpMultiplier += (targetAmp - currentAmpMultiplier) * 0.05f

        if (!isActive && currentAmpMultiplier < 0.01f) {
            currentAmpMultiplier = 0f
        }

        val isAnimating = isActive || currentAmpMultiplier > 0f
        if (isAnimating) animTime += deltaTime

        val width = width.toFloat()
        val centerY = height / 2f

        for (i in layers.indices) drawWave(canvas, i, width, centerY, animTime, true)
        for (i in layers.indices) drawWave(canvas, i, width, centerY, animTime, false)

        if (isAnimating) postInvalidateOnAnimation()
    }

    private fun drawWave(canvas: Canvas, layerIndex: Int, width: Float, centerY: Float, time: Float, isTop: Boolean) {
        val layer = layers[layerIndex]
        wavePath.reset()
        wavePath.moveTo(0f, centerY)

        wavePaint.color = if (isTop) topColors[layerIndex] else bottomColors[layerIndex]
        val direction = if (isTop) -1f else 1f

        // --- THE FIX: DYNAMIC BOUNDING ---
        // 1. Leave a 10% padding from the top/bottom edges of the View so it never clips.
        val maxAvailableHeight = centerY * 0.90f

        // 2. Calculate this specific layer's allowed height based on its percentage
        val currentLayerMaxPx = maxAvailableHeight * layer.ampPercentage * currentAmpMultiplier

        val baseThickness = 2f * density * direction

        for (x in 0..width.toInt() step 5) {
            val xf = x.toFloat()
            val normalizedX = (xf / width * 10f) + layer.offset

            // 3. NORMALIZE THE SINE WAVES:
            // Your three sine waves have multipliers of 1.0, 0.8, and 0.3.
            // The maximum possible value if they all peak together is ~2.1.
            // By dividing by 2.1f, we guarantee the result is always between -1.0 and 1.0!
            val rawSineSum = (
                    sin(normalizedX * layer.freq + time * layer.speed) +
                            sin(normalizedX * (layer.freq * 1.2f) + time * (layer.speed * 0.8f)) * 0.8f +
                            sin(normalizedX * (layer.freq * 2.5f) + time * (layer.speed * 1.2f)) * 0.3f
                    ) / 2.1f

            // 4. Multiply our normalized sine (-1 to 1) by our safely calculated pixel limit
            val yOffset = rawSineSum * currentLayerMaxPx * direction

            wavePath.lineTo(xf, centerY + yOffset + baseThickness)
        }

        wavePath.lineTo(width, centerY)
        wavePath.close()
        canvas.drawPath(wavePath, wavePaint)
    }
}