package yourstageskybox.skybox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ISkyRenderHandler;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

/**
 * 天空盒渲染器 —— 1.16.5 版本。
 * 通过 ISkyRenderHandler 替换原版天空，使用 MatrixStack 响应视角旋转。
 */
@OnlyIn(Dist.CLIENT)
public class SkyboxRenderer implements ISkyRenderHandler {

    private static final float SKYBOX_SIZE = 200.0F;
    private static final float HALF = SKYBOX_SIZE / 2.0F;

    @Override
    public void render(int ticks, float partialTicks, MatrixStack matrixStack,
                       ClientWorld world, Minecraft mc) {
        int dim = SkyboxManager.getDimensionId(world);

        TransitionState transition = SkyboxManager.updateTransition(dim);
        SkyboxState state = SkyboxManager.getSkyboxState(dim);

        if (transition != null) {
            float progress = transition.getProgress();
            ResourceLocation[] fromTex = SkyboxManager.getSkyboxTextures(transition.fromName);
            ResourceLocation[] toTex = SkyboxManager.getSkyboxTextures(transition.toName);

            doEnterGL();
            matrixStack.pushPose();

            if (fromTex == null && toTex == null) {
                matrixStack.popPose();
                doExitGL();
                return;
            }
            if (fromTex == null && toTex != null) {
                drawCubemap(mc, matrixStack.last().pose(), toTex,
                        transition.toR, transition.toG, transition.toB,
                        transition.toAlpha * progress);
            } else if (fromTex != null && toTex == null) {
                drawCubemap(mc, matrixStack.last().pose(), fromTex,
                        transition.fromR, transition.fromG, transition.fromB,
                        transition.fromAlpha * (1.0f - progress));
            } else {
                drawCubemap(mc, matrixStack.last().pose(), fromTex,
                        transition.fromR, transition.fromG, transition.fromB,
                        transition.fromAlpha);
                float newA = transition.toAlpha * progress;
                if (newA > 0.001f) {
                    drawCubemap(mc, matrixStack.last().pose(), toTex,
                            transition.toR, transition.toG, transition.toB, newA);
                }
            }

            matrixStack.popPose();
            doExitGL();
            return;
        }

        if (state == null || state.isVanilla()) return;
        ResourceLocation[] textures = SkyboxManager.getSkyboxTextures(state.name);
        if (textures == null) return;

        doEnterGL();
        matrixStack.pushPose();
        drawCubemap(mc, matrixStack.last().pose(), textures,
                state.red, state.green, state.blue, state.alpha);
        matrixStack.popPose();
        doExitGL();
    }

    // ==================== GL 状态（仅修改必须的，不动 Minecraft 的渲染管线状态） ====================

    private static void doEnterGL() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
    }

    private static void doExitGL() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ==================== 立方体绘制 ====================

    private static void drawCubemap(Minecraft mc, Matrix4f pose, ResourceLocation[] textures,
                                     float r, float g, float b, float alpha) {
        if (alpha <= 0.001f) return;

        RenderSystem.color4f(r, g, b, alpha);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();

        // 北 (-Z)
        mc.getTextureManager().bind(textures[0]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.vertex(pose, -HALF,  HALF, -HALF).uv(0.0f, 0.0f).endVertex();
        buffer.vertex(pose, -HALF, -HALF, -HALF).uv(0.0f, 1.0f).endVertex();
        buffer.vertex(pose,  HALF, -HALF, -HALF).uv(1.0f, 1.0f).endVertex();
        buffer.vertex(pose,  HALF,  HALF, -HALF).uv(1.0f, 0.0f).endVertex();
        tessellator.end();

        // 东 (+X)
        mc.getTextureManager().bind(textures[1]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.vertex(pose,  HALF,  HALF, -HALF).uv(0.0f, 0.0f).endVertex();
        buffer.vertex(pose,  HALF, -HALF, -HALF).uv(0.0f, 1.0f).endVertex();
        buffer.vertex(pose,  HALF, -HALF,  HALF).uv(1.0f, 1.0f).endVertex();
        buffer.vertex(pose,  HALF,  HALF,  HALF).uv(1.0f, 0.0f).endVertex();
        tessellator.end();

        // 南 (+Z)
        mc.getTextureManager().bind(textures[2]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.vertex(pose,  HALF,  HALF,  HALF).uv(0.0f, 0.0f).endVertex();
        buffer.vertex(pose,  HALF, -HALF,  HALF).uv(0.0f, 1.0f).endVertex();
        buffer.vertex(pose, -HALF, -HALF,  HALF).uv(1.0f, 1.0f).endVertex();
        buffer.vertex(pose, -HALF,  HALF,  HALF).uv(1.0f, 0.0f).endVertex();
        tessellator.end();

        // 西 (-X)
        mc.getTextureManager().bind(textures[3]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.vertex(pose, -HALF,  HALF,  HALF).uv(0.0f, 0.0f).endVertex();
        buffer.vertex(pose, -HALF, -HALF,  HALF).uv(0.0f, 1.0f).endVertex();
        buffer.vertex(pose, -HALF, -HALF, -HALF).uv(1.0f, 1.0f).endVertex();
        buffer.vertex(pose, -HALF,  HALF, -HALF).uv(1.0f, 0.0f).endVertex();
        tessellator.end();

        // 上 (+Y)
        mc.getTextureManager().bind(textures[4]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.vertex(pose, -HALF,  HALF,  HALF).uv(0.0f, 0.0f).endVertex();
        buffer.vertex(pose,  HALF,  HALF,  HALF).uv(1.0f, 0.0f).endVertex();
        buffer.vertex(pose,  HALF,  HALF, -HALF).uv(1.0f, 1.0f).endVertex();
        buffer.vertex(pose, -HALF,  HALF, -HALF).uv(0.0f, 1.0f).endVertex();
        tessellator.end();

        // 下 (-Y)
        mc.getTextureManager().bind(textures[5]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.vertex(pose, -HALF, -HALF, -HALF).uv(0.0f, 0.0f).endVertex();
        buffer.vertex(pose,  HALF, -HALF, -HALF).uv(1.0f, 0.0f).endVertex();
        buffer.vertex(pose,  HALF, -HALF,  HALF).uv(1.0f, 1.0f).endVertex();
        buffer.vertex(pose, -HALF, -HALF,  HALF).uv(0.0f, 1.0f).endVertex();
        tessellator.end();
    }
}
