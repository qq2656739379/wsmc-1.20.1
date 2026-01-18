package wsmc;

public final class ArgHolder<T> {
    // -----------------------------------------------------------
    // [Critical Change] Change ThreadLocal to InheritableThreadLocal
    // This allows child threads (Server Connector) to read parameters set by the main thread
    // -----------------------------------------------------------
    private final ThreadLocal<T> wsAddress = new InheritableThreadLocal<>() {
        @Override
        protected T initialValue() {
            return null;
        }
    };
    // -----------------------------------------------------------

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
        // InheritableThreadLocal has an initial value in child threads, so we just check if current thread has overridden it
        // To simplify, we allow re-assignment in the same thread
        // We keep it simple and just set it, usually push is done once in the main thread
        this.wsAddress.set(val);
    }

    public T peek() {
        T ret = this.wsAddress.get();

        if (ret == null && !nullable) {
            // We can keep the exception, but under crash-proof patches it shouldn't trigger
             throw new RuntimeException("WebsocketUriHolder.wsAddress is not available!");
        }

        return ret;
    }

    public boolean isEmpty() {
        return this.wsAddress.get() == null;
    }

    public T pop() {
        T ret = this.peek();
        this.wsAddress.remove(); // Use remove() to clear completely
        return ret;
    }
}
