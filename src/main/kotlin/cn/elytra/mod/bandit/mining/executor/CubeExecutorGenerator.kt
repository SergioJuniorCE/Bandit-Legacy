package cn.elytra.mod.bandit.mining.executor

import cn.elytra.mod.bandit.BanditConfig
import cn.elytra.mod.bandit.common.mining.VeinMiningContext
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mines a cube around the block the player broke, starting at that block and growing outwards.
 */
class CubeExecutorGenerator(
    private val name: String,
    private val radius: Int = 1,
) : ShapedExecutorGenerator() {

    override fun getUnlocalizedName(): String = name

    override fun createSequence(context: VeinMiningContext): Flow<BlockPos> = flow {
        val center = context.center
        val candidates = ArrayList<BlockPos>()
        for(dx in -radius..radius) {
            for(dy in -radius..radius) {
                for(dz in -radius..radius) {
                    candidates.add(BlockPos(center.x + dx, center.y + dy, center.z + dz))
                }
            }
        }
        candidates.sortBy {
            val dx = it.x - center.x
            val dy = it.y - center.y
            val dz = it.z - center.z
            dx * dx + dy * dy + dz * dz
        }
        for(pos in candidates) {
            if(isBlockMatchingVein(context, pos)) emit(pos)
        }
    }

}
