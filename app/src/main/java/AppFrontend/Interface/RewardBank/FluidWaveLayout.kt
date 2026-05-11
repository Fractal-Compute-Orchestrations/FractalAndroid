//package AppFrontend.Interface.RewardBank
//
//import android.content.Context
//import android.graphics.Canvas
//import android.graphics.Color
//import android.graphics.Paint
//import android.graphics.Path
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//import android.util.AttributeSet
//import android.view.Choreographer
//import android.widget.FrameLayout
//import kotlin.math.abs
//import kotlin.math.cos
//import kotlin.math.sin
//import kotlin.random.Random
//
//class FluidWaveLayout @JvmOverloads constructor(
//    context: Context,
//    attrs: AttributeSet? = null,
//    defStyleAttr: Int = 0
//) : FrameLayout(context, attrs, defStyleAttr), SensorEventListener {
//
//    // --- NEW: Physics-Based Target Tracking ---
//    var targetVaporPercentage: Float = 0f
//        set(value) {
//            if (isFirstVaporSet) {
//                currentVaporPercentage = value // Snap instantly on first load
//                isFirstVaporSet = false
//            }
//            field = value
//        }
//
//    var targetLiquidPercentage: Float = 0f
//        set(value) {
//            if (isFirstLiquidSet) {
//                currentLiquidPercentage = value // Snap instantly on first load
//                isFirstLiquidSet = false
//            }
//            field = value
//        }
//
//    private var isFirstVaporSet = true
//    private var isFirstLiquidSet = true
//
//    // Internal physics states
//    private var currentVaporPercentage: Float = 0f
//    private var currentLiquidPercentage: Float = 0f
//
//    private val depthFactor: Float
//        get() = Math.min(1f, 0.1f + (currentVaporPercentage * 6.0f))
//
//    // Liquid Paints (Verified - Solid Black)
//    private val paintLiquidFront = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        color = Color.parseColor("#000000")
//        style = Paint.Style.FILL
//    }
//    private val paintLiquidBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        color = Color.parseColor("#80000000")
//        style = Paint.Style.FILL
//    }
//
//    // Vapor Paints (Unverified - Shimmery Silver/Grey)
//    private val paintVaporFront = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        color = Color.parseColor("#809E9E9E")
//        style = Paint.Style.FILL
//    }
//    private val paintVaporBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        color = Color.parseColor("#409E9E9E")
//        style = Paint.Style.FILL
//    }
//
//    private val pathLiquidFront = Path()
//    private val pathLiquidBack = Path()
//    private val pathVaporFront = Path()
//    private val pathVaporBack = Path()
//
//    private var phaseShift = 0f
//    private var lastFrameTime = 0L
//
//    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
//    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
//
//    private var targetTiltOffset = 0f
//    private var currentTiltOffset = 0f
//
//    private var targetVolatility = 1f
//    private var currentVolatility = 1f
//
//    private var lastAccel = 9.81f
//    private var vaporBaseHeight = 0f
//    private var liquidBaseHeight = 0f
//
//    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var radius: Float, var isFront: Boolean) {
//        var active = false
//    }
//    private val maxParticles = 40
//    private val particles = Array(maxParticles) { Particle(0f, 0f, 0f, 0f, 0f, true) }
//
//    private val frameCallback = object : Choreographer.FrameCallback {
//        override fun doFrame(frameTimeNanos: Long) {
//            if (lastFrameTime == 0L) {
//                lastFrameTime = frameTimeNanos
//                Choreographer.getInstance().postFrameCallback(this)
//                return
//            }
//            var dt = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
//            lastFrameTime = frameTimeNanos
//            if (dt > 0.05f) dt = 0.05f
//
//            updatePhysics(dt * 60f)
//            invalidate()
//            Choreographer.getInstance().postFrameCallback(this)
//        }
//    }
//
//    init {
//        setWillNotDraw(false)
//    }
//
//    override fun onAttachedToWindow() {
//        super.onAttachedToWindow()
//        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
//        lastFrameTime = 0L
//        Choreographer.getInstance().postFrameCallback(frameCallback)
//    }
//
//    override fun onDetachedFromWindow() {
//        super.onDetachedFromWindow()
//        sensorManager.unregisterListener(this)
//        Choreographer.getInstance().removeFrameCallback(frameCallback)
//    }
//
//    private fun updatePhysics(timeScale: Float) {
//        phaseShift += 0.01047f * timeScale
//        if (phaseShift > Math.PI * 2) phaseShift -= (Math.PI.toFloat() * 2)
//
//        // --- NEW: Continuous organic fluid rising ---
//        // This makes the water chase the target continuously instead of stepping
//        currentVaporPercentage += (targetVaporPercentage - currentVaporPercentage) * Math.min(0.015f * timeScale, 1f)
//        currentLiquidPercentage += (targetLiquidPercentage - currentLiquidPercentage) * Math.min(0.025f * timeScale, 1f)
//
//        currentTiltOffset += (targetTiltOffset - currentTiltOffset) * Math.min(0.08f * timeScale, 1f)
//        currentVolatility += (targetVolatility - currentVolatility) * Math.min(0.05f * timeScale, 1f)
//
//        if (targetVolatility > 1f) {
//            targetVolatility -= 0.02f * timeScale
//            if (targetVolatility < 1f) targetVolatility = 1f
//        }
//
//        for (p in particles) {
//            if (p.active) {
//                p.vy += 1.5f * timeScale
//                p.x += p.vx * timeScale
//                p.y += p.vy * timeScale
//                if (p.y > height || p.y > vaporBaseHeight + 50f) p.active = false
//            }
//        }
//    }
//
//    override fun onSensorChanged(event: SensorEvent?) {
//        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
//            val ax = event.values[0]
//            val ay = event.values[1]
//            val az = event.values[2]
//
//            targetTiltOffset = ax * 35f * depthFactor
//            val currentAccel = Math.sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
//            val jerk = abs(currentAccel - lastAccel)
//
//            if (jerk > 2.5f) {
//                targetVolatility = Math.min(targetVolatility + (jerk * 0.4f * depthFactor), 1f + (4f * depthFactor))
//                spawnSplatters()
//            }
//            lastAccel = currentAccel
//        }
//    }
//
//    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
//
//    private fun spawnSplatters() {
//        if (vaporBaseHeight == 0f || currentVaporPercentage < 0.02f) return
//        val splattersToSpawn = Random.nextInt(2, 6)
//        var spawned = 0
//        for (p in particles) {
//            if (!p.active) {
//                p.x = Random.nextFloat() * width
//                p.y = vaporBaseHeight - 10f
//                p.vx = Random.nextFloat() * 10f - 5f
//                p.vy = -(Random.nextFloat() * 15f + 10f) * (currentVolatility * 0.5f) * depthFactor
//                p.radius = Random.nextFloat() * 6f + 3f
//                p.isFront = Random.nextBoolean()
//                p.active = true
//                spawned++
//                if (spawned >= splattersToSpawn) break
//            }
//        }
//    }
//
//    private fun buildWavePath(pathFront: Path, pathBack: Path, baseHeight: Float, phaseOffset: Float) {
//        val w = width.toFloat()
//        val h = height.toFloat()
//        val TWO_PI = (Math.PI * 2).toFloat()
//        val step = 20f
//        val globalSlosh = 8f * sin((phaseShift + phaseOffset) * 2f) * currentVolatility * depthFactor
//        var x = 0f
//        var isFirstPoint = true
//
//        while (x <= w + step) {
//            val nx = x / w
//            val tiltY = (nx - 0.5f) * currentTiltOffset
//
//            var yF = (18f * depthFactor) * currentVolatility * sin(TWO_PI * 1.0f * nx + phaseShift + phaseOffset)
//            yF += (8f * depthFactor) * currentVolatility * cos(TWO_PI * 2.5f * nx - (phaseShift + phaseOffset) * 2f)
//            val finalYFront = baseHeight + yF + globalSlosh + tiltY
//
//            var yB = (15f * depthFactor) * currentVolatility * cos(TWO_PI * 1.2f * nx + phaseShift + phaseOffset)
//            yB += (12f * depthFactor) * currentVolatility * sin(TWO_PI * 2.2f * nx - (phaseShift + phaseOffset) * 2f)
//            val finalYBack = baseHeight + yB - globalSlosh + tiltY
//
//            if (isFirstPoint) {
//                pathFront.moveTo(x, finalYFront)
//                pathBack.moveTo(x, finalYBack)
//                isFirstPoint = false
//            } else {
//                pathFront.lineTo(x, finalYFront)
//                pathBack.lineTo(x, finalYBack)
//            }
//            x += step
//        }
//        pathFront.lineTo(w, h); pathFront.lineTo(0f, h); pathFront.close()
//        pathBack.lineTo(w, h); pathBack.lineTo(0f, h); pathBack.close()
//    }
//
//    override fun onDraw(canvas: Canvas) {
//        super.onDraw(canvas)
//        val w = width.toFloat()
//        val h = height.toFloat()
//        if (w == 0f || h == 0f) return
//
//        pathVaporFront.reset(); pathVaporBack.reset()
//        pathLiquidFront.reset(); pathLiquidBack.reset()
//
//        val waveYEmpty = h - 100f
//        val waveYFull = -80f
//
//        // Use the smoothed current percentages for rendering
//        vaporBaseHeight = waveYEmpty + (waveYFull - waveYEmpty) * currentVaporPercentage
//        liquidBaseHeight = waveYEmpty + (waveYFull - waveYEmpty) * currentLiquidPercentage
//
//        buildWavePath(pathVaporFront, pathVaporBack, vaporBaseHeight, 0f)
//        buildWavePath(pathLiquidFront, pathLiquidBack, liquidBaseHeight, 0.5f)
//
//        canvas.drawPath(pathVaporBack, paintVaporBack)
//        canvas.drawPath(pathVaporFront, paintVaporFront)
//        canvas.drawPath(pathLiquidBack, paintLiquidBack)
//        canvas.drawPath(pathLiquidFront, paintLiquidFront)
//
//        for (p in particles) {
//            if (p.active) {
//                val pPaint = if (p.isFront) paintVaporFront else paintVaporBack
//                canvas.drawCircle(p.x, p.y, p.radius, pPaint)
//            }
//        }
//    }
//
//    override fun dispatchDraw(canvas: Canvas) {
//        canvas.save()
//        if (!pathVaporFront.isEmpty) {
//            canvas.clipPath(pathVaporFront)
//        }
//        super.dispatchDraw(canvas)
//        canvas.restore()
//    }
//}
package AppFrontend.Interface.RewardBank

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.Choreographer
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FluidWaveLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), SensorEventListener {

    // --- PHYSICS ENGINE UPGRADE: Immune to snapping and overlapping ---
    var targetVaporPercentage: Float = 0f
        set(value) {
            if (isFirstVaporSet) {
                vaporStartPct = value; vaporTargetPct = value; vaporSurgeX = 1.5f
                isFirstVaporSet = false
            } else if (value > vaporTargetPct) {
                if (vaporSurgeX >= 1.5f) {
                    // Start completely off-screen (-1.0f) to prevent the "snap" dislocation
                    vaporStartPct = vaporTargetPct
                    vaporTargetPct = value
                    vaporSurgeX = -1.0f
                } else {
                    // Wave is already moving! Just swell the target without resetting position.
                    vaporTargetPct = value
                }
            } else {
                vaporStartPct = value; vaporTargetPct = value; vaporSurgeX = 1.5f
            }
            field = value
        }

    var targetLiquidPercentage: Float = 0f
        set(value) {
            if (isFirstLiquidSet) {
                liquidStartPct = value; liquidTargetPct = value; liquidSurgeX = 1.5f
                isFirstLiquidSet = false
            } else if (value > liquidTargetPct) {
                if (liquidSurgeX >= 1.5f) {
                    liquidStartPct = liquidTargetPct
                    liquidTargetPct = value
                    liquidSurgeX = -1.0f
                } else {
                    liquidTargetPct = value
                }
            } else {
                liquidStartPct = value; liquidTargetPct = value; liquidSurgeX = 1.5f
            }
            field = value
        }

    private var isFirstVaporSet = true
    private var isFirstLiquidSet = true

    // Sweeping Wave Variables
    private var vaporStartPct = 0f
    private var vaporTargetPct = 0f
    private var vaporSurgeX = 1.5f // 1.5 means the surge has finished crossing

    private var liquidStartPct = 0f
    private var liquidTargetPct = 0f
    private var liquidSurgeX = 1.5f

    private val depthFactor: Float
        get() = Math.min(1f, 0.1f + (vaporTargetPct * 6.0f))

    // Paints
    private val paintLiquidFront = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#000000"); style = Paint.Style.FILL }
    private val paintLiquidBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#80000000"); style = Paint.Style.FILL }
    private val paintVaporFront = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#809E9E9E"); style = Paint.Style.FILL }
    private val paintVaporBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#409E9E9E"); style = Paint.Style.FILL }

    private val pathLiquidFront = Path(); private val pathLiquidBack = Path()
    private val pathVaporFront = Path(); private val pathVaporBack = Path()

    private var phaseShift = 0f
    private var lastFrameTime = 0L

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var targetTiltOffset = 0f
    private var currentTiltOffset = 0f
    private var targetVolatility = 1f
    private var currentVolatility = 1f
    private var lastAccel = 9.81f

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var radius: Float, var isFront: Boolean) { var active = false }
    private val maxParticles = 40
    private val particles = Array(maxParticles) { Particle(0f, 0f, 0f, 0f, 0f, true) }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameTime == 0L) { lastFrameTime = frameTimeNanos; Choreographer.getInstance().postFrameCallback(this); return }
            var dt = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
            lastFrameTime = frameTimeNanos
            if (dt > 0.05f) dt = 0.05f

            updatePhysics(dt * 60f)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init { setWillNotDraw(false) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        lastFrameTime = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.unregisterListener(this)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun updatePhysics(timeScale: Float) {
        phaseShift += 0.01047f * timeScale
        if (phaseShift > Math.PI * 2) phaseShift -= (Math.PI.toFloat() * 2)

        // Smooth, relaxed speed (0.015f).
        if (vaporSurgeX < 1.5f) vaporSurgeX += 0.015f * timeScale
        if (liquidSurgeX < 1.5f) liquidSurgeX += 0.015f * timeScale

        currentTiltOffset += (targetTiltOffset - currentTiltOffset) * Math.min(0.08f * timeScale, 1f)
        currentVolatility += (targetVolatility - currentVolatility) * Math.min(0.05f * timeScale, 1f)
        if (targetVolatility > 1f) targetVolatility = Math.max(1f, targetVolatility - 0.02f * timeScale)

        for (p in particles) {
            if (p.active) {
                p.vy += 1.5f * timeScale
                p.x += p.vx * timeScale
                p.y += p.vy * timeScale
                if (p.y > height) p.active = false
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]; val ay = event.values[1]; val az = event.values[2]
            targetTiltOffset = ax * 35f * depthFactor
            val currentAccel = Math.sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
            if (abs(currentAccel - lastAccel) > 2.5f) {
                targetVolatility = Math.min(targetVolatility + (abs(currentAccel - lastAccel) * 0.4f * depthFactor), 1f + (4f * depthFactor))
                spawnSplatters()
            }
            lastAccel = currentAccel
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun spawnSplatters() {
        if (vaporTargetPct < 0.02f) return
        val splattersToSpawn = Random.nextInt(2, 6)
        var spawned = 0
        for (p in particles) {
            if (!p.active) {
                p.x = Random.nextFloat() * width
                p.y = (height - 100f) + (-80f - (height - 100f)) * vaporTargetPct - 10f

                // Blast the particles to the right if a wave is rolling in
                if (vaporSurgeX > -0.5f && vaporSurgeX < 1.1f) {
                    p.vx = Random.nextFloat() * 15f + 5f
                } else {
                    p.vx = Random.nextFloat() * 10f - 5f
                }

                p.vy = -(Random.nextFloat() * 15f + 10f) * (currentVolatility * 0.5f) * depthFactor
                p.radius = Random.nextFloat() * 6f + 3f
                p.isFront = Random.nextBoolean()
                p.active = true
                spawned++
                if (spawned >= splattersToSpawn) break
            }
        }
    }

    private fun getLocalPercentage(nx: Float, isVapor: Boolean): Float {
        val surgeX = if (isVapor) vaporSurgeX else liquidSurgeX
        val target = if (isVapor) vaporTargetPct else liquidTargetPct
        val start = if (isVapor) vaporStartPct else liquidStartPct

        if (surgeX >= 1.5f) return target

        // This math is now completely immune to the "snap" because the wave
        // starts at -1.0f, rendering the left edge perfectly flat until the wave arrives.
        val diff = (nx - surgeX) * 10f
        val weight = 1f / (1f + Math.exp(diff.toDouble())).toFloat()
        return start + (target - start) * weight
    }

    private fun getSurgeCrest(nx: Float, isVapor: Boolean): Float {
        val surgeX = if (isVapor) vaporSurgeX else liquidSurgeX
        if (surgeX >= 1.5f) return 0f

        val diff = (nx - surgeX) * 15f
        val amount = if (isVapor) vaporTargetPct - vaporStartPct else liquidTargetPct - liquidStartPct
        if (amount <= 0) return 0f

        val maxCrest = (amount * 500f).coerceAtLeast(30f).coerceAtMost(100f)
        return maxCrest * Math.exp(-(diff * diff).toDouble()).toFloat()
    }

    private fun buildWavePath(pathFront: Path, pathBack: Path, isVapor: Boolean, phaseOffset: Float) {
        val w = width.toFloat(); val h = height.toFloat(); val TWO_PI = (Math.PI * 2).toFloat()
        val step = 20f

        val dFactor = Math.min(1f, 0.1f + ((if (isVapor) vaporTargetPct else liquidTargetPct) * 6.0f))
        val globalSlosh = 8f * sin((phaseShift + phaseOffset) * 2f) * currentVolatility * dFactor

        var x = 0f
        var isFirstPoint = true
        val waveYEmpty = h - 100f
        val waveYFull = -80f

        while (x <= w + step) {
            val nx = x / w
            val tiltY = (nx - 0.5f) * currentTiltOffset

            val localPct = getLocalPercentage(nx, isVapor)
            val surgeCrest = getSurgeCrest(nx, isVapor)
            val baseHeight = waveYEmpty + (waveYFull - waveYEmpty) * localPct - surgeCrest

            var yF = (18f * dFactor) * currentVolatility * sin(TWO_PI * 1.0f * nx + phaseShift + phaseOffset)
            yF += (8f * dFactor) * currentVolatility * cos(TWO_PI * 2.5f * nx - (phaseShift + phaseOffset) * 2f)
            val finalYFront = baseHeight + yF + globalSlosh + tiltY

            var yB = (15f * dFactor) * currentVolatility * cos(TWO_PI * 1.2f * nx + phaseShift + phaseOffset)
            yB += (12f * dFactor) * currentVolatility * sin(TWO_PI * 2.2f * nx - (phaseShift + phaseOffset) * 2f)
            val finalYBack = baseHeight + yB - globalSlosh + tiltY

            if (isFirstPoint) { pathFront.moveTo(x, finalYFront); pathBack.moveTo(x, finalYBack); isFirstPoint = false }
            else { pathFront.lineTo(x, finalYFront); pathBack.lineTo(x, finalYBack) }
            x += step
        }
        pathFront.lineTo(w, h); pathFront.lineTo(0f, h); pathFront.close()
        pathBack.lineTo(w, h); pathBack.lineTo(0f, h); pathBack.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        pathVaporFront.reset(); pathVaporBack.reset()
        pathLiquidFront.reset(); pathLiquidBack.reset()

        buildWavePath(pathVaporFront, pathVaporBack, true, 0f)
        buildWavePath(pathLiquidFront, pathLiquidBack, false, 0.5f)

        canvas.drawPath(pathVaporBack, paintVaporBack)
        canvas.drawPath(pathVaporFront, paintVaporFront)
        canvas.drawPath(pathLiquidBack, paintLiquidBack)
        canvas.drawPath(pathLiquidFront, paintLiquidFront)

        for (p in particles) {
            if (p.active) {
                val pPaint = if (p.isFront) paintVaporFront else paintVaporBack
                canvas.drawCircle(p.x, p.y, p.radius, pPaint)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        if (!pathVaporFront.isEmpty) canvas.clipPath(pathVaporFront)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}