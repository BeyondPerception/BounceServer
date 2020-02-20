package ml.dent.server;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class ConnectionManager {
	private ConcurrentHashMap<SocketAddress, Connection>	channels;

	private ConnectionPair[]								pairs;

	public ConnectionManager() {
		channels = new ConcurrentHashMap<>();
		pairs = new ConnectionPair[16];
	}

	public Connection put(SocketAddress address, Connection connection) {
		return channels.put(address, connection);
	}

	public Connection put(Connection connection) {
		return put(connection.remoteAddress(), connection);
	}

	public Connection get(SocketAddress address) {
		return channels.get(address);
	}

	public Connection remove(SocketAddress address) {
		return channels.remove(address);
	}

	public Connection remove(Connection connection) {
		return channels.remove(connection.remoteAddress());
	}

	public boolean addConnection(Connection add) {
		int n = add.getIdentity();
		if (pairs[n] == null) {
			pairs[n] = new ConnectionPair();
		}
		try {
			pairs[n].addConnection(add);
		} catch (IllegalArgumentException e) {
			add.close("Connection Pair Full");
			return false;
		}

		return true;
	}

	public void writeToAll(Connection incoming, Object msg) {
		int n = incoming.getIdentity();
		if (pairs[n] == null) {
			pairs[n] = new ConnectionPair();
		}

		if (!pairs[n].contains(incoming)) {
			addConnection(incoming);
		}

		if (NettyServer.VERBOSE_CHANNEL == incoming.getIdentity()) {
			ByteBuf buf = (ByteBuf) msg;
			byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			System.out.println(Arrays.toString(bytes));

			msg = Unpooled.copiedBuffer(bytes);
		}

		pairs[n].write(incoming, msg);
	}

	/**
	 * A class that links two connections in software
	 */
	private class ConnectionPair {
		HashSet<Connection> connections;

		public ConnectionPair() {
			connections = new HashSet<>();
		}

		public void addConnection(Connection n) {
			if (connections.size() >= 2) {
				throw new IllegalArgumentException("Connection Pair Full");
			}
			n.getChannel().closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
				@Override
				public void operationComplete(Future<? super Void> future) throws Exception {
					connections.remove(n);
				}
			});
			connections.add(n);
		}

		/**
		 * Writes the given message to the other connection
		 */
		public void write(Connection incoming, Object msg) {
			for (Connection connection : connections) {
				if (NettyServer.ECHO) {
					connection.writeAndFlush(ReferenceCountUtil.retain(msg));
				} else {
					if (!connection.equals(incoming)) {
						connection.writeAndFlush(ReferenceCountUtil.retain(msg));
					}
				}
			}
		}

		public boolean contains(Connection n) {
			return connections.contains(n);
		}
	}
}