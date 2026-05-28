package com.diskok.game

/**
 * All positions and sizes are normalized to the play field (0..1), where
 * (0,0) is bottom-left and (1,1) is top-right (y points up, design-friendly;
 * the engine flips it to Canvas coordinates). x is normalized to the field
 * width, y to the height. This keeps every level resolution-independent.
 *
 * Aiming is free 360° drag-and-release with only a short aim stub (no full
 * trajectory preview), so reading the arc is the core skill. Difficulty also
 * comes from: deadly obstacles (touch = fail), moving targets, and rings the
 * ball must fly through, in order, before the target counts.
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
    val deadly: Boolean = false,     // touching it fails the attempt
    val moveDx: Float = 0f,          // back-and-forth motion, normalized
    val moveDy: Float = 0f,
    val moveDuration: Float = 1.6f,  // seconds for one leg
    val rotationDeg: Float = 0f,     // for rect obstacles
)

/** A ring the ball must pass through (center within radius). */
data class Gate(val x: Float, val y: Float, val r: Float = 0.07f)

data class Level(
    val ballX: Float,
    val ballY: Float,
    val targetX: Float,
    val targetY: Float,
    val targetRadius: Float = 0.05f,     // normalized to min dim
    val gravityScale: Float = 1f,
    val obstacles: List<Obstacle> = emptyList(),
    val gates: List<Gate> = emptyList(), // must be passed in order before the target
    val targetMoveDx: Float = 0f,        // moving target, normalized
    val targetMoveDy: Float = 0f,
    val targetMoveDuration: Float = 1.6f,
    val hint: String = "",
)

object Levels {

    private fun rect(
        x: Float, y: Float, w: Float, h: Float,
        bouncy: Boolean = false, deadly: Boolean = false, rot: Float = 0f,
        mdx: Float = 0f, mdy: Float = 0f, mdur: Float = 1.6f,
    ) = Obstacle(ObstacleKind.Rect(w, h), x, y, bouncy, deadly, mdx, mdy, mdur, rot)

    private fun circ(
        x: Float, y: Float, r: Float, bouncy: Boolean = false, deadly: Boolean = false,
    ) = Obstacle(ObstacleKind.Circle(r), x, y, bouncy, deadly)

    /** Builds a pocket that only opens downward, so it must be entered by a
     *  ricochet off the bouncy floor placed beneath it. */
    private fun downPocket(tx: Float, ty: Float, floorY: Float): List<Obstacle> = listOf(
        rect(tx, ty + 0.085f, 0.32f, 0.035f),          // roof
        rect(tx - 0.15f, ty + 0.04f, 0.03f, 0.17f),    // left upper wall
        rect(tx + 0.15f, ty + 0.04f, 0.03f, 0.17f),    // right upper wall
        rect(tx, floorY, 0.50f, 0.04f, bouncy = true), // bouncy landing pad
    )

