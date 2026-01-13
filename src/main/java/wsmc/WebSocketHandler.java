package wsmc;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.channel.Channel;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

import java.util.function.Consumer;

public abstract class WebSocketHandler extends ChannelDuplexHandler {
	public final String outboundPrefix;
	public final String inboundPrefix;

	public boolean multiplexing = false;
	private Consumer<ByteBuf> voicePacketConsumer;
	private Channel channel;

	public WebSocketHandler(String inboundPrefix, String outboundPrefix) {
		this.inboundPrefix = inboundPrefix;
		this.outboundPrefix = outboundPrefix;
	}

	public void setVoicePacketConsumer(Consumer<ByteBuf> consumer) {
		this.voicePacketConsumer = consumer;
	}

	public void sendVoice(ByteBuf data) {
		if (channel != null && channel.isActive()) {
			channel.writeAndFlush(new VoicePacket(data));
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		this.channel = ctx.channel();
		super.channelActive(ctx);
	}

	public static void dumpByteArray(ByteBuf byteArray) {
		if (!WSMC.dumpBytes)
			return;

		int maxBytesPerLine = 32;
		int totalBytes = byteArray.readableBytes();
		byteArray.markReaderIndex();

		for (int i = 0; i < totalBytes; i += maxBytesPerLine) {
			int remainingBytes = Math.min(maxBytesPerLine, totalBytes - i);
			StringBuilder line = new StringBuilder();

			for (int j = 0; j < remainingBytes; j++) {
				byte currentByte = byteArray.readByte();
				line.append(String.format("%02X ", currentByte));
			}

			System.out.println(line.toString().trim());
		}
		byteArray.resetReaderIndex();
	}

	protected abstract void sendWsFrame(ChannelHandlerContext ctx, WebSocketFrame frame, ChannelPromise promise) throws Exception;

	@Override
	public final void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof ByteBuf) {
			ByteBuf byteBuf = (ByteBuf) msg;

			if (WSMC.debug()) {
				WSMC.debug(this.outboundPrefix + " (" + byteBuf.readableBytes() + "):");
				dumpByteArray(byteBuf);
			}

			if (multiplexing) {
				ByteBuf wrapped = ctx.alloc().buffer(1 + byteBuf.readableBytes());
				wrapped.writeByte(0x00);
				wrapped.writeBytes(byteBuf);
				// original byteBuf is released by channel pipeline? No, if we consume it we should release.
				// But here we are wrapping it.
				// If we assume ownership of byteBuf (since it's passed to write), we should release it if we don't pass it down.
				// Since we create a new wrapped buffer, we should release the old one if we are responsible.
				// However, if we don't call ctx.write(byteBuf), we break the chain.
				// Usually the encoder handles release?
				// To be safe, we release it here if we don't use it anymore.
				// wait, if write fails?
				// Let's assume standard Netty ownership: write() consumes reference count.
				// We must release 'byteBuf' if we don't forward it.
				// But we are forwarding 'wrapped'.
				// So we release 'byteBuf'.
				// But be careful if it is a slice.
				ReferenceCountUtil.release(byteBuf);
				sendWsFrame(ctx, new BinaryWebSocketFrame(wrapped), promise);
			} else {
				sendWsFrame(ctx, new BinaryWebSocketFrame(byteBuf), promise);
			}
		} else if (msg instanceof VoicePacket) {
			if (multiplexing) {
				ByteBuf payload = ((VoicePacket) msg).payload();
				ByteBuf wrapped = ctx.alloc().buffer(1 + payload.readableBytes());
				wrapped.writeByte(0x01);
				wrapped.writeBytes(payload);
				payload.release();
				sendWsFrame(ctx, new BinaryWebSocketFrame(wrapped), promise);
			}
		} else {
			WSMC.debug(this.outboundPrefix + " Passthrough: " + msg.getClass().getName());
			ctx.write(msg, promise);
		}
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof WebSocketFrame) {
			if (msg instanceof BinaryWebSocketFrame) {
				WebSocketFrame frame = (WebSocketFrame) msg;
				ByteBuf content = frame.content();
				content.retain();

				if (WSMC.debug()) {
					WSMC.debug(this.inboundPrefix + " (" + content.readableBytes() + "):");
					dumpByteArray(content);
				}

				if (multiplexing) {
					if (content.readableBytes() > 0) {
						byte type = content.readByte();
						if (type == 0x00) {
							// Minecraft packet
							// slice the rest? readByte advanced the index.
							// we need to pass a new buffer or the same buffer with advanced index?
							// ctx.fireChannelRead(content) works because readerIndex is advanced.
							// content is retained.
							ctx.fireChannelRead(content);
						} else if (type == 0x01) {
							// Voice packet
							if (voicePacketConsumer != null) {
								voicePacketConsumer.accept(content);
							} else {
								content.release();
							}
						} else {
							WSMC.debug("Unknown packet type: " + type);
							content.release();
						}
					} else {
						content.release();
					}
				} else {
					ctx.fireChannelRead(content);
				}

				frame.release();
			} else if (msg instanceof TextWebSocketFrame) {
				// 透传文本帧，供信令使用
				ctx.fireChannelRead(((TextWebSocketFrame) msg).retain());
			} else if (msg instanceof CloseWebSocketFrame) {
				WSMC.debug("CloseWebSocketFrame (" + ((CloseWebSocketFrame) msg).statusCode()
						+ ") received : " + ((CloseWebSocketFrame) msg).reasonText());
			} else {
				WSMC.debug("Unsupported WebSocketFrame: " + msg.getClass().getName());
			}
		}
	}

	public static class WebSocketServerHandler extends WebSocketHandler {
		public WebSocketServerHandler() {
			super("C->S", "S->C");
		}

		@Override
		protected void sendWsFrame(ChannelHandlerContext ctx, WebSocketFrame frame, ChannelPromise promise) throws Exception {
			ctx.write(frame, promise);
		}
	}
}
