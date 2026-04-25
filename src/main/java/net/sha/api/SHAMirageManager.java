package net.sha.api;

public class SHAMirageManager {
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

    public static void beginHandoff(int requiredChunks, double ox, double oy, double oz) {
        offsetX = ox;
        offsetY = oy;
        offsetZ = oz;
        if (fadingManager != null) {

            fadingManager = null;
        }
        
        initializedAlphaCamera = false;
        prepareFrameFailed = false;
        hasLobotomized = false;
        isTransitioning = true;
        minChunksRequired = requiredChunks;
    }

    public static void endTransition() {
        isTransitioning = false;
    }

    public static void cleanUp() {
        isTransitioning = false;
        prepareFrameFailed = false;
        if (fadingManager != null) {
            fadingManager.destroy();
            fadingManager = null;
        }
        hasLobotomized = false;
    }
}
