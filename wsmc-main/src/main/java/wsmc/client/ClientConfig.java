package wsmc.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 轻量的客户端配置，保存于 config/wsmc-client.json。
 * 仅影响客户端行为（GUI、日志、帧长），不改变服务器逻辑。
 */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = resolveConfigDir().resolve("wsmc-client.json");

	public String wsUri = ""; // 可选的默认连接地址覆盖
	public String endpoint = ""; // 可选的路径过滤标识
	public int maxFramePayloadLength = 2097152;
	public boolean statusOverWebSocket = true; // 服务器列表是否也走 WS
	public boolean disableVanillaTCP = false;
	public boolean debug = false;
	public boolean dumpBytes = false;

	private static ClientConfig INSTANCE;

	private ClientConfig() {
	}

	public static synchronized ClientConfig get() {
		if (INSTANCE == null) {
			INSTANCE = loadInternal();
		}
		return INSTANCE;
	}

	public static synchronized void save(ClientConfig cfg) {
		Objects.requireNonNull(cfg, "config");
		INSTANCE = cfg;
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
		} catch (IOException ignored) {
		}
		try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
			GSON.toJson(cfg, w);
		} catch (IOException ignored) {
		}
	}

	private static ClientConfig loadInternal() {
		if (Files.isReadable(CONFIG_PATH)) {
			try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
				ClientConfig cfg = GSON.fromJson(r, ClientConfig.class);
				return cfg != null ? cfg : new ClientConfig();
			} catch (Exception ignored) {
			}
		}
		return new ClientConfig();
	}

	private static Path resolveConfigDir() {
		try {
			Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
			Object loader = fabricLoader.getMethod("getInstance").invoke(null);
			Object path = fabricLoader.getMethod("getConfigDir").invoke(loader);
			if (path instanceof Path p) {
				return p;
			}
		} catch (Exception ignored) {
		}
		try {
			Class<?> forgePaths = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
			Object cfgDir = forgePaths.getField("CONFIGDIR").get(null);
			Object path = cfgDir.getClass().getMethod("get").invoke(cfgDir);
			if (path instanceof Path p) {
				return p;
			}
		} catch (Exception ignored) {
		}
		try {
			Class<?> neoPaths = Class.forName("net.neoforged.fml.loading.FMLPaths");
			Object cfgDir = neoPaths.getField("CONFIGDIR").get(null);
			Object path = cfgDir.getClass().getMethod("get").invoke(cfgDir);
			if (path instanceof Path p) {
				return p;
			}
		} catch (Exception ignored) {
		}
		return Path.of("config");
	}
}
