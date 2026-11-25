package cn.elytra.mod.bandit.mining.filter

import cn.elytra.mod.bandit.common.mining.VeinMiningContext
import cn.elytra.mod.bandit.common.util.HasUnlocalizedName
import cn.elytra.mod.bandit.mining.MiningRules
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos
import net.minecraft.world.World

interface VeinMiningBlockFilter : HasUnlocalizedName {

    /**
     * Filter the blocks to harvest. You can get [World] in [context], because the vein mining jobs
     * are not crossing dimensions.
     *
     * @return `true` to harvest the block at the given [pos].
     */
    fun isBlockMatching(context: VeinMiningContext, pos: BlockPos): Boolean

    companion object {

        val ALL = DelegatedBlockFilter("bandit.block-filter.all") { _, _ -> true }

        @Suppress("unused")
        val NONE = DelegatedBlockFilter("bandit.block-filter.none") { _, _ -> false }

        val MATCH_BLOCK = DelegatedBlockFilter("bandit.block-filter.match-block") { context, pos ->
            context.blockAndMeta.first == context.world.getBlock(pos.x, pos.y, pos.z)
        }

        val MATCH_BLOCK_AND_META = DelegatedBlockFilter("bandit.block-filter.match-block-and-meta") { context, pos ->
            context.blockAndMeta.first == context.world.getBlock(pos.x, pos.y, pos.z) &&
                context.blockAndMeta.second == context.world.getBlockMetadata(pos.x, pos.y, pos.z)
        }

        /**
         * Follows a whole ore vein: blocks that are registered under the same ore dictionary entry are treated as
         * the same block. This is the GTNH flavored answer to FTB Ultimine's `forge:ores` merge tag, and it is what
         * makes GT ore blocks of different metadata come out in one go.
         */
        val MATCH_ORE_DICTIONARY = DelegatedBlockFilter("bandit.block-filter.match-ore-dictionary") { context, pos ->
            val (centerBlock, centerMeta) = context.blockAndMeta
            MiningRules.sharesOreDictionary(
                centerBlock,
                centerMeta,
                context.world.getBlock(pos.x, pos.y, pos.z),
                context.world.getBlockMetadata(pos.x, pos.y, pos.z),
            )
        }
    }
}
