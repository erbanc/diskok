package com.diskok.game

import kotlin.math.PI

/**
 * All positions and sizes are normalized to the play field (0..1), where
 * (0,0) is bottom-left and (1,1) is top-right (y points up, design-friendly;
 * the engine flips it to Canvas coordinates). This keeps every level
 * resolution-independent across phones.
 */

sealed class ObstacleKind {
    /** Size normalized to field width/height. */
    data class Rect(val w: Float, val h: Float) : ObstacleKind()
    /** Radius normalized to min(width, height). */
    data class Circle(val r: Float) : ObstacleKind()
}

data class Obstacle(
    val kind: ObstacleKind,
    val x: Float,
    val y: Float,
    val bouncy: Boolean = false,
    val moveDx: Float = 0f,          // back-and-forth motion, normalized
    val moveDy: Float = 0f,
    val moveDuration: Float = 1.6f,  // seconds for one leg
    val rotationDeg: Float = 0f,     // for rect obstacles
)

data class Level(
    val ballX: Float,
    val ballY: Float,
    val targetX: Float,
    val targetY: Float,
    val targetRadius: Float = 0.05f,     // normalized to min dim
    val gravityScale: Float = 1f,
    val aimCenter: Float = (PI / 2).toFloat(),   // straight up
    val aimRange: Float = (PI * 0.9).toFloat(),  // total sweep
    val aimSpeed: Float = 1.4f,                  // higher = harder timing
    val powerSpeed: Float = 1.3f,
    val obstacles: List<Obstacle> = emptyList(),
    val hint: String = "",
)

object Levels {
    private val P = PI.toFloat()

