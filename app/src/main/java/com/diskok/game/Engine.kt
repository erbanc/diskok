package com.diskok.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The whole game: state machine, a compact 2D physics step, and rendering.
 * Coordinates are in pixels with y pointing DOWN (Canvas convention). Level
 * data is authored with y up, so we flip on load.
 */
class Engine(initialLevel: Int) {

    enum class Phase { AIMING, FLYING, WON, LOST }

    // Hooks supplied by the view.
    var saveLevel: (Int) -> Unit = {}
    var haptic: (Int) -> Unit = {}   // 0 = tick, 1 = lock, 2 = win

    var width = 0f; private set
    var height = 0f; private set
    private val minDim get() = min(width, height)

    private var levelIndex = initialLevel.coerceIn(0, Levels.all.size - 1)
    // Initialized up front so rendering before configure() never sees a null.
    private var level: Level = Levels.all[levelIndex]
    private var theme: Theme = Palette.theme(levelIndex)

    var phase = Phase.AIMING; private set

    // Ball
    private var bx = 0f; private var by = 0f
    private var vx = 0f; private var vy = 0f
    private var ballR = 0f

    // Target
    private var tx = 0f; private var ty = 0f; private var tr = 0f

    // Drag-to-aim: press anywhere, drag to pull back from the ball, release to
    // launch. The launch goes opposite the drag; distance sets the power.
    private var dragging = false
    private var dragStartX = 0f; private var dragStartY = 0f
    private var dragX = 0f; private var dragY = 0f
    private val maxDrag get() = minDim * 0.42f
    private val launchThreshold = 0.06f   // min power to actually fire

    // Timers
    private var elapsed = 0f          // drives obstacle motion + halo
    private var outcomeTimer = 0f
    private var levelFade = 1f        // 1 -> 0 fade-in on load

    private val obstacles = ArrayList<Obs>()
    private val particles = ArrayList<Particle>()

    // Rings to pass through, in order, before the target counts.
    private val gates = ArrayList<GateR>()
    private var gateIndex = 0

    // Moving target.
    private var targetOX = 0f; private var targetOY = 0f
    private var targetDX = 0f; private var targetDY = 0f
    private var targetDur = 1.6f

    // Deadly obstacles share one clearly "hot" color across all themes.
    private val dangerColor = Color.rgb(224, 104, 104)

