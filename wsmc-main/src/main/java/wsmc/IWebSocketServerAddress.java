package wsmc;

import javax.annotation.Nullable;

import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * 将被混入原版 {@link ServerAddress}。
 * 假定仅 {@link ServerAddress} 实现此接口。
 * <p>
 * 可在 {@link ServerAddress} 与本接口之间互相强转。
 */
public interface IWebSocketServerAddress {
	void setWsConnectionInfo(@Nullable WebSocketConnectionInfo connInfo);

	@Nullable
	WebSocketConnectionInfo getWsConnectionInfo();

	/**
	 * 与 {@link ServerAddress#getHost()} 不同：
	 * @return 返回可能包含非 ASCII 的原始主机名。
	 */
	String getRawHost();

	default boolean isVanilla() {
		return getWsConnectionInfo() == null;
	}

	default ServerAddress asServerAddress() {
		return (ServerAddress) (Object) this;
	}

	public static IWebSocketServerAddress from(ServerAddress serverAddress) {
		return (IWebSocketServerAddress) (Object) serverAddress;
	}
}
