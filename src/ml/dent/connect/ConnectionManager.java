package ml.dent.connect;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import ml.dent.app.Main;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static ml.dent.connect.Connection.State;

public class ConnectionManager {
    private ConcurrentHashMap<SocketAddress, Connection> channels;
    private ConcurrentSkipListMap<Integer, Connection>   ids;

    private ConnectionGroup[] pairs;

    public static final int MAX_PAIRS = 65535;

    public ConnectionManager() {
        channels = new ConcurrentHashMap<>();
        ids = new ConcurrentSkipListMap<>(Collections.reverseOrder());
        pairs = new ConnectionGroup[MAX_PAIRS];
    }

    public Connection register(SocketAddress address, Connection connection) {
        int curId;
        synchronized (this) {
            if (connection.getId() == -1) {
                if (ids.isEmpty()) {
                    curId = 0;
                } else {
                    curId = ids.firstKey() + 1;
                }
            } else {
                curId = connection.getId();
            }
            ids.put(curId, connection);
        }
        connection.setId(curId);
        return channels.put(address, connection);
    }

    public Connection register(Connection connection) {
        return register(connection.remoteAddress(), connection);
    }

    public Connection get(SocketAddress address) {
        return channels.get(address);
    }

    public Connection remove(SocketAddress address) {
        ids.remove(channels.get(address).getId());
        return channels.remove(address);
    }

    public Connection remove(Connection connection) {
        return remove(connection.remoteAddress());
    }

    public Collection<Connection> getAllConnections() {
        return Collections.unmodifiableCollection(channels.values());
    }

    private HashMap<Connection, Integer> authIndex = new HashMap<>();

    public void handleConnectionRead(SocketAddress address, Object msg) {
        Connection connection = channels.get(address);
        switch (connection.getState()) {
            case CONNECTED:
                handleConnected(connection, msg);
                break;
            case AUTHENTICATED:
                handleAuthenticated(connection, msg);
                break;
            case READY:
                handleReady(connection, msg);
                break;
        }
    }

    private void handleConnected(Connection connection, Object msg) {
        if (connection.getState() != State.CONNECTED) {
            return;
        }
        try {
            ByteBuf buf = (ByteBuf) msg;
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            String authString = Main.getAuthString();

            int i = 0;
            int j = authIndex.getOrDefault(connection, 0);
            while (i < bytes.length && j < authString.length()) {
                if ((char) bytes[i] == authString.charAt(j)) {
                    i++;
                    j++;
                } else {
                    break;
                }
            }
            if (i == bytes.length && j == authString.length()) {
                // end of packet, end of auth string
                connection.setState(State.AUTHENTICATED);
            } else if (i != bytes.length && j == authString.length()) { // tested & correct
                // end of auth string, but still more to packet
                connection.setState(State.AUTHENTICATED);
                ByteBuf nextBuf = Unpooled.buffer(bytes.length - i);
                nextBuf.writeBytes(bytes, i, bytes.length - i);

                authIndex.remove(connection);
                handleConnectionRead(connection.remoteAddress(), nextBuf);
            } else if (i == bytes.length) {
                // end of packet, more to auth string
                authIndex.put(connection, j);
            } else {
                // more to packet, more to auth string
                connection.close("Authentication failure");
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void handleAuthenticated(Connection connection, Object msg) {
        if (connection.getState() != State.AUTHENTICATED) {
            return;
        }
        try {
            StringBuilder channel = new StringBuilder();
            ByteBuf buf = (ByteBuf) msg;
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);

            int maxHexLen = Integer.toHexString(MAX_PAIRS).length();

            int i = 0;
            while (i < maxHexLen && i < bytes.length && Character.toString((char) bytes[i]).matches("[0-9A-Fa-f]")) {
                channel.append((char) bytes[i]);
                i++;
            }
            int channelNum = Integer.parseInt(channel.toString(), 16);
            if (channelNum < MAX_PAIRS) {
                connection.setChannelNumber(channelNum);
                if (pairs[channelNum] == null) {
                    pairs[channelNum] = new ConnectionGroup();
                }
                try {
                    pairs[channelNum].addConnection(connection);
                } catch (IllegalArgumentException e) {
                    connection.close(e.getMessage());
                    return;
                }
                connection.setState(State.READY);
                if (i != bytes.length) {
                    ByteBuf nextBuf = Unpooled.buffer(bytes.length - i);
                    nextBuf.writeBytes(bytes, i, bytes.length - i);
                    handleConnectionRead(connection.remoteAddress(), nextBuf);
                }
            } else {
                connection.close("Failed to negotiate channel number");
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void handleReady(Connection connection, Object msg) {
        if (connection.getState() != State.READY) {
            return;
        }
        pairs[connection.getChannelNumber()].write(connection, msg);
    }

    public boolean kill(int id) {
        Connection connection = ids.get(id);
        if (connection != null) {
            connection.close("Disconnected by server");
            return true;
        }
        return false;
    }

    /**
     * A class that links two connections in software
     */
    private static class ConnectionGroup {
        private Set<Connection> connections;
        private int             maxConnections;

        public ConnectionGroup() {
            this(2);
        }

        public ConnectionGroup(int maxConnections) {
            connections = Collections.newSetFromMap(new ConcurrentHashMap<>());
            this.maxConnections = maxConnections;
        }

        public void addConnection(Connection n) throws IllegalArgumentException {
            synchronized (this) {
                if (connections.size() >= maxConnections) {
                    throw new IllegalArgumentException("Connection Group Full");
                }
                n.closeFuture().addListener(future -> connections.remove(n));
                connections.add(n);
            }
        }

        /**
         * Writes the given message to the other connections
         */
        public void write(Connection incoming, Object msg) {
            try {
                if (Main.getVerboseChannel() == incoming.getChannelNumber() && Main.getVerbosity() >= 3) {
                    System.out.println(incoming + ": " + msg);
                    if (Main.getVerbosity() >= 4) {
                        ByteBuf buf = (ByteBuf) msg;
                        byte[] bytes = new byte[buf.readableBytes()];
                        buf.getBytes(buf.readerIndex(), bytes);
                        System.out.println(Arrays.toString(bytes));
                    }
                }
                for (Connection connection : connections) {
                    if (connection != incoming || Main.getEcho()) {
                        connection.write(ReferenceCountUtil.retain(msg));
                    }
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }
}