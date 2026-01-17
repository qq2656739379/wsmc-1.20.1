package wsmc.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.Connection;
import io.netty.channel.Channel;

import wsmc.WSMC;
import wsmc.WebSocketHandler;
// import wsmc.plasmo.VoiceConnectionManager;

@Mod(WSMC.MODID)
public class ForgeEntry {
	public static ForgeEntry instance;

	public ForgeEntry() {
		if (instance == null)
			instance = this;
		else
			throw new RuntimeException("Duplicated Class Instantiation: net.mobz.forge.MobZ");

//		if (ModList.get().isLoaded("plasmovoice")) {
//			try {
//				PlasmoWrapper.load();
//			} catch (Throwable t) {
//				WSMC.info("Failed to load Plasmo Voice integration: " + t.getMessage());
//			}
//		}
	}

	@Mod.EventBusSubscriber(modid = WSMC.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
	public final static class ModEventBusHandler {

	}

	@Mod.EventBusSubscriber(modid = WSMC.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
	public final static class ForgeEventBusHandler {
		@SubscribeEvent
		public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
//			if (event.getEntity() instanceof ServerPlayer) {
//				ServerPlayer player = (ServerPlayer) event.getEntity();
//				if (player.connection != null && player.connection.connection != null) {
//					Connection netManager = player.connection.connection;
//					if (netManager.channel != null) {
//						Channel channel = netManager.channel;
//						if (channel.pipeline().get("WsmcWebSocketServerHandler") instanceof WebSocketHandler) {
//							WebSocketHandler handler = (WebSocketHandler) channel.pipeline().get("WsmcWebSocketServerHandler");
////							VoiceConnectionManager.get().register(player.getUUID(), handler);
//							WSMC.info("Registered Voice Handler for " + player.getName().getString());
//						}
//					}
//				}
//			}
		}

		@SubscribeEvent
		public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
//			VoiceConnectionManager.get().unregister(event.getEntity().getUUID());
		}
	}
}
