package catroom.asynctools;

import io.netty.util.concurrent.FastThreadLocalThread;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;

final class WorldTickWorker extends FastThreadLocalThread implements IServerTickMarker {
	WorldTickWorker(Runnable target, String name) {
		super(SidedThreadGroups.SERVER, target, name);
	}

	@Override
	public boolean permitBlockingCalls() {
		return true;
	}
}
