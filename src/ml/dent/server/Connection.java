package ml.dent.server;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;

public class Connection {
	private Channel channel;
	private String closeReason;

	private final ScheduledExecutorService scheduler;

	private int channelIdentity;

	public enum State {
		CONNECTED, AUTHENTICATED, IDENTIFIED
	}

	/**
	 * Defines the state of this connection. If the state is AUTHENTICATED, it is
	 * implicitly CONNECTED, and if it IDENTIFIED, it is implicitly AUTHENTICATED
	 */
	private State state;

	public Connection(Channel channel) {
		this.channel = channel;
		state = State.CONNECTED;
		closeReason = "Closed by remote host";
		channelIdentity = 0;
		scheduler = Executors.newScheduledThreadPool(1);
		scheduler.schedule(new Runnable() {
			@Override
			public void run() {
				if (!isAuthenticated()) {
					close("Did Not Recieve Authentication");
				}
			}
		}, 5, TimeUnit.SECONDS);
	}

	public void writeAndFlush(Object o) {
		channel.writeAndFlush(o);
	}

	public Channel getChannel() {
		return channel;
	}

	public SocketAddress remoteAddress() {
		return channel.remoteAddress();
	}

	public boolean isAuthenticated() {
		return state != State.CONNECTED;
	}

	public boolean isIdentified() {
		return state == State.IDENTIFIED;
	}

	public State getState() {
		return state;
	}

	public void setState(State newState) {
		state = newState;
	}

	public int getIdentity() {
		return channelIdentity;
	}

	public void setIdentity(int newIdentity) {
		channelIdentity = newIdentity;
	}

	public void close() {
		close("No Reason");
	}

	public void close(String reason) {
		channel.close();
		closeReason = reason;
		scheduler.shutdownNow();
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
		return remoteAddress().toString() + "(" + getIdentity() + ") - Auth: " + isAuthenticated();
	}
}