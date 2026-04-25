package net.sha.api;

public class SHARaycastConfig {
    public static final ThreadLocal<Boolean> IS_RAYCASTING = ThreadLocal.withInitial(() -> false);
}
