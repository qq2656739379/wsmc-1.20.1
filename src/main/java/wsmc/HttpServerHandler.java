package wsmc;

import java.util.function.Consumer;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;

public class HttpServerHandler extends ChannelInboundHandlerAdapter {
	public final static String wsmcEndpoint = System.getProperty("wsmc.wsmcEndpoint", null);

	/**
	 * 控制允许的最大帧载荷长度。
	 * 大型整合包可酌情调高。
	 */
	public final static String maxFramePayloadLength = System.getProperty("wsmc.maxFramePayloadLength", "2097152");

	private static final int MAX_FRAME_PAYLOAD_LENGTH;

	static {
		int length;
		try {
			length = Integer.parseInt(HttpServerHandler.maxFramePayloadLength);
		} catch (Exception e) {
			WSMC.info("Invalid maxFramePayloadLength='" + HttpServerHandler.maxFramePayloadLength + "', fallback to 2097152");
			length = 2097152;
		}
		MAX_FRAME_PAYLOAD_LENGTH = length;
	}

	/**
	 * 收到 WebSocket 升级请求时回调。
	 * 注意：此时并不保证握手一定成功。
	 */
	private final Consumer<HttpRequest> onWsmcHandshake;

	public HttpServerHandler(Consumer<HttpRequest> onWsmcHandshake) {
		this.onWsmcHandshake = onWsmcHandshake;
	}

	// 服务端开发辅助：输出握手中的关键信息（真实 IP / 地区等）。
	private void logHandshake(ChannelHandlerContext ctx, HttpRequest httpRequest) {
		HttpHeaders headers = httpRequest.headers();
		String remote = String.valueOf(ctx.channel().remoteAddress());
		String host = headers.get("Host");
		String xff = headers.get("X-Forwarded-For");
		String cfCountry = headers.get("CF-IPCountry");
		WSMC.info("WSMC handshake from " + remote
				+ ", host=" + host
				+ (xff != null ? ", X-Forwarded-For=" + xff : "")
				+ (cfCountry != null ? ", CF-IPCountry=" + cfCountry : ""));
	}

	/**
	 * 判断入站请求路径是否匹配 {@link wsmc.HttpServerHandler#wsmcEndpoint}。
	 * 若 {@link wsmc.HttpServerHandler#wsmcEndpoint} 为空，则放行任意路径。
	 *
	 * @param endpoint 请求路径
	 * @return 匹配或无需匹配则为 true，否则为 false。
	 */
	private boolean isWsmcEndpoint(String endpoint) {
		if (HttpServerHandler.wsmcEndpoint == null)
			return true;

		// This has to be case-sensitive!
		return HttpServerHandler.wsmcEndpoint.equals(endpoint);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof HttpRequest) {
			HttpRequest httpRequest = (HttpRequest) msg;
			String endpoint = httpRequest.uri();

			WSMC.info("收到 HTTP 请求: " + httpRequest.uri());

			HttpHeaders headers = httpRequest.headers();
				String connectionHeader = headers.get(HttpHeaderNames.CONNECTION);
				String upgradeHeader = headers.get(HttpHeaderNames.UPGRADE);
				String hostHeader = headers.get("Host");
				String wsmcVersion = headers.get("X-WSMC-Version");

			if ("Upgrade".equalsIgnoreCase(connectionHeader)
					&& "WebSocket".equalsIgnoreCase(upgradeHeader)
					&& isWsmcEndpoint(endpoint)) {
				if (hostHeader == null) {
					WSMC.info("Missing Host header on WebSocket upgrade, closing");
					ctx.close();
					return;
				}

				String url = "ws://" + hostHeader + httpRequest.uri();
				WSMC.debug("Upgrade to: " + headers.get("Upgrade") + " for: " + url);
				WSMC.info("收到 WebSocket 升级请求: " + url);

				if (this.onWsmcHandshake != null) {
					this.onWsmcHandshake.accept(httpRequest);
				}

				logHandshake(ctx, httpRequest);

				boolean enableMultiplexing = "2".equals(wsmcVersion);
				WebSocketHandler.WebSocketServerHandler wsHandler = new WebSocketHandler.WebSocketServerHandler();
				if (enableMultiplexing) {
					wsHandler.multiplexing = true;
					WSMC.info("Enabling WSMC v2 multiplexing for " + url);
				}

				// 在管线中加入 WebSocket 处理器
				if (ctx.pipeline().context("WsmcWebSocketServerHandler") != null) {
					ctx.pipeline().remove(this);
				} else {
					ctx.pipeline().replace(this, "WsmcWebSocketServerHandler", wsHandler);
				}

				WSMC.debug("Opened Channel: " + ctx.channel());

				int maxFramePayloadLength = MAX_FRAME_PAYLOAD_LENGTH;
				WSMC.info("WSMC maxFramePayloadLength=" + maxFramePayloadLength);
				// 发起握手，将 HTTP 升级为 WebSocket 协议
				WebSocketServerHandshakerFactory wsFactory =
							new WebSocketServerHandshakerFactory(url, null, true, maxFramePayloadLength);
				WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(httpRequest);

					if (handshaker == null) {
					WSMC.info("Unsupported WebSocket version");
					WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
				} else {
					WSMC.debug("Handshaking starts...");
					HttpHeaders responseHeaders = new DefaultHttpHeaders();
					if (enableMultiplexing) {
						responseHeaders.set("X-WSMC-Version", "2");
					}
						handshaker.handshake(ctx.channel(), httpRequest, responseHeaders, ctx.channel().newPromise())
							.addListener((future) -> {
								if (future.isSuccess()) {
									WSMC.info("WebSocket 握手完成: " + url);
								} else {
									WSMC.info("WebSocket 握手失败: " + url + ", 错误=" + future.cause());
								}
							});
				}

				// 假定服务端不会在收到客户端数据前主动发送数据。
			} else {
				// 非 WebSocket 升级请求，返回默认 HTTP 响应
				DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
						HttpResponseStatus.OK, Unpooled.copiedBuffer("HTTP default response", CharsetUtil.UTF_8));
				response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
				response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
				response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

				ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
			}
		} else if (msg == LastHttpContent.EMPTY_LAST_CONTENT) {
			WSMC.debug("EMPTY_LAST_CONTENT");
		} else {
			WSMC.debug("HttpServerHandler got unknown incoming request: " + msg.getClass().getName());
		}
	}
}
