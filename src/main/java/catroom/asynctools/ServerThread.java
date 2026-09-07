package catroom.asynctools;

import io.netty.util.concurrent.FastThreadLocalThread;

public final class ServerThread extends FastThreadLocalThread implements IServerTickMarker {
	public ServerThread(ThreadGroup group, Runnable task, String name) {
		super(group, task, name);
	}
}
