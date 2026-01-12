package wsmc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;

public class HttpGetSniffer extends ByteToMessageDecoder {
	public final static boolean disableVanillaTCP =
			System.getProperty("wsmc.disableVanillaTCP", "false").equalsIgnoreCase("true");

	private final Consumer<HttpRequest> onWsmcHandshake;

	public HttpGetSniffer(Consumer<HttpRequest> onWsmcHandshake) {
		this.onWsmcHandshake = onWsmcHandshake;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		if (in.readableBytes() > 3) {
			byte[] byteBuffer = new byte[3];
			in.markReaderIndex();
			in.readBytes(byteBuffer, 0, 3);
			in.resetReaderIndex();
			String methodString = new String(byteBuffer, StandardCharsets.US_ASCII);

			if (methodString.equalsIgnoreCase("GET")) {
				WSMC.info("检测到 HTTP GET，尝试升级 WebSocket");
				ChannelHandlerContext ctxCodec = ctx.pipeline().context("WsmcHttpCodec");
				if (ctxCodec == null) {
					ctx.pipeline().replace(this, "WsmcHttpCodec", new HttpServerCodec());
				} else {
					ctx.pipeline().remove(this);
				}

				if (ctx.pipeline().context("WsmcHttpHandler") == null) {
					ctx.pipeline().addAfter("WsmcHttpCodec", "WsmcHttpHandler", new HttpServerHandler(this.onWsmcHandshake));
				}
			} else {
				if (HttpGetSniffer.disableVanillaTCP) {
					WSMC.info(ctx.channel().remoteAddress().toString() +
							" 尝试建立原版 TCP 已被禁用，连接关闭。");
					throw new RuntimeException("Vanilla TCP connection has been disabled by WSMC.");
				}

				WSMC.info("检测到原版 TCP，按原版处理");
				ctx.pipeline().remove(this);
			}
		}
	}
}
