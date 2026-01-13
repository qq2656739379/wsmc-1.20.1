package wsmc.api;

import javax.annotation.Nullable;

import io.netty.handler.codec.http.HttpRequest;
import net.minecraft.network.Connection;

/**
 * 此接口被混入 {@link net.minecraft.network.Connection}，可在两者间强转。
 * <p>
 * 服务端可借此在 WebSocket 握手时获取 HTTP 头。
 * <p>
 * 示例：服务端可从握手头中取到客户端真实 IP。
 * <pre>
 * MinecraftServer server = ...;
 * // 遍历已建立的连接
 * for (Connection conn: server.getConnection().getConnections()) {
 * 	@Nullable
 * 	HttpRequest httpRequest = IWebSocketServerConnection.of(conn).getWsHandshakeRequest();
 * 	if (httpRequest != null) {
 * 		@Nullable
 * 		String clientRealIP = httpRequest.headers().get("X-Forwarded-For");
 * 		// 业务逻辑
 * 	}
 * }
 * </pre>
 * 对经代理接入的客户端尤其有用。
 */
public interface IWebSocketServerConnection {
	/**
	 * 仅服务端可用。
	 * @return WebSocket 握手时的 HTTP 请求。
	 */
	@Nullable
	HttpRequest getWsHandshakeRequest();

	@Nullable
	public static IWebSocketServerConnection of(Connection connection) {
		return (IWebSocketServerConnection)(Object)connection;
	}
}