    val all: List<Level> = listOf(
        // 1 — first contact: gentle, wide, slow.
        Level(0.5f, 0.18f, 0.5f, 0.80f, targetRadius = 0.075f,
            aimSpeed = 0.9f, powerSpeed = 0.9f,
            hint = "Tap for angle, tap for power."),

        // 2 — a little off to the side.
        Level(0.22f, 0.18f, 0.80f, 0.70f, targetRadius = 0.065f,
            aimCenter = P * 0.42f, aimSpeed = 1.0f, powerSpeed = 1.0f),

        // 3 — clear a low wall.
        Level(0.18f, 0.18f, 0.82f, 0.30f, targetRadius = 0.06f,
            aimSpeed = 1.1f, powerSpeed = 1.1f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.04f, 0.34f), 0.5f, 0.17f),
            )),

        // 4 — first bouncer: use the ricochet.
        Level(0.15f, 0.20f, 0.85f, 0.55f, targetRadius = 0.06f,
            aimSpeed = 1.2f, powerSpeed = 1.15f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.30f, 0.035f), 0.5f, 0.30f, bouncy = true),
            ),
            hint = "Orange blocks bounce."),

        // 5 — narrow gap.
        Level(0.5f, 0.15f, 0.5f, 0.85f, targetRadius = 0.055f,
            aimSpeed = 1.25f, powerSpeed = 1.2f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.30f, 0.035f), 0.18f, 0.55f),
                Obstacle(ObstacleKind.Rect(0.30f, 0.035f), 0.82f, 0.55f),
            )),

        // 6 — circular pillar in the middle.
        Level(0.16f, 0.22f, 0.84f, 0.22f, targetRadius = 0.055f,
            aimCenter = P * 0.5f, aimRange = P * 0.8f,
            aimSpeed = 1.3f, powerSpeed = 1.25f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Circle(0.10f), 0.5f, 0.30f),
            )),

        // 7 — low gravity floaty arc.
        Level(0.18f, 0.20f, 0.82f, 0.78f, targetRadius = 0.055f,
            gravityScale = 0.55f, aimSpeed = 1.35f, powerSpeed = 1.3f,
            hint = "Lighter gravity here."),

        // 8 — bounce off a wall to a corner.
        Level(0.5f, 0.18f, 0.12f, 0.62f, targetRadius = 0.05f,
            aimSpeed = 1.4f, powerSpeed = 1.35f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.035f, 0.45f), 0.30f, 0.62f, bouncy = true),
            )),

        // 9 — two bouncers, a corridor.
        Level(0.5f, 0.14f, 0.5f, 0.86f, targetRadius = 0.05f,
            aimRange = P * 0.6f, aimSpeed = 1.45f, powerSpeed = 1.4f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.035f, 0.5f), 0.30f, 0.5f, bouncy = true),
                Obstacle(ObstacleKind.Rect(0.035f, 0.5f), 0.70f, 0.5f, bouncy = true),
            )),

        // 10 — a moving block.
        Level(0.16f, 0.20f, 0.84f, 0.45f, targetRadius = 0.05f,
            aimSpeed = 1.4f, powerSpeed = 1.35f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.04f, 0.30f), 0.5f, 0.55f,
                    moveDy = -0.30f, moveDuration = 1.5f),
            ),
            hint = "Time the moving block."),

        // 11 — small target, fast timing.
        Level(0.20f, 0.20f, 0.80f, 0.72f, targetRadius = 0.04f,
            aimSpeed = 1.6f, powerSpeed = 1.55f),

        // 12 — diagonal bouncer ramp.
        Level(0.14f, 0.55f, 0.86f, 0.55f, targetRadius = 0.05f,
            aimCenter = -P * 0.15f, aimRange = P * 0.7f,
            aimSpeed = 1.5f, powerSpeed = 1.45f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.40f, 0.03f), 0.5f, 0.28f,
                    bouncy = true, rotationDeg = 18f),
            )),

        // 13 — pillar gauntlet.
        Level(0.5f, 0.14f, 0.5f, 0.88f, targetRadius = 0.045f,
            aimRange = P * 0.5f, aimSpeed = 1.6f, powerSpeed = 1.55f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Circle(0.06f), 0.35f, 0.45f),
                Obstacle(ObstacleKind.Circle(0.06f), 0.65f, 0.62f),
            )),

        // 14 — moving bouncer.
        Level(0.16f, 0.22f, 0.84f, 0.62f, targetRadius = 0.045f,
            aimSpeed = 1.6f, powerSpeed = 1.55f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.22f, 0.03f), 0.5f, 0.40f,
                    bouncy = true, moveDy = 0.18f, moveDuration = 1.3f),
            )),

        // 15 — tight S-curve.
        Level(0.16f, 0.16f, 0.84f, 0.84f, targetRadius = 0.04f,
            aimSpeed = 1.7f, powerSpeed = 1.6f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.40f, 0.03f), 0.30f, 0.40f),
                Obstacle(ObstacleKind.Rect(0.40f, 0.03f), 0.70f, 0.62f),
            )),

        // 16 — low gravity, closing blocks.
        Level(0.5f, 0.14f, 0.5f, 0.84f, targetRadius = 0.04f,
            gravityScale = 0.6f, aimRange = P * 0.45f,
            aimSpeed = 1.7f, powerSpeed = 1.65f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.04f, 0.26f), 0.35f, 0.5f,
                    moveDx = 0.30f, moveDuration = 1.4f),
                Obstacle(ObstacleKind.Rect(0.04f, 0.26f), 0.65f, 0.5f,
                    moveDx = -0.30f, moveDuration = 1.4f),
            )),

        // 17 — double ricochet required.
        Level(0.14f, 0.20f, 0.14f, 0.78f, targetRadius = 0.04f,
            aimCenter = P * 0.30f, aimRange = P * 0.6f,
            aimSpeed = 1.75f, powerSpeed = 1.7f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Rect(0.03f, 0.55f), 0.86f, 0.5f, bouncy = true),
                Obstacle(ObstacleKind.Rect(0.40f, 0.03f), 0.55f, 0.30f, bouncy = true),
            )),

        // 18 — finale: small target, fast, busy.
        Level(0.5f, 0.13f, 0.5f, 0.88f, targetRadius = 0.035f,
            aimRange = P * 0.5f, aimSpeed = 1.9f, powerSpeed = 1.85f,
            obstacles = listOf(
                Obstacle(ObstacleKind.Circle(0.05f), 0.30f, 0.40f),
                Obstacle(ObstacleKind.Circle(0.05f), 0.70f, 0.40f),
                Obstacle(ObstacleKind.Rect(0.035f, 0.40f), 0.5f, 0.66f,
                    bouncy = true, moveDx = 0.18f, moveDuration = 1.2f),
            ),
            hint = "Last one. Good luck."),
    )
}
