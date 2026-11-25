package cn.elytra.mod.bandit.mining

import cn.elytra.mod.bandit.BanditConfig
import cn.elytra.mod.bandit.mining.executor.CubeExecutorGenerator
import cn.elytra.mod.bandit.mining.executor.LargeScanExecutorGenerator
import cn.elytra.mod.bandit.mining.executor.ManhattanExecutorGenerator
import cn.elytra.mod.bandit.mining.executor.TunnelExecutorGenerator
import cn.elytra.mod.bandit.mining.executor.VeinMiningExecutorGenerator

object ExecutorGeneratorRegistry {

    private val executorGeneratorMap = mutableMapOf<Int, VeinMiningExecutorGenerator>()

    init {
        executorGeneratorMap[0] = ManhattanExecutorGenerator("bandit.executor.manhattan", BanditConfig.manhattanRadius)
        executorGeneratorMap[1] =
            ManhattanExecutorGenerator("bandit.executor.manhattan-plus", BanditConfig.manhattanRadius, true)
        executorGeneratorMap[2] = LargeScanExecutorGenerator(BanditConfig.largeScanRadiusXZ, BanditConfig.largeScanRadiusY)
        executorGeneratorMap[3] =
            ManhattanExecutorGenerator("bandit.executor.manhattan-large", BanditConfig.manhattanLargeRadius, true)

        // shaped executors, the FTB Ultimine style modes
        executorGeneratorMap[4] = TunnelExecutorGenerator("bandit.executor.tunnel", 3, 3)
        executorGeneratorMap[5] = TunnelExecutorGenerator("bandit.executor.tunnel-small", 1, 2)
        executorGeneratorMap[6] = TunnelExecutorGenerator("bandit.executor.tunnel-escape", 1, 2, riseEvery = 1)
        executorGeneratorMap[7] = CubeExecutorGenerator("bandit.executor.cube", BanditConfig.cubeRadius)
    }

    @Suppress("unused")
    fun register(id: Int, executor: VeinMiningExecutorGenerator) {
        if(id in executorGeneratorMap) {
            error("Duplicate executor: $id")
        }
        executorGeneratorMap[id] = executor
    }

    fun get(id: Int): VeinMiningExecutorGenerator? {
        return executorGeneratorMap[id]
    }

    fun getOrDefault(id: Int): VeinMiningExecutorGenerator {
        return get(id) ?: executorGeneratorMap[0]!!
    }

    fun all(): Map<Int, VeinMiningExecutorGenerator> = executorGeneratorMap.toMap()
}
