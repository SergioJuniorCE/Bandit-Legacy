package cn.elytra.mod.bandit.common.player_data

import cn.elytra.mod.bandit.common.util.HasUnlocalizedName

/**
 * How the vein mining key behaves.
 */
enum class ActivationMode : HasUnlocalizedName {

    /**
     * Hold the key to arm vein mining, release it to stop. This is how FTB Ultimine behaves and the default.
     */
    HOLD {
        override fun getUnlocalizedName(): String = "bandit.activation-mode.hold"
    },

    /**
     * Press the key once to arm vein mining, press it again to disarm.
     */
    TOGGLE {
        override fun getUnlocalizedName(): String = "bandit.activation-mode.toggle"
    },
    ;

    val stopsOnKeyRelease: Boolean
        get() = this == HOLD

}
