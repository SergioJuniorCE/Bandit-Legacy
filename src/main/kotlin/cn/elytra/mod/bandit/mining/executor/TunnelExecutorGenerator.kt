package cn.elytra.mod.bandit.mining.executor

import cn.elytra.mod.bandit.BanditConfig
import cn.elytra.mod.bandit.common.mining.VeinMiningContext
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.minecraftforge.common.util.ForgeDirection

/**
 * Digs a tunnel away from the player, along the direction they are looking at.
 *
 * @param width the horizontal size of the tunnel cross section
 * @param height the vertical size of the tunnel cross section
 * @param riseEvery when greater than zero, the tunnel climbs one block every this many blocks,
 *                  which is how the escape tunnel gets you out of a cave
 */
class TunnelExecutorGenerator(
    private val name: String,
    private val width: Int = 3,
    private val height: Int = 3,
    private val riseEvery: Int = 0,
) : ShapedExecutorGenerator() {

    override fun getUnlocalizedName(): String = name

    override fun createSequence(context: VeinMiningContext): Flow<BlockPos> = flow {
        val center = context.center
        // the block the player started on is always part of the tunnel
        emit(center)

        val direction = context.lookDirection.let {
            if(it == ForgeDirection.UNKNOWN) ForgeDirection.SOUTH else it
        }
        val length = BanditConfig.tunnelLength
        val halfWidth = width / 2
        val halfHeight = height / 2

        if(direction == ForgeDirection.UP || direction == ForgeDirection.DOWN) {
            for(step in 1..length) {
                val y = center.y + direction.offsetY * step
                for(dx in -halfWidth..halfWidth) {
                    for(dz in -halfWidth..halfWidth) {
                        val pos = BlockPos(center.x + dx, y, center.z + dz)
                        if(isBlockMatchingVein(context, pos)) emit(pos)
                    }
                }
            }
            return@flow
        }

        // horizontal tunnels run along one axis, so the cross section spans the other two
        val alongX = direction.offsetX != 0
        for(step in 1..length) {
            val rise = if(riseEvery > 0) step / riseEvery else 0
            val baseX = center.x + direction.offsetX * step
            val baseY = center.y + rise
            val baseZ = center.z + direction.offsetZ * step
            for(side in -halfWidth..halfWidth) {
                for(up in -halfHeight..halfHeight) {
                    val x = if(alongX) baseX else baseX + side
                    val z = if(alongX) baseZ + side else baseZ
                    val pos = BlockPos(x, baseY + up, z)
                    if(isBlockMatchingVein(context, pos)) emit(pos)
                }
            }
        }
    }

}
