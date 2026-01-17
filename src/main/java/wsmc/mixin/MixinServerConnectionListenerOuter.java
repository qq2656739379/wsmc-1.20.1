package wsmc.mixin;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

import io.netty.handler.ssl.ClientAuth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SniHandler;
import io.netty.util.DomainNameMapping;
import io.netty.util.DomainNameMappingBuilder;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.Varint21FrameDecoder;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerConnectionListener;

import wsmc.HttpServerHandler;
import wsmc.IConnectionEx;
import wsmc.WSMC;
import wsmc.WsmcConfig;

@Mixin(ServerConnectionListener.class)
public abstract class MixinServerConnectionListenerOuter {

    @Unique
    private EventLoopGroup wsmcBossGroup;
    @Unique
    private EventLoopGroup wsmcWorkerGroup;

    @Inject(method = "startTcpServerListener", at = @At("RETURN"), require = 0)
    public void startTcpServerListener(InetAddress address, int port, CallbackInfo ci) {
        startWsmcTlsServer(address);
    }

    // In some versions, it might be named differently or just 'start'
    @Inject(method = "start", at = @At("RETURN"), require = 0)
    public void start(CallbackInfo ci) {
        // Fallback injection
    }

    @Inject(method = "stop", at = @At("RETURN"))
    public void stop(CallbackInfo ci) {
        if (wsmcBossGroup != null) {
            WSMC.info("Stopping WSMC SSL Boss Group");
            wsmcBossGroup.shutdownGracefully();
            wsmcBossGroup = null;
        }
        if (wsmcWorkerGroup != null) {
            WSMC.info("Stopping WSMC SSL Worker Group");
            wsmcWorkerGroup.shutdownGracefully();
            wsmcWorkerGroup = null;
        }
    }

    @Unique
    private void startWsmcTlsServer(InetAddress vanillaAddress) {
        WsmcConfig.load();
        int sslPort = WsmcConfig.sslPort;
        File certDir = WsmcConfig.getCertPath().toFile();

        if (!certDir.exists() || !certDir.isDirectory()) {
            WSMC.info("WSMC SSL Cert directory not found: " + certDir.getAbsolutePath());
            return;
        }

        // Scan for certs
        File[] files = certDir.listFiles((dir, name) -> name.endsWith(".crt") || name.endsWith(".pem"));
        if (files == null || files.length == 0) {
            WSMC.info("No certificates found in " + certDir.getAbsolutePath());
            return;
        }

        WSMC.info("Starting WSMC SSL Listener on port " + sslPort);

        try {
            // Pre-scan to find first valid cert for default
            SslContext firstContext = null;
            // Iterate first just to find a default context
             for (File certFile : files) {
                 String fileName = certFile.getName();
                 String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                 File keyFile = new File(certDir, baseName + ".key");
                 if (keyFile.exists()) {
                      firstContext = SslContextBuilder.forServer(certFile, keyFile)
                            .clientAuth(ClientAuth.NONE)
                            .build();
                      break;
                 }
             }

            if (firstContext == null) {
                WSMC.info("No valid certificate/key pairs found.");
                return;
            }

            DomainNameMappingBuilder<SslContext> mappingBuilder = new DomainNameMappingBuilder<>(firstContext);
            int certCount = 0;

            for (File certFile : files) {
                String fileName = certFile.getName();
                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                File keyFile = new File(certDir, baseName + ".key");

                if (keyFile.exists()) {
                    WSMC.info("Loading certificate: " + certFile.getName());
                    SslContext ctx = SslContextBuilder.forServer(certFile, keyFile)
                            .clientAuth(ClientAuth.NONE)
                            .build();

                    mappingBuilder.add(baseName, ctx);
                    certCount++;
                }
            }

            final int finalCertCount = certCount;
            final SslContext finalDefaultContext = firstContext;
            final DomainNameMapping<SslContext> mapping = mappingBuilder.build();

            // Setup Netty Bootstrap
            ServerConnectionListener listener = (ServerConnectionListener) (Object) this;
            ServerConnectionListenerAccessor accessor = (ServerConnectionListenerAccessor) listener;
            List<ChannelFuture> channels = accessor.getChannels();
            List<Connection> connections = accessor.getConnections();

            Class<? extends io.netty.channel.ServerChannel> channelClass;

            if (Epoll.isAvailable() && Boolean.getBoolean("wsmc.useEpoll")) {
                wsmcBossGroup = new EpollEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("WSMC Epoll Server IO #%d").setDaemon(true).build());
                wsmcWorkerGroup = new EpollEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("WSMC Epoll Server Worker #%d").setDaemon(true).build());
                channelClass = EpollServerSocketChannel.class;
            } else {
                wsmcBossGroup = new NioEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("WSMC Server IO #%d").setDaemon(true).build());
                wsmcWorkerGroup = new NioEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("WSMC Server Worker #%d").setDaemon(true).build());
                channelClass = NioServerSocketChannel.class;
            }

            ServerBootstrap b = new ServerBootstrap()
                    .group(wsmcBossGroup, wsmcWorkerGroup)
                    .channel(channelClass)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();

                            // 1. SSL/SNI
                            if (finalCertCount > 1) {
                                p.addLast("ssl", new SniHandler(mapping));
                            } else {
                                // Re-use the handler from the first context if single or default
                                p.addLast("ssl", finalDefaultContext.newHandler(ch.alloc()));
                            }

                            // 2. HTTP Codec (for WebSocket upgrade)
                            p.addLast("http", new HttpServerCodec());

                            // 3. WSMC Handlers & Minecraft Handlers
                            Connection connection = new Connection(PacketFlow.SERVERBOUND);
                            ((IConnectionEx) connection).setWsInfo(null); // Server side

                            connections.add(connection);

                            p.addLast("wsmc-handler", new HttpServerHandler(req -> {
                                ((IConnectionEx) connection).setWsHandshakeRequest(req);
                            }));

                            // These handlers are dormant until data comes through, but WS handler replaces wsmc-handler and emits data.
                            p.addLast("splitter", new Varint21FrameDecoder());
                            p.addLast("decoder", new PacketDecoder(PacketFlow.SERVERBOUND));
                            p.addLast("prepender", new Varint21LengthFieldPrepender());
                            p.addLast("encoder", new PacketEncoder(PacketFlow.CLIENTBOUND));
                            p.addLast("packet_handler", connection);
                        }
                    });

            ChannelFuture f = b.bind(sslPort).syncUninterruptibly();
            channels.add(f);
            WSMC.info("WSMC SSL Server started on port " + sslPort);

        } catch (Exception e) {
            WSMC.info("Failed to start WSMC SSL Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
