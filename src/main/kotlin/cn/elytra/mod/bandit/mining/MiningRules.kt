package cn.elytra.mod.bandit.mining

import cn.elytra.mod.bandit.BanditConfig
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary
import java.util.concurrent.ConcurrentHashMap

/**
 * Central place for the questions that decide whether a block may be vein mined at all.
 *
 * The rules come from the server configuration, and are cached so that checking a few thousand blocks per vein
 * mining action stays cheap.
 */
object MiningRules {

    private const val ORE_DICT_PREFIX = "oredict:"

    /** Cached, compiled form of a configured block list. */
    private class BlockList(list: List<String>) {

        private val exactNames = HashSet<String>()
        private val namespaceWildcards = ArrayList<String>()
        private val oreNames = HashSet<String>()

        init {
            for (raw in list) {
                val entry = raw.trim().lowercase()
                if(entry.isEmpty()) continue
                when {
                    entry.startsWith(ORE_DICT_PREFIX) ->
                        oreNames.add(entry.removePrefix(ORE_DICT_PREFIX))

                    entry.endsWith(":*") ->
                        namespaceWildcards.add(entry.removeSuffix("*"))

                    else -> exactNames.add(entry)
                }
            }
        }

        val isEmpty: Boolean
            get() = exactNames.isEmpty() && namespaceWildcards.isEmpty() && oreNames.isEmpty()

        fun matches(block: Block, meta: Int): Boolean {
            if(isEmpty) return false
            val name = blockName(block) ?: return false
            if(name in exactNames) return true
            if(namespaceWildcards.any { name.startsWith(it) }) return true
            if(oreNames.isNotEmpty()) {
                val ids = oreIds(block, meta)
                for(id in ids) {
                    if(OreDictionary.getOreName(id).lowercase() in oreNames) return true
                }
            }
            return false
        }
    }

    private var blacklist: BlockList? = null
    private var whitelist: BlockList? = null

    private val nameCache = ConcurrentHashMap<Block, String>()
    private val oreIdCache = ConcurrentHashMap<Long, IntArray>()

    /** Drop the cached rules, so that the next query reads the configuration again. */
    fun invalidate() {
        blacklist = null
        whitelist = null
        nameCache.clear()
        oreIdCache.clear()
    }

    private fun blacklist(): BlockList = blacklist ?: BlockList(BanditConfig.blockBlacklist).also { blacklist = it }

    private fun whitelist(): BlockList = whitelist ?: BlockList(BanditConfig.blockWhitelist).also { whitelist = it }

    private fun blockName(block: Block): String? {
        return nameCache.getOrPut(block) {
            Block.blockRegistry.getNameForObject(block)?.lowercase() ?: ""
        }.takeIf { it.isNotEmpty() }
    }

    private fun oreIds(block: Block, meta: Int): IntArray {
        val key = (Block.getIdFromBlock(block).toLong() shl 32) or (meta.toLong() and 0xFFFFFFFFL)
        return oreIdCache.getOrPut(key) {
            val item = Item.getItemFromBlock(block)
            if(item == null) IntArray(0) else OreDictionary.getOreIDs(ItemStack(item, 1, meta))
        }
    }

    /** @return `true` if the configured blacklist and whitelist allow this block to be mined. */
    fun isBlockAllowed(block: Block, meta: Int): Boolean {
        if(blacklist().matches(block, meta)) return false
        val whitelist = whitelist()
        return whitelist.isEmpty || whitelist.matches(block, meta)
    }

    /**
     * @return `true` if the block is either identical to the center block of the action, or shares an ore dictionary
     * entry with it. This is the 1.7.10 equivalent of FTB Ultimine's `forge:ores` merge tag.
     */
    fun sharesOreDictionary(centerBlock: Block, centerMeta: Int, block: Block, meta: Int): Boolean {
        if(centerBlock === block && centerMeta == meta) return true
        val centerIds = oreIds(centerBlock, centerMeta)
        if(centerIds.isEmpty()) return false
        val ids = oreIds(block, meta)
        if(ids.isEmpty()) return false
        for(centerId in centerIds) {
            for(id in ids) {
                if(centerId == id) return true
            }
        }
        return false
    }

    /** @return `true` if the item in the main hand is a tool, i.e. an item that can take damage. */
    fun isHoldingTool(player: EntityPlayer): Boolean {
        val stack = player.currentEquippedItem ?: return false
        return stack.isItemStackDamageable
    }

    /** @return `true` if the held tool is able to harvest the given block properly. */
    fun canHarvestWithHeldTool(player: EntityPlayer, block: Block): Boolean {
        val stack = player.currentEquippedItem ?: return false
        return stack.getItem().canHarvestBlock(block, stack)
    }

    /**
     * @return `true` if the held tool still has more durability left than the configured reserve.
     */
    fun hasUsableDurability(player: EntityPlayer, reserve: Int): Boolean {
        val stack = player.currentEquippedItem ?: return false
        if(!stack.isItemStackDamageable) return true
        return stack.maxDamage - stack.itemDamage > reserve
    }

}
