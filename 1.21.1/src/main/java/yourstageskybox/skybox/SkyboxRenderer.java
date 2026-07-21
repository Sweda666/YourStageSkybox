package yourstageskybox.skybox;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

/**
 * 天空盒渲染器 —— 1.21.1 版本，通过 DimensionSpecialEffects 原生钩子实现。
 */
@OnlyIn(Dist.CLIENT)
public class SkyboxRenderer {

    private static final float SKYBOX_SIZE = 300.0F;
    private static final float HALF = SKYBOX_SIZE / 2.0F;

    /**
     * 为 overworld/the_nether/the_end 注册天空盒渲染。
     */
    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.withDefaultNamespace("overworld"), new SkyboxEffects(DimensionSpecialEffects.SkyType.NORMAL, true));
        event.register(ResourceLocation.withDefaultNamespace("the_nether"), new SkyboxEffects(DimensionSpecialEffects.SkyType.NONE, false));
        event.register(ResourceLocation.withDefaultNamespace("the_end"), new SkyboxEffects(DimensionSpecialEffects.SkyType.END, false));
    }

    /**
     * 自定义 DimensionSpecialEffects —— 仅重写 renderSky。
     */
    @OnlyIn(Dist.CLIENT)
    private static class SkyboxEffects extends DimensionSpecialEffects {
        SkyboxEffects(DimensionSpecialEffects.SkyType skyType, boolean hasGround) {
            super(Float.NaN, hasGround, skyType, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 color, float sunHeight) {
            return color;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick,
                                  PoseStack poseStack, Camera camera, Matrix4f projection,
                                  boolean isFoggy, Runnable setupFog) {
            int dim = SkyboxManager.getDimensionId(level);
            TransitionState trans = SkyboxManager.updateTransition(dim);
            SkyboxState state = SkyboxManager.getSkyboxState(dim);

            if (trans != null) {
                renderTransition(poseStack, trans);
                return true;
            }
            if (state == null || state.isVanilla()) return false;

            ResourceLocation[] textures = SkyboxManager.getSkyboxTextures(state.name);
            if (textures == null) return false;

            renderSkybox(poseStack, textures, state.red, state.green, state.blue, state.alpha);
            return true;
        }
    }

    // ==================== 渲染 ====================

    private static void renderTransition(PoseStack poseStack, TransitionState transition) {
        float progress = transition.getProgress();
        ResourceLocation[] fromTex = SkyboxManager.getSkyboxTextures(transition.fromName);
        ResourceLocation[] toTex = SkyboxManager.getSkyboxTextures(transition.toName);

        if (fromTex == null && toTex == null) return;

        if (fromTex != null && toTex == null) {
            renderSkybox(poseStack, fromTex, transition.fromR, transition.fromG, transition.fromB,
                    transition.fromAlpha * (1.0f - progress));
        } else if (fromTex == null) {
            renderSkybox(poseStack, toTex, transition.toR, transition.toG, transition.toB,
                    transition.toAlpha * progress);
        } else {
            renderSkybox(poseStack, fromTex, transition.fromR, transition.fromG, transition.fromB,
                    transition.fromAlpha);
            float newA = transition.toAlpha * progress;
            if (newA > 0.001f)
                renderSkybox(poseStack, toTex, transition.toR, transition.toG, transition.toB, newA);
        }
    }

    private static void renderSkybox(PoseStack poseStack, ResourceLocation[] textures,
                                      float r, float g, float b, float alpha) {
        if (alpha <= 0.001f) return;

        poseStack.pushPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(r, g, b, alpha);

        Matrix4f pose = poseStack.last().pose();
        Tesselator ts = Tesselator.getInstance();
        BufferBuilder buf = ts.getBuilder();

        // 水平四面：纹理索引逆时针轮转一位以对齐坐标系
        drawFace(buf, pose, textures[3], -HALF,  HALF, -HALF,  -HALF, -HALF, -HALF,   HALF, -HALF, -HALF,   HALF,  HALF, -HALF);
        drawFace(buf, pose, textures[0],  HALF,  HALF, -HALF,   HALF, -HALF, -HALF,   HALF, -HALF,  HALF,   HALF,  HALF,  HALF);
        drawFace(buf, pose, textures[1],  HALF,  HALF,  HALF,   HALF, -HALF,  HALF,  -HALF, -HALF,  HALF,  -HALF,  HALF,  HALF);
        drawFace(buf, pose, textures[2], -HALF,  HALF,  HALF,  -HALF, -HALF,  HALF,  -HALF, -HALF, -HALF,  -HALF,  HALF, -HALF);

        // 上 (+Y)：UV 逆时针旋转 90° 以对齐旋转后的水平面
        RenderSystem.setShaderTexture(0, textures[4]);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(pose, -HALF,  HALF, -HALF).uv(0.0f, 0.0f).endVertex();
        buf.vertex(pose, -HALF,  HALF,  HALF).uv(1.0f, 0.0f).endVertex();
        buf.vertex(pose,  HALF,  HALF,  HALF).uv(1.0f, 1.0f).endVertex();
        buf.vertex(pose,  HALF,  HALF, -HALF).uv(0.0f, 1.0f).endVertex();
        BufferUploader.drawWithShader(buf.end());

        // 下 (-Y)：顶点与 UV 同时逆时针旋转一位（与顶面一致）
        RenderSystem.setShaderTexture(0, textures[5]);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(pose, -HALF, -HALF,  HALF).uv(1.0f, 1.0f).endVertex();  // D→1: 西南
        buf.vertex(pose, -HALF, -HALF, -HALF).uv(0.0f, 1.0f).endVertex();  // A→2: 西北
        buf.vertex(pose,  HALF, -HALF, -HALF).uv(0.0f, 0.0f).endVertex();  // B→3: 东北
        buf.vertex(pose,  HALF, -HALF,  HALF).uv(1.0f, 0.0f).endVertex();  // C→4: 东南
        BufferUploader.drawWithShader(buf.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        poseStack.popPose();
    }

    private static void drawFace(BufferBuilder buf, Matrix4f pose, ResourceLocation tex,
                                  float x1, float y1, float z1, float x2, float y2, float z2,
                                  float x3, float y3, float z3, float x4, float y4, float z4) {
        RenderSystem.setShaderTexture(0, tex);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(pose, x1, y1, z1).uv(0, 0).endVertex();
        buf.vertex(pose, x2, y2, z2).uv(0, 1).endVertex();
        buf.vertex(pose, x3, y3, z3).uv(1, 1).endVertex();
        buf.vertex(pose, x4, y4, z4).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }
}
