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

    var fillPercentage: Float = 0f

    // --- THE DYNAMIC DAMPENER ---
    // At 0% full -> 0.1 (Very stiff, hides the tab bar block)
    // At 15% full -> 1.0 (Full physics unlocked)
    private val depthFactor: Float
        get() = Math.min(1f, 0.1f + (fillPercentage * 6.0f))

    private val paintFront = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#000000")
        style = Paint.Style.FILL
    }

    private val paintBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40181818")
        style = Paint.Style.FILL
    }

    private val pathFront = Path()
    private val pathBack = Path()

    // Physics Engine Variables
    private var phaseShift = 0f
    private var lastFrameTime = 0L

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var targetTiltOffset = 0f
    private var currentTiltOffset = 0f

    // Smooth volatility tracking
    private var targetVolatility = 1f
    private var currentVolatility = 1f

    private var lastAccel = 9.81f
    private var currentBaseHeight = 0f

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var radius: Float, var isFront: Boolean) {
        var active = false
    }
    private val maxParticles = 40
    private val particles = Array(maxParticles) { Particle(0f, 0f, 0f, 0f, 0f, true) }

    // --- THE GAME LOOP ---
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameTime == 0L) {
                lastFrameTime = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
                return
            }

            var dt = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
            lastFrameTime = frameTimeNanos

            if (dt > 0.05f) dt = 0.05f

            val timeScale = dt * 60f

            updatePhysics(timeScale)

            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setWillNotDraw(false)
    }

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

    // --- PHYSICS CALCULATIONS ---
    private fun updatePhysics(timeScale: Float) {
        phaseShift += 0.01047f * timeScale
        if (phaseShift > Math.PI * 2) phaseShift -= (Math.PI.toFloat() * 2)

        val tiltLerpFactor = Math.min(0.08f * timeScale, 1f)
        currentTiltOffset += (targetTiltOffset - currentTiltOffset) * tiltLerpFactor

        val volLerpFactor = Math.min(0.05f * timeScale, 1f)
        currentVolatility += (targetVolatility - currentVolatility) * volLerpFactor

        if (targetVolatility > 1f) {
            targetVolatility -= 0.02f * timeScale
            if (targetVolatility < 1f) targetVolatility = 1f
        }

        for (p in particles) {
            if (p.active) {
                p.vy += 1.5f * timeScale
                p.x += p.vx * timeScale
                p.y += p.vy * timeScale

                if (p.y > height || p.y > currentBaseHeight + 50f) {
                    p.active = false
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            // Dampen the tilt based on water level
            targetTiltOffset = ax * 35f * depthFactor

            val currentAccel = Math.sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
            val jerk = abs(currentAccel - lastAccel)

            if (jerk > 2.5f) {
                // Dampen the volatility spikes based on water level
                val maxVol = 1f + (4f * depthFactor)
                targetVolatility = Math.min(targetVolatility + (jerk * 0.4f * depthFactor), maxVol)
                spawnSplatters()
            }
            lastAccel = currentAccel
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun spawnSplatters() {
        // Prevent splatters entirely if water is too low (lowered to 2% so it starts splashing earlier)
        if (currentBaseHeight == 0f || fillPercentage < 0.02f) return

        val splattersToSpawn = Random.nextInt(2, 6)
        var spawned = 0
        for (p in particles) {
            if (!p.active) {
                p.x = Random.nextFloat() * width
                p.y = currentBaseHeight - 10f
                p.vx = Random.nextFloat() * 10f - 5f

                // Dampen splatter height based on water level
                p.vy = -(Random.nextFloat() * 15f + 10f) * (currentVolatility * 0.5f) * depthFactor

                p.radius = Random.nextFloat() * 6f + 3f
                p.isFront = Random.nextBoolean()
                p.active = true
                spawned++
                if (spawned >= splattersToSpawn) break
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        pathFront.reset()
        pathBack.reset()

        val TWO_PI = (Math.PI * 2).toFloat()
        val step = 20f

        val minimumVisibleLiquid = 00f
        val waveYEmpty = h - minimumVisibleLiquid
        val waveYFull = -80f

        currentBaseHeight = waveYEmpty + (waveYFull - waveYEmpty) * fillPercentage

        // Dampen global sloshing
        val globalSlosh = 8f * sin(phaseShift * 2f) * currentVolatility * depthFactor

        var x = 0f
        var isFirstPoint = true

        while (x <= w + step) {
            val nx = x / w
            val tiltY = (nx - 0.5f) * currentTiltOffset

            // Dampen the wave amplitudes (18f and 8f) based on water level
            var yF = (18f * depthFactor) * currentVolatility * sin(TWO_PI * 1.0f * nx + phaseShift)
            yF += (8f * depthFactor) * currentVolatility * cos(TWO_PI * 2.5f * nx - phaseShift * 2f)
            val finalYFront = currentBaseHeight + yF + globalSlosh + tiltY

            // Dampen the back wave amplitudes (15f and 12f)
            var yB = (15f * depthFactor) * currentVolatility * cos(TWO_PI * 1.2f * nx + phaseShift)
            yB += (12f * depthFactor) * currentVolatility * sin(TWO_PI * 2.2f * nx - phaseShift * 2f)
            val finalYBack = currentBaseHeight + yB - globalSlosh + tiltY

            if (isFirstPoint) {
                pathFront.moveTo(x, finalYFront)
                pathBack.moveTo(x, finalYBack)
                isFirstPoint = false
            } else {
                pathFront.lineTo(x, finalYFront)
                pathBack.lineTo(x, finalYBack)
            }
            x += step
        }

        pathFront.lineTo(w, h)
        pathFront.lineTo(0f, h)
        pathFront.close()

        pathBack.lineTo(w, h)
        pathBack.lineTo(0f, h)
        pathBack.close()

        canvas.drawPath(pathBack, paintBack)

        for (p in particles) {
            if (p.active) {
                val pPaint = if (p.isFront) paintFront else paintBack
                canvas.drawCircle(p.x, p.y, p.radius, pPaint)
            }
        }

        canvas.drawPath(pathFront, paintFront)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        if (!pathFront.isEmpty) {
            canvas.clipPath(pathFront)
        }
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}