package wsmc;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.net.InetAddresses;

import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * 在原版 {@link ServerAddress} 基础上附加 WebSocket 相关信息。
 * <p>
 * 不要直接比较两个 {@link WebSocketConnectionInfo}；
 * <p>
 * 应比较原版的 {@link ServerAddress} 实例。
 * <p>
 * 原版 {@link ServerAddress} 与本类皆为不可变对象。
 */
public class WebSocketConnectionInfo {
	public final ServerAddress owner;
	public final URI uri;
	public final String sni;
	public final String httpHostname;

	private WebSocketConnectionInfo(ServerAddress owner, URI uri,
		String sni, String httpHostname
	) {
		this.owner = owner;
		this.uri = uri;
		this.sni = sni;
		this.httpHostname = httpHostname;
	}

	/**
	 * 比较 URI、SNI 与 httpHostname。
	 * <p>
	 * 不检查所属的 {@link ServerAddress}。
	 * <p>
	 * 原版 ServerAddress 只比较 Host 与端口。
	 */
	public boolean equalTo(WebSocketConnectionInfo that) {
		if (that == null)
			return false;

		if (!Objects.equal(this.uri, that.uri))
			return false;

		if (!Objects.equal(this.sni, that.sni))
			return false;

		if (!Objects.equal(this.httpHostname, that.httpHostname))
			return false;

		return true;
	}

	@Override
	public boolean equals(Object that) {
		if (this == that){
			return true;
		} else if (!(that instanceof WebSocketConnectionInfo)){
			return false;
		}

		WebSocketConnectionInfo other = (WebSocketConnectionInfo) that;

		// 先检查原版字段
		if (!IWebSocketServerAddress.from(this.owner).getRawHost().equals(
				IWebSocketServerAddress.from(other.owner).getRawHost()))
			return false;

		if (this.owner.getPort() != other.owner.getPort())
			return false;

		return this.equalTo(other);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(
			this.owner.getPort(),
			IWebSocketServerAddress.from(this.owner).getRawHost(),
			this.uri,
			this.sni,
			this.httpHostname);
	}

	@Override
	public String toString() {
		return this.uri.toString() + "\n" +
			"TLS SNI: " + this.sni + "\n" +
			"HTTP Hostname: " + this.httpHostname;
	}

	private static String[] splitUserInfo(@Nullable String userInfo) {
		if (null == userInfo)
			return new String[0];

		// 拆分用户信息，保留空串
		return userInfo.split(":", -1);
	}

	private static boolean isIpLiteral(String host) {
		return InetAddresses.isInetAddress(host);
	}

	private static int defaultPortForScheme(String scheme) {
		if (scheme == null)
			return 25565;
		if (scheme.equalsIgnoreCase("ws"))
			return 80;
		if (scheme.equalsIgnoreCase("wss"))
			return 443;
		return 25565;
	}

	private static String bracketIfIpv6(String host) {
		if (host == null)
			return null;
		if (host.indexOf(':') >= 0 && !host.startsWith("[") && !host.endsWith("]"))
			return "[" + host + "]";
		return host;
	}

	private static String buildHostHeader(String host, int port, String scheme) {
		String base = bracketIfIpv6(host == null ? "" : host);
		int defaultPort = defaultPortForScheme(scheme);
		if (port != defaultPort)
			return base + ":" + port;
		return base;
	}

	private static class TargetHost {
		final String host;
		final int port; // -1 if absent

		TargetHost(String host, int port) {
			this.host = host;
			this.port = port;
		}
	}

	private static TargetHost parseTargetHost(String target) {
		if (target == null || target.isEmpty())
			return null;

		String host = target;
		int port = -1;
		if (target.startsWith("[")) {
			int idx = target.indexOf(']');
			if (idx > 0) {
				host = target.substring(1, idx);
				if (target.length() > idx + 1 && target.charAt(idx + 1) == ':') {
					try {
						port = Integer.parseInt(target.substring(idx + 2));
					} catch (NumberFormatException ignored) {
						port = -1;
					}
				}
			}
		} else {
			int colon = target.lastIndexOf(':');
			if (colon > 0) {
				String portPart = target.substring(colon + 1);
				try {
					port = Integer.parseInt(portPart);
					host = target.substring(0, colon);
				} catch (NumberFormatException ignored) {
					// keep entire target as host
					port = -1;
				}
			}
		}

		return new TargetHost(host, port);
	}

	/**
	 * 语法: (wss|ws)://sni.com:host.com@ip.ip.ip.ip[:port][/path]
	 * @param uriString 输入的 URI 字符串
	 * @return 无效则返回 null（包含原版 TCP 的情况）。
	 */
	@Nullable
	public static ServerAddress fromWsUri(String uriString) {
		try {
			URI uri = new URI(uriString);

			String scheme = uri.getScheme();
			String hostname = uri.getHost();
			boolean ipLiteral = hostname != null && InetAddresses.isInetAddress(hostname);

			if (hostname == null)
				return null;

			if (!ipLiteral) {
				try {
					IDN.toASCII(hostname);
				} catch (IllegalArgumentException e) {
					return null;
				}
			}

			if (scheme == null)
				return null;

			// scheme 为空则视为原版 TCP；ws/wss 视为 WebSocket；其他协议视为不支持。

			if (!scheme.equalsIgnoreCase("ws") && !scheme.equalsIgnoreCase("wss"))
				return null;

			int port = uri.getPort();
			if (port < 0 || port > 65535) {
				port = defaultPortForScheme(scheme);
			}

			String path = uri.getPath();
			if (path == null) {
				path = "/";
			}
			String targetOverrideRaw = path.length() > 1 ? path.substring(1) : null;
			TargetHost targetOverride = parseTargetHost(targetOverrideRaw);

			String sni = ipLiteral ? null : hostname;
			String hostnameInHeader = hostname;

			if ("wss".equalsIgnoreCase(scheme)) {
				String[] splitted = splitUserInfo(uri.getUserInfo());
				if (splitted.length > 0) {
					String userSni = splitted[0];
					if (!userSni.isEmpty()) {
						sni = userSni;
					}

					String userHostHeader = (splitted.length == 1) ? userSni : splitted[1];
					if (!userHostHeader.isEmpty()) {
						hostnameInHeader = userHostHeader;
					}
				}
			} else if ("ws".equalsIgnoreCase(scheme)) {
				String[] splitted = splitUserInfo(uri.getUserInfo());
				if (splitted.length > 0 && !splitted[0].isBlank()) {
					hostnameInHeader = splitted[0];
				}
			}

			// 基于路径的目标覆盖：ws://domain:port/<actualHost[:port]> 把路径当作逻辑目标
			if (targetOverride != null && targetOverride.host != null && !targetOverride.host.isBlank()) {
				int targetPort = targetOverride.port >= 0 ? targetOverride.port : defaultPortForScheme(scheme);
				hostnameInHeader = buildHostHeader(targetOverride.host, targetPort, scheme);
				if (!isIpLiteral(targetOverride.host)) {
					sni = targetOverride.host;
				}
			}

			hostnameInHeader = buildHostHeader(hostnameInHeader, port, scheme);

			uri = new URI(
				scheme,
				null,
				hostname,
				port,
				path,
				uri.getQuery(),
				uri.getFragment());

			ServerAddress result = new ServerAddress(hostname, port);
			WebSocketConnectionInfo connInfo = new WebSocketConnectionInfo(result, uri, sni, hostnameInHeader);
			((IWebSocketServerAddress)(Object)result).setWsConnectionInfo(connInfo);
			return result;
		} catch (URISyntaxException e) {
		}

		return null;
	}
}
