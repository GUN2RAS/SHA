package net.sha.api.util;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.sha.api.entity.VirtualStructureEntity;

public class VirtualBlockHitResult extends BlockHitResult {
    public final VirtualStructureEntity structure;
    public final int entityId;

    public VirtualBlockHitResult(VirtualStructureEntity structure, Vec3 pos, Direction side, BlockPos blockPos, boolean insideBlock) {
        super(pos, side, blockPos, insideBlock);
        this.structure = structure;
        this.entityId = structure.getId();
    }

    @Override
    public VirtualBlockHitResult withDirection(Direction side) {
        return new VirtualBlockHitResult(this.structure, this.getLocation(), side, this.getBlockPos(), this.isInside());
    }

    @Override
    public VirtualBlockHitResult withPosition(BlockPos blockPos) {
        return new VirtualBlockHitResult(this.structure, this.getLocation(), this.getDirection(), blockPos, this.isInside());
    }
}
