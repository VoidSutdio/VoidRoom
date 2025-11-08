package tech.funkyra.catroom;

import net.minecraft.world.chunk.Chunk;

public interface AsyncChunkLoadCallback extends Runnable {
    void run(Chunk chunk);
}
