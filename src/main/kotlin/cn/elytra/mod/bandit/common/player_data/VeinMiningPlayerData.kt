package cn.elytra.mod.bandit.common.player_data

import cn.elytra.mod.bandit.BanditConfig
import cn.elytra.mod.bandit.BanditMod
import cn.elytra.mod.bandit.common.BanditCoroutines
import cn.elytra.mod.bandit.common.mining.VeinMiningContext
import cn.elytra.mod.bandit.common.mining.VeinMiningContext.DropPosition
import cn.elytra.mod.bandit.common.mining.VeinMiningContext.DropTiming
import cn.elytra.mod.bandit.common.mining.VeinMiningDataSave
import cn.elytra.mod.bandit.common.mining.VeinMiningHandler
import cn.elytra.mod.bandit.common.util.lookDirection
import cn.elytra.mod.bandit.common.util.parseValueToEnum
import cn.elytra.mod.bandit.mining.BlockFilterRegistry
import cn.elytra.mod.bandit.mining.MiningRules
import cn.elytra.mod.bandit.mining.ExecutorGeneratorRegistry
import cn.elytra.mod.bandit.mining.exception.CommandCancellation
import cn.elytra.mod.bandit.mining.exception.FriendlyCancellationException
import cn.elytra.mod.bandit.mining.exception.KeyReleaseCancellation
import cn.elytra.mod.bandit.mining.exception.PlayerLeftCancellation
import cn.elytra.mod.bandit.mining.exception.ToolMaxDamage
import cn.elytra.mod.bandit.mining.executor.BlockPosCacheableExecutorGenerator
import cn.elytra.mod.bandit.mining.executor.VeinMiningExecutorGenerator
import cn.elytra.mod.bandit.mining.filter.VeinMiningBlockFilter
import cn.elytra.mod.bandit.network.BanditNetwork
import codechicken.lib.raytracer.RayTracer
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.util.MovingObjectPosition
import net.minecraftforge.common.util.ForgeDirection
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.properties.Delegates

