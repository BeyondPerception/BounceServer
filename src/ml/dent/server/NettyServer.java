package ml.dent.server;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import ml.dent.server.Connection.State;

/** @author Ronak Malik */
public class NettyServer {

	private static int		PORT;
	public static boolean	ECHO;
	public static boolean	VERBOSE;
	public static int		VERBOSE_CHANNEL;

	public static void main(String... args) throws Exception {
		if (args.length != 0) {
			Queue<String> argQ = new LinkedList<String>();
			for (String val : args) {
				argQ.offer(val);
			}

			while (!argQ.isEmpty()) {
				String cur = argQ.poll();

				switch (cur) {
					case "-p":
						try {
							int x = Integer.parseInt(argQ.poll());
							if (x <= 0 || x > 65535) {
								System.out.println("-p option requires port to be between 1 and 65535");
								System.exit(1);
							}
							PORT = x;
						} catch (NumberFormatException e) {
							System.out.println("-p option requires a number passed to it");
							System.exit(1);
						}
						break;
					case "-e":
						ECHO = true;
						break;
					case "-v":
						VERBOSE = true;

						try {
							int x = Integer.parseInt(argQ.peek(), 16);
							VERBOSE_CHANNEL = x;
							argQ.poll();
						} catch (NumberFormatException e) {
							VERBOSE_CHANNEL = -1;
						}
						break;
					default:
						System.out.println(
								"Available Flags:\n\n-p: Speceify port number\nUsage: -p [1-65535]\n\n-e: Echo all data back to sender\n\n-v: increase verbosity\n\n -v [NUMBER]: Print all data written on a channel");
						System.exit(0);
				}
			}

			if (PORT == 0) {
				PORT = 1111;
			}
		} else {
			PORT = 1111;
			ECHO = false;
			VERBOSE = false;
			VERBOSE_CHANNEL = -1;
		}

		new NettyServer().run();
	}

	public void run() throws Exception {
		EventLoopGroup masterGroup = new NioEventLoopGroup();
		EventLoopGroup slaveGroup = new NioEventLoopGroup();

		try {
			ServerBootstrap boot = new ServerBootstrap();
			boot.group(masterGroup, slaveGroup).channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>() {

						@Override
						protected void initChannel(SocketChannel ch) throws Exception {
							ch.pipeline().addLast(new NettyServerHandler());
						}

					}).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

			System.out.println("Starting bounce server on port [" + PORT + "]...");
			ChannelFuture future = boot.bind(PORT).sync();
			System.out.println("Server started. Waiting for connections");

			future.channel().closeFuture().sync();
		} finally {
			slaveGroup.shutdownGracefully();
			masterGroup.shutdownGracefully();
		}
	}
}

class NettyServerHandler extends ChannelInboundHandlerAdapter {

//	private static final String AUTHKEY = "Hx9YY7ucKfs7OVqdRSI2xPWO3FNDEtYGw6QgsqzKvpUGBggEnYM3EdoOel3N582arTAvIsGO1XwmzHCVeYMpATQeaV3WVroxpeAkbvRUoIpreFoJnMFqsbvyln1QQcQuhQbEI8Frx8BIbaLaiRFQgqrznhR3o9MR83fRtQNQ9P6B7uhNgDSg8W4SS7eflY49rC6kI1kVTy1P40twlhffqQpI4Ny6dNBWyzBtZx7XcdFg2m2zJk2AeP31koB1L7vIeLegXcbs5Wf7htq08Z5pbKbhRXdysTcOymaWIbalpdxe9HxI82r7gU04nxlycq21fxYod8wkycCmINSI2d1uqup502AR6kGYdbizXNfVFlDGVtTBESomisilu7dfYQbqPUKcVOc2Ne132yEo3gPhBb0jbQ6H9ieFE8M3xFzytXVNgmTHq4mLVnfg4qxRi1Zyg7CY92Cf7z2bcGdzA41PHkCYw6rtpPtzpkZby30jR6DFNIoMOL2mvk3OpbKCdz52VBe1DUyQPniGMbaJdALf6AeNFsIVBdDKdylgyxHBXqRQUvTKkYIAyb0DjVGL6fIC2fvPUOa0eDVuH9e4Ey3ixHBHymm4c1s5ABr44In6IGjLrxvSXzxwtuERJNiazJdZ9MBU3oQmCgNcTPLNrQwC7vSVv433OZNopvvBgDYjjLWCFlnsH7svmTUWhqeK0HHxrMyAZnqWrBLrUi4MgNSH9RKFZojBOtHE6L7i5rzuKrAWZ8SmaGnmKDeLYwbFQ0n6VdVmgGX3Y409KjznRKbNBMQk77C3Kq7IDgEHvLXbkbO6nLbyI3l2Ve4hhQQSGScCuFHWldV7l9NeNDuhOvkJInLCn4mkLpz4CbxHBWnvba0wv2ALklRJxjLGgTZoMigq4Xmtp4PvIUpiVAgrjMbuqklwHk3t5DIvhL8LIQLmYDtelslEwwmltlri52BbMWkbAyCWGVeUHM91sjPgg2SIiafJKRHAOBhAngImXTuDuQ84kF57g2OUhC3WpK2Hszzu";
	private static final String				AUTHKEY		= "hi";

