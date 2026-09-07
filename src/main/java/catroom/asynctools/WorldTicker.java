package catroom.asynctools;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.crash.CrashReport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public final class WorldTicker implements AutoCloseable {
	private final Thread mainThread;
	private final ObjectArrayList<WorldServer> worlds;
	private final Int2ObjectMap<long[]> tickTimes;
	private final WorldTickWorker[] workers;
	private final AtomicInteger cursor = new AtomicInteger();
	private final AtomicInteger remainingWorkers = new AtomicInteger();

	private int tickCounter;
	private long nextEpoch;
	private volatile long epoch;
	private volatile boolean closed;

	@SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
	public WorldTicker(Thread mainThread, ObjectArrayList<WorldServer> worldList, Int2ObjectMap<long[]> worldTickTimes) { // TODO: config
		this.worlds = worldList;
		this.mainThread = mainThread;
		this.tickTimes = worldTickTimes;
		this.workers = new WorldTickWorker[2];

		for (int i = 0; i < 2; i++) {
			this.workers[i] = new WorldTickWorker(this::workerLoop, "world-ticker-" + i);
		}

		for (WorldTickWorker worker : this.workers) {
			worker.start();
		}
	}

	public static void preTick(WorldServer world) {
		FMLCommonHandler.instance().onPreWorldTick(world);
	}

	public void tick(int tickCounter) {
		this.cursor.setPlain(0);
		this.remainingWorkers.setPlain(this.workers.length);
		this.tickCounter = tickCounter;
		this.epoch = ++this.nextEpoch;

		for (WorldTickWorker worker : this.workers) {
			LockSupport.unpark(worker);
		}

		this.drain();
		this.awaitWorkers();
	}

	public static void postTick(WorldServer world) {
		FMLCommonHandler.instance().onPostWorldTick(world);
	}

	private void drain() {
		for (;;) {
			final int index = this.cursor.getAndIncrement();

			if (index >= this.worlds.size()) {
				return;
			}

			final WorldServer world = this.worlds.get(index);
			final long nanoTime = System.nanoTime();
			final int dimID = world.dimension;

			try {
				world.tick();
			} catch (Throwable throwable1) {
				CrashReport crashreport = CrashReport.makeCrashReport(throwable1, "Exception ticking world");
				world.addWorldInfoToCrashReport(crashreport);
				this.processError(world, crashreport);
				return;
			}

			try {
				world.updateEntities();
			} catch (Throwable throwable) {
				CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Exception ticking world entities");
				world.addWorldInfoToCrashReport(crashreport);
				this.processError(world, crashreport);
				return;
			}

			world.getEntityTracker().tick();
			world.explosionDensityCache.clear(); // Paper - Optimize explosions

			if (this.tickTimes.containsKey(dimID))
				this.tickTimes.get(dimID)[this.tickCounter % 100] = System.nanoTime() - nanoTime; // CatServer - check world in tickTime list, prevent plugin unload world from causing NPE
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void workerLoop() {
		long processedEpoch = 0L;

		for (;;) {
			final long currentEpoch = this.epoch;

			if (currentEpoch == processedEpoch) {
				LockSupport.park(this);
				Thread.interrupted();

				if (this.closed) {
					return;
				}

				continue;
			}

			if (this.closed) {
				return;
			}

			processedEpoch = currentEpoch;

			try {
				this.drain();
			} catch (Throwable unexpected) {
				this.processError(unexpected);
			} finally {
				if (this.remainingWorkers.decrementAndGet() == 0) {
					LockSupport.unpark(this.mainThread);
				}
			}
		}
	}

	private void processError(WorldServer world, CrashReport report) {
		int id = world.dimension;
		if (id == 1 || id == 0 || id == -1) {
			MinecraftServer.getServerInst().processQueue.add(MinecraftServer.getServerInst()::stopServer);
		}
	}

	@SuppressWarnings("CallToPrintStackTrace")
	private void processError(Throwable unexpected) { // TODO: should be restart policy here
		unexpected.printStackTrace();
		MinecraftServer.getServerInst().processQueue.add(MinecraftServer.getServerInst()::stopServer);
	}

	private void awaitWorkers() {
		boolean interrupted = false;

		while (this.remainingWorkers.getAcquire() != 0) {
			LockSupport.park(this);

			if (Thread.interrupted()) {
				interrupted = true;
			}
		}

		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void close() {
		this.closed = true;

		for (WorldTickWorker worker : this.workers) {
			LockSupport.unpark(worker);
		}

		for (WorldTickWorker worker : this.workers) {
			for (;;) {
				try {
					worker.join();
					break;
				} catch (InterruptedException ignored) {}
			}
		}
	}
}
