package net.sha.api.sandbox;

import net.sha.api.entity.VirtualStructureEntity;

// Песочница для виртуальных структур, чтобы энтити не съебались за пределы
public class VirtualSandbox {
    private static final ThreadLocal<VirtualStructureEntity> CURRENT = new ThreadLocal<>();

    public static void enter(VirtualStructureEntity entity) {
        CURRENT.set(entity);
    }

    public static void exit() {
        CURRENT.remove();
    }

    public static VirtualStructureEntity current() {
        return CURRENT.get();
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }
}
