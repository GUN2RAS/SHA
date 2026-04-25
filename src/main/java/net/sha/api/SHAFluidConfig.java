package net.sha.api;

public class SHAFluidConfig {
    public static final ThreadLocal<Boolean> IS_FLUID_TICKING = ThreadLocal.withInitial(() -> false);
}
