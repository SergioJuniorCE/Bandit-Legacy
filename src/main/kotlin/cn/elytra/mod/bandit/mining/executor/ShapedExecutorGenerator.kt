package cn.elytra.mod.bandit.mining.executor

import cn.elytra.mod.bandit.BanditConfig
import cn.elytra.mod.bandit.common.mining.VeinMiningContext
import cn.elytra.mod.bandit.mining.MiningRules
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos

/**
 * Base of the executors that mine a fixed geometric shape instead of a connected vein, mirroring the shaped mining
 * modes of FTB Ultimine.
 *
 * Unlike the vein executors these do not consult the selected block filter: a tunnel digs whatever is in its way,
 * limited by the blacklist, the whitelist, the tool and [BanditConfig.shapedSkipTileEntities].
 */
abstract class ShapedExecutorGenerator : SequencedVeinMiningExecutorGenerator() {

    final override fun isBlockMatchingVein(context: VeinMiningContext, pos: BlockPos): Boolean {
        val world = context.world
        val block = world.getBlock(pos.x, pos.y, pos.z) ?: return false
        if(block.isAir(world, pos.x, pos.y, pos.z)) return false
        if(block.getBlockHardness(world, pos.x, pos.y, pos.z) < 0.0f) return false

        val meta = world.getBlockMetadata(pos.x, pos.y, pos.z)
        if(!MiningRules.isBlockAllowed(block, meta)) return false

        if(context.requireValidTool && !MiningRules.canHarvestWithHeldTool(context.getPlayer(), block)) {
            return false
        }

        // never eat machines, chests or pipe networks by accident; the block the player started on is exempt,
        // so a GT ore vein can still be mined with a shaped executor
        if(BanditConfig.shapedSkipTileEntities && world.getTileEntity(pos.x, pos.y, pos.z) != null) {
            val (centerBlock, centerMeta) = context.blockAndMeta
            if(block !== centerBlock || meta != centerMeta) return false
        }

        return true
    }

}
