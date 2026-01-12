package wsmc.mixin;

import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;

import wsmc.IConnectionEx;
import wsmc.IWebSocketServerAddress;
import wsmc.client.ConnectionEvent;
import wsmc.client.ConnectStageNotifier;
import wsmc.client.StatusLogStore;
import wsmc.WSMC;

@Debug(export = true)
@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public class MixinConnectScreenThread {
	@Unique
	private ServerAddress serverAddress;

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	protected void init(CallbackInfo callback, @Local(ordinal = 0, argsOnly = true) ServerAddress serverAddress) {
		// 目标地址保持不变
		this.serverAddress = serverAddress;
	}

	@Inject(method = "run", require = 1, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
	public void beforeCallConnect(CallbackInfo callback, @Local(ordinal = 0, argsOnly = false) Connection connection) {
		IWebSocketServerAddress wsAddress = IWebSocketServerAddress.from(serverAddress);
		IConnectionEx con = (IConnectionEx) connection;
		con.setWsInfo(wsAddress);
		WSMC.info("准备调用 Connection.connect，目标=" + serverAddress.getHost() + ":" + serverAddress.getPort()
				+ " ws=" + (wsAddress.isVanilla() ? "vanilla" : "ws")
				+ " rawHost=" + wsAddress.getRawHost());
		String mode = wsAddress.isVanilla() ? "原版 TCP" : "WebSocket";
		StatusLogStore.get().append(ConnectionEvent.Type.INFO, "开始建立 TCP 连接 (" + mode + "): " + serverAddress.toString());
		ConnectStageNotifier.status("开始建立 TCP 连接 (" + mode + "): " + serverAddress.toString());
	}
}