data class VeinMiningPlayerData(
    val uuid: String,
) {

    private fun getPlayer(): EntityPlayer {
        return checkNotNull(getPlayerFromUUID(uuid)) { "Failed to find the player with UUID: $uuid" }
    }

    var currentJob: TypedJob? by Delegates.observable(null) { _, oldValue, newValue ->
        if(oldValue != newValue) {
            // cancel ongoing job
            oldValue?.job?.takeIf { it.isActive }?.cancel()
            // make new job unregister itself when completed
            newValue?.job?.invokeOnCompletion { currentJob = null }
        }
    }

    /**
     * @return `true` if there is a job running. `false` if the job is `null` or has ended for any reason, including completed or canceled.
     */
    val hasJobRunning: Boolean get() = currentJob?.job?.isActive == true

    var veinMiningExecutorId: Int by Delegates.observable(0) { _, oldValue, newValue ->
        if(oldValue != newValue) {
            save()
            precalculatedVeinBlocks = null
        }
    }

    var veinMiningBlockFilterId: Int by Delegates.observable(0) { _, oldValue, newValue ->
        if(oldValue != newValue) {
            save()
            precalculatedVeinBlocks = null
        }
    }

    var veinMiningKeyPressed: Boolean by Delegates.observable(false) { _, oldValue, newValue ->
        if(oldValue != newValue) {
            if(!newValue) { // key released
                // Releasing the key disarms the next action, but never interrupts one that has already started:
                // clicking a block is what commits to it, exactly like FTB Ultimine.
                currentJob
                    ?.takeIf { it.type == TypedJob.JobType.VeinMiningPrecalculating }
                    ?.job
                    ?.cancel(KeyReleaseCancellation())
                if(activationMode.stopsOnKeyRelease) {
                    precalculatedVeinBlocks = null
                }
            }
        }
    }

    var harvestedDropPosition: DropPosition by Delegates.observable(DropPosition.DROP_TO_PLAYER) { _, oldValue, newValue ->
        if(oldValue != newValue) {
            save()
        }
    }

    var harvestedDropTiming: DropTiming by Delegates.observable(DropTiming.ITEM_IMMEDIATELY_XP_EVENTUALLY) { _, oldValue, newValue ->
        if(oldValue != newValue) {
            save()
        }
    }

    var activationMode: ActivationMode by Delegates.observable(ActivationMode.HOLD) { _, oldValue, newValue ->
        if(oldValue != newValue) {
            save()
            precalculatedVeinBlocks = null
        }
    }

    /**
     * The world time, in ticks, when the last vein mining action of this player ended. Used for the cooldown.
     */
    internal var lastActionEndedAt: Long = 0L

    /**
     * Set while a right-click action (crop harvesting, tilling) is breaking blocks, so that the blocks it breaks
     * do not start a vein mining action of their own.
     */
    var directActionInProgress: Boolean = false

    /**
     * The hash of the context when the precalculate take place.
     * It is used to check if there is going to start another "SAME" precalculate.
     */
    internal var precalculatedHash: Int = -1

    /**
     * The cached block pos list with given context (Executor, Filter, Center Block Position and Metadata).
     */
    var precalculatedVeinBlocks: List<BlockPos>? by Delegates.observable(null) { _, oldValue, newValue ->
        if(oldValue != newValue) {
            val player = getPlayerFromUUID(uuid)?.asMP
            if(newValue == null) {
                precalculatedHash = -1
                if(player != null) BanditNetwork.syncBlockCacheToClient(player, null)
            } else {
                if(player != null) BanditNetwork.syncBlockCacheToClient(player, newValue)
            }
        }
    }

    companion object {
        const val TAG_EXECUTOR_GENERATOR = "VeinExecutorGeneratorData"
        const val TAG_BLOCK_FILTER = "VeinBlockFilterData"
        const val TAG_PREFERRED_DROP_POS = "HarvestedDropPosition"
        const val TAG_PREFERRED_DROP_TIMING = "HarvestedDropTiming"
        const val TAG_STOP_VEIN_MINING_ON_KEY_RELEASE = "StopVeinMiningOnKeyRelease"
        const val TAG_ACTIVATION_MODE = "ActivationMode"

        internal val InstanceMap = mutableMapOf<String, VeinMiningPlayerData>()

        fun getOrCreate(player: EntityPlayer): VeinMiningPlayerData {
            val uuidStr = player.uniqueID.toString()
            return InstanceMap.computeIfAbsent(uuidStr) { VeinMiningPlayerData(uuidStr) }
        }

        /**
         * Used to calculate the hash of cached vein blocks.
         */
        internal fun precalculatedHash(
            bp: BlockPos,
            block: Block,
            blockMeta: Int,
            direction: ForgeDirection,
        ): Int {
            return Objects.hash(bp, block, blockMeta, direction)
        }

        fun getPlayerFromUUID(uuid: String): EntityPlayer? {
            return MinecraftServer.getServer()
                .configurationManager
                .playerEntityList
                .firstOrNull { it.uniqueID.toString() == uuid }
        }
    }

    init {
        load()
    }

    internal fun load() {
        val nbt = VeinMiningDataSave.get().getPlayerData(uuid)
        readFromNBT(nbt)
    }

    internal fun save() {
        val nbt = NBTTagCompound().apply { writeToNBT(this) }
        VeinMiningDataSave.get().setPlayerData(uuid, nbt)
        VeinMiningDataSave.get().markDirty()
    }

    /**
     * Read from "WorldSavedData".
     */
    internal fun readFromNBT(nbtTag: NBTTagCompound) = apply {
        veinMiningExecutorId = nbtTag.getInteger(TAG_EXECUTOR_GENERATOR)
        veinMiningBlockFilterId = nbtTag.getInteger(TAG_BLOCK_FILTER)
        harvestedDropPosition = parseValueToEnum(
            nbtTag.getString(TAG_PREFERRED_DROP_POS),
            DropPosition.DROP_TO_PLAYER
        )
        harvestedDropTiming = parseValueToEnum(
            nbtTag.getString(TAG_PREFERRED_DROP_TIMING),
            DropTiming.ITEM_IMMEDIATELY_XP_EVENTUALLY
        )
        activationMode = when {
            nbtTag.hasKey(TAG_ACTIVATION_MODE) -> parseValueToEnum(nbtTag.getString(TAG_ACTIVATION_MODE), ActivationMode.HOLD)
            // migrate the 1.x boolean, where "stop on release" meant the same thing as hold mode
            nbtTag.hasKey(TAG_STOP_VEIN_MINING_ON_KEY_RELEASE) ->
                if(nbtTag.getBoolean(TAG_STOP_VEIN_MINING_ON_KEY_RELEASE)) ActivationMode.HOLD else ActivationMode.TOGGLE

            else -> ActivationMode.HOLD
        }
    }

    /**
     * Write to "WorldSavedData".
     */
    internal fun writeToNBT(nbtTag: NBTTagCompound) = apply {
        nbtTag.setInteger(TAG_EXECUTOR_GENERATOR, veinMiningExecutorId)
        nbtTag.setInteger(TAG_BLOCK_FILTER, veinMiningBlockFilterId)
        nbtTag.setString(TAG_PREFERRED_DROP_POS, harvestedDropPosition.name)
        nbtTag.setString(TAG_PREFERRED_DROP_TIMING, harvestedDropTiming.name)
        nbtTag.setString(TAG_ACTIVATION_MODE, activationMode.name)
    }

    fun getExecutorGenerator(): VeinMiningExecutorGenerator {
        return ExecutorGeneratorRegistry.getOrDefault(veinMiningExecutorId)
    }

    fun getBlockFilter(): VeinMiningBlockFilter {
        return BlockFilterRegistry.getOrDefault(veinMiningBlockFilterId)
    }

    /**
     * Precalculate the block pos list with current context.
     *
     * The executor generator must be [BlockPosCacheableExecutorGenerator].
     */
    fun precalculateVeinBlocks() {
        if(hasJobRunning) return

        val execGenerator = getExecutorGenerator()
        if(execGenerator !is BlockPosCacheableExecutorGenerator) return

        val player = getPlayer()
        val world = player.worldObj
        val mop = RayTracer.reTrace(world, player)
        // invalid raytrace hit type
        if(mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return

        val pos = BlockPos(mop.blockX, mop.blockY, mop.blockZ)
        val block = world.getBlock(mop.blockX, mop.blockY, mop.blockZ)
        val blockMeta = world.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ)
        val blockAndMeta = block to blockMeta
        val blockTileEntity = world.getTileEntity(mop.blockX, mop.blockY, mop.blockZ)

        val lookDirection = player.lookDirection()
        val hash = precalculatedHash(pos, block, blockMeta, lookDirection)
        if(precalculatedVeinBlocks != null) {
            if(hash == precalculatedHash) return // same cache hit
        }
        precalculatedHash = hash

        val executionId = VeinMiningHandler.executionCounter.andIncrement
        val context = VeinMiningContext(
            world = world,
            center = pos,
            blockAndMeta = blockAndMeta,
            blockTileEntity = blockTileEntity,
            playerSupplier = { getPlayer().asMP },
            filter = getBlockFilter(),
            executionId = executionId,
            veinMiningMaxCountLimit = BanditConfig.maxBlocksPerOperation,
            lookDirection = lookDirection,
        )

        BanditMod.logger.info("Start caching #${executionId}")
        val job = BanditCoroutines.VeinMiningScope.launch(start = CoroutineStart.LAZY) {
            val result = execGenerator.precalculateBlockPosList(context)
            precalculatedVeinBlocks = result
        }
        job.invokeOnCompletion {
            BanditMod.logger.info("Cached #${executionId}")
            if(it != null) {
                when(it) {
                    is FriendlyCancellationException -> {
                        /* ignored */
                    }

                    else -> BanditMod.logger.warn("Job was cancelled because of an throwable", it)
                }
            }
        }
        currentJob = TypedJob(job, TypedJob.JobType.VeinMiningPrecalculating)

        job.start()
    }

    fun startVeinMining(x: Int, y: Int, z: Int) {
        val currentJobNow = currentJob
        if(currentJobNow != null) {
            when(currentJobNow.type) {
                // stop precalculate job and continue to start vein mining
                TypedJob.JobType.VeinMiningPrecalculating -> currentJob = null
                // stop if there is another vein mining job
                TypedJob.JobType.VeinMining -> return
            }
        }

        val player = getPlayer()
        val world = player.worldObj

        if(isBlockedByCooldown(player)) return
        if(!passesActionRequirements(player)) return

        val pos = BlockPos(x, y, z)
        val block = world.getBlock(x, y, z)
        val blockMeta = world.getBlockMetadata(x, y, z)
        if(!MiningRules.isBlockAllowed(block, blockMeta)) {
            notifyRefused(player)
            return
        }
        val blockAndMeta = block to blockMeta
        val blockTileEntity = world.getTileEntity(x, y, z)
        val executionId = VeinMiningHandler.executionCounter.andIncrement

        // check hash, in case of MOP traced to a wrong block or a direction change.
        // if so, we need to recalculate the blocks now, and give up the cached ones.
        val lookDirection = player.lookDirection()
        val hash = precalculatedHash(pos, block, blockMeta, lookDirection)
        val precalculatedVeinBlocksChecked = if(hash == precalculatedHash) precalculatedVeinBlocks else null

        val context = VeinMiningContext(
            world = world,
            center = pos,
            blockAndMeta = blockAndMeta,
            blockTileEntity = blockTileEntity,
            playerSupplier = { getPlayer().asMP },
            filter = getBlockFilter(),
            executionId = executionId,
            precalculatedBlockPosList = precalculatedVeinBlocksChecked,
            veinMiningMaxCountLimit = BanditConfig.maxBlocksPerOperation,
            veinMiningCountPerOperation = BanditConfig.blocksPerOperation,
            harvestedDropPosition = harvestedDropPosition,
            harvestedDropTiming = harvestedDropTiming,
            exhaustionPerBlock = BanditConfig.exhaustionPerBlock,
            preventToolBreakAt = BanditConfig.preventToolBreakAt,
            requireValidTool = BanditConfig.requireValidToolForBlock,
            lookDirection = lookDirection,
        )
        val executor = getExecutorGenerator().generate(context)

        BanditMod.logger.info("Executing Vein Mining #${executionId}")
        BanditMod.logger.debug("Executing Vein Mining #${executionId} at (${pos.x}, ${pos.y}, ${pos.z} @ ${world.provider.dimensionId}) ref ${blockAndMeta.first.unlocalizedName} @ ${blockAndMeta.second} te ${blockTileEntity?.toString() ?: "null"}")
        val notices = mutableListOf<Long>()
        notices.add(BanditNetwork.pushSimpleNoticeToClient(player.asMP, VeinMiningNoticeType.TASK_STARTING))
        notices.add(BanditNetwork.pushSimpleNoticeToClient(player.asMP, VeinMiningNoticeType.TASK_HALT_HINT))

        val job = BanditCoroutines.VeinMiningScope.launch(start = CoroutineStart.LAZY) {
            world.playSoundEffect(player.posX, player.posY, player.posZ, "note.harp", 3.0F, 1.0F)
            executor()
        }
        job.invokeOnCompletion {
            BanditMod.logger.info("VeinMining #${executionId} has ended")

            for (noticeId in notices) {
                BanditNetwork.endNoticeToClient(player.asMP, noticeId, fadeDelay = 0, fadeTicks = 0)
            }

            when(it) {
                is KeyReleaseCancellation -> {
                    BanditNetwork.pushSimpleNoticeToClient(player.asMP,
                        noticeType = VeinMiningNoticeType.TASK_STOP_KEY_RELEASE,
                        fadeDelay = 40,
                        fadeTicks = 20
                    )
                }

                is CommandCancellation -> {
                    BanditNetwork.pushSimpleNoticeToClient(player.asMP,
                        noticeType = VeinMiningNoticeType.TASK_STOP_FOR_COMMAND,
                        fadeDelay = 40,
                        fadeTicks = 20
                    )
                }

                is ToolMaxDamage -> {
                    BanditNetwork.pushSimpleNoticeToClient(player.asMP,
                        noticeType = VeinMiningNoticeType.TASK_STOP_BECAUSE_TOOL_MAX_DAMAGE,
                        fadeDelay = 40,
                        fadeTicks = 20
                    )
                }

                is PlayerLeftCancellation -> {
                    BanditMod.logger.info("VeinMining #${executionId} has ended because the player left")
                }

                else -> BanditMod.logger.warn("Job was cancelled because of an throwable", it)
            }

            BanditNetwork.pushCompletionNoticeToClient(
                player.asMP,
                extraData = mapOf(
                    "statBlocksMined" to context.statBlocksMined.get(),
                    "statItemDropped" to context.statItemDropped.values.sum()
                ),
                fadeDelay = 40,
                fadeTicks = 20
            )

            // play sound effects
            world.playSoundEffect(player.posX, player.posY, player.posZ, "note.harp", 3.0F, 3.5F)
            // remember when this ended, the cooldown is measured from here
            lastActionEndedAt = world.totalWorldTime
            // clear caches
            precalculatedVeinBlocks = null
        }
        currentJob = TypedJob(job, TypedJob.JobType.VeinMining)

        job.start()
    }

    /**
     * @return `true` while the configured cooldown between two vein mining actions is still running.
     */
    private fun isBlockedByCooldown(player: EntityPlayer): Boolean {
        val cooldown = BanditConfig.cooldownTicks
        if(cooldown <= 0) return false
        return player.worldObj.totalWorldTime - lastActionEndedAt < cooldown
    }

    /**
     * Checks the tool requirement of the server configuration.
     */
    private fun passesActionRequirements(player: EntityPlayer): Boolean {
        if(BanditConfig.requireTool && !MiningRules.isHoldingTool(player)) {
            notifyRefused(player)
            return false
        }
        return true
    }

    /**
     * Tells the player that the action was refused, so that holding the key and getting nothing does not look like
     * a broken key binding.
     */
    private fun notifyRefused(player: EntityPlayer) {
        val mp = player as? EntityPlayerMP ?: return
        BanditNetwork.pushSimpleNoticeToClient(
            mp,
            VeinMiningNoticeType.TASK_REFUSED,
            fadeDelay = 20,
            fadeTicks = 20,
        )
    }

    fun stopJob(reason: String = "no reason") {
        currentJob?.job?.cancel("stopping for $reason")
    }

    /**
     * Cancel the ongoing job for the player.
     *
     * It's safe to invoke this even there's no ongoing job for the player.
     *
     * Exceptions except [FriendlyCancellationException] are handled as errors.
     */
    internal fun cancelJob(cause: CancellationException?) {
        currentJob?.job?.cancel(cause)
    }

    fun stopAndClear(cause: CancellationException? = null) {
        if (cause != null) {
            cancelJob(cause)
        }
        else{
            stopJob()
        }
        veinMiningKeyPressed = false
        InstanceMap -= this.uuid
    }

}

internal val EntityPlayer.veinMiningData: VeinMiningPlayerData get() = VeinMiningPlayerData.getOrCreate(this)

internal val EntityPlayer.asMP: EntityPlayerMP get() = this as EntityPlayerMP
