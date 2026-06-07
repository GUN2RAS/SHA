package net.sha.api;

// Тоже ThreadLocal, потому что майнкрафт любит спамить рейкастами во все стороны
public class SHARaycastConfig {
    public static final ThreadLocal<Boolean> IS_RAYCASTING = ThreadLocal.withInitial(() -> false);
}
