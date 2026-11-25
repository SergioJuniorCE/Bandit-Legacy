package cn.elytra.mod.bandit.client

import codechicken.lib.render.RenderUtils
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.util.ForgeDirection
import org.lwjgl.opengl.GL11.*

/**
 * Draws the outline of the blocks a vein mining action would harvest.
 *
 * Only the faces that are not covered by another selected block are drawn, which keeps the preview readable and
 * cheap, just like FTB Ultimine's outline.
 */
object VeinMiningSelectionRenderer {

    private val FACES = arrayOf(
        ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.NORTH,
        ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST,
    )

    /** The four corners of every face, in block local coordinates, in the order of [FACES]. */
    private val FACE_CORNERS: Array<Array<DoubleArray>> = arrayOf(
        arrayOf(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(1.0, 0.0, 1.0), doubleArrayOf(0.0, 0.0, 1.0)),
        arrayOf(doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 1.0, 1.0), doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(1.0, 1.0, 0.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(1.0, 1.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(1.0, 0.0, 1.0), doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(0.0, 1.0, 1.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, 1.0, 1.0), doubleArrayOf(0.0, 1.0, 0.0)),
        arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(1.0, 1.0, 0.0), doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(1.0, 0.0, 1.0)),
    )

    fun render(event: RenderWorldLastEvent, posList: List<BlockPos>) {
        val limit = VeinMiningConfigClient.outlineBlockLimit
        if(limit <= 0 || posList.isEmpty() || posList.size > limit) return

        val entity = Minecraft.getMinecraft().renderViewEntity ?: return
        val selected = HashSet<Long>(posList.size * 2)
        for(pos in posList) {
            selected.add(pos.asLong())
        }

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_LIGHTING)
        glDepthMask(false)
        glLineWidth(VeinMiningConfigClient.outlineLineWidth)
        glColor4f(1.0f, 1.0f, 1.0f, VeinMiningConfigClient.outlineAlpha / 255.0f)

        glPushMatrix()
        RenderUtils.translateToWorldCoords(entity, event.partialTicks)

        glBegin(GL_LINES)
        for(pos in posList) {
            for(i in FACES.indices) {
                val face = FACES[i]
                val packedNeighbor = neighborPacked(pos.x, pos.y, pos.z, face)
                if(selected.contains(packedNeighbor)) continue
                emitFace(pos.x, pos.y, pos.z, FACE_CORNERS[i])
            }
        }
        glEnd()

        glPopMatrix()
        glDepthMask(true)
        glLineWidth(1.0f)
        glEnable(GL_LIGHTING)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
    }

    private fun emitFace(x: Int, y: Int, z: Int, corners: Array<DoubleArray>) {
        for(i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            glVertex3d(x + a[0], y + a[1], z + a[2])
            glVertex3d(x + b[0], y + b[1], z + b[2])
        }
    }

    private fun neighborPacked(x: Int, y: Int, z: Int, face: ForgeDirection): Long {
        return CoordinatePacker.pack(x + face.offsetX, y + face.offsetY, z + face.offsetZ)
    }

}
