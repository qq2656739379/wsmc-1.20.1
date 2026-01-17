package wsmc.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;

@Mixin(ServerConnectionListener.class)
public interface ServerConnectionListenerAccessor {
    @Accessor("channels")
    List<ChannelFuture> getChannels();

    @Accessor("connections")
    List<Connection> getConnections();
}
