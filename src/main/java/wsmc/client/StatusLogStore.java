package wsmc.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * 内存环形缓冲，存近期连接事件，并以轻量 JSON 持久化。
 */
public final class StatusLogStore {
	private static final int MAX_EVENTS = 50;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path LOG_PATH = resolveConfigDir().resolve("wsmc-status.json");

	private static StatusLogStore INSTANCE;

	private final Deque<ConnectionEvent> events = new ArrayDeque<>();

	private StatusLogStore() {
	}

	public static synchronized StatusLogStore get() {
		if (INSTANCE == null) {
			INSTANCE = new StatusLogStore();
			INSTANCE.load();
		}
		return INSTANCE;
	}

	public synchronized void append(ConnectionEvent.Type type, String message) {
		if (events.size() >= MAX_EVENTS) {
			events.removeFirst();
		}
		events.addLast(new ConnectionEvent(type, message));
		save();
	}

	public synchronized List<ConnectionEvent> snapshot() {
		return new ArrayList<>(events);
	}

	private void load() {
		if (Files.isReadable(LOG_PATH)) {
			try (Reader r = Files.newBufferedReader(LOG_PATH)) {
				List<ConnectionEvent> list = GSON.fromJson(r,
					new TypeToken<List<ConnectionEvent>>(){}.getType());
				if (list != null) {
					for (ConnectionEvent ev : list) {
						append(ev.type, ev.message);
					}
				}
			} catch (Exception ignored) {
			}
		}
	}

	private void save() {
		try {
			Files.createDirectories(LOG_PATH.getParent());
		} catch (IOException ignored) {
		}
		try (Writer w = Files.newBufferedWriter(LOG_PATH)) {
			GSON.toJson(events, w);
		} catch (IOException ignored) {
		}
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
