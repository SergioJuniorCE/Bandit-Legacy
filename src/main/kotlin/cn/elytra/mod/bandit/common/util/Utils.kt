package cn.elytra.mod.bandit.common.util

import gnu.trove.map.hash.TCustomHashMap
import gnu.trove.strategy.HashingStrategy
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection
import java.util.*

internal inline fun <reified T : Enum<T>> parseValueToEnum(value: String): T? {
    return enumValues<T>().firstOrNull { it.name.lowercase() == value.lowercase() }
}

internal inline fun <reified T : Enum<T>> parseValueToEnum(value: String, defaultValue: T): T {
    return enumValues<T>().firstOrNull { it.name.lowercase() == value.lowercase() } ?: defaultValue
}

/**
 * @return the direction the player is looking at, snapped to the six axis directions.
 */
internal fun EntityPlayer.lookDirection(): ForgeDirection {
    if(rotationPitch > 45.0f) return ForgeDirection.DOWN
    if(rotationPitch < -45.0f) return ForgeDirection.UP

    // Minecraft yaw: 0 is south (+Z), 90 is west (-X), 180 is north (-Z) and -90 is east (+X)
    var yaw = rotationYaw % 360.0f
    if(yaw < 0) yaw += 360.0f
    return when {
        yaw >= 315.0f || yaw < 45.0f -> ForgeDirection.SOUTH
        yaw < 135.0f -> ForgeDirection.WEST
        yaw < 225.0f -> ForgeDirection.NORTH
        else -> ForgeDirection.EAST
    }
}

object ItemStackHashingStrategy : HashingStrategy<ItemStack> {
    @Suppress("unused")
    private fun readResolve(): Any = ItemStackHashingStrategy

    override fun computeHashCode(itemstack: ItemStack?): Int {
        return when(itemstack) {
            null -> 0
            else -> Objects.hash(itemstack.item, itemstack.itemDamage, itemstack.tagCompound)
        }
    }

    override fun equals(itemstack1: ItemStack?, itemstack2: ItemStack?): Boolean {
        return ItemStack.areItemStacksEqual(itemstack1, itemstack2)
    }
}

internal fun <T> newItemStackHashMap(): MutableMap<ItemStack, T> {
    return TCustomHashMap<ItemStack, T>(ItemStackHashingStrategy)
}
