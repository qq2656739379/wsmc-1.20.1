package wsmc.plasmo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;
import wsmc.WebSocketHandler;

public class VoiceConnectionManager {
	private static final VoiceConnectionManager INSTANCE = new VoiceConnectionManager();

	private final Map<UUID, WebSocketHandler> playerHandlers = new ConcurrentHashMap<>();
	private final Map<WebSocketHandler, UUID> handlerPlayers = new ConcurrentHashMap<>();

	// Interface for the bridge to implement for receiving packets
	public interface ServerPacketReceiver {
		void onVoicePacket(UUID player, ByteBuf packet);
	}

	private ServerPacketReceiver packetReceiver;

	private VoiceConnectionManager() {}

	public static VoiceConnectionManager get() {
		return INSTANCE;
	}

	public void setPacketReceiver(ServerPacketReceiver receiver) {
		this.packetReceiver = receiver;
	}

	public void register(UUID playerId, WebSocketHandler handler) {
		playerHandlers.put(playerId, handler);
		handlerPlayers.put(handler, playerId);

		// Set the consumer on the handler
		handler.setVoicePacketConsumer((data) -> {
			onPacketFromClient(handler, data);
		});
	}

	public void unregister(UUID playerId) {
		WebSocketHandler handler = playerHandlers.remove(playerId);
		if (handler != null) {
			handlerPlayers.remove(handler);
			handler.setVoicePacketConsumer(null);
		}
	}

	public void unregister(WebSocketHandler handler) {
		UUID pid = handlerPlayers.remove(handler);
		if (pid != null) {
			playerHandlers.remove(pid);
			handler.setVoicePacketConsumer(null);
		}
	}

	public void sendToClient(UUID playerId, ByteBuf packet) {
		WebSocketHandler handler = playerHandlers.get(playerId);
		if (handler != null && handler.multiplexing) {
			handler.sendVoice(packet);
		} else {
			// If we cannot send, we should release the packet to avoid leak if the caller transferred ownership
			// But usually caller retains?
			// Let's assume caller transfers ownership (standard Netty inbound -> outbound)
			if (packet.refCnt() > 0) {
				packet.release();
			}
		}
	}

	private void onPacketFromClient(WebSocketHandler handler, ByteBuf data) {
		UUID pid = handlerPlayers.get(handler);
		if (pid != null && packetReceiver != null) {
			packetReceiver.onVoicePacket(pid, data);
		} else {
			// Release if not handled
			data.release();
		}
	}
}
