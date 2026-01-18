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
import net.minecraft.network.protocol.PacketFlow;
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

	// [CHANGE] Replaced injection into connectToServer with Constructor injection
	@Inject(method = "<init>", at = @At("RETURN"))
	public void onConstructed(PacketFlow flow, CallbackInfo ci) {
		// Use peek/pop from ArgHolder. Note: IConnectionEx.connectToServerArg is the ArgHolder instance.
		// The previous code used IConnectionEx.connectToServerArg.pop()

		if (!IConnectionEx.connectToServerArg.isEmpty()) {
			IWebSocketServerAddress wsAddress = IConnectionEx.connectToServerArg.pop();
			if (wsAddress != null) {
				this.setWsInfo(wsAddress);
				WSMC.info("MixinConnection: Found WS info in ArgHolder, applying to Connection. Host=" + wsAddress.getRawHost());
			}
		}
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
			WSMC.debug("MixinConnection: handleDisconnection called with null packetListener. Cancelling to prevent NPE.");
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
