package cn.elytra.mod.bandit.common.command

import cn.elytra.mod.bandit.BanditConfig
import cn.elytra.mod.bandit.common.mining.VeinMiningContext.DropPosition
import cn.elytra.mod.bandit.common.mining.VeinMiningContext.DropTiming
import cn.elytra.mod.bandit.common.player_data.ActivationMode
import cn.elytra.mod.bandit.common.player_data.veinMiningData
import cn.elytra.mod.bandit.common.util.parseValueToEnum
import cn.elytra.mod.bandit.mining.BlockFilterRegistry
import cn.elytra.mod.bandit.mining.ExecutorGeneratorRegistry
import cn.elytra.mod.bandit.mining.exception.CommandCancellation
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.ChatComponentText
import net.minecraft.util.ChatComponentTranslation

object BanditCommand : CommandBase() {
    override fun getCommandName(): String = "bandit"
    override fun getCommandUsage(sender: ICommandSender?): String = "command.bandit.usage"

    override fun getRequiredPermissionLevel(): Int = 0

    private val SUB_COMMANDS = listOf(
        "executor", "filter", "mode", "limits",
        "drop_pos", "drop_timing", "stop_on_release", "stop", "help",
    )

    override fun processCommand(
        sender: ICommandSender,
        argsArray: Array<String>,
    ) {
        val args = argsArray.toMutableList()

        when(args.removeFirstOrNull()) {
            "executor", "executor-generator" -> handleExecutorSettings(sender, args)
            "filter", "block-filter" -> handleBlockFilterSettings(sender, args)
            "mode" -> handleMode(sender, args)
            "limits", "limit" -> handleLimits(sender)
            "stop", "halt" -> handleHaltRequest(sender)
            "drop_pos" -> handleDropPos(sender, args)
            "drop_timing" -> handleDropTiming(sender, args)
            "stop_on_release" -> handleStopOnRelease(sender, args)
            else -> handleHelp(sender)
        }
    }

    override fun addTabCompletionOptions(sender: ICommandSender, args: Array<String>): List<String>? {
        if (args.isEmpty()) return super.addTabCompletionOptions(sender, args)

        val argsList = args.toMutableList()

        return when (argsList.removeFirstOrNull()) {
            null, "" -> getListOfStringsMatchingLastWord(args, *SUB_COMMANDS.toTypedArray())

            "executor-generator", "executor" ->
                getListOfStringsMatchingLastWord(args, *executorNames().toTypedArray())

            "block-filter", "filter" ->
                getListOfStringsMatchingLastWord(args, *blockFilterNames().toTypedArray())

            "mode" ->
                getListOfStringsMatchingLastWord(args, *getValidEnumValues<ActivationMode>().toTypedArray())

            "drop_pos" ->
                getListOfStringsMatchingLastWord(args, *getValidEnumValues<DropPosition>().toTypedArray())

            "drop_timing" ->
                getListOfStringsMatchingLastWord(args, *getValidEnumValues<DropTiming>().toTypedArray())

            "stop_on_release" ->
                getListOfStringsMatchingLastWord(args, "true", "false")

            "limits", "limit", "stop", "help" -> emptyList()

            else -> super.addTabCompletionOptions(sender, args)
        }
    }

    private fun ICommandSender.withEntityPlayer(block: (EntityPlayerMP) -> Unit) {
        if(this is EntityPlayerMP) {
            block(this)
        } else {
            addChatMessage(ChatComponentText("This command sender type is not supported!"))
        }
    }

    private fun executorNames(): List<String> = ExecutorGeneratorRegistry.all().map { shortName(it.value.getUnlocalizedName()) }

    private fun blockFilterNames(): List<String> = BlockFilterRegistry.all().map { shortName(it.value.getUnlocalizedName()) }

    private fun shortName(unlocalizedName: String): String = unlocalizedName.substringAfterLast('.')

