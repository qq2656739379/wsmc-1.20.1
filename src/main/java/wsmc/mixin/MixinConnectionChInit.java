package wsmc.mixin;

import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;

import wsmc.IConnectionEx;
import wsmc.IWebSocketServerAddress;
import wsmc.client.WebSocketClientHandler;
import wsmc.WSMC;

@Debug(export = true)
@Mixin(targets = "net.minecraft.network.Connection$1")
public class MixinConnectionChInit {
    @Unique
    private Connection connection;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    protected void init(CallbackInfo callback, @Local(ordinal = 0, argsOnly = true) Connection connection) {
        this.connection = connection;
    }

    @Inject(method = "initChannel", at = @At("RETURN"), require = 1)
    protected void initChannel(Channel channel, CallbackInfo callback) {
        // --- 终极兼容性防御 ---
        try {
            // 1. 检查 Connection 对象是否存在 (修复 PacketFixer 导致的 NPE)
            if (this.connection == null) {
                // 仅在调试模式下输出，避免刷屏
                // WSMC.info("initChannel: connection is null, fallback to vanilla.");
                return;
            }

            // 2. 检查接口转换是否安全
            if (!(this.connection instanceof IConnectionEx)) {
                WSMC.info("initChannel: connection does not implement IConnectionEx, skipping.");
                return;
            }

            IConnectionEx connection = (IConnectionEx) this.connection;
            IWebSocketServerAddress wsInfo = connection.getWsInfo();

            // 3. 如果是原版连接，直接跳过 (不打印日志，保持安静)
            if (wsInfo == null || wsInfo.isVanilla()) {
                return;
            }

            // 4. 只有确认为 WebSocket 且环境正常时，才进行注入
            WSMC.info("initChannel: Injecting WS handler for " + wsInfo.getRawHost());
            WebSocketClientHandler.hookPipeline(channel.pipeline(), wsInfo);

        } catch (Throwable t) {
            // 5. 捕获所有潜在错误 (LinkageError, ClassCastException 等)
            // 这一点至关重要：Netty 如果在 initChannel 中抛出异常，会直接关闭连接而不报错！
            WSMC.info("initChannel 发生意外错误 (已忽略，尝试按原版连接): " + t.toString());
            t.printStackTrace();
        }
    }
}
