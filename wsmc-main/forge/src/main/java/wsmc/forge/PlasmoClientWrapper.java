package wsmc.forge;

import su.plo.voice.api.client.PlasmoVoiceClient;
import wsmc.plasmo.PlasmoVoiceClientBridge;

public class PlasmoClientWrapper {
	public static void load() {
		try {
			PlasmoVoiceClient.getAddonsLoader().load(new PlasmoVoiceClientBridge());
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
