package ml.dent.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import ml.dent.app.Main;
import ml.dent.connect.Connection;
import ml.dent.connect.ConnectionManager;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;

public class MainServer {

    private int         bindPort;
    private int         backlog;
    private InetAddress bindAddress;

    private ConnectionManager connectionManager;

    public MainServer(int bindPort) {
        this(bindPort, 50);
    }

    /**
     * @param bindPort The local port to bind to
     * @param backlog  The number of connections allows in the queue before they are accepted
     */
    public MainServer(int bindPort, int backlog) {
        this(bindPort, backlog, null);
    }

    public MainServer(int bindPort, int backlog, InetAddress bindAddress) {
        this.bindPort = bindPort;
        this.backlog = backlog;
        this.bindAddress = bindAddress;
        connectionManager = new ConnectionManager();
    }

    private EventLoopGroup parentGroup;
    private EventLoopGroup childGroup;

    /**
     * @return A {@link ChannelFuture} that will be notified when the server is
     * closed.
     * @throws InterruptedException If the server is interrupted while trying to
     *                              bind.
     */
    public ChannelFuture listen() throws InterruptedException {
        parentGroup = new EpollEventLoopGroup();
        childGroup = new EpollEventLoopGroup();

        ServerBootstrap boot = new ServerBootstrap();
        boot.group(parentGroup, childGroup).channel(EpollServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ServerHandler());
                    }
                }).option(ChannelOption.SO_BACKLOG, backlog).childOption(ChannelOption.SO_KEEPALIVE, true);

        ChannelFuture future = boot.bind(bindAddress, bindPort).sync();

        return future.channel().closeFuture();
    }

    public Collection<Connection> getConnections() {
        return connectionManager.getAllConnections();
    }

    public void close() {
        parentGroup.shutdownGracefully();
        childGroup.shutdownGracefully();
    }

    /**
     * @param id The id of the connection to kill
     * @return true if the connection was successfully killed, false if not
     */
    public boolean kill(int id) {
        return connectionManager.kill(id);
    }

    class ServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Connection connection = new Connection(ctx.channel());
            System.out.println(ctx.channel().remoteAddress() + " connected");
            connection.write(Integer.toHexString(ConnectionManager.MAX_PAIRS).length() + "-" + "BounceServer_" + Main.VERSION + "\n");
            connectionManager.register(connection);
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            Connection connection = connectionManager.get(ctx.channel().remoteAddress());
            System.out.println(connection.remoteAddress() + " disconnected: " + connection.getCloseReason());
            connectionManager.remove(connection);
            super.channelInactive(ctx);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            if (ctx.channel().isWritable()) {
                ctx.read();
            }
            super.channelWritabilityChanged(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (Main.getVerbosity() >= 3 && Main.getVerboseChannel() == -1) {
                Connection connection = connectionManager.get(ctx.channel().remoteAddress());
                if (connection != null) {
                    System.out.println(connection + ": " + msg);
                } else {
                    System.out.println(ctx.channel().remoteAddress() + ": " + msg);
                }
                if (Main.getVerbosity() >= 4) {
                    ByteBuf buf = (ByteBuf) msg;
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.getBytes(buf.readerIndex(), bytes);
                    System.out.println(Arrays.toString(bytes));
                }
            }
            connectionManager.handleConnectionRead(ctx.channel().remoteAddress(), msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            Connection connection = connectionManager.get(ctx.channel().remoteAddress());
            if (connection != null) {
                if (Main.getVerbosity() >= 2) {
                    cause.printStackTrace();
                }
                connectionManager.get(ctx.channel().remoteAddress()).close(cause.getMessage());
            } else {
                cause.printStackTrace();
                ctx.close();
            }
        }
    }
}
