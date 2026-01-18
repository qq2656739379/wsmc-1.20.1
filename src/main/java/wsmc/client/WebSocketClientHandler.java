package wsmc.client;

import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import wsmc.IWebSocketServerAddress;
import wsmc.WSMC;
import wsmc.WebSocketConnectionInfo;
import wsmc.WebSocketHandler;
import wsmc.client.ConnectionEvent.Type;
import wsmc.client.ConnectStageNotifier;
import wsmc.client.StatusLogStore;
import wsmc.client.ClientConfig;
// import wsmc.plasmo.VoiceClientManager;

public class WebSocketClientHandler extends WebSocketHandler {
	private final WebSocketClientHandshaker handshaker;
	private ChannelPromise handshakeFuture;
	private final String targetInfo;
	private final WebSocketConnectionInfo connInfo;
	private ScheduledFuture<?> pingFuture;
	private static final AtomicBoolean nettyLocationLogged = new AtomicBoolean(false);
	private static final AtomicBoolean frameLengthLogged = new AtomicBoolean(false);

	private void log(Type type, String msg) {
		StatusLogStore.get().append(type, msg);
	}

	/**
	 * 控制最大允许的帧载荷长度。
	 * 大型整合包可适当调高。
	 */
	public final static String maxFramePayloadLength = System.getProperty("wsmc.maxFramePayloadLength", "1048576");

	private static int parseMaxFrameLength() {
		String rawSysProp = System.getProperty("wsmc.maxFramePayloadLength");
		boolean sysPropProvided = rawSysProp != null;
		int sysVal = parseIntOrDefault(sysPropProvided ? rawSysProp : WebSocketClientHandler.maxFramePayloadLength, 1048576);
		int cfgVal = ClientConfig.get().maxFramePayloadLength;
		int effective = cfgVal > 0 ? cfgVal : sysVal;
		int floor = sysPropProvided ? sysVal : 1048576;
		if (effective < floor) {
			WSMC.info("maxFramePayloadLength too low (" + effective + "), raising to " + floor + " to avoid frame overflow");
			effective = floor;
			persistFrameLength(effective);
		}
		logFrameLengthOnce(effective, cfgVal, sysVal, sysPropProvided);
		return effective;
	}

	private static int parseIntOrDefault(String value, int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (Exception e) {
			return fallback;
		}
	}

	private static void persistFrameLength(int value) {
		try {
			ClientConfig cfg = ClientConfig.get();
			if (cfg.maxFramePayloadLength != value) {
				cfg.maxFramePayloadLength = value;
				ClientConfig.save(cfg);
			}
		} catch (Exception e) {
			WSMC.info("Failed to persist maxFramePayloadLength upgrade: " + e.getMessage());
		}
	}

	private static void logFrameLengthOnce(int effective, int cfgVal, int sysVal, boolean sysPropProvided) {
		if (frameLengthLogged.compareAndSet(false, true)) {
			String origin = sysPropProvided ? "sysProp" : "default";
			WSMC.info("maxFramePayloadLength in use=" + effective + " (config=" + cfgVal + ", " + origin + "=" + sysVal + ")");
		}
	}

	public WebSocketClientHandler(WebSocketConnectionInfo connInfo) {
		super("S->C", "C->S");
		this.connInfo = connInfo;
		this.targetInfo = connInfo.uri.toString();

		logNettyLocationOnce();

		int maxFramePayloadLength = parseMaxFrameLength();

		DefaultHttpHeaders headers = new DefaultHttpHeaders();
		headers.set("Host", connInfo.httpHostname);
		headers.set("X-WSMC-Version", "2");

		// 使用 V13（RFC 6455 / HyBi-17）握手；如改为 V08 或 V00，需注意 V00 不支持 ping，
		// 同时管线中的 HttpResponseDecoder 也要换成 WebSocketHttpResponseDecoder。
		this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(connInfo.uri,
				WebSocketVersion.V13, null, true, headers, maxFramePayloadLength);
	}

