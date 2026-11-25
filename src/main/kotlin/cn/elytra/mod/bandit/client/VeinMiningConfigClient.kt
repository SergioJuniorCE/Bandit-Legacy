package cn.elytra.mod.bandit.client

import cpw.mods.fml.client.config.IConfigElement
import cpw.mods.fml.common.Loader
import net.minecraftforge.common.config.ConfigElement
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.common.config.Property
import java.io.File

object VeinMiningConfigClient {

    val config = Configuration(File(Loader.instance().configDir, "bandit-client.cfg"))

    var menuMarginX = 40
    var menuMarginY = 40

    var menuPosition = MenuPosition.TOP_LEFT

    /** Max amount of blocks the selection outline is drawn for. 0 disables the outline. */
    var outlineBlockLimit = 512

    /** Alpha (0-255) of the selection outline. */
    var outlineAlpha = 160

    /** Width of the selection outline lines. */
    var outlineLineWidth = 2.0f

    /** Whether the amount of selected blocks is shown next to the HUD. */
    var showBlockCount = true

    private val propMarginX = config.get("client", "menu-margin-x", 40)
    private val propMarginY = config.get("client", "menu-margin-y", 40)

    private val propMenuPosition = config.getAsEnum("client", "menu-position", MenuPosition.TOP_LEFT)
    private val propOutlineBlockLimit = config.get(
        "client", "outline-block-limit", 512,
        "How many blocks the selection outline is rendered for. 0 disables the outline."
    ).setMinValue(0)
    private val propOutlineAlpha = config.get(
        "client", "outline-alpha", 160,
        "Alpha of the selection outline, from 0 (invisible) to 255 (solid)."
    ).setMinValue(0).setMaxValue(255)
    private val propOutlineLineWidth = config.get(
        "client", "outline-line-width", 2.0,
        "Width of the selection outline lines."
    ).setMinValue(0.5).setMaxValue(8.0)
    private val propShowBlockCount = config.get(
        "client", "show-block-count", true,
        "Show how many blocks the current selection would mine."
    )

    fun reload() {
        menuMarginX = propMarginX.getInt(menuMarginX)
        menuMarginY = propMarginY.getInt(menuMarginY)
        menuPosition = try {
            enumValueOf<MenuPosition>(propMenuPosition.string)
        } catch(_: Exception) {
            propMenuPosition.set(MenuPosition.TOP_LEFT.name)
            MenuPosition.TOP_LEFT
        }
        outlineBlockLimit = propOutlineBlockLimit.getInt(outlineBlockLimit)
        outlineAlpha = propOutlineAlpha.getInt(outlineAlpha)
        outlineLineWidth = propOutlineLineWidth.getDouble(outlineLineWidth.toDouble()).toFloat()
        showBlockCount = propShowBlockCount.getBoolean(showBlockCount)
    }

    fun save() {
        config.save()
    }

    internal inline fun <reified T : Enum<T>> Configuration.getAsEnum(category: String, key: String, defaultValue: T): Property {
        return get(category, key, defaultValue.name).apply {
            setValidValues(enumValues<T>().map { it.name }.toTypedArray())
            comment = "[valid values: ${validValues.joinToString()}]"
        }
    }

    enum class MenuPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    fun listElements(): List<IConfigElement<*>> = ConfigElement<Any>(config.getCategory("client")).childElements

}