    private fun handleExecutorSettings(
        sender: ICommandSender,
        strings: MutableList<String>,
    ) {
        sender.withEntityPlayer { p ->
            val input = strings.removeFirstOrNull()
            if(input == null) {
                val current = p.veinMiningData.getExecutorGenerator()
                sender.addChatMessage(
                    ChatComponentTranslation(
                        "command.bandit.executor.current",
                        ChatComponentTranslation(current.getUnlocalizedName())
                    )
                )
                sender.addChatMessage(ChatComponentTranslation("command.bandit.executor.list"))
                ExecutorGeneratorRegistry.all().forEach { id, executor ->
                    sender.addChatMessage(
                        ChatComponentTranslation(
                            "command.bandit.executor.list.entry",
                            id,
                            ChatComponentTranslation(executor.getUnlocalizedName())
                        )
                    )
                }
                return@withEntityPlayer
            }

            val id = resolveId(input, ExecutorGeneratorRegistry.all().keys)
                ?: resolveByName(input, ExecutorGeneratorRegistry.all().mapValues { it.value.getUnlocalizedName() })
            if(id == null) {
                sender.addChatMessage(ChatComponentTranslation("command.bandit.executor.set.fail"))
                return@withEntityPlayer
            }
            p.veinMiningData.veinMiningExecutorId = id
            sender.addChatMessage(ChatComponentTranslation("command.bandit.executor.set.ok"))
        }
    }

    private fun handleBlockFilterSettings(
        sender: ICommandSender,
        strings: MutableList<String>,
    ) {
        sender.withEntityPlayer { p ->
            val input = strings.removeFirstOrNull()
            if(input == null) {
                val current = p.veinMiningData.getBlockFilter()
                sender.addChatMessage(
                    ChatComponentTranslation(
                        "command.bandit.filter.current",
                        ChatComponentTranslation(current.getUnlocalizedName())
                    )
                )
                sender.addChatMessage(ChatComponentTranslation("command.bandit.filter.list"))
                BlockFilterRegistry.all().forEach { id, filter ->
                    sender.addChatMessage(
                        ChatComponentTranslation(
                            "command.bandit.filter.list.entry",
                            id,
                            ChatComponentTranslation(filter.getUnlocalizedName())
                        )
                    )
                }
                return@withEntityPlayer
            }

            val id = resolveByName(input, BlockFilterRegistry.all().mapValues { it.value.getUnlocalizedName() })
                ?: resolveId(input, BlockFilterRegistry.all().keys)
            if(id == null) {
                sender.addChatMessage(ChatComponentTranslation("command.bandit.filter.set.fail"))
                return@withEntityPlayer
            }
            p.veinMiningData.veinMiningBlockFilterId = id
            sender.addChatMessage(ChatComponentTranslation("command.bandit.filter.set.ok"))
        }
    }

    private fun resolveId(input: String, validIds: Set<Int>): Int? {
        val id = input.toIntOrNull() ?: return null
        return id.takeIf { it in validIds }
    }

    private fun resolveByName(input: String, names: Map<Int, String>): Int? {
        val needle = input.lowercase()
        return names.entries.firstOrNull { (_, name) ->
            shortName(name).lowercase() == needle || name.lowercase() == needle
        }?.key
    }

    private fun handleMode(sender: ICommandSender, args: MutableList<String>) {
        sender.withEntityPlayer { p ->
            if(args.isEmpty()) {
                sender.addChatMessage(
                    ChatComponentTranslation(
                        "command.bandit.mode.current",
                        ChatComponentTranslation(p.veinMiningData.activationMode.getUnlocalizedName()),
                        getValidEnumValues<ActivationMode>().joinToString()
                    )
                )
                return@withEntityPlayer
            }

            val value = parseValueToEnum<ActivationMode>(args[0])
            if(value == null) {
                sender.addChatMessage(
                    ChatComponentTranslation(
                        "command.bandit.mode.invalid_argument",
                        args[0],
                        getValidEnumValues<ActivationMode>().joinToString()
                    )
                )
                return@withEntityPlayer
            }

            p.veinMiningData.activationMode = value
            sender.addChatMessage(
                ChatComponentTranslation("command.bandit.mode.ok", ChatComponentTranslation(value.getUnlocalizedName()))
            )
        }
    }

