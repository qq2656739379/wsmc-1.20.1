package wsmc;

public final class ArgHolder<T> {
	private final ThreadLocal<T> wsAddress =
			ThreadLocal.withInitial(() -> null);

	private final boolean nullable;

	private ArgHolder(boolean nullable) {
		this.nullable = nullable;
	}

	public static <T> ArgHolder<T> nullable() {
		return new ArgHolder<>(true);
	}

	public static <T> ArgHolder<T> nonnull() {
		return new ArgHolder<>(false);
	}

	public void push(T val) {
		if (this.wsAddress.get() != null) {
			throw new RuntimeException("Previous WebsocketUriHolder.wsAddress has not been used!");
		}

		this.wsAddress.set(val);
	}

	/**
	 * 读取当前值。
	 * @return 之前设置的值
	 */
	public T peek() {
		T ret = this.wsAddress.get();

		if (ret == null && !nullable) {
			throw new RuntimeException("WebsocketUriHolder.wsAddress is not available!");
		}

		return ret;
	}

	/**
	 * [新增] 检查当前是否为空，不会抛出异常。
	 * 用于兼容其他模组的连接调用。
	 */
	public boolean isEmpty() {
		return this.wsAddress.get() == null;
	}

	/**
	 * 读取当前值并重置。
	 * @return 之前设置的值
	 */
	public T pop() {
		T ret = this.peek();
		this.wsAddress.set(null);
		return ret;
	}
}