	private static void logNettyLocationOnce() {
		if (nettyLocationLogged.getAndSet(true)) return;
		WSMC.info("Netty resource lookup: DefaultHeaders=" + locateResource("io/netty/handler/codec/DefaultHeaders.class")
				+ ", DefaultHttpHeaders=" + locateResource("io/netty/handler/codec/http/DefaultHttpHeaders.class")
				+ ", ValueValidator=" + locateResource("io/netty/handler/codec/DefaultHeaders$ValueValidator.class"));
	}

	private static String locateResource(String resource) {
		try {
			URL url = WebSocketClientHandler.class.getClassLoader().getResource(resource);
			return url == null ? "not found" : url.toString();
		} catch (Exception e) {
			return "error: " + e.getMessage();
		}
	}

	public static void hookPipeline(ChannelPipeline pipeline, IWebSocketServerAddress wsInfo) {
		// Do not perform WebSocket handshake for vanilla TCP Minecraft
		if (wsInfo == null) {
			StatusLogStore.get().append(Type.WARN, "未获取到 WS 信息，跳过握手");
			return;
		}
		if (wsInfo.isVanilla()) {
			StatusLogStore.get().append(Type.INFO, "目标为原版 TCP，跳过 WebSocket 握手");
			return;
		}

		if (wsInfo != null && !wsInfo.isVanilla()) {
			WebSocketConnectionInfo connInfo = wsInfo.getWsConnectionInfo();
			final WebSocketClientHandler handler = new WebSocketClientHandler(connInfo);
			WSMC.info("Connecting to WebSocket Server:\n" + connInfo.toString());
			StatusLogStore.get().append(Type.INFO, "已注入 WebSocket 管线，等待握手: " + connInfo.uri);
			WSMC.info("hookPipeline: adding client HTTP/WS handlers (scheme=" + connInfo.uri.getScheme() + ")");

			addAfterOrLast(pipeline, "timeout", "WsmcHttpClient", new HttpClientCodec());
			int aggSize = parseMaxFrameLength();
			addAfterOrLast(pipeline, "WsmcHttpClient", "WsmcHttpAggregator", new HttpObjectAggregator(aggSize));
			addAfterOrLast(pipeline, "WsmcHttpAggregator", "WsmcCompressionHandler", WebSocketClientCompressionHandler.INSTANCE);
			addAfterOrLast(pipeline, "WsmcCompressionHandler", "WsmcWebSocketClientHandler", handler);

			if ("wss".equalsIgnoreCase(connInfo.uri.getScheme())) {
				try {
					SslContext sslCtx = SslContextBuilder.forClient()
							// Prefer TLS 1.3 while keeping 1.2 as fallback
							.protocols("TLSv1.3", "TLSv1.2")
							.trustManager(InsecureTrustManagerFactory.INSTANCE).build();

					// 默认用 httpHostname 作为 SNI/peer host，除非显式提供了 sni
					String sniHost = (connInfo.sni == null || connInfo.sni.isBlank()) ? connInfo.httpHostname : connInfo.sni;
					int port = connInfo.uri.getPort();
					if (port <= 0) {
						port = 443;
					}
					SSLEngine sslEngine = sslCtx.newEngine(ByteBufAllocator.DEFAULT, sniHost, port);
					SSLParameters sslParameters = sslEngine.getSSLParameters();
					sslParameters.setServerNames(Collections.singletonList(new SNIHostName(sniHost)));
					sslEngine.setSSLParameters(sslParameters);

					// SSL 处理器
					SslHandler sslHandler = new SslHandler(sslEngine);

					addAfterOrLast(pipeline, "timeout", "WsmcSslHandler", sslHandler);
					StatusLogStore.get().append(Type.INFO, "已启用 TLS/SNI，继续握手: " + connInfo.uri);
				} catch (SSLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static void addAfterOrLast(ChannelPipeline pipeline, String anchor, String name, io.netty.channel.ChannelHandler handler) {
		try {
			if (pipeline.get(name) != null) {
				return;
			}
			if (pipeline.get(anchor) != null) {
				pipeline.addAfter(anchor, name, handler);
				WSMC.debug("Pipeline addAfter " + anchor + " -> " + name);
			} else {
				pipeline.addLast(name, handler);
				WSMC.debug("Pipeline addLast " + name + " (anchor missing: " + anchor + ")");
			}
		} catch (Exception e) {
			WSMC.info("无法注入管线节点 " + name + ": " + e.getMessage());
		}
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		super.handlerAdded(ctx);
		this.handshakeFuture = ctx.newPromise();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);

		// --- [兼容 PacketFixer] ---
		// 它可能在连接建立瞬间清空管线。
		// 必须确保 HttpClientCodec 存在，否则无法发送 WebSocket 握手请求。
		if (ctx.pipeline().get(HttpClientCodec.class) == null) {
			WSMC.info("检测到 HttpClientCodec 缺失（可能是 PacketFixer 清理了），正在恢复...");
			ctx.pipeline().addFirst("WsmcHttpClient", new HttpClientCodec());
		}

		if (ctx.pipeline().get(HttpObjectAggregator.class) == null) {
			 // Aggregator 加在 Codec 后面
			 // 注意：这里需要确保 WsmcHttpClient 已经存在或刚被加回来
			 int aggSize = parseMaxFrameLength();
			 if (ctx.pipeline().get("WsmcHttpClient") != null) {
				ctx.pipeline().addAfter("WsmcHttpClient", "WsmcHttpAggregator", new HttpObjectAggregator(aggSize));
			 } else {
				 // 兜底
				 ctx.pipeline().addFirst("WsmcHttpAggregator", new HttpObjectAggregator(aggSize));
			 }
		}
		// ---------------------------------------

		log(Type.INFO, "开始握手: " + this.targetInfo);
		ConnectStageNotifier.status("开始 WebSocket 握手: " + this.targetInfo);
		handshaker.handshake(ctx.channel());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		if (pingFuture != null) {
			pingFuture.cancel(false);
			pingFuture = null;
		}
		if (multiplexing) {
//			VoiceClientManager.get().clearActiveHandler();
		}
		WSMC.debug(this.inboundPrefix + " WebSocket Client disconnected!");
		log(Type.WARN, "连接断开: " + this.targetInfo);
		ConnectStageNotifier.status("连接断开: " + this.targetInfo);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		super.exceptionCaught(ctx, cause);
		cause.printStackTrace();
		if (!handshakeFuture.isDone()) {
			handshakeFuture.setFailure(cause);
		}
		log(Type.ERROR, "异常: " + cause.getMessage());
		ConnectStageNotifier.status("异常: " + cause.getMessage());
		ctx.close();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		Channel ch = ctx.channel();

		// --- [修复补丁 2：收到响应时的防御] ---
		if (!handshaker.isHandshakeComplete()) {
			try {
				// Packet Fixer 可能在握手期间又把处理器删了。
				// 我们再次检查，确保 finishHandshake 能找到它们并正常移除，而不是报错。

				if (ctx.pipeline().get(HttpClientCodec.class) == null) {
					WSMC.info("检测到 HttpClientCodec 缺失（channelRead），正在防御性恢复...");
					ctx.pipeline().addFirst("WsmcHttpClient", new HttpClientCodec());
				}

				if (ctx.pipeline().get(HttpObjectAggregator.class) == null) {
					 int aggSize = parseMaxFrameLength();
					 if (ctx.pipeline().get("WsmcHttpClient") != null) {
						ctx.pipeline().addAfter("WsmcHttpClient", "WsmcHttpAggregator", new HttpObjectAggregator(aggSize));
					 }
				}

				// 正常完成握手
				handshaker.finishHandshake(ch, (FullHttpResponse) msg);

				// WSMC 协议协商逻辑
				if (msg instanceof FullHttpResponse) {
					HttpHeaders h = ((FullHttpResponse) msg).headers();
					if ("2".equals(h.get("X-WSMC-Version"))) {
						this.multiplexing = true;
						log(Type.INFO, "Negotiated WSMC v2 (Multiplexing)");
					}
				}

				WSMC.debug(this.inboundPrefix + " WebSocket Client connected!");
				log(Type.INFO, "握手成功: " + this.targetInfo);
				ConnectStageNotifier.status("握手成功，进入登录: " + this.targetInfo);
				startPing(ctx);
				handshakeFuture.setSuccess();

				// --- [兼容 PacketFixer] ---
				// 握手成功后，移除它的 splitter，避免它拦截 WebSocket 帧
				if (ctx.pipeline().get("splitter") != null) {
					WSMC.info("移除 PacketFixer 的 splitter 以允许 WebSocket 数据通过...");
					ctx.pipeline().remove("splitter");
				}
				// ------------------------------------------------

				// 注意：握手成功后，不要去动 decoder/encoder，
				// 让 Packet Fixer 的处理器留在那，只要它们不干扰 WebSocket 帧（通常经过 HTTP 升级后它们就接触不到数据了）。

			} catch (WebSocketHandshakeException e) {
				WSMC.debug(this.inboundPrefix + " WebSocket Client failed to connect");
				handshakeFuture.setFailure(e);
				String detail = (msg instanceof FullHttpResponse) ? formatHttpResponse((FullHttpResponse) msg) : "";
				log(Type.ERROR, "握手失败: " + e.getMessage() + (detail.isEmpty() ? "" : " | " + detail));
				ConnectStageNotifier.status("握手失败: " + e.getMessage());
			}
			return;
		}

		if (msg instanceof FullHttpResponse) {
			FullHttpResponse response = (FullHttpResponse) msg;
			String detail = formatHttpResponse(response);
			log(Type.ERROR, "Unexpected HTTP Response: " + detail);
			throw new IllegalStateException("Unexpected FullHttpResponse " + detail);
		}


		if (msg instanceof CloseWebSocketFrame) {
			CloseWebSocketFrame close = (CloseWebSocketFrame) msg;
			log(Type.WARN, "收到服务器关闭: code=" + close.statusCode() + " reason=" + close.reasonText());
			ConnectStageNotifier.status("服务器关闭连接: " + close.statusCode() + " / " + close.reasonText());
		}
		if (msg instanceof PingWebSocketFrame) {
			log(Type.INFO, "收到服务器 Ping");
		}
		if (msg instanceof PongWebSocketFrame) {
			log(Type.INFO, "收到服务器 Pong");
		}

		super.channelRead(ctx, msg);
	}

	@Override
	protected void sendWsFrame(ChannelHandlerContext ctx, WebSocketFrame frame, ChannelPromise promise) throws Exception {
		if (handshakeFuture.isSuccess()) {
			ctx.write(frame, promise);
		} else {
			handshakeFuture.addListener((future) -> {
				if (handshakeFuture.isSuccess()) {
					ctx.write(frame, promise);
				}
			});
		}
	}

	private void startPing(ChannelHandlerContext ctx) {
		if (pingFuture != null && !pingFuture.isCancelled()) return;
		pingFuture = ctx.executor().scheduleAtFixedRate(() -> {
			if (!ctx.channel().isActive()) return;
			ctx.writeAndFlush(new PingWebSocketFrame());
			log(Type.INFO, "发送 Ping");
		}, 10, 10, TimeUnit.SECONDS);
	}

	private static String formatHttpResponse(FullHttpResponse response) {
		try {
			HttpHeaders headers = response.headers();
			StringBuilder sb = new StringBuilder();
			sb.append("status=").append(response.status());
			sb.append(" headers=").append(headers.entries());
			String content = response.content().toString(CharsetUtil.UTF_8);
			if (content.length() > 512) {
				content = content.substring(0, 512) + "...";
			}
			sb.append(" body=").append(content.replace('\n', ' '));
			return sb.toString();
		} catch (Exception ex) {
			return "status=" + response.status() + " (format error: " + ex.getMessage() + ")";
		}
	}
}
