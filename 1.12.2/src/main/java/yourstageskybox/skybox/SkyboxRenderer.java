package yourstageskybox.skybox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * 天空盒渲染器 —— 支持平滑过渡（旧 100% 打底 + 新渐显 = 交叉淡入淡出）。
 */
@SideOnly(Side.CLIENT)
public class SkyboxRenderer extends IRenderHandler {

    private static final float SKYBOX_SIZE = 200.0F;
    private static final float HALF = SKYBOX_SIZE / 2.0F;

    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc) {
        int dim = world.provider.getDimension();

        TransitionState transition = SkyboxManager.updateTransition(dim);
        SkyboxState state = SkyboxManager.getSkyboxState(dim);

        if (transition != null) {
            float progress = transition.getProgress();
            float toAlpha = transition.toAlpha;
            ResourceLocation[] fromTex = SkyboxManager.getSkyboxTextures(transition.fromName);
            ResourceLocation[] toTex = SkyboxManager.getSkyboxTextures(transition.toName);

            doEnterState();

            if (fromTex == null && toTex == null) {
                doExitState();
                return;
            }
            if (fromTex == null && toTex != null) {
                drawCubemap(mc, toTex, transition.toR, transition.toG, transition.toB, toAlpha * progress);
            } else if (fromTex != null && toTex == null) {
                drawCubemap(mc, fromTex, transition.fromR, transition.fromG, transition.fromB,
                        transition.fromAlpha * (1.0f - progress));
            } else {
                float newA = transition.toAlpha * progress;
                drawCubemap(mc, fromTex, transition.fromR, transition.fromG, transition.fromB, transition.fromAlpha);
                if (newA > 0.001f) {
                    drawCubemap(mc, toTex, transition.toR, transition.toG, transition.toB, newA);
                }
            }

            doExitState();
            return;
        }

        if (state == null || state.isVanilla()) return;
        ResourceLocation[] textures = SkyboxManager.getSkyboxTextures(state.name);
        if (textures == null) return;

        doEnterState();
        drawCubemap(mc, textures, state.red, state.green, state.blue, state.alpha);
        doExitState();
    }

    // ==================== GL 状态 ====================

    private static void doEnterState() {
        GlStateManager.pushMatrix();
        GlStateManager.disableFog();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void doExitState() {
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableFog();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    // ==================== 立方体绘制 ====================

    private static void drawCubemap(Minecraft mc, ResourceLocation[] textures,
                                     float r, float g, float b, float alpha) {
        if (alpha <= 0.001f) return;

        GlStateManager.color(r, g, b, alpha);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // 北 (-Z)
        mc.renderEngine.bindTexture(textures[0]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-HALF,  HALF, -HALF).tex(0.0, 0.0).endVertex();
        buffer.pos(-HALF, -HALF, -HALF).tex(0.0, 1.0).endVertex();
        buffer.pos( HALF, -HALF, -HALF).tex(1.0, 1.0).endVertex();
        buffer.pos( HALF,  HALF, -HALF).tex(1.0, 0.0).endVertex();
        tessellator.draw();

        // 东 (+X)
        mc.renderEngine.bindTexture(textures[1]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos( HALF,  HALF, -HALF).tex(0.0, 0.0).endVertex();
        buffer.pos( HALF, -HALF, -HALF).tex(0.0, 1.0).endVertex();
        buffer.pos( HALF, -HALF,  HALF).tex(1.0, 1.0).endVertex();
        buffer.pos( HALF,  HALF,  HALF).tex(1.0, 0.0).endVertex();
        tessellator.draw();

        // 南 (+Z)
        mc.renderEngine.bindTexture(textures[2]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos( HALF,  HALF,  HALF).tex(0.0, 0.0).endVertex();
        buffer.pos( HALF, -HALF,  HALF).tex(0.0, 1.0).endVertex();
        buffer.pos(-HALF, -HALF,  HALF).tex(1.0, 1.0).endVertex();
        buffer.pos(-HALF,  HALF,  HALF).tex(1.0, 0.0).endVertex();
        tessellator.draw();

        // 西 (-X)
        mc.renderEngine.bindTexture(textures[3]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-HALF,  HALF,  HALF).tex(0.0, 0.0).endVertex();
        buffer.pos(-HALF, -HALF,  HALF).tex(0.0, 1.0).endVertex();
        buffer.pos(-HALF, -HALF, -HALF).tex(1.0, 1.0).endVertex();
        buffer.pos(-HALF,  HALF, -HALF).tex(1.0, 0.0).endVertex();
        tessellator.draw();

        // 上 (+Y)
        mc.renderEngine.bindTexture(textures[4]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-HALF,  HALF,  HALF).tex(0.0, 0.0).endVertex();
        buffer.pos( HALF,  HALF,  HALF).tex(1.0, 0.0).endVertex();
        buffer.pos( HALF,  HALF, -HALF).tex(1.0, 1.0).endVertex();
        buffer.pos(-HALF,  HALF, -HALF).tex(0.0, 1.0).endVertex();
        tessellator.draw();

        // 下 (-Y)
        mc.renderEngine.bindTexture(textures[5]);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-HALF, -HALF, -HALF).tex(0.0, 0.0).endVertex();
        buffer.pos( HALF, -HALF, -HALF).tex(1.0, 0.0).endVertex();
        buffer.pos( HALF, -HALF,  HALF).tex(1.0, 1.0).endVertex();
        buffer.pos(-HALF, -HALF,  HALF).tex(0.0, 1.0).endVertex();
        tessellator.draw();
    }
}
