package wsmc.mixin;

import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;

import wsmc.IConnectionEx;
import wsmc.IWebSocketServerAddress;
import wsmc.client.WebSocketClientHandler;
import wsmc.WSMC;

/**
 * 修改 {@link net.minecraft.network.Connection} 的内部类 {@link io.netty.channel.ChannelInitializer}，
 * 在客户端连接服务器时向管线注入额外处理器。
 */
@Debug(export = true)
@Mixin(targets = "net.minecraft.network.Connection$1")
public class MixinConnectionChInit {
	@Unique
	private Connection connection;

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	protected void init(CallbackInfo callback, @Local(ordinal = 0, argsOnly = true) Connection connection) {
		// 连接实例保持不变
		this.connection = connection;
	}

	@Inject(method = "initChannel", at = @At("RETURN"), require = 1)
	protected void initChannel(Channel channel, CallbackInfo callback) {
		IConnectionEx connection = (IConnectionEx) this.connection;
		IWebSocketServerAddress wsInfo = connection.getWsInfo();
		WSMC.info("initChannel: wsInfo=" + (wsInfo == null ? "null" : (wsInfo.isVanilla() ? "vanilla" : "ws")));
		WebSocketClientHandler.hookPipeline(channel.pipeline(), wsInfo);
	}
}