    // Launch tuning (points/second), resolution independent.
    private val minSpeed get() = height * 0.70f
    private val maxSpeed get() = height * 1.95f
    private val gravity get() = height * 1.70f * level.gravityScale

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val story = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        textAlign = Paint.Align.CENTER
    }

    private class Obs(
        val isRect: Boolean,
        var cx: Float, var cy: Float,
        val hx: Float, val hy: Float,     // half extents (rect)
        val radius: Float,                // (circle)
        val angle: Float,                 // radians
        val bouncy: Boolean,
        val deadly: Boolean,
        val originX: Float, val originY: Float,
        val dx: Float, val dy: Float,
        val duration: Float,
    ) {
        var prevCx = cx; var prevCy = cy
        var velX = 0f; var velY = 0f
        val restitution get() = if (bouncy) 0.96f else 0.22f
    }

    private class GateR(val x: Float, val y: Float, val r: Float)

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float,
    )

    // MARK: - Setup

    fun configure(w: Float, h: Float) {
        if (w <= 0 || h <= 0) return
        width = w; height = h
        loadLevel(levelIndex)
    }

    private fun px(nx: Float) = nx * width
    private fun py(ny: Float) = (1f - ny) * height   // flip y-up -> y-down

    private fun loadLevel(index: Int) {
        levelIndex = index.coerceIn(0, Levels.all.size - 1)
        level = Levels.all[levelIndex]
        theme = Palette.theme(levelIndex)
        saveLevel(levelIndex)

        phase = Phase.AIMING
        dragging = false
        elapsed = 0f; outcomeTimer = 0f
        levelFade = 1f
        particles.clear()
        gateIndex = 0

        ballR = max(6f, minDim * 0.026f)
        bx = px(level.ballX); by = py(level.ballY)
        vx = 0f; vy = 0f

        tx = px(level.targetX); ty = py(level.targetY)
        tr = level.targetRadius * minDim
        targetOX = tx; targetOY = ty
        targetDX = level.targetMoveDx * width
        targetDY = -level.targetMoveDy * height   // flip y-up -> y-down
        targetDur = level.targetMoveDuration

        gates.clear()
        for (g in level.gates) gates.add(GateR(px(g.x), py(g.y), g.r * minDim))

        obstacles.clear()
        for (o in level.obstacles) {
            val cx = px(o.x); val cy = py(o.y)
            val dxp = o.moveDx * width
            // y is flipped, so a positive design dy moves up (negative screen y)
            val dyp = -o.moveDy * height
            val isRect = o.kind is ObstacleKind.Rect
            val hx = if (o.kind is ObstacleKind.Rect) o.kind.w * width / 2f else 0f
            val hy = if (o.kind is ObstacleKind.Rect) o.kind.h * height / 2f else 0f
            val r = if (o.kind is ObstacleKind.Circle) o.kind.r * minDim else 0f
            // Design rotation is CCW in y-up; screen y-down flips its sign.
            val ang = Math.toRadians((-o.rotationDeg).toDouble()).toFloat()
            obstacles.add(
                Obs(isRect, cx, cy, hx, hy, r, ang, o.bouncy, o.deadly,
                    cx, cy, dxp, dyp, o.moveDuration)
            )
        }
    }

    fun resetLevel() = loadLevel(levelIndex)
    private fun nextLevel() = loadLevel((levelIndex + 1) % Levels.all.size)

    // MARK: - Input

    fun onPointerDown(x: Float, y: Float) {
        when (phase) {
            Phase.AIMING -> {
                dragging = true
                dragStartX = x; dragStartY = y
                dragX = x; dragY = y
            }
            Phase.FLYING, Phase.LOST -> resetLevel()  // immediate retry
            Phase.WON -> {}
        }
    }

    fun onPointerMove(x: Float, y: Float) {
        if (phase == Phase.AIMING && dragging) { dragX = x; dragY = y }
    }

    fun onPointerUp(x: Float, y: Float) {
        if (phase != Phase.AIMING || !dragging) return
        dragX = x; dragY = y
        dragging = false
        val aim = aimVector()
        if (aim != null && aim.power >= launchThreshold) {
            haptic(1)
            launch(aim.dirX, aim.dirY, aim.power)
        }
        // Below threshold: treat as a tap that cancels; stay in AIMING.
    }

    private class Aim(val dirX: Float, val dirY: Float, val power: Float)

    /** Current pull-back aim, or null if the drag is too tiny to matter. */
    private fun aimVector(): Aim? {
        val dx = dragX - dragStartX; val dy = dragY - dragStartY
        val len = hypot(dx, dy)
        if (len < 1e-3f) return null
        val power = (len / maxDrag).coerceIn(0f, 1f)
        return Aim(-dx / len, -dy / len, power)   // launch opposite the drag
    }

    private fun launch(dirX: Float, dirY: Float, power: Float) {
        phase = Phase.FLYING
        val speed = minSpeed + (maxSpeed - minSpeed) * power
        vx = dirX * speed; vy = dirY * speed
    }

    // MARK: - Update

    fun update(dtRaw: Float) {
        if (width <= 0) return
        val dt = min(dtRaw, 1f / 30f)
        elapsed += dt
        if (levelFade > 0f) levelFade = max(0f, levelFade - dt * 4f)

        updateObstacles(dt)
        updateTarget()
        updateParticles(dt)

        when (phase) {
            Phase.AIMING -> { /* waiting for a drag */ }
            Phase.FLYING -> stepBall(dt)
            Phase.WON -> {
                outcomeTimer += dt
                if (outcomeTimer > 0.55f) nextLevel()
            }
            Phase.LOST -> {
                outcomeTimer += dt
                if (outcomeTimer > 0.12f) resetLevel()
            }
        }
    }

    private fun updateObstacles(dt: Float) {
        if (dt <= 0f) return
        for (o in obstacles) {
            o.prevCx = o.cx; o.prevCy = o.cy
            if (o.dx != 0f || o.dy != 0f) {
                val frac = (elapsed / o.duration).mod(2f)
                val tri = if (frac < 1f) frac else 2f - frac
                val eased = tri * tri * (3f - 2f * tri)   // smoothstep ease
                o.cx = o.originX + o.dx * eased
                o.cy = o.originY + o.dy * eased
            }
            o.velX = (o.cx - o.prevCx) / dt
            o.velY = (o.cy - o.prevCy) / dt
        }
    }

    private fun updateTarget() {
        if (targetDX == 0f && targetDY == 0f) return
        val frac = (elapsed / targetDur).mod(2f)
        val tri = if (frac < 1f) frac else 2f - frac
        val eased = tri * tri * (3f - 2f * tri)
        tx = targetOX + targetDX * eased
        ty = targetOY + targetDY * eased
    }

    private fun stepBall(dt: Float) {
        // Substep to keep fast shots from tunneling through thin bouncers.
        val speed = hypot(vx, vy)
        val sub = ceil(speed * dt / (ballR * 0.8f)).toInt().coerceIn(1, 10)
        val h = dt / sub
        repeat(sub) {
            vy += gravity * h
            val damp = 1f - 0.10f * h
            vx *= damp; vy *= damp
            bx += vx * h; by += vy * h
            if (collide()) return        // hit a deadly obstacle
            updateGates()
            if (checkWin()) return
            if (checkOut()) return
        }
        spawnTrail()
    }

    private fun updateGates() {
        if (gateIndex >= gates.size) return
        val g = gates[gateIndex]
        if (hypot(bx - g.x, by - g.y) <= ballR + g.r) {
            gateIndex++
            haptic(0)
        }
    }

    /** Returns true if the ball hit a deadly obstacle (attempt failed). */
    private fun collide(): Boolean {
        for (o in obstacles) {
            var nX: Float; var nY: Float; var pen: Float
            if (!o.isRect) {
                val dx = bx - o.cx; val dy = by - o.cy
                val dist = hypot(dx, dy)
                pen = (ballR + o.radius) - dist
                if (pen <= 0f) continue
                if (dist > 1e-4f) { nX = dx / dist; nY = dy / dist }
                else { nX = 0f; nY = -1f }
            } else {
                // Transform ball into the rect's local frame.
                val rx = bx - o.cx; val ry = by - o.cy
                val ca = cos(-o.angle); val sa = sin(-o.angle)
                val lx = rx * ca - ry * sa
                val ly = rx * sa + ry * ca
                val clampedX = lx.coerceIn(-o.hx, o.hx)
                val clampedY = ly.coerceIn(-o.hy, o.hy)
                val ddx = lx - clampedX; val ddy = ly - clampedY
                val dist = hypot(ddx, ddy)
                var lnx: Float; var lny: Float
                if (dist > 1e-4f) {
                    pen = ballR - dist
                    if (pen <= 0f) continue
                    lnx = ddx / dist; lny = ddy / dist
                } else {
                    // Center inside the box: push out along the nearest face.
                    val depthX = o.hx - abs(lx)
                    val depthY = o.hy - abs(ly)
                    if (depthX < depthY) {
                        lnx = if (lx >= 0f) 1f else -1f; lny = 0f; pen = ballR + depthX
                    } else {
                        lnx = 0f; lny = if (ly >= 0f) 1f else -1f; pen = ballR + depthY
                    }
                }
                // Rotate normal back to world space.
                val cb = cos(o.angle); val sb = sin(o.angle)
                nX = lnx * cb - lny * sb
                nY = lnx * sb + lny * cb
            }

            if (o.deadly) {
                phase = Phase.LOST
                outcomeTimer = 0f
                burst(bx, by, dangerColor)
                return true
            }

            // Positional correction.
            bx += nX * pen; by += nY * pen

            // Reflect velocity relative to a (possibly moving) obstacle.
            val relX = vx - o.velX; val relY = vy - o.velY
            val vn = relX * nX + relY * nY
            if (vn < 0f) {
                val e = o.restitution
                var newRelX = relX - (1f + e) * vn * nX
                var newRelY = relY - (1f + e) * vn * nY
                // A touch of tangential friction so resting contacts settle.
                val tX = newRelX - (newRelX * nX + newRelY * nY) * nX
                val tY = newRelY - (newRelX * nX + newRelY * nY) * nY
                newRelX -= tX * 0.04f; newRelY -= tY * 0.04f
                vx = newRelX + o.velX; vy = newRelY + o.velY
                haptic(0)
            }
        }
        return false
    }

    private fun checkWin(): Boolean {
        if (gateIndex < gates.size) return false      // rings first
        if (hypot(bx - tx, by - ty) <= ballR + tr) {
            phase = Phase.WON
            outcomeTimer = 0f
            haptic(2)
            burst(tx, ty, theme.target)
            burst(bx, by, theme.ball)
            return true
        }
        return false
    }

    private fun checkOut(): Boolean {
        val m = ballR * 2f + minDim * 0.08f
        if (bx < -m || bx > width + m || by < -m || by > height + m) {
            phase = Phase.LOST
            outcomeTimer = 0f
            return true
        }
        return false
    }

    // MARK: - Particles

    private fun spawnTrail() {
        particles.add(
            Particle(
                bx + Random.nextFloat() * 2 - 1, by + Random.nextFloat() * 2 - 1,
                -vx * 0.02f, -vy * 0.02f,
                0.42f, 0.42f, theme.ball, ballR * 0.95f
            )
        )
    }

    private fun burst(x: Float, y: Float, color: Int) {
        repeat(22) {
            val a = Random.nextFloat() * 2f * Math.PI.toFloat()
            val s = minDim * (0.5f + Random.nextFloat() * 0.6f)
            particles.add(
                Particle(x, y, cos(a) * s, sin(a) * s, 0.6f, 0.6f, color, ballR * 1.1f)
            )
        }
    }

    private fun updateParticles(dt: Float) {
        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) { particles.removeAt(i); continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= (1f - 2.2f * dt); p.vy *= (1f - 2.2f * dt)
            i++
        }
    }

    // MARK: - Render

    fun render(c: Canvas) {
        c.drawColor(theme.background)
        if (width <= 0) return

        drawLevelWatermark(c)
        drawObstacles(c)
        drawGates(c)
        drawTarget(c)
        drawParticles(c)
        drawBall(c)
        if (phase == Phase.AIMING && dragging) drawAim(c)
        drawHud(c)

        if (levelFade > 0f) {
            paint.color = withAlpha(theme.background, levelFade)
            c.drawRect(0f, 0f, width, height, paint)
        }
    }

    private fun drawLevelWatermark(c: Canvas) {
        text.color = withAlpha(theme.ink, 0.05f)
        text.textSize = minDim * 0.55f
        c.drawText("${levelIndex + 1}", width / 2f,
            height / 2f - (text.descent() + text.ascent()) / 2f, text)
    }

    private fun drawObstacles(c: Canvas) {
        for (o in obstacles) {
            paint.color = when {
                o.deadly -> dangerColor
                o.bouncy -> theme.bouncer
                else -> theme.obstacle
            }
            if (o.isRect) {
                c.save()
                c.translate(o.cx, o.cy)
                c.rotate(Math.toDegrees(o.angle.toDouble()).toFloat())
                val r = min(o.hx, o.hy)
                c.drawRoundRect(-o.hx, -o.hy, o.hx, o.hy, r, r, paint)
                c.restore()
            } else {
                c.drawCircle(o.cx, o.cy, o.radius, paint)
            }
        }
    }

    private fun drawGates(c: Canvas) {
        if (gates.isEmpty()) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2.5f, minDim * 0.01f)
        for (i in gates.indices) {
            val g = gates[i]
            when {
                i < gateIndex -> paint.color = withAlpha(theme.target, 0.30f) // passed
                i == gateIndex -> paint.color = withAlpha(theme.aim, 0.95f)    // next
                else -> paint.color = withAlpha(theme.ink, 0.22f)             // later
            }
            c.drawCircle(g.x, g.y, g.r, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawTarget(c: Canvas) {
        val armed = gateIndex >= gates.size
        if (!armed) {
            // Not yet active: a faint hollow ring until the rings are cleared.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2.5f, minDim * 0.012f)
            paint.color = withAlpha(theme.target, 0.5f)
            c.drawCircle(tx, ty, tr, paint)
            paint.style = Paint.Style.FILL
            return
        }
        // Pulsing halo.
        val t = (elapsed * 1.1f).mod(1f)
        val scale = 1f + t * 0.7f
        paint.color = withAlpha(theme.target, (1f - t) * 0.30f)
        c.drawCircle(tx, ty, tr * scale, paint)
        paint.color = theme.target
        c.drawCircle(tx, ty, tr, paint)
    }

    private fun drawBall(c: Canvas) {
        if (phase == Phase.WON) return
        paint.color = theme.ball
        c.drawCircle(bx, by, ballR, paint)
    }

    private fun drawParticles(c: Canvas) {
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            paint.color = withAlpha(p.color, a * 0.85f)
            c.drawCircle(p.x, p.y, p.size * (0.4f + a * 0.6f), paint)
        }
    }

    private fun drawAim(c: Canvas) {
        val aim = aimVector() ?: return
        // A short aim stub only: direction plus a power-scaled length and a
        // few fading dots. The full arc is NOT shown — reading it is the skill.
        val maxLen = ballR * 2.0f + minDim * 0.16f
        val len = ballR * 1.6f + (maxLen - ballR * 1.6f) * aim.power
        val ex = bx + aim.dirX * len
        val ey = by + aim.dirY * len

        paint.color = theme.aim
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = max(3f, minDim * 0.012f)
        paint.style = Paint.Style.STROKE
        c.drawLine(bx, by, ex, ey, paint)
        paint.style = Paint.Style.FILL

        // Three small dots continuing the launch direction to read the angle.
        for (i in 1..3) {
            val d = len + i * ballR * 1.1f
            paint.color = withAlpha(theme.aim, 0.45f - i * 0.1f)
            c.drawCircle(bx + aim.dirX * d, by + aim.dirY * d, ballR * 0.22f, paint)
        }
        paint.color = theme.aim
        c.drawCircle(bx, by, ballR * 0.55f, paint)
    }

    private fun drawHud(c: Canvas) {
        text.color = withAlpha(theme.ink, 0.45f)
        text.textSize = minDim * 0.035f
        if (level.hint.isNotEmpty()) {
            c.drawText(level.hint, width / 2f, height * 0.10f, text)
        }
        // Keep the control prompt only where it helps: the first level (to
        // teach the gesture) and whenever a shot is in flight.
        val prompt = when {
            phase == Phase.AIMING && levelIndex == 0 -> "drag to aim · release to launch"
            phase == Phase.FLYING -> "tap to retry"
            else -> ""
        }
        if (prompt.isNotEmpty()) {
            text.color = withAlpha(theme.ink, 0.55f)
            text.textSize = minDim * 0.035f
            c.drawText(prompt, width / 2f, height * 0.915f, text)
        }

        // The quiet tale, one line per level, italic and faint along the bottom.
        val tale = Levels.stories.getOrElse(levelIndex) { "" }
        if (tale.isNotEmpty()) {
            story.color = withAlpha(theme.ink, 0.40f)
            story.textSize = minDim * 0.034f
            val maxW = width * 0.92f
            val w = story.measureText(tale)
            if (w > maxW) story.textSize = story.textSize * maxW / w
            c.drawText(tale, width / 2f, height * 0.965f, story)
        }
    }

    private fun withAlpha(color: Int, a: Float): Int {
        val alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
