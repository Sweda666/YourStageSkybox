package yourstageskybox.network;

import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * 同步天空盒激活状态的网络包（1.20.1 版本，FriendlyByteBuf）。
 */
public class PacketSyncSkybox {

    private int dimension;
    private String skyboxName;
    private float alpha = 1.0f;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private long durationMs = 2000L;

    public PacketSyncSkybox() {}

    public PacketSyncSkybox(int dimension, String skyboxName) {
        this(dimension, skyboxName, 1.0f, 1.0f, 1.0f, 1.0f, 2000L);
    }

    public PacketSyncSkybox(int dimension, String skyboxName,
                            float alpha, float red, float green, float blue, long durationMs) {
        this.dimension = dimension;
        this.skyboxName = (skyboxName != null && !skyboxName.isEmpty()) ? skyboxName : null;
        this.alpha = alpha;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.durationMs = durationMs;
    }

    public static void encode(PacketSyncSkybox msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.dimension);
        if (msg.skyboxName != null && !msg.skyboxName.isEmpty()) {
            byte[] bytes = msg.skyboxName.getBytes(StandardCharsets.UTF_8);
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
        } else {
            buf.writeShort(0);
        }
        buf.writeFloat(msg.alpha);
        buf.writeFloat(msg.red);
        buf.writeFloat(msg.green);
        buf.writeFloat(msg.blue);
        buf.writeLong(msg.durationMs);
    }

    public static PacketSyncSkybox decode(FriendlyByteBuf buf) {
        int dim = buf.readInt();
        int len = buf.readShort();
        String name = null;
        if (len > 0) {
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            name = new String(bytes, StandardCharsets.UTF_8);
        }
        float alpha = buf.readFloat();
        float r = buf.readFloat();
        float g = buf.readFloat();
        float b = buf.readFloat();
        long dur = buf.readLong();
        return new PacketSyncSkybox(dim, name, alpha, r, g, b, dur);
    }

    public static void handle(PacketSyncSkybox msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            int dim = msg.dimension;
            String name = msg.skyboxName;
            if (name == null || name.isEmpty()) {
                SkyboxState current = SkyboxManager.getSkyboxState(dim);
                if (current != null && !current.isVanilla()) {
                    SkyboxState vanilla = new SkyboxState(null);
                    vanilla.durationMs = current.durationMs;
                    vanilla.alpha = current.alpha;
                    SkyboxManager.setActiveSkybox(dim, vanilla);
                } else {
                    SkyboxManager.clearActiveSkybox(dim);
                }
            } else {
                SkyboxState state = new SkyboxState(name, msg.alpha, msg.durationMs);
                state.red = msg.red;
                state.green = msg.green;
                state.blue = msg.blue;
                SkyboxManager.setActiveSkybox(dim, state);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public int getDimension() { return dimension; }
    public String getSkyboxName() { return skyboxName; }
    public float getAlpha() { return alpha; }
    public float getRed() { return red; }
    public float getGreen() { return green; }
    public float getBlue() { return blue; }
    public long getDurationMs() { return durationMs; }
}
