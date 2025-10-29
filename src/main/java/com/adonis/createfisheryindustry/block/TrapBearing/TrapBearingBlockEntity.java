package com.adonis.createfisheryindustry.block.TrapBearing;

import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapBlock;
import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapFakePlayer;
import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapMovementBehaviour;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;

import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrapBearingBlockEntity extends MechanicalBearingBlockEntity implements IDisplayAssemblyExceptions {

    protected ScrollOptionBehaviour<RotationMode> movementMode;
    protected ControlledContraptionEntity movedContraption;
    protected float angle;
    protected boolean running;
    protected boolean assembleNextTick;
    protected float clientAngleDiff;
    protected AssemblyException lastException;
    protected double sequencedAngleLimit;
    protected FishingContext fishingContext;

    private float prevAngle;

    public TrapBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(3);
        sequencedAngleLimit = -1;
        fishingContext = new FishingContext((ServerLevel) level, new ItemStack(Items.FISHING_ROD));
        fishingContext.setWorldPosition(pos);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        movementMode = new ScrollOptionBehaviour<>(RotationMode.class,
                CreateLang.translateDirect("contraptions.movement_mode"), this, getMovementModeSlot());
        behaviours.add(movementMode);
        registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
    }

    @Override
    protected boolean isWindmill() {
        return false;
    }

    @Override
    public float getGeneratedSpeed() {
        if (!running || movedContraption == null) return 0;
        float direction = (float) Math.signum(getSpeed());
        return 8 * direction;
    }

    @Override
    public void assemble() {
        if (!(level.getBlockState(worldPosition).getBlock() instanceof TrapBearingBlock)) return;

        Direction direction = getBlockState().getValue(BearingBlock.FACING);
        BearingContraption contraption = new BearingContraption(isWindmill(), direction);

        try {
            if (!contraption.assemble(level, worldPosition)) return;
            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }

        List<BlockPos> framePositions = new ArrayList<>();
        for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry : contraption.getBlocks().entrySet()) {
            BlockPos relPos = entry.getKey();
            BlockState state = entry.getValue().state();
            if (state.getBlock() instanceof FrameTrapBlock) {
                framePositions.add(relPos);
            }
        }

        int totalFrames = framePositions.size();
        if (totalFrames < 8) {
            lastException = new AssemblyException(Component.translatable("trap.frames.insufficient"));
            sendData();
            return;
        }

        BlockPos anchor = worldPosition.relative(direction);
        BlockState anchorState = level.getBlockState(anchor);
        if (!(anchorState.getBlock() instanceof FrameTrapBlock)) {
            lastException = new AssemblyException(Component.translatable("trap.anchor.invalid"));
            sendData();
            return;
        }

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(level, this, contraption);
        BlockPos anchorPos = worldPosition.relative(direction);
        movedContraption.setPos(anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());
        movedContraption.setRotationAxis(direction.getAxis());
        fishingContext.setFramePositions(framePositions);
        fishingContext.updateEffective((ServerLevel) level, movedContraption.position(), angle);
        level.addFreshEntity(movedContraption);

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

        running = true;
        angle = 0;
        sendData();
        updateGeneratedRotation();
    }

    @Override
    public void disassemble() {
        if (!running && movedContraption == null) return;
        angle = 0;
        sequencedAngleLimit = -1;
        if (isWindmill()) applyRotation();
        if (movedContraption != null) {
            movedContraption.disassemble();
            AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);
        }

        movedContraption = null;
        running = false;
        updateGeneratedRotation();
        assembleNextTick = false;
        if (fishingContext != null) fishingContext.invalidate((ServerLevel) level);
        sendData();
    }

    @Override
    public void tick() {
        super.tick();

        prevAngle = angle;
        if (level.isClientSide) {
            clientAngleDiff /= 2;
            return;
        }

        if (assembleNextTick) {
            assembleNextTick = false;
            if (running) {
                boolean canDisassemble = movementMode.get() == RotationMode.ROTATE_PLACE
                        || (isNearInitialAngle() && movementMode.get() == RotationMode.ROTATE_PLACE_RETURNED);
                if (getSpeed() == 0 && (canDisassemble || movedContraption == null || movedContraption.getContraption().getBlocks().isEmpty())) {
                    if (movedContraption != null) movedContraption.getContraption().stop(level);
                    disassemble();
                    return;
                }
            } else {
                if (getSpeed() == 0 && !isWindmill()) return;
                assemble();
            }
        }

        if (!running) return;

        if (movedContraption != null) {
            fishingContext.updateEffective((ServerLevel) level, movedContraption.position(), angle);
            int effective = fishingContext.getEffectiveCount();
            if (effective < 8) {
                fishingContext.timeUntilCatch = Integer.MAX_VALUE;
            } else {
                int base = 200;
                int reduction = (effective - 8) * 5;
                fishingContext.timeUntilCatch = Math.max(80, base - reduction);
            }

            if (fishingContext.timeUntilCatch > 0) {
                fishingContext.timeUntilCatch--;
            } else if (effective >= 8 && CreateFisheryCommonConfig.isFishingEnabled()) {
                triggerFishing();
                fishingContext.reset((ServerLevel) level, fishingContext.getRandomEffectivePos((ServerLevel) level, movedContraption.position(), angle));
            }

            if (level.getGameTime() % 100 == 0) {
                fishingContext.updateOpenWater((ServerLevel) level, movedContraption.position(), angle);
            }
        }

        if (!(movedContraption != null && movedContraption.isStalled())) {
            float angularSpeed = getAngularSpeed();
            if (sequencedAngleLimit >= 0) {
                angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
                sequencedAngleLimit = Math.max(0, sequencedAngleLimit - Math.abs(angularSpeed));
            }
            float newAngle = angle + angularSpeed;
            angle = (float) (newAngle % 360);
        }

        applyRotation();
    }

    private void triggerFishing() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos randomPos = fishingContext.getRandomEffectivePos(serverLevel, movedContraption.position(), angle);
        boolean isLava = fishingContext.isValidLavaFishingPosition(serverLevel, randomPos);
        double successRate = isLava ? CreateFisheryCommonConfig.getLavaFishingSuccessRate() : CreateFisheryCommonConfig.getFishingSuccessRate();
        if (serverLevel.random.nextFloat() >= successRate) return;

        LootTable lootTable = fishingContext.getLootTable(serverLevel, randomPos);
        LootParams params = fishingContext.buildLootContext(null, serverLevel, randomPos);
        List<ItemStack> loots = lootTable.getRandomItems(params);

        if (serverLevel.random.nextFloat() < 0.05f) {
            loots.add(new ItemStack(CreateFisheryItems.WORN_HARPOON.get()));
        }

        FrameTrapFakePlayer fakePlayer = new FrameTrapFakePlayer(serverLevel);
        FishingHook hook = new FishingHook(EntityType.FISHING_BOBBER, serverLevel);
        ItemFishedEvent event = NeoForge.EVENT_BUS.post(new ItemFishedEvent(loots, 0, hook));
        if (!event.isCanceled()) {
            for (ItemStack stack : loots) {
                dropItem(stack, randomPos);
            }
            addExperienceNugget(randomPos);
            FrameTrapMovementBehaviour.spawnFishingParticles(serverLevel, randomPos.getX() + 0.5, randomPos.getY() + 0.5, randomPos.getZ() + 0.5, isLava);
        }
    }

    private void dropItem(ItemStack stack, BlockPos relPos) {
        Vec3 worldPos = movedContraption.position().add(relPos.getX() + 0.5, relPos.getY() + 0.5, relPos.getZ() + 0.5);
        ItemEntity entity = new ItemEntity(level, worldPos.x, worldPos.y, worldPos.z, stack);
        level.addFreshEntity(entity);
    }

    private void addExperienceNugget(BlockPos relPos) {
        Vec3 worldPos = movedContraption.position().add(relPos.getX() + 0.5, relPos.getY() + 0.5, relPos.getZ() + 0.5);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(new ExperienceOrb(serverLevel, worldPos.x, worldPos.y, worldPos.z, 1));
        }
    }

    public float getAngularSpeed() {
        float speed = convertToAngular(isWindmill() ? getGeneratedSpeed() : getSpeed());
        if (getSpeed() == 0) speed = 0;
        if (level.isClientSide) {
            speed *= ServerSpeedProvider.get();
            speed += clientAngleDiff / 3f;
        }
        return speed;
    }

    public boolean isNearInitialAngle() {
        return Math.abs(angle) < 22.5 || Math.abs(angle) > 360 - 22.5;
    }

    protected void applyRotation() {
        if (movedContraption == null) return;
        movedContraption.setAngle(angle);
        BlockState blockState = getBlockState();
        if (blockState.hasProperty(BlockStateProperties.FACING))
            movedContraption.setRotationAxis(blockState.getValue(BlockStateProperties.FACING).getAxis());
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putBoolean("Running", running);
        compound.putFloat("Angle", angle);
        if (sequencedAngleLimit >= 0) compound.putDouble("SequencedAngleLimit", sequencedAngleLimit);
        AssemblyException.write(compound, registries, lastException);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (wasMoved) {
            super.read(compound, registries, clientPacket);
            return;
        }

        float angleBefore = angle;
        running = compound.getBoolean("Running");
        angle = compound.getFloat("Angle");
        sequencedAngleLimit = compound.contains("SequencedAngleLimit") ? compound.getDouble("SequencedAngleLimit") : -1;
        lastException = AssemblyException.read(compound, registries);
        super.read(compound, registries, clientPacket);
        if (!clientPacket) return;
        if (running) {
            if (movedContraption == null || !movedContraption.isStalled()) {
                clientAngleDiff = AngleHelper.getShortestAngleDiff(angleBefore, angle);
                angle = angleBefore;
            }
        } else movedContraption = null;
    }

    @Override
    public float getInterpolatedAngle(float partialTicks) {
        if (isVirtual()) return Mth.lerp(partialTicks + .5f, prevAngle, angle);
        if (movedContraption == null || movedContraption.isStalled() || !running) partialTicks = 0;
        float angularSpeed = getAngularSpeed();
        if (sequencedAngleLimit >= 0) angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
        return Mth.lerp(partialTicks, angle, angle + angularSpeed);
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        assembleNextTick = true;
        sequencedAngleLimit = -1;

        if (movedContraption != null && Math.signum(prevSpeed) != Math.signum(getSpeed()) && prevSpeed != 0) {
            if (!movedContraption.isStalled()) {
                angle = Math.round(angle);
                applyRotation();
            }
            movedContraption.getContraption().stop(level);
        }

        if (!isWindmill() && sequenceContext != null && sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE)
            sequencedAngleLimit = sequenceContext.getEffectiveValue(getTheoreticalSpeed());
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (movedContraption != null && !level.isClientSide) sendData();
    }

    @Override
    public void remove() {
        if (!level.isClientSide) disassemble();
        super.remove();
    }

    @Override
    protected boolean syncSequenceContext() {
        return true;
    }

    @Override
    public boolean isWoodenTop() {
        return false;
    }

    @Override
    public BlockPos getBlockPosition() {
        return worldPosition;
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        BlockState blockState = getBlockState();
        if (!(contraption.getContraption() instanceof BearingContraption)) return;
        if (!blockState.hasProperty(BearingBlock.FACING)) return;

        this.movedContraption = contraption;
        setChanged();
        BlockPos anchor = worldPosition.relative(blockState.getValue(BearingBlock.FACING));
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        if (!level.isClientSide) {
            this.running = true;
            sendData();
        }
    }

    @Override
    public void onStall() {
        if (!level.isClientSide) sendData();
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity contraption) {
        return movedContraption == contraption;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (super.addToTooltip(tooltip, isPlayerSneaking)) return true;
        if (isPlayerSneaking) return false;
        if (!isWindmill() && getSpeed() == 0) return false;
        if (running) return false;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BearingBlock)) return false;

        BlockState attachedState = level.getBlockState(worldPosition.relative(state.getValue(BearingBlock.FACING)));
        if (attachedState.canBeReplaced()) return false;
        TooltipHelper.addHint(tooltip, "hint.empty_bearing");
        return true;
    }

    public void setAngle(float forcedAngle) {
        angle = forcedAngle;
    }

    public ControlledContraptionEntity getMovedContraption() {
        return movedContraption;
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    public FishingContext getFishingContext() {
        return fishingContext;
    }
}