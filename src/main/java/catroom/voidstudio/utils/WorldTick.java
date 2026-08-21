package catroom.voidstudio.utils;

import net.minecraft.crash.CrashReport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ReportedException;
import net.minecraft.world.WorldServer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WorldTick {
	private static final ExecutorService executor = Executors.newFixedThreadPool(6);

	public static CompletableFuture<Void> worldTask(WorldServer world) {
		return CompletableFuture.runAsync(() -> {
			try {
				world.tick();
			} catch (Throwable throwable1) {
				CrashReport crashreport = CrashReport.makeCrashReport(throwable1, "Exception ticking world");
				world.addWorldInfoToCrashReport(crashreport);
				throw new ReportedException(crashreport);
			}

			try {
				world.updateEntities();
			} catch (Throwable throwable) {
				CrashReport crashreport1 = CrashReport.makeCrashReport(throwable, "Exception ticking world entities");
				world.addWorldInfoToCrashReport(crashreport1);
				throw new ReportedException(crashreport1);
			}

			world.getEntityTracker().tick();
			world.explosionDensityCache.clear(); // Paper - Optimize explosions
		}, executor).exceptionally((err) -> { // TODO: make exception
			int id = world.dimension;
			if (id == 1 || id == 0 || id == -1) {
				MinecraftServer.getServerInst().processQueue.add(MinecraftServer.getServerInst()::stopServer);
				return null;
			}

			return null;
		});
	}
}
