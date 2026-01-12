package wsmc.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import su.plo.voice.api.server.PlasmoVoiceServer;
import wsmc.plasmo.PlasmoVoiceServerBridge;

public class PlasmoWrapper {
	public static void load() {
		try {
			try {
				PlasmoVoiceServer.getAddonsLoader().load(new PlasmoVoiceServerBridge());
			} catch (Throwable t) {
			}

			if (FMLEnvironment.dist == Dist.CLIENT) {
				PlasmoClientWrapper.load();
			}

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
