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

	// [CHANGE] Replaced injection into connectToServer with HEAD injection to avoid crash with FancyMenu
	@Inject(method = "run", at = @At("HEAD"))
	public void onRunStart(CallbackInfo ci) {
		if (this.serverAddress != null) {
			IWebSocketServerAddress wsAddr = IWebSocketServerAddress.from(this.serverAddress);

			// Always logging intended connection attempt for debugging
			WSMC.debug("MixinConnectScreenThread: Run start. Target: " + serverAddress.getHost());

			if (!wsAddr.isVanilla()) {
				// Push to ArgHolder so MixinConnection can pick it up in its constructor
				IConnectionEx.connectToServerArg.push(wsAddr);
				WSMC.info("MixinConnectScreenThread: Pushed WS address to ArgHolder: " + wsAddr.getRawHost());

				String mode = "WebSocket";
				StatusLogStore.get().append(ConnectionEvent.Type.INFO, "开始建立 TCP 连接 (" + mode + "): " + serverAddress.toString());
				ConnectStageNotifier.status("开始建立 TCP 连接 (" + mode + "): " + serverAddress.toString());
			} else {
				// Also handle vanilla case logging if needed, or leave it as is
				// Previously only logged for non-vanilla or generally before connect
				// Re-adding the log that was present in the old injection for consistency
				String mode = "原版 TCP";
				StatusLogStore.get().append(ConnectionEvent.Type.INFO, "开始建立 TCP 连接 (" + mode + "): " + serverAddress.toString());
				ConnectStageNotifier.status("开始建立 TCP 连接 (" + mode + "): " + serverAddress.toString());
			}
		}
	}
}