    private fun handleLimits(sender: ICommandSender) {
        sender.addChatMessage(
            ChatComponentTranslation(
                "command.bandit.limits",
                BanditConfig.maxBlocksPerOperation,
                BanditConfig.blocksPerOperation,
                BanditConfig.cooldownTicks,
                BanditConfig.exhaustionPerBlock,
            )
        )
    }

    private fun handleHelp(sender: ICommandSender) {
        for(i in 0..7) {
            sender.addChatMessage(ChatComponentTranslation("command.bandit.help.$i"))
        }
    }

    private fun handleHaltRequest(sender: ICommandSender) {
        sender.withEntityPlayer { p ->
            p.veinMiningData.cancelJob(CommandCancellation())
        }
    }

    private inline fun <reified T : Enum<T>> getValidEnumValues(): List<String> {
        return enumValues<T>().map { it.name.lowercase() }
    }

    private fun handleDropPos(
        sender: ICommandSender,
        args: MutableList<String>,
    ) {
        sender.withEntityPlayer { p ->
            if(args.isEmpty()) {
                sender.addChatMessage(
                    ChatComponentTranslation(
                        "command.bandit.drop_pos",
                        p.veinMiningData.harvestedDropPosition.toChatComponent(),
                        getValidEnumValues<DropPosition>().joinToString()
                    )
                )
            } else {
                when(val value = parseValueToEnum<DropPosition>(args[0])) {
                    null -> {
                        p.addChatMessage(
                            ChatComponentTranslation(
                                "command.bandit.drop_pos.invalid_argument",
                                args[0],
                                getValidEnumValues<DropPosition>().joinToString()
                            )
                        )
                    }

                    else -> {
                        p.veinMiningData.harvestedDropPosition = value
                        p.addChatMessage(ChatComponentTranslation("command.bandit.drop_pos.ok", value))
                    }
                }
            }
        }
    }

    private fun handleDropTiming(sender: ICommandSender, args: MutableList<String>) {
        sender.withEntityPlayer { p ->
            if(args.isEmpty()) {
                sender.addChatMessage(
                    ChatComponentTranslation(
                        "command.bandit.drop_timing",
                        p.veinMiningData.harvestedDropTiming.toChatComponent(),
                        getValidEnumValues<DropTiming>().joinToString()
                    )
                )
            } else {
                when(val value = parseValueToEnum<DropTiming>(args[0])) {
                    null -> {
                        p.addChatMessage(
                            ChatComponentTranslation(
                                "command.bandit.drop_timing.invalid_argument",
                                args[0],
                                getValidEnumValues<DropTiming>().joinToString()
                            )
                        )
                    }

                    else -> {
                        p.veinMiningData.harvestedDropTiming = value
                        p.addChatMessage(ChatComponentTranslation("command.bandit.drop_timing.ok", value))
                    }
                }
            }
        }
    }

    /**
     * Kept for compatibility with 1.x: `stop_on_release true` is hold mode, `false` is toggle mode.
     */
    private fun handleStopOnRelease(sender: ICommandSender, args: MutableList<String>) {
        sender.withEntityPlayer { p ->
            if(args.isEmpty()) {
                p.addChatMessage(
                    ChatComponentTranslation(
                        "command.bandit.stop_on_release",
                        p.veinMiningData.activationMode.stopsOnKeyRelease
                    )
                )
                return@withEntityPlayer
            }

            when(val value = args[0].toBooleanStrictOrNull()) {
                null -> {
                    p.addChatMessage(
                        ChatComponentTranslation(
                            "command.bandit.stop_on_release.invalid_argument",
                            args[0],
                            "[true, false]"
                        )
                    )
                }

                else -> {
                    p.veinMiningData.activationMode = if(value) ActivationMode.HOLD else ActivationMode.TOGGLE
                    p.addChatMessage(ChatComponentTranslation("command.bandit.stop_on_release.ok", value))
                }
            }
        }
    }
}
