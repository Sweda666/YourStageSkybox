package yourstageskybox.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 同步天空盒激活状态的网络包。
 * <p>
 * 服务端 → 客户端：通知客户端切换 / 清除指定维度的天空盒，
 * 附带透明度、色相、过渡时长等完整状态。
 * </p>
 */
public class PacketSyncSkybox implements IMessage {

    private int dimension;
    private String skyboxName; // null 或空字符串表示清除
    private float alpha = 1.0f;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private long durationMs = 2000L;

    public PacketSyncSkybox() {
    }

    public PacketSyncSkybox(int dimension, String skyboxName) {
        this(dimension, skyboxName, 1.0f, 1.0f, 1.0f, 1.0f, 2000L);
    }

    public PacketSyncSkybox(int dimension, String skyboxName, float alpha, float red, float green, float blue, long durationMs) {
        this.dimension = dimension;
        this.skyboxName = skyboxName;
        this.alpha = alpha;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.durationMs = durationMs;
    }

    public int getDimension() {
        return dimension;
    }

    public String getSkyboxName() {
        return skyboxName;
    }

    public float getAlpha() {
        return alpha;
    }

    public float getRed() {
        return red;
    }

    public float getGreen() {
        return green;
    }

    public float getBlue() {
        return blue;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimension = buf.readInt();
        int len = buf.readShort();
        if (len > 0) {
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            skyboxName = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            skyboxName = null;
        }
        alpha = buf.readFloat();
        red = buf.readFloat();
        green = buf.readFloat();
        blue = buf.readFloat();
        durationMs = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        if (skyboxName != null && !skyboxName.isEmpty()) {
            byte[] bytes = skyboxName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
        } else {
            buf.writeShort(0);
        }
        buf.writeFloat(alpha);
        buf.writeFloat(red);
        buf.writeFloat(green);
        buf.writeFloat(blue);
        buf.writeLong(durationMs);
    }
}
