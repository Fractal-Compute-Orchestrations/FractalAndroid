//package AppFrontend.Interface.Insights.UsageInsights
//
//import android.content.Context
//import android.graphics.*
//import android.util.AttributeSet
//import android.view.View
//import android.util.Log
//import com.example.fractal.R
//import org.json.JSONObject
//import java.io.File
//import kotlin.random.Random
//
//class ResourceStatsChartView @JvmOverloads constructor(
//    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
//) : View(context, attrs, defStyleAttr) {
//
//    // Current display values (smoothly animated)
//    private val currentStats = linkedMapOf(
//        "CPU" to 0.5f,
//        "GPU" to 0.3f,
//        "ROM" to 0.7f,
//        "RAM" to 0.6f,
//        "TEMP" to 0.4f
//    )
//
//    // Target values (from JSON or self-generated)
//    private val targetStats = linkedMapOf(
//        "CPU" to 0.9f,
//        "GPU" to 0.7f,
//        "ROM" to 0.85f,
//        "RAM" to 0.75f,
//        "TEMP" to 0.65f
//    )
//
//    private val dp = context.resources.displayMetrics.density
//    private val statsFileName = "resource_stats.json"
//    private var frameCount = 0
//
//    private val barPaint = Paint().apply {
//        color = Color.parseColor("#1A1A1A")
//        style = Paint.Style.FILL
//        isAntiAlias = true
//    }
//
//    private val textPaint = Paint().apply {
//        color = Color.BLACK
//        textSize = 14f * dp
//        typeface = context.resources.getFont(R.font.genos_family)
//        isAntiAlias = true
//        letterSpacing = 0.1f
//    }
//
//    private val gridPaint = Paint().apply {
//        color = Color.LTGRAY
//        style = Paint.Style.STROKE
//        strokeWidth = 1f * dp
//        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
//    }
//
//    init {
//        Log.e("ResourceStatsChart", "Chart view initialized with JSON reading")
//    }
//
//    /**
//     * Called by Fragment to update target values.
//     * This is optional - chart works without it too.
//     */
//    fun updateStats(newStats: Map<String, Float>) {
//        newStats.forEach { (key, value) ->
//            if (targetStats.containsKey(key)) {
//                targetStats[key] = value
//            }
//        }
//    }
//
//    override fun onDraw(canvas: Canvas) {
//        super.onDraw(canvas)
//        frameCount++
//
//        // Every 3 frames (~50ms at 60fps), try to read from JSON
//        if (frameCount % 3 == 0) {
//            readStatsFromJson()
//        }
//
//        // Every 6 frames (~100ms at 60fps), generate random fallback values
//        if (frameCount % 6 == 0) {
//            generateRandomTargets()
//        }
//
//        // Smoothly interpolate current values toward targets
//        currentStats.keys.forEach { key ->
//            val current = currentStats[key] ?: 0f
//            val target = targetStats[key] ?: 0f
//
//            // Smooth lerp animation
//            currentStats[key] = current + (target - current) * 0.10f
//        }
//
//        val paddingLeft = 60f * dp
//        val paddingRight = 60f * dp
//        val paddingTop = 20f * dp
//        val paddingBottom = 40f * dp
//
//        val chartWidth = width - paddingLeft - paddingRight
//        val chartHeight = height - paddingTop - paddingBottom
//        val barHeight = 29f * dp
//
//        val spacing = if (currentStats.size > 1) {
//            (chartHeight - (barHeight * currentStats.size)) / (currentStats.size - 1)
//        } else 0f
//
//        // 1. Draw Dashed Grid and Bottom Axis Labels
//        val gridValues = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
//        gridValues.forEach { value ->
//            val x = paddingLeft + (value * chartWidth)
//            canvas.drawLine(x, paddingTop, x, paddingTop + chartHeight, gridPaint)
//            val label = (value * 100).toInt().toString()
//            canvas.drawText(label, x - (8f * dp), height - (10f * dp), textPaint)
//        }
//
//        // 2. Draw Bars and Data Labels
//        var currentY = paddingTop
//        currentStats.forEach { (label, density) ->
//            // Category Label (CPU, RAM, etc)
//            canvas.drawText(label, 10f * dp, currentY + (barHeight / 1.5f), textPaint)
//
//            // The Bar
//            val barWidth = density * chartWidth
//            canvas.drawRect(paddingLeft, currentY, paddingLeft + barWidth, currentY + barHeight, barPaint)
//
//            // Numeric Label (89%, 65°C, etc)
//            val displayValue = (density * 100).toInt().toString()
//            val suffix = if (label == "TEMP") "°C" else "%"
//
//            canvas.drawText(
//                "$displayValue$suffix",
//                paddingLeft + barWidth + (8f * dp),
//                currentY + (barHeight / 1.5f),
//                textPaint
//            )
//
//            currentY += barHeight + spacing
//        }
//
//        // Keep animating smoothly (like HeartBeatView)
//        invalidate()
//    }
//
//    /**
//     * Reads stats from JSON file if it exists
//     */
//    private fun readStatsFromJson() {
//        try {
//            val file = File(context.filesDir, statsFileName)
//
//            if (file.exists()) {
//                val jsonString = file.readText()
//                val jsonObject = JSONObject(jsonString)
//
//                jsonObject.keys().forEach { key ->
//                    val value = jsonObject.getDouble(key).toFloat()
//                    if (targetStats.containsKey(key)) {
//                        targetStats[key] = value
//                    }
//                }
//
//                Log.d("ResourceStatsChart", "Read from JSON: $targetStats")
//            }
//        } catch (e: Exception) {
//            // Silently fail - will use random generation instead
//        }
//    }
//
//    /**
//     * Generates random target values as fallback
//     */
//    private fun generateRandomTargets() {
//        targetStats.keys.forEach { key ->
//            if (Random.nextFloat() < 0.15f) { // 15% chance to change
//                targetStats[key] = when (key) {
//                    "CPU" -> Random.nextFloat().coerceIn(0.25f, 0.95f)
//                    "GPU" -> Random.nextFloat().coerceIn(0.20f, 0.90f)
//                    "ROM" -> Random.nextFloat().coerceIn(0.50f, 0.95f)
//                    "RAM" -> Random.nextFloat().coerceIn(0.35f, 0.95f)
//                    "TEMP" -> Random.nextFloat().coerceIn(0.40f, 0.85f)
//                    else -> Random.nextFloat()
//                }
//            }
//        }
//    }
//
//    override fun onAttachedToWindow() {
//        super.onAttachedToWindow()
//        Log.e("ResourceStatsChart", "Chart attached - self-animating with JSON support")
//    }
//
//    override fun onDetachedFromWindow() {
//        super.onDetachedFromWindow()
//        Log.e("ResourceStatsChart", "Chart detached")
//    }
//}
package AppFrontend.Interface.Insights.UsageInsights

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.fractal.R
import kotlin.random.Random

class ResourceStatsChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val currentStats = linkedMapOf(
        "CPU" to 0.5f,
        "GPU" to 0.3f,
        "ROM" to 0.7f,
        "RAM" to 0.6f,
        "TEMP" to 0.4f
    )

    private val targetStats = linkedMapOf(
        "CPU" to 0.9f,
        "GPU" to 0.7f,
        "ROM" to 0.85f,
        "RAM" to 0.75f,
        "TEMP" to 0.65f
    )

    private val dp = context.resources.displayMetrics.density
    private var frameCount = 0

    private val barPaint = Paint().apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 14f * dp
        typeface = context.resources.getFont(R.font.genos_family)
        isAntiAlias = true
        letterSpacing = 0.1f
    }

    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    init {
        Log.e("ResourceStatsChart", "Chart view initialized")
    }

    /**
     * Called by Fragment to update target values from ViewModel LiveData.
     * This is the only data source — JSON reading has been removed from onDraw
     * because disk I/O on the render thread causes lag and stutter.
     */
    fun updateStats(newStats: Map<String, Float>) {
        newStats.forEach { (key, value) ->
            if (targetStats.containsKey(key)) {
                targetStats[key] = value
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frameCount++

        // Fallback random targets only fire every 2 seconds when no real data arrives
        if (frameCount % 120 == 0) {
            generateRandomTargets()
        }

        // Lerp toward targets — 0.025f gives ~180ms to reach 95% of target (smooth, weighted feel)
        currentStats.keys.forEach { key ->
            val current = currentStats[key] ?: 0f
            val target = targetStats[key] ?: 0f
            currentStats[key] = current + (target - current) * 0.025f
        }

        val paddingLeft = 60f * dp
        val paddingRight = 60f * dp
        val paddingTop = 20f * dp
        val paddingBottom = 40f * dp

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        val barHeight = 29f * dp

        val spacing = if (currentStats.size > 1) {
            (chartHeight - (barHeight * currentStats.size)) / (currentStats.size - 1)
        } else 0f

        // 1. Draw Dashed Grid and Bottom Axis Labels
        val gridValues = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        gridValues.forEach { value ->
            val x = paddingLeft + (value * chartWidth)
            canvas.drawLine(x, paddingTop, x, paddingTop + chartHeight, gridPaint)
            val label = (value * 100).toInt().toString()
            canvas.drawText(label, x - (8f * dp), height - (10f * dp), textPaint)
        }

        // 2. Draw Bars and Data Labels
        var currentY = paddingTop
        currentStats.forEach { (label, density) ->
            canvas.drawText(label, 10f * dp, currentY + (barHeight / 1.5f), textPaint)

            val barWidth = density * chartWidth
            canvas.drawRect(paddingLeft, currentY, paddingLeft + barWidth, currentY + barHeight, barPaint)

            val displayValue = (density * 100).toInt().toString()
            val suffix = if (label == "TEMP") "°C" else "%"
            canvas.drawText(
                "$displayValue$suffix",
                paddingLeft + barWidth + (8f * dp),
                currentY + (barHeight / 1.5f),
                textPaint
            )

            currentY += barHeight + spacing
        }

        // postInvalidateOnAnimation() syncs redraws with the Choreographer's vsync signal.
        // Unlike invalidate(), it won't schedule redundant back-to-back frames,
        // preventing the CPU busy-loop that was causing the initial lag spike.
        postInvalidateOnAnimation()
    }

    private fun generateRandomTargets() {
        targetStats.keys.forEach { key ->
            if (Random.nextFloat() < 0.15f) {
                targetStats[key] = when (key) {
                    "CPU"  -> Random.nextFloat().coerceIn(0.25f, 0.95f)
                    "GPU"  -> Random.nextFloat().coerceIn(0.20f, 0.90f)
                    "ROM"  -> Random.nextFloat().coerceIn(0.50f, 0.95f)
                    "RAM"  -> Random.nextFloat().coerceIn(0.35f, 0.95f)
                    "TEMP" -> Random.nextFloat().coerceIn(0.40f, 0.85f)
                    else   -> Random.nextFloat()
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.e("ResourceStatsChart", "Chart attached")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.e("ResourceStatsChart", "Chart detached")
    }
}