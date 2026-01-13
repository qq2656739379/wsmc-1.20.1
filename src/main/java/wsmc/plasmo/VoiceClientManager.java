package wsmc.plasmo;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBuf;
import wsmc.WebSocketHandler;

public class VoiceClientManager {
	private static final VoiceClientManager INSTANCE = new VoiceClientManager();

	private final AtomicReference<WebSocketHandler> activeHandler = new AtomicReference<>();
	private int proxyPort = -1;

	public void setProxyPort(int port) {
		this.proxyPort = port;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public interface ClientPacketReceiver {
		void onVoicePacket(ByteBuf packet);
	}

	private ClientPacketReceiver packetReceiver;

	private VoiceClientManager() {}

	public static VoiceClientManager get() {
		return INSTANCE;
	}

	public void setPacketReceiver(ClientPacketReceiver receiver) {
		this.packetReceiver = receiver;
	}

	public void setActiveHandler(WebSocketHandler handler) {
		this.activeHandler.set(handler);
		handler.setVoicePacketConsumer(this::onPacketFromServer);
	}

	public void clearActiveHandler() {
		WebSocketHandler h = this.activeHandler.getAndSet(null);
		if (h != null) {
			h.setVoicePacketConsumer(null);
		}
	}

	public void sendToServer(ByteBuf packet) {
		WebSocketHandler handler = activeHandler.get();
		if (handler != null && handler.multiplexing) {
			handler.sendVoice(packet);
		} else {
			if (packet.refCnt() > 0) {
				packet.release();
			}
		}
	}

	private void onPacketFromServer(ByteBuf data) {
		if (packetReceiver != null) {
			packetReceiver.onVoicePacket(data);
		} else {
			data.release();
		}
	}
}
