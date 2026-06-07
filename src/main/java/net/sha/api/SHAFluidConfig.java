package net.sha.api;

// Адекватно: оптимизация кэша через ThreadLocal, иначе GC захлебнется ссаными объектами
public class SHAFluidConfig {
    public static final ThreadLocal<Boolean> IS_FLUID_TICKING = ThreadLocal.withInitial(() -> false);
}
