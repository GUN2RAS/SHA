package net.sha.api;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.sha.mixin.RenderSectionManagerAccessor;


public class SHARenderBridge {
    private static volatile RenderSectionManager manager;
    private static boolean needsGraphDirty = false;

    public static void capture(RenderSectionManager mgr) {
        manager = mgr;
    }

    public static void beginBatch() {
        needsGraphDirty = false;
    }

    public static void endBatch() {
        if (needsGraphDirty) {
            RenderSectionManager mgr = manager;
            if (mgr != null) {
                mgr.markGraphDirty();
            }
            needsGraphDirty = false;
        }
    }


    public static void forceRebuildSection(int x, int y, int z) {
        RenderSectionManager mgr = manager;
        if (mgr == null) return;

        RenderSectionManagerAccessor acc = (RenderSectionManagerAccessor) mgr;
        RenderSection section = acc.invokeGetRenderSection(x, y, z);
        if (section == null) return;

        if (!section.isBuilt()) {

            acc.getRenderableSectionTree().add(section);
        }

        section.setPendingUpdate(6, acc.getLastFrameAtTime());
        needsGraphDirty = true;
    }

    public static void preloadHologramChunks(int centerChunkX, int centerChunkZ, int radius, java.util.function.BiPredicate<Integer, Integer> filter) {
        RenderSectionManager mgr = manager;
        if (mgr == null) return;

        Runnable action = () -> {
            RenderSectionManager currentMgr = manager;
            if (currentMgr == null) return;
            
            currentMgr.beforeSectionUpdates();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = centerChunkX + dx;
                    int cz = centerChunkZ + dz;
                    if (filter == null || filter.test(cx, cz)) {
                        currentMgr.onChunkAdded(cx, cz);
                    }
                }
            }
            currentMgr.markGraphDirty();
        };

        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            action.run();
        } else {
            net.minecraft.client.Minecraft.getInstance().execute(action);
        }
    }
}
