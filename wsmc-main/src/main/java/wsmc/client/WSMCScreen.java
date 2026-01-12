package wsmc.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import wsmc.WSMC;

public class WSMCScreen extends Screen {
	private static final DateTimeFormatter TS_FMT =
			DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	private EditBox wsUriField;
	private EditBox endpointField;
	private EditBox frameField;
	private Checkbox disableVanillaBox;
	private Checkbox debugBox;
	private Checkbox dumpBox;
	private Checkbox statusWsBox;
	private MultiLineEditBox logBox;
	private int formLeft;
	private int logTop;
	private int logHeight;

	private static final String[] HELP_LINES = new String[] {
		"填写示例：ws://example.com/ws",
		"Endpoint 可留空；最大帧长度单位为字节，默认 2097152。",
		"禁用原版 TCP=只走 WS，失败不回落；不勾选则失败回落原版。",
		"调试/转储仅排查时暂时开启，避免冗长日志。"
	};

	public WSMCScreen() {
		super(Component.literal("WSMC"));
	}

	@Override
	protected void init() {
		super.init();
		ClientConfig cfg = ClientConfig.get();

		int left = this.width / 2 - 150;
		int top = this.height / 2 - 90;
		int row = 0;
		int rowH = 24;
		this.formLeft = left;

		wsUriField = new EditBox(this.font, left, top + rowH * row++, 300, 20,
			Component.literal("WebSocket 地址"));
		wsUriField.setValue(cfg.wsUri);
		wsUriField.setHint(Component.literal("示例: wss://example.com/ws"));
		addRenderableWidget(wsUriField);

		endpointField = new EditBox(this.font, left, top + rowH * row++, 300, 20,
			Component.literal("服务器标识 (endpoint)"));
		endpointField.setValue(cfg.endpoint);
		endpointField.setHint(Component.literal("可留空"));
		addRenderableWidget(endpointField);

		frameField = new EditBox(this.font, left, top + rowH * row++, 300, 20,
			Component.literal("最大帧长度 (字节)"));
		frameField.setValue(String.valueOf(cfg.maxFramePayloadLength));
		frameField.setHint(Component.literal("默认 65536"));
		addRenderableWidget(frameField);

		disableVanillaBox = new Checkbox(left, top + rowH * row++, 150, 20,
				Component.literal("禁用原版 TCP"), cfg.disableVanillaTCP);
		addRenderableWidget(disableVanillaBox);

		statusWsBox = new Checkbox(left + 160, top + rowH * (row - 1), 150, 20,
				Component.literal("列表使用 WS"), cfg.statusOverWebSocket);
		addRenderableWidget(statusWsBox);

		debugBox = new Checkbox(left, top + rowH * row++, 80, 20,
				Component.literal("调试"), cfg.debug);
		addRenderableWidget(debugBox);

		dumpBox = new Checkbox(left + 90, top + rowH * (row - 1), 110, 20,
				Component.literal("转储字节"), cfg.dumpBytes);
		addRenderableWidget(dumpBox);

		int buttonY = top + rowH * row + 6;
		addRenderableWidget(Button.builder(Component.literal("保存并应用"), b -> saveConfig())
				.pos(left, buttonY)
				.size(140, 20)
				.build());
		addRenderableWidget(Button.builder(Component.literal("重连"), b -> reconnect())
				.pos(left + 160, buttonY)
				.size(140, 20)
				.build());

		this.logTop = buttonY + 30;
		this.logHeight = 100;
		logBox = new MultiLineEditBox(this.font, left, this.logTop, 300, this.logHeight,
				Component.literal("WSMC 日志"), Component.empty());
		updateLogBox();
		addRenderableWidget(logBox);
	}

	private void saveConfig() {
		ClientConfig cfg = ClientConfig.get();
		cfg.wsUri = wsUriField.getValue();
		cfg.endpoint = endpointField.getValue();
		try {
			cfg.maxFramePayloadLength = Integer.parseInt(frameField.getValue().trim());
		} catch (NumberFormatException e) {
			cfg.maxFramePayloadLength = 2097152;
		}
		cfg.statusOverWebSocket = statusWsBox.selected();
		cfg.disableVanillaTCP = disableVanillaBox.selected();
		cfg.debug = debugBox.selected();
		cfg.dumpBytes = dumpBox.selected();
		ClientConfig.save(cfg);
		StatusLogStore.get().append(ConnectionEvent.Type.INFO, "配置已保存");
		updateLogBox();
	}

	private void reconnect() {
		Minecraft mc = Minecraft.getInstance();
		ServerData data = mc.getCurrentServer();
		if (data == null) {
			StatusLogStore.get().append(ConnectionEvent.Type.WARN, "无当前服务器可重连");
			updateLogBox();
			return;
		}
		StatusLogStore.get().append(ConnectionEvent.Type.INFO, "尝试重连: " + data.ip);
		updateLogBox();
		ServerAddress address = ServerAddress.parseString(data.ip);
		mc.execute(() -> ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, address, data, false));
	}

	private void updateLogBox() {
		List<ConnectionEvent> events = StatusLogStore.get().snapshot();
		StringBuilder sb = new StringBuilder();
		for (ConnectionEvent ev : events) {
			sb.append("[")
				.append(TS_FMT.format(Instant.ofEpochMilli(ev.timestamp)))
				.append("] ")
				.append(ev.type.name())
				.append(" ")
				.append(ev.message)
				.append("\n");
		}
		logBox.setValue(sb.toString());
	}

	@Override
	public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(gfx);
		super.render(gfx, mouseX, mouseY, partialTicks);
		gfx.drawString(this.font, this.title, this.width / 2 - 20, this.height / 2 - 110, 0xFFFFFF, false);
		int helpY = this.logTop + this.logHeight + 8;
		for (String line : HELP_LINES) {
			gfx.drawString(this.font, line, this.formLeft, helpY, 0xAAAAAA, false);
			helpY += 10;
		}
	}
}
