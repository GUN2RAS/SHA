package net.sha.api.physics;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.NoSuchElementException;

public class RapierEngine {
    private static final MethodHandle CREATE_WORLD;
    private static final MethodHandle DESTROY_WORLD;
    private static final MethodHandle STEP;
    private static final MethodHandle ADD_BOX;
    private static final MethodHandle ADD_STATIC_BOX;
    private static final MethodHandle ADD_PLANE;
    private static final MethodHandle ADD_STRUCTURE;
    private static final MethodHandle ADD_BLOCK;
    private static final MethodHandle GET_POSITION;
    private static final MethodHandle GET_ROTATION;
    private static final MethodHandle MOVE_CHARACTER;
    private static final MethodHandle REMOVE_STRUCTURE;
    private static final MethodHandle REMOVE_COLLIDER;
    private static final MethodHandle SET_ROTATION;
    private static final MethodHandle SET_LINEAR_VELOCITY;

    static {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            String osFolder = osName.contains("win") ? "windows" : (osName.contains("mac") ? "macos" : "linux");
            String libExtension = osName.contains("win") ? "dll" : (osName.contains("mac") ? "dylib" : "so");
            String libName = "sha_rapier." + libExtension;
            
            java.io.File tempLib = java.io.File.createTempFile("sha_rapier", "." + libExtension);
            tempLib.deleteOnExit();
            
            try (java.io.InputStream is = RapierEngine.class.getResourceAsStream("/assets/sha/bin/" + osFolder + "/" + libName)) {
                if (is == null) {
                    throw new RuntimeException("Could not find " + libName + " in JAR!");
                }
                java.nio.file.Files.copy(is, tempLib.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            System.load(tempLib.getAbsolutePath());
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to extract Rapier native library!", e);
        }

        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = SymbolLookup.loaderLookup();

        try {
            CREATE_WORLD = linker.downcallHandle(lookup.find("rapier_create_world").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT));
            DESTROY_WORLD = linker.downcallHandle(lookup.find("rapier_destroy_world").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
            STEP = linker.downcallHandle(lookup.find("rapier_step").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
            
            ADD_BOX = linker.downcallHandle(
                lookup.find("rapier_add_dynamic_box").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
            );

            ADD_STATIC_BOX = linker.downcallHandle(
                lookup.find("rapier_add_static_box").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
            );

            ADD_PLANE = linker.downcallHandle(
                lookup.find("rapier_add_static_plane").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT)
            );

            ADD_STRUCTURE = linker.downcallHandle(
                lookup.find("rapier_add_dynamic_structure").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            );

            ADD_BLOCK = linker.downcallHandle(
                lookup.find("rapier_add_block_to_structure").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
            );

            REMOVE_STRUCTURE = linker.downcallHandle(
                lookup.find("rapier_remove_dynamic_structure").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
            );

            REMOVE_COLLIDER = linker.downcallHandle(
                lookup.find("rapier_remove_collider").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
            );
            
            GET_POSITION = linker.downcallHandle(
                lookup.find("rapier_get_position").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            );
            
            GET_ROTATION = linker.downcallHandle(
                lookup.find("rapier_get_rotation").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            );
            
            MOVE_CHARACTER = linker.downcallHandle(
                lookup.find("rapier_move_character").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_INT, 
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.ADDRESS
                )
            );
            
            SET_ROTATION = linker.downcallHandle(
                lookup.find("rapier_set_rotation").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
            );

            SET_LINEAR_VELOCITY = linker.downcallHandle(
                lookup.find("rapier_set_linear_velocity").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
            );
        } catch (NoSuchElementException e) {
            throw new RuntimeException("Failed to find Rapier ABI symbols!", e);
        }
    }

