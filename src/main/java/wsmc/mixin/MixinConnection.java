package wsmc.mixin;

import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import io.netty.handler.codec.http.HttpRequest;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import wsmc.IConnectionEx;
import wsmc.IWebSocketServerAddress;
import wsmc.WSMC;

@Debug(export = true)
@Mixin(Connection.class)
public class MixinConnection implements IConnectionEx {
	// [新增] 引用原版字段，用于空指针检查
	@Shadow
	private PacketListener packetListener;

	@Unique
	private IWebSocketServerAddress wsInfo = null;

	@Unique
	private HttpRequest wsHandshakeRequest = null;

	@Inject(method = "connectToServer", require = 1, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
	private static void beforeCallConnect(CallbackInfoReturnable<Connection> callback, @Local(ordinal = 0, argsOnly = false) Connection connection) {
		// --- [兼容性修复 1] 防止空栈异常 ---
		// 如果 IConnectionEx.connectToServerArg 为空，说明这次连接不是 WSMC 发起的
		// (可能是 FancyMenu、ServerStatusPinger 或 PacketFixer 发起的)
		// 此时直接返回，不要执行 pop()，否则会导致客户端崩溃。
		if (IConnectionEx.connectToServerArg.isEmpty()) {
			return;
		}
		// ---------------------------------

		IWebSocketServerAddress wsAddress = IConnectionEx.connectToServerArg.pop();
		IConnectionEx con = (IConnectionEx) connection;
		con.setWsInfo(wsAddress);
	}

	// ==================================================================================
	// [兼容性修复 2] 修复服务端 NPE 崩服
	// 原理：当连接在握手阶段被 PacketFixer 强制断开或超时时，Connection 可能还没绑定监听器。
	// 原版逻辑会尝试调用 getPacketListener().onDisconnect()，导致空指针崩溃。
	// ==================================================================================
	@Inject(method = "handleDisconnection", at = @At("HEAD"), cancellable = true)
	private void onHandleDisconnection(CallbackInfo ci) {
		if (this.packetListener == null) {
			// 如果监听器为空，直接拦截断开处理，防止服务器崩溃。
			// WSMC.info("MixinConnection: 检测到空监听器的断开处理 (PacketFixer 兼容)，已拦截。");
			ci.cancel();
		}
	}
	// ==================================================================================

	@Override
	public IWebSocketServerAddress getWsInfo() {
		return this.wsInfo;
	}

	@Override
	public void setWsInfo(IWebSocketServerAddress wsInfo) {
		this.wsInfo = wsInfo;
	}

	@Override
	public HttpRequest getWsHandshakeRequest() {
		return this.wsHandshakeRequest;
	}

	@Override
	public void setWsHandshakeRequest(HttpRequest wsHandshakeRequest) {
		this.wsHandshakeRequest = wsHandshakeRequest;
	}
}
