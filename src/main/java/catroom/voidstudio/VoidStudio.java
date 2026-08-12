package catroom.voidstudio;

import catroom.voidstudio.utils.WorldTick;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.concurrent.CompletableFuture;

public final class VoidStudio {
	public static void worldTick(int tickCounter, Int2ObjectOpenHashMap<long[]> worldTickTimes) {
		int[] ids = DimensionManager.getIDs(tickCounter % 200 == 0);
		long nanoTime = System.nanoTime();
		int worldsCount = ids.length;
		WorldServer[] worldServers = new WorldServer[worldsCount];
		CompletableFuture<Void>[] worldTasks = new CompletableFuture[worldsCount];

		for (int i = 0; i < worldsCount; i++) {
			WorldServer worldserver = DimensionManager.getWorld(ids[i]);
			worldServers[i] = worldserver;
			FMLCommonHandler.instance().onPreWorldTick(worldserver);
			worldTasks[i] = WorldTick.worldTask(worldserver);
		}

		CompletableFuture.allOf(worldTasks).join();

		for (int i = 0; i < worldsCount; i++) {
			int id = ids[i];
			WorldServer worldServer = worldServers[i];
			FMLCommonHandler.instance().onPostWorldTick(worldServer);
			if (worldTickTimes.containsKey(id)) worldTickTimes.get(id)[tickCounter % 100] = System.nanoTime() - nanoTime; // CatServer - check world in tickTime list, prevent plugin unload world from causing NPE
		}

		DimensionManager.unloadWorlds();
	}
}
