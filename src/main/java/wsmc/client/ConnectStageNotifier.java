package wsmc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import wsmc.mixin.ConnectScreenAccessor;

/**
 * 在连接界面上显示更细的阶段提示（线程安全，通过主线程执行）。
 */
public final class ConnectStageNotifier {
	private static volatile ConnectScreen screen;

	private ConnectStageNotifier() {}

	public static void setScreen(ConnectScreen scr) {
		Minecraft.getInstance().execute(() -> screen = scr);
	}

	public static void clear() {
		Minecraft.getInstance().execute(() -> screen = null);
	}

	public static void status(String msg) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			ConnectScreen s = screen;
			if (s != null) {
				((ConnectScreenAccessor) s).wsmc$updateStatus(Component.literal(msg));
			}
		});
	}
}
