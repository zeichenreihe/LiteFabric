package io.github.zeichenreihe.liteornithe.runtime;

import io.github.zeichenreihe.liteornithe.liteloader.PluginChannelListener;
import io.github.zeichenreihe.liteornithe.liteloader.core.ClientPluginChannels;
import io.github.zeichenreihe.liteornithe.liteloader.core.PluginChannels;
import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.PacketByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;

public class ClientPluginChannelsImpl extends ClientPluginChannels {
    private static final Logger LOGGER = LogManager.getLogger();

    public void onPostLogin() {
        this.pluginChannels.clear();
        this.remotePluginChannels.clear();
    }

    public void onJoinGame() {
        for (PluginChannelListener listener : listeners) {
            addPluginChannelsFor(listener);
        }
        if (pluginChannels.isEmpty()) return;
        String list = String.join("\u0000", pluginChannels.keySet());
        byte[] bytes = list.getBytes(StandardCharsets.UTF_8);
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
        dispatch("REGISTER", buffer);
    }

    public void addListener(PluginChannelListener listener) {
        addPluginChannelListener(listener);
    }

    @Override
    public void onPluginChannelMessage(CustomPayloadS2CPacket customPayload) {
        if (customPayload == null) return;
        String channel = customPayload.getChannel();
        if (channel == null) return;
        PacketByteBuf data = customPayload.getData();
        if ("REGISTER".equals(channel)) {
            onRegisterPacketReceived(data);
        } else if (pluginChannels.containsKey(channel)) {
            for (PluginChannelListener listener : pluginChannels.get(channel)) {
                try {
                    listener.onCustomPayload(channel, data);
                } catch (Exception e) {
                    LOGGER.warn("{} failed to handle message on plugin channel {}", listener.getName(), channel, e);
                }
            }
        }
    }

    @Override
    protected boolean send(String channel, PacketByteBuf data, ChannelPolicy policy) {
        if (!PluginChannels.isValidChannelName(channel)) throw new IllegalArgumentException("Invalid channel name");
        if (!policy.check(this, channel)) return false;
        return dispatch(channel, data);
    }

    private static boolean dispatch(String channel, PacketByteBuf data) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.networkHandler == null) return false;
        client.player.networkHandler.sendPacket(new CustomPayloadC2SPacket(channel, data));
        return true;
    }
}
