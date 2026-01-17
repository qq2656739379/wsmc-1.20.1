package wsmc.plasmo;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;

@Addon(
	id = "wsmc-voice-server",
	name = "WSMC Voice Server Bridge",
	version = "1.0.0",
	authors = {"WSMC"}
)
public class PlasmoVoiceServerBridge implements AddonInitializer {

	@InjectPlasmoVoice
	private PlasmoVoiceServer voiceServer;

	private int voicePort = -1;
	private final Map<UUID, DatagramSocket> playerSockets = new ConcurrentHashMap<>();
	private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
	private boolean running = true;
	private InetAddress localHost;

	@Override
	public void onAddonInitialize() {
		System.out.println("[WSMC] Plasmo Voice Server Bridge Initialized");

		try {
			this.localHost = InetAddress.getLocalHost();
		} catch (Exception e) {
			System.out.println("[WSMC] Failed to resolve local host: " + e.getMessage());
			e.printStackTrace();
		}

		try {
			// Reflection to get Config and Port
			Object config = voiceServer.getClass().getMethod("getConfig").invoke(voiceServer);
			// getPort might return int or Integer
			Object portObj = config.getClass().getMethod("getPort").invoke(config);
			if (portObj instanceof Number) {
				this.voicePort = ((Number) portObj).intValue();
				System.out.println("[WSMC] Detected Plasmo Voice Port: " + this.voicePort);
			}
		} catch (Exception e) {
			System.out.println("[WSMC] Failed to detect Plasmo Voice port via reflection: " + e.getMessage());
			// Fallback?
			this.voicePort = 0; // Invalid
		}

		VoiceConnectionManager.get().setPacketReceiver(this::onVoicePacketFromWs);
	}

	private void onVoicePacketFromWs(UUID playerId, ByteBuf packet) {
		if (voicePort <= 0 || localHost == null) return;

		try {
			DatagramSocket socket = playerSockets.computeIfAbsent(playerId, this::createSocket);
			if (socket != null) {
				byte[] data = new byte[packet.readableBytes()];
				packet.readBytes(data);
				DatagramPacket udpPacket = new DatagramPacket(data, data.length, localHost, voicePort);
				socket.send(udpPacket);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			packet.release();
		}
	}

	private DatagramSocket createSocket(UUID playerId) {
		try {
			DatagramSocket socket = new DatagramSocket();
			ioExecutor.submit(() -> readLoop(playerId, socket));
			return socket;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private void readLoop(UUID playerId, DatagramSocket socket) {
		byte[] buffer = new byte[4096];
		while (running && !socket.isClosed()) {
			try {
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet);
				// Send back to WS
				ByteBuf buf = Unpooled.wrappedBuffer(packet.getData(), packet.getOffset(), packet.getLength());
				VoiceConnectionManager.get().sendToClient(playerId, buf);
			} catch (Exception e) {
				if (running) e.printStackTrace();
			}
		}
	}

	@Override
	public void onAddonShutdown() {
		System.out.println("[WSMC] Plasmo Voice Server Bridge Shutdown");
		running = false;
		ioExecutor.shutdownNow();
		for (DatagramSocket s : playerSockets.values()) {
			s.close();
		}
		playerSockets.clear();
	}
}
