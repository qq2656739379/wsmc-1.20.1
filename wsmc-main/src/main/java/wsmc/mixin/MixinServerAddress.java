package wsmc.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.net.HostAndPort;

import net.minecraft.client.multiplayer.resolver.ServerAddress;
import wsmc.IWebSocketServerAddress;
import wsmc.WebSocketConnectionInfo;

@Debug(export = true)
@Mixin(ServerAddress.class)
public class MixinServerAddress implements IWebSocketServerAddress {
	@Shadow
	@Final
	private HostAndPort hostAndPort;

	@Unique
	@Nullable
	private WebSocketConnectionInfo connInfo;

	@Override
	public void setWsConnectionInfo(@Nullable WebSocketConnectionInfo connInfo) {
		this.connInfo = connInfo;
	}

	@Override
	public WebSocketConnectionInfo getWsConnectionInfo() {
		return this.connInfo;
	}

	@Override
	public String getRawHost() {
		return hostAndPort.getHost();
	}

	@Inject(at = @At("HEAD"), method = "toString", require = 1, cancellable = true)
	private void toStringCustom(CallbackInfoReturnable<String> callback) {
		if (!this.isVanilla()) {
			callback.setReturnValue(this.connInfo.toString());
		}
	}

	@Inject(at = @At("HEAD"), method = "equals", require = 1, cancellable = true)
	private void equalsCustom(Object object, CallbackInfoReturnable<Boolean> callback) {
		// 重定向原版 equals()

		if (this == object)
			callback.setReturnValue(true);

		if (this.isVanilla()) {
			// 当前为原版

			if (object instanceof IWebSocketServerAddress) {
				IWebSocketServerAddress ws = (IWebSocketServerAddress) object;

				// 当前原版，对方已被修改
				if (!ws.isVanilla())
					callback.setReturnValue(false);
			}

			// 交给原版继续检查 hostAndPort
			return;
		} else {
			// 当前已被修改

			if (object instanceof IWebSocketServerAddress) {
				IWebSocketServerAddress ws = (IWebSocketServerAddress) object;

				if (ws.isVanilla())
					callback.setReturnValue(false);

				if (!this.connInfo.equalTo(ws.getWsConnectionInfo()))
					callback.setReturnValue(false);

				// 交给原版继续检查 hostAndPort
				return;
			}

			callback.setReturnValue(false);
		}
	}

	@Inject(at = @At("HEAD"), method = "hashCode", require = 1, cancellable = true)
	private void hashCodeCustom(CallbackInfoReturnable<Integer> callback) {
		if (!this.isVanilla()) {
			// 已被修改：哈希需覆盖主机、端口与 connInfo
			int hash = this.getWsConnectionInfo().hashCode();
			callback.setReturnValue(hash);
		}
	}

	@Inject(at = @At("HEAD"), method = "parseString", require = 1, cancellable = true)
	private static void parseString(String uriString, CallbackInfoReturnable<ServerAddress> callback) {
		ServerAddress serverAddress = WebSocketConnectionInfo.fromWsUri(uriString);
		if (serverAddress != null)
			callback.setReturnValue(serverAddress);
	}

	@Inject(at = @At("HEAD"), method = "isValidAddress", require = 1, cancellable = true)
	private static void isValidAddress(String uriString, CallbackInfoReturnable<Boolean> callback) {
		if (WebSocketConnectionInfo.fromWsUri(uriString) != null)
			callback.setReturnValue(true);
	}
}