    public static int createWorld() {
        try { return (int) CREATE_WORLD.invokeExact(); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void destroyWorld(int worldId) {
        try { DESTROY_WORLD.invokeExact(worldId); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void step(int worldId) {
        try { STEP.invokeExact(worldId); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static int addDynamicBox(int worldId, float x, float y, float z, float hx, float hy, float hz) {
        try { return (int) ADD_BOX.invokeExact(worldId, x, y, z, hx, hy, hz); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static long addStaticBox(int worldId, float x, float y, float z, float hx, float hy, float hz) {
        try { return (long) ADD_STATIC_BOX.invokeExact(worldId, x, y, z, hx, hy, hz); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static int addStaticPlane(int worldId, float y) {
        try { return (int) ADD_PLANE.invokeExact(worldId, y); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static int addDynamicStructure(int worldId, float x, float y, float z, MemorySegment blocksPtr, int blockCount, MemorySegment outHandlesPtr) {
        try { return (int) ADD_STRUCTURE.invokeExact(worldId, x, y, z, blocksPtr, blockCount, outHandlesPtr); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static long addBlockToStructure(int worldId, int bodyId, int bx, int by, int bz) {
        try { return (long) ADD_BLOCK.invokeExact(worldId, bodyId, bx, by, bz); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void removeDynamicStructure(int worldId, int bodyId) {
        try { REMOVE_STRUCTURE.invokeExact(worldId, bodyId); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void removeCollider(int worldId, long colliderId) {
        try { REMOVE_COLLIDER.invokeExact(worldId, colliderId); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static float[] getPosition(int worldId, int bodyId) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment posPtr = arena.allocate(ValueLayout.JAVA_FLOAT, 3);
            GET_POSITION.invokeExact(worldId, bodyId, posPtr);
            return new float[] {
                posPtr.getAtIndex(ValueLayout.JAVA_FLOAT, 0),
                posPtr.getAtIndex(ValueLayout.JAVA_FLOAT, 1),
                posPtr.getAtIndex(ValueLayout.JAVA_FLOAT, 2)
            };
        } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static float[] getRotation(int worldId, int bodyId) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment quatPtr = arena.allocate(ValueLayout.JAVA_FLOAT, 4);
            GET_ROTATION.invokeExact(worldId, bodyId, quatPtr);
            return new float[] {
                quatPtr.getAtIndex(ValueLayout.JAVA_FLOAT, 0),
                quatPtr.getAtIndex(ValueLayout.JAVA_FLOAT, 1),
                quatPtr.getAtIndex(ValueLayout.JAVA_FLOAT, 2),
                quatPtr.getAtIndex(ValueLayout.JAVA_FLOAT, 3)
            };
        } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static float[] moveCharacter(int worldId, float px, float py, float pz, float hw, float hh, float mx, float my, float mz, float stepHeight) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outMov = arena.allocate(ValueLayout.JAVA_FLOAT, 3);
            MOVE_CHARACTER.invokeExact(worldId, px, py, pz, hw, hh, mx, my, mz, stepHeight, outMov);
            return new float[] {
                outMov.getAtIndex(ValueLayout.JAVA_FLOAT, 0),
                outMov.getAtIndex(ValueLayout.JAVA_FLOAT, 1),
                outMov.getAtIndex(ValueLayout.JAVA_FLOAT, 2)
            };
        } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void setRotation(int worldId, int bodyId, float qx, float qy, float qz, float qw) {
        try { SET_ROTATION.invokeExact(worldId, bodyId, qx, qy, qz, qw); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void setLinearVelocity(int worldId, int bodyId, float vx, float vy, float vz) {
        try { SET_LINEAR_VELOCITY.invokeExact(worldId, bodyId, vx, vy, vz); } catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void main(String[] args) {
        int worldId = createWorld();
        System.out.println("Created world ID: " + worldId);
        
        System.out.println("Allocating flat zero-copy C memory array for blocks in Java...");
        try (Arena arena = Arena.ofConfined()) {
            
            MemorySegment blocksPtr = arena.allocate(ValueLayout.JAVA_INT, 6);
            blocksPtr.setAtIndex(ValueLayout.JAVA_INT, 0, 0); 
            blocksPtr.setAtIndex(ValueLayout.JAVA_INT, 1, 0); 
            blocksPtr.setAtIndex(ValueLayout.JAVA_INT, 2, 0); 

            blocksPtr.setAtIndex(ValueLayout.JAVA_INT, 3, 0); 
            blocksPtr.setAtIndex(ValueLayout.JAVA_INT, 4, 1); 
            blocksPtr.setAtIndex(ValueLayout.JAVA_INT, 5, 0); 

            MemorySegment outHandlesPtr = arena.allocate(ValueLayout.JAVA_LONG, 2);
            int bodyId = addDynamicStructure(worldId, 0.0f, 10.0f, 0.0f, blocksPtr, 2, outHandlesPtr);
            System.out.println("Successfully generated Rapier compound structure Entity ID: " + bodyId + " at y=10.0");
            
            for (int i = 0; i < 5; i++) {
                step(worldId);
                float[] pos = getPosition(worldId, bodyId);
                System.out.printf("Tick %d -> Y: %.3f\n", i, pos[1]);
            }
        }
        
        destroyWorld(worldId);
    }
}
