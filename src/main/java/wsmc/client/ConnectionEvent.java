package wsmc.client;

import java.time.Instant;

public final class ConnectionEvent {
	public enum Type {
		INFO,
		WARN,
		ERROR
	}

	public final Type type;
	public final String message;
	public final long timestamp;

	public ConnectionEvent(Type type, String message) {
		this(type, message, Instant.now().toEpochMilli());
	}

	public ConnectionEvent(Type type, String message, long timestamp) {
		this.type = type;
		this.message = message;
		this.timestamp = timestamp;
	}
}
