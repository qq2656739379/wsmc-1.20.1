package wsmc;

import io.netty.buffer.ByteBuf;

public class VoicePacket {
	private final ByteBuf payload;

	public VoicePacket(ByteBuf payload) {
		this.payload = payload;
	}

	public ByteBuf payload() {
		return payload;
	}
}
