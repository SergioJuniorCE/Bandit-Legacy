package cn.elytra.mod.bandit.common.listener

import cn.elytra.mod.bandit.BanditConfig
import cn.elytra.mod.bandit.BanditMod
import cn.elytra.mod.bandit.common.player_data.VeinMiningNoticeType
import cn.elytra.mod.bandit.common.player_data.veinMiningData
import cn.elytra.mod.bandit.mining.MiningRules
import cn.elytra.mod.bandit.network.BanditNetwork
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import net.minecraft.block.Block
import net.minecraft.block.BlockCrops
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemHoe
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.event.entity.player.PlayerInteractEvent

/**
 * The right-click half of the vein mining key, the same actions FTB Ultimine offers: harvest a patch of crops and
 * till a patch of dirt with a hoe.
 *
 * Both actions are small and bounded, so they run on the server thread right away instead of going through a
 * coroutine job. [cn.elytra.mod.bandit.common.player_data.VeinMiningPlayerData.directActionInProgress] keeps the
 * block breaking listener from treating the harvest as a fresh vein mining trigger.
 */
@Suppress("unused")
@EventBusSubscriber
class VeinMiningActionListener {

    companion object {

        @JvmStatic
        @SubscribeEvent
        fun onRightClickBlock(e: PlayerInteractEvent) {
            if(e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return

            val p = e.entityPlayer
            if(p !is EntityPlayerMP || p is FakePlayer) return

            val data = p.veinMiningData
            if(!data.veinMiningKeyPressed || data.hasJobRunning || data.directActionInProgress) return

            val heldItem = p.currentEquippedItem
            if(BanditConfig.rightClickTill && heldItem != null && heldItem.item is ItemHoe) {
                if(tillArea(p, e.x, e.y, e.z)) e.isCanceled = true
                return
            }

            if(BanditConfig.rightClickHarvest) {
                val block = p.worldObj.getBlock(e.x, e.y, e.z)
                if(block is BlockCrops) {
                    if(harvestArea(p, block, e.x, e.y, e.z)) e.isCanceled = true
                }
            }
        }

        /**
         * Tills every dirt and grass block of the configured radius around the clicked block, as long as the hoe
         * does not run out of durability.
         */
        private fun tillArea(p: EntityPlayerMP, x: Int, y: Int, z: Int): Boolean {
            val world = p.worldObj
            val stack = p.currentEquippedItem ?: return false
            if(!isUsable(stack)) return false

            val radius = BanditConfig.tillRadius
            var tilled = 0
            p.veinMiningData.directActionInProgress = true
            try {
                for(dx in -radius..radius) {
                    for(dz in -radius..radius) {
                        val bx = x + dx
                        val bz = z + dz
                        val block = world.getBlock(bx, y, bz)
                        if(block !== Blocks.dirt && block !== Blocks.grass) continue
                        if(!world.isAirBlock(bx, y + 1, bz)) continue
                        if(!isUsable(stack)) break

                        world.setBlock(bx, y, bz, Blocks.farmland, 0, 3)
                        world.playSoundEffect(
                            bx + 0.5, y + 0.5, bz + 0.5,
                            Blocks.farmland.stepSound.getStepResourcePath(),
                            (Blocks.farmland.stepSound.getVolume() + 1.0f) / 2.0f,
                            Blocks.farmland.stepSound.getPitch() * 0.8f,
                        )
                        stack.damageItem(1, p)
                        tilled++
                    }
                }
            } finally {
                p.veinMiningData.directActionInProgress = false
            }

            if(tilled > 0) {
                BanditNetwork.pushSimpleNoticeToClient(
                    p,
                    VeinMiningNoticeType.TASK_HARVEST_DONE,
                    extraData = mapOf("harvested" to tilled),
                    fadeDelay = 20,
                    fadeTicks = 20,
                )
            }
            return tilled > 0
        }

        /**
         * Harvests the connected, fully grown crops of the same kind, and replants them when the player has seeds.
         */
        private fun harvestArea(p: EntityPlayerMP, cropBlock: Block, x: Int, y: Int, z: Int): Boolean {
            val world = p.worldObj
            val limit = BanditConfig.maxBlocksPerOperation

            var harvested = 0
            val visited = HashSet<Long>()
            val queue = ArrayDeque<Long>()
            queue.addLast(CoordinatePacker.pack(x, y, z))

            p.veinMiningData.directActionInProgress = true
            try {
                while(queue.isNotEmpty() && harvested < limit) {
                    val packed = queue.removeFirst()
                    if(!visited.add(packed)) continue

                    val px = CoordinatePacker.unpackX(packed)
                    val py = CoordinatePacker.unpackY(packed)
                    val pz = CoordinatePacker.unpackZ(packed)

                    val block = world.getBlock(px, py, pz)
                    if(block !== cropBlock) continue
                    val meta = world.getBlockMetadata(px, py, pz)
                    if(meta < 7) continue
                    if(!MiningRules.isBlockAllowed(block, meta)) continue

                    p.theItemInWorldManager.tryHarvestBlock(px, py, pz)
                    harvested++
                    if(BanditConfig.rightClickReplant) {
                        replant(world, cropBlock, px, py, pz, p)
                    }

                    for(direction in ForgeDirection.VALID_DIRECTIONS) {
                        queue.addLast(
                            CoordinatePacker.pack(
                                px + direction.offsetX,
                                py + direction.offsetY,
                                pz + direction.offsetZ,
                            )
                        )
                    }
                }
            } finally {
                p.veinMiningData.directActionInProgress = false
            }

            if(harvested > 0) {
                BanditMod.logger.debug("Harvested {} crop blocks for {}", harvested, p.commandSenderName)
                BanditNetwork.pushSimpleNoticeToClient(
                    p,
                    VeinMiningNoticeType.TASK_HARVEST_DONE,
                    extraData = mapOf("harvested" to harvested),
                    fadeDelay = 20,
                    fadeTicks = 20,
                )
            }
            return harvested > 0
        }

        private fun replant(world: World, cropBlock: Block, x: Int, y: Int, z: Int, p: EntityPlayerMP) {
            val seed = seedOf(cropBlock) ?: return
            if(world.getBlock(x, y, z) !== Blocks.air) return
            if(!p.inventory.consumeInventoryItem(seed)) return
            world.setBlock(x, y, z, cropBlock, 0, 3)
        }

        private fun seedOf(cropBlock: Block): Item? = when (cropBlock) {
            Blocks.wheat -> Items.wheat_seeds
            Blocks.carrots -> Items.carrot
            Blocks.potatoes -> Items.potato
            else -> null
        }

        private fun isUsable(stack: ItemStack): Boolean {
            if(!stack.isItemStackDamageable) return true
            return stack.maxDamage - stack.itemDamage > BanditConfig.preventToolBreakAt
        }

    }
}
