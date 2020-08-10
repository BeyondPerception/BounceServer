package ml.dent.connect;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.CharsetUtil;
import ml.dent.app.Logger;
import ml.dent.app.Main;

import java.net.SocketAddress;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Connection {
    private static Logger logger = Logger.getInstance();

    private Channel channel;

    public enum State {
        CONNECTED,
        AUTHENTICATED,
        READY,
        WAITING
    }

    /**
     * Defines the state of this connection. If the state is AUTHENTICATED, it is
     * implicitly CONNECTED, if it NEGOTIATING, it has authenticated,
     * and if it is ready,it has successfully negotiated
     */
    private State state;

    private int channelNumber;
    private int id;

    public Connection(Channel channel) {
        this.channel = channel;
        this.channelNumber = -1;
        this.id = -1;
        state = State.CONNECTED;
        ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.schedule(() -> {
            if (state == State.CONNECTED) {
                close("Did not receive authentication");
            }
        }, 5, TimeUnit.SECONDS);
    }

    public void write(String msg) {
        write(Unpooled.copiedBuffer(msg, CharsetUtil.UTF_8));
    }

    public void write(Object msg) {
        channel.writeAndFlush(msg, channel.voidPromise());
    }

    public Channel getChannel() {
        return channel;
    }

    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    public State getState() {
        return state;
    }

    public void setState(State newState) {
        if (Main.getVerbosity() >= 2) {
            logger.logln(remoteAddress() + " changed state to " + newState);
        }
        state = newState;
    }

    public void setChannelNumber(int channel) {
        channelNumber = channel;
    }

    public int getChannelNumber() {
        return channelNumber;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /**
     * @return A ChannelFuture that is notified when this channel is closed
     */
    public ChannelFuture closeFuture() {
        return channel.closeFuture();
    }

    private String closeReason = "Closed by remote host";

    public void close(String closeReason) {
        this.closeReason = closeReason;
        close();
    }

    public void close() {
        channel.close();
    }

    public String getCloseReason() {
        return closeReason;
    }

    @Override
    public int hashCode() {
        return remoteAddress().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Connection) {
            Connection o = (Connection) obj;
            return remoteAddress().equals(o.remoteAddress());
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        String info = String.format("[id: %4d, channel: %4d]", getId(), getChannelNumber());
        return remoteAddress().toString() + " - " + info + ": (" + state + ")";
    }
}