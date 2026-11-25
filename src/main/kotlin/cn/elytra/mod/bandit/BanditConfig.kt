package cn.elytra.mod.bandit

import cpw.mods.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.common.config.Configuration

/**
 * Server-side configuration of Bandit Legacy.
 *
 * Everything that decides *how much* a single vein mining action is allowed to do is configured here, so that a
 * server owner can balance the mod without touching the client. Client-only preferences (HUD position, outline
 * rendering, ...) live in [cn.elytra.mod.bandit.client.VeinMiningConfigClient].
 */
object BanditConfig {

    lateinit var config: Configuration

    fun init(e: FMLPreInitializationEvent) {
        config = Configuration(e.suggestedConfigurationFile)

        // run all the configuration getters, so that the configuration is properly set up
        try {
            this::class.java.declaredMethods
                .filter { it.isAnnotationPresent(ConfigGetter::class.java) }
                .filter { it.parameterCount == 0 }
                .forEach {
                    runCatching {
                        it.invoke(this)
                        BanditMod.logger.debug("Invoked ${it.name}")
                    }.onFailure { e ->
                        BanditMod.logger.error("Failed to invoke ${it.name}", e)
                    }
                }
        } catch(e: Exception) {
            BanditMod.logger.error("Failed to initialize configuration", e)
        }

        // and save it when we've done
        config.save()
    }

    @Retention(AnnotationRetention.RUNTIME)
    private annotation class ConfigGetter

    private const val EXECUTOR = "executor"
    private const val LIMITS = "limits"
    private const val COSTS = "costs"
    private const val MATCHING = "matching"
    private const val FEATURES = "features"

    // ---------------------------------------------------------------------------------------------
    // Executors
    // ---------------------------------------------------------------------------------------------

    val manhattanRadius: Int
        @ConfigGetter get() = config.getInt(
            "manhattan-radius", EXECUTOR, 8, 1, Int.MAX_VALUE,
            "The radius (in blocks) the connected-vein executors scan within."
        )

    val manhattanLargeRadius: Int
        @ConfigGetter get() = config.getInt(
            "manhattan-large-radius", EXECUTOR, 16, 1, Int.MAX_VALUE,
            "The radius (in blocks) of the large connected-vein executor."
        )

    val largeScanRadiusXZ: Int
        @ConfigGetter get() = config.getInt(
            "large-scan-radius-xz", EXECUTOR, 32, 1, Int.MAX_VALUE,
            "Horizontal radius of the large-scan executor."
        )

    val largeScanRadiusY: Int
        @ConfigGetter get() = config.getInt(
            "large-scan-radius-y", EXECUTOR, 32, 1, Int.MAX_VALUE,
            "Vertical radius of the large-scan executor."
        )

    val tunnelLength: Int
        @ConfigGetter get() = config.getInt(
            "tunnel-length", EXECUTOR, 16, 1, 256,
            "How deep the tunnel-shaped executors dig, in blocks."
        )

    val cubeRadius: Int
        @ConfigGetter get() = config.getInt(
            "cube-radius", EXECUTOR, 1, 0, 3,
            "Radius of the cube executor, 1 means a 3x3x3 cube."
        )

    // ---------------------------------------------------------------------------------------------
    // Limits
    // ---------------------------------------------------------------------------------------------

    val maxBlocksPerOperation: Int
        @ConfigGetter get() = config.getInt(
            "max-blocks", LIMITS, 1024, 1, 32768,
            "The hard upper bound of blocks a single vein mining action may harvest.",
            "The player stops mining once this many blocks have been broken."
        )

    val blocksPerOperation: Int
        @ConfigGetter get() = config.getInt(
            "blocks-per-operation", LIMITS, 64, 1, 1024,
            "How many blocks are harvested per server tick. Lower values spread the work (and the lag) out."
        )

    val cooldownTicks: Int
        @ConfigGetter get() = config.getInt(
            "cooldown-ticks", LIMITS, 0, 0, Int.MAX_VALUE,
            "Ticks the player has to wait between two vein mining actions. 0 disables the cooldown."
        )

    // ---------------------------------------------------------------------------------------------
    // Costs
    // ---------------------------------------------------------------------------------------------

    val requireTool: Boolean
        @ConfigGetter get() = config.getBoolean(
            "require-tool", COSTS, true,
            "Only allow vein mining while holding a tool, i.e. an item with durability."
        )

    val requireValidToolForBlock: Boolean
        @ConfigGetter get() = config.getBoolean(
            "require-valid-tool-for-block", COSTS, true,
            "Skip blocks the held tool cannot harvest properly, instead of breaking them slowly."
        )

    val preventToolBreakAt: Int
        @ConfigGetter get() = config.getInt(
            "prevent-tool-break-at", COSTS, 1, 0, Int.MAX_VALUE,
            "Stop vein mining once the held tool has this much durability left.",
            "1 means the tool never breaks, 0 disables the check."
        )

    val exhaustionPerBlock: Double
        @ConfigGetter get() = config.get(
            COSTS, "exhaustion-per-block", 0.0,
            "Hunger exhaustion added to the player per block harvested. Vanilla sprinting costs 0.1 per tick.\n" +
                "0 disables the extra hunger cost."
        ).setMinValue(0.0).setMaxValue(1000.0).getDouble(0.0)

    // ---------------------------------------------------------------------------------------------
    // Matching
    // ---------------------------------------------------------------------------------------------

    val blockBlacklist: List<String>
        @ConfigGetter get() = config.getStringList(
            "block-blacklist", MATCHING, emptyArray(),
            "Blocks that can never be vein mined. Accepts block registry names such as minecraft:stone,\n" +
                "and ore dictionary entries written as oredict:oreIron."
        ).toList()

    val blockWhitelist: List<String>
        @ConfigGetter get() = config.getStringList(
            "block-whitelist", MATCHING, emptyArray(),
            "If this list is not empty, only these blocks can be vein mined. Same syntax as the blacklist."
        ).toList()

    val shapedSkipTileEntities: Boolean
        @ConfigGetter get() = config.getBoolean(
            "shaped-skip-tile-entities", MATCHING, true,
            "Shaped executors (tunnels, cubes) skip blocks that carry a tile entity, such as GT machines and\n" +
                "pipes, unless the block is the same one the player started on. Keeps a 3x3 tunnel from eating a base."
        )

    // ---------------------------------------------------------------------------------------------
    // Features
    // ---------------------------------------------------------------------------------------------

    val rightClickHarvest: Boolean
        @ConfigGetter get() = config.getBoolean(
            "right-click-harvest", FEATURES, true,
            "Harvest fully grown crops with the vein mining key held down."
        )

    val rightClickReplant: Boolean
        @ConfigGetter get() = config.getBoolean(
            "right-click-replant", FEATURES, true,
            "Replant harvested crops when the player has the matching seed."
        )

    val rightClickTill: Boolean
        @ConfigGetter get() = config.getBoolean(
            "right-click-till", FEATURES, true,
            "Till a small area of dirt and grass with a hoe while the vein mining key is held down."
        )

    val tillRadius: Int
        @ConfigGetter get() = config.getInt(
            "till-radius", FEATURES, 1, 0, 4,
            "Radius of the area tilled by a single hoe right-click."
        )

}