	private static final ConnectionManager	connections	= new ConnectionManager();

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		Connection newConnection = new Connection(ctx.channel());

		System.out.println(newConnection.remoteAddress() + " connected");
		connections.put(newConnection.remoteAddress(), newConnection);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		Connection c = connections.get(ctx.channel().remoteAddress());

		System.out.println(c.remoteAddress() + " disconnected: " + c.getCloseReason());
		connections.remove(c.remoteAddress());
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
//		System.out.println(((ByteBuf) msg).toString(CharsetUtil.UTF_8));
		if (NettyServer.VERBOSE && NettyServer.VERBOSE_CHANNEL == -1) {
			ByteBuf buf = (ByteBuf) msg;
			byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			System.out.println(Arrays.toString(bytes));

			msg = Unpooled.copiedBuffer(bytes);
		}

		try {
			Channel incomingChannel = ctx.channel();
			Connection incoming = connections.get(incomingChannel.remoteAddress());
			String message = null;
			int len = 0;
			if (!incoming.isAuthenticated()) {
				// The first message that is received by the incoming connection must be
				// authentication. This message must be sent within 5 seconds of connecting.
				String tmp = ((ByteBuf) msg).toString(CharsetUtil.UTF_8).trim();
				len = tmp.length();
				String auth = tmp.substring(0, AUTHKEY.length());
				if (auth.equals(AUTHKEY)) {
					incoming.setState(State.AUTHENTICATED);
				} else {
					incoming.close("Authentication Failure");
					return;
				}
				message = tmp.substring(AUTHKEY.length());
			}
			if (!incoming.isIdentified()) {
				// The next character after authentication is then used to set the connection
				// identity.
				String tmp;
				if (message == null) {
					tmp = ((ByteBuf) msg).toString(CharsetUtil.UTF_8).trim();
					len = tmp.length();
				} else {
					tmp = message;
				}
				if (tmp.length() <= 0) {
					return;
				}
				try {
					incoming.setIdentity(Integer.parseInt(tmp.substring(0, 1), 16));
				} catch (NumberFormatException e) {
					e.printStackTrace();
					System.out.println(incoming.remoteAddress() + ": Failed to set identity");
					return;
				}
				incoming.setState(State.IDENTIFIED);
				connections.addConnection(incoming);
				message = tmp.substring(1);
			}
			if (message == null) {
				connections.writeToAll(incoming, msg);
			} else if (message.length() > 0) {
				ByteBuf toWrite = Unpooled.buffer();
				((ByteBuf) msg).getBytes(len - message.length(), toWrite);
				connections.writeToAll(incoming, toWrite);
			}
		} finally {
			// TODO Fix ReferenceCount issue
			ReferenceCountUtil.release(msg);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		connections.get(ctx.channel().remoteAddress()).close(cause.getLocalizedMessage());
	}
}
