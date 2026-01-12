package wsmc.plasmo;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;

@Addon(
	id = "wsmc-voice-client",
	name = "WSMC Voice Client Bridge",
	version = "1.0.0",
	authors = {"WSMC"}
)
public class PlasmoVoiceClientBridge implements AddonInitializer {

	@InjectPlasmoVoice
	private PlasmoVoiceClient voiceClient;

	private DatagramSocket socket;
	private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
	private boolean running = true;
	private InetSocketAddress pvClientAddress;

	@Override
	public void onAddonInitialize() {
		System.out.println("[WSMC] Plasmo Voice Client Bridge Initialized");

		try {
			// Bind to an ephemeral port or specific if configured
			socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
			int localPort = socket.getLocalPort();
			System.out.println("[WSMC] Client Voice Proxy listening on: " + localPort);
			VoiceClientManager.get().setProxyPort(localPort);

			// Notify user via chat when possible (needs main thread and player)
			// We can poll or wait?
			// Better: Hook into WS Client connection success?
			// ConnectStageNotifier can help?
			// Or just print to log.

			ioExecutor.submit(this::readLoop);
			VoiceClientManager.get().setPacketReceiver(this::onPacketFromWs);

			// Try to auto-configure or warn?
			// We can't access Player here easily (might be null).

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void onPacketFromWs(ByteBuf packet) {
		if (socket == null || socket.isClosed()) return;
		try {
			if (pvClientAddress != null) {
				byte[] data = new byte[packet.readableBytes()];
				packet.readBytes(data);
				DatagramPacket udpPacket = new DatagramPacket(data, data.length, pvClientAddress);
				socket.send(udpPacket);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			packet.release();
		}
	}

	private void readLoop() {
		byte[] buffer = new byte[4096];
		while (running && !socket.isClosed()) {
			try {
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet);

				// Capture the sender address (PV Client)
				if (pvClientAddress == null || !pvClientAddress.equals(packet.getSocketAddress())) {
					pvClientAddress = (InetSocketAddress) packet.getSocketAddress();
					// System.out.println("Captured PV Client Address: " + pvClientAddress);
				}

				ByteBuf buf = Unpooled.wrappedBuffer(packet.getData(), packet.getOffset(), packet.getLength());
				VoiceClientManager.get().sendToServer(buf);
			} catch (Exception e) {
				if (running) e.printStackTrace();
			}
		}
	}

	@Override
	public void onAddonShutdown() {
		System.out.println("[WSMC] Plasmo Voice Client Bridge Shutdown");
		running = false;
		ioExecutor.shutdownNow();
		if (socket != null) socket.close();
	}
}