    val all: List<Level> = listOf(
        // 1 — warmup: a straight lob.
        Level(0.5f, 0.16f, 0.5f, 0.80f, targetRadius = 0.08f,
            hint = "Drag back, release to launch."),

        // 2 — diagonal arc.
        Level(0.2f, 0.16f, 0.82f, 0.62f, targetRadius = 0.07f),

        // 3 — arc over a wall.
        Level(0.16f, 0.16f, 0.84f, 0.26f, targetRadius = 0.06f,
            obstacles = listOf(rect(0.5f, 0.22f, 0.05f, 0.44f))),

        // 4 — meet the bouncer.
        Level(0.15f, 0.18f, 0.85f, 0.55f, targetRadius = 0.065f,
            obstacles = listOf(rect(0.5f, 0.30f, 0.34f, 0.035f, bouncy = true)),
            hint = "Orange surfaces bounce."),

        // 5 — thread a narrow gap.
        Level(0.5f, 0.15f, 0.5f, 0.85f, targetRadius = 0.05f,
            obstacles = listOf(
                rect(0.20f, 0.55f, 0.34f, 0.035f),
                rect(0.80f, 0.55f, 0.34f, 0.035f),
            )),

        // 6 — pillar dodge, and a first deadly floor punishing short shots.
        Level(0.16f, 0.20f, 0.84f, 0.24f, targetRadius = 0.055f,
            obstacles = listOf(
                circ(0.5f, 0.32f, 0.10f),
                rect(0.5f, 0.05f, 0.34f, 0.04f, deadly = true),
            ),
            hint = "Red is death — don't fall short."),

        // 7 — fly through the ring first (low gravity).
        Level(0.18f, 0.20f, 0.82f, 0.74f, targetRadius = 0.05f,
            gravityScale = 0.55f,
            gates = listOf(Gate(0.5f, 0.58f, 0.07f)),
            hint = "Pass through the ring, then the target."),

        // 8 — bank off the bouncy floor (a wall blocks the low line).
        Level(0.14f, 0.55f, 0.86f, 0.30f, targetRadius = 0.05f,
            obstacles = listOf(
                rect(0.5f, 0.62f, 0.05f, 0.55f),
                rect(0.70f, 0.18f, 0.40f, 0.035f, bouncy = true)
            ),
            hint = "Bank off the orange floor."),

        // 9 — corridor of bouncers, and the target drifts.
        Level(0.5f, 0.14f, 0.5f, 0.84f, targetRadius = 0.05f,
            obstacles = listOf(
                rect(0.26f, 0.5f, 0.035f, 0.5f, bouncy = true),
                rect(0.74f, 0.5f, 0.035f, 0.5f, bouncy = true),
            ),
            targetMoveDx = 0.26f, targetMoveDuration = 1.6f,
            hint = "Lead the moving target."),

        // 10 — moving block with a deadly floor below.
        Level(0.16f, 0.20f, 0.84f, 0.45f, targetRadius = 0.05f,
            obstacles = listOf(
                rect(0.5f, 0.55f, 0.04f, 0.30f, mdy = -0.30f, mdur = 1.5f),
                rect(0.5f, 0.05f, 0.40f, 0.04f, deadly = true),
            ),
            hint = "Mind the red floor."),

        // 11 — FORCED bounce: a down-facing pocket over a bouncy pad.
        Level(0.20f, 0.20f, 0.62f, 0.52f, targetRadius = 0.05f,
            obstacles = downPocket(0.62f, 0.52f, 0.26f),
            hint = "Bounce up into it from below."),

        // 12 — diagonal bouncy ramp, through a ring.
        Level(0.14f, 0.55f, 0.86f, 0.55f, targetRadius = 0.05f,
            obstacles = listOf(
                rect(0.5f, 0.30f, 0.44f, 0.03f, bouncy = true, rot = 18f),
                rect(0.5f, 0.80f, 0.55f, 0.035f),
            ),
            gates = listOf(Gate(0.52f, 0.46f, 0.06f))),

        // 13 — moving bouncer and a bobbing target.
        Level(0.16f, 0.22f, 0.84f, 0.60f, targetRadius = 0.045f,
            obstacles = listOf(
                rect(0.5f, 0.40f, 0.24f, 0.03f, bouncy = true, mdy = 0.18f, mdur = 1.3f),
            ),
            targetMoveDy = 0.14f, targetMoveDuration = 1.4f),

        // 14 — FORCED bounce: off-center pocket, tighter, with a pillar to route around.
        Level(0.84f, 0.20f, 0.36f, 0.58f, targetRadius = 0.045f,
            obstacles = downPocket(0.36f, 0.58f, 0.30f) + listOf(
                circ(0.62f, 0.32f, 0.06f),
            ),
            hint = "Same idea, tighter."),

        // 15 — pillar gauntlet through two rings in order.
        Level(0.5f, 0.14f, 0.5f, 0.88f, targetRadius = 0.04f,
            obstacles = listOf(
                circ(0.35f, 0.45f, 0.06f),
                circ(0.65f, 0.66f, 0.06f),
            ),
            gates = listOf(Gate(0.5f, 0.30f, 0.055f), Gate(0.5f, 0.74f, 0.055f)),
            hint = "Both rings, in order."),

        // 16 — finale: forced-bounce pocket, gated.
        Level(0.5f, 0.13f, 0.5f, 0.70f, targetRadius = 0.04f,
            obstacles = downPocket(0.5f, 0.70f, 0.40f),
            gates = listOf(Gate(0.5f, 0.55f, 0.055f)),
            hint = "Last one. Good luck."),
    )

    /**
     * A quiet philosophical tale about the little ball we steer — one line per
     * level, continuing the story. Shown in italic, faint, at the bottom edge.
     */
    val stories: List<String> = listOf(
        "Au commencement, la bille ignorait qu'elle pouvait s'élancer.",
        "Une main invisible lui révéla qu'un ailleurs pouvait être un but.",
        "Elle apprit qu'un obstacle n'est qu'une raison de viser plus haut.",
        "Certaines parois, comprit-elle, rendent l'élan au lieu de le briser.",
        "Entre deux murs, elle fit l'apprentissage des passages étroits.",
        "Elle frôla le danger, et sut que tout élan a son prix.",
        "On lui demanda de passer par où il fallait, non par où c'était simple.",
        "Elle découvrit qu'on rejoint souvent un lieu par le détour.",
        "Ce qu'elle visait bougeait ; elle apprit alors à anticiper.",
        "Le temps aussi, dit-elle, demande à être visé juste.",
        "Pour entrer, il lui fallut renoncer à choir, et choisir de remonter.",
        "Une pente bien lue change une chute en envol.",
        "Rien n'est immobile ; viser, c'est épouser le mouvement.",
        "Plus le passage se resserre, plus le geste doit s'apaiser.",
        "Parmi les écueils, elle ne visait plus la cible, mais le trajet.",
        "Et lorsqu'elle toucha enfin, elle sut que le but n'avait été qu'un prétexte au voyage.",
    )
}
