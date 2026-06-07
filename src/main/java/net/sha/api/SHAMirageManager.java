package net.sha.api;

// синхронизация миража с содиумом. этот класс пережил 14 полных переписываний. четырнадцать, карл. на пятнадцатом я сказал "заебись, шипим как есть" и пошел спать
public class SHAMirageManager {
    // Тут костыль для содиума, без него фризит как сука при переходах
    public static net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager fadingManager = null;
    
    public static double fadingCameraX = 0;
    public static double fadingCameraY = 0;
    public static double fadingCameraZ = 0;
    
    public static boolean initializedAlphaCamera = false;
    public static double alphaCameraStartX = 0;
    public static double alphaCameraStartY = 0;
    public static double alphaCameraStartZ = 0;

    public static boolean isTransitioning = false;
    public static boolean prepareFrameFailed = false;
    public static boolean hasLobotomized = false;
    public static int minChunksRequired = 250;
    public static double offsetX = 0;
    public static double offsetY = 0;
    public static double offsetZ = 0;
    public static boolean flipY = false;
    public static boolean flipZ = false;
    public static double flipPivotY = 0;
    public static double flipPivotZ = 0;

    public static Runnable onSetupTerrainHead = null;
    public static java.util.function.Supplier<Boolean> isHologramReady = () -> true;

    public static void beginHandoff(int requiredChunks, double ox, double oy, double oz) {
        offsetX = ox;
        offsetY = oy;
        offsetZ = oz;
        if (fadingManager != null) {
            // бля ну тут раньше был дебаг, просто чистим ссылку
            fadingManager = null;
        }
        
        initializedAlphaCamera = false;
        prepareFrameFailed = false;
        hasLobotomized = false;
        isTransitioning = true;
        minChunksRequired = requiredChunks;
        flipY = false;
        flipZ = false;
        flipPivotY = 0;
        flipPivotZ = 0;
    }

    public static void endTransition() {
        isTransitioning = false;
    }

    public static void cleanUp() {
        isTransitioning = false;
        prepareFrameFailed = false;
        flipY = false;
        flipZ = false;
        flipPivotY = 0;
        flipPivotZ = 0;
        if (fadingManager != null) {
            final net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager managerToDestroy = fadingManager;
            fadingManager = null;
            java.util.concurrent.CompletableFuture.runAsync(() -> {}, 
                java.util.concurrent.CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS))
            .thenRunAsync(() -> {
                net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.enterManagedCode();
                try {
                    managerToDestroy.destroy();
                } finally {
                    net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.exitManagedCode();
                }
            }, net.minecraft.client.Minecraft.getInstance());
        }
        hasLobotomized = false;
    }
}
