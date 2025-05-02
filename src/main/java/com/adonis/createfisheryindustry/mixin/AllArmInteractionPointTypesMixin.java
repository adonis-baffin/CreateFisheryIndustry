package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapInteractionPointType;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshInteractionPointType;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleInteractionPointType;
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(AllArmInteractionPointTypes.class)
public class AllArmInteractionPointTypesMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void injectInteractionPointTypes(CallbackInfo ci) {
        try {
            Method registerMethod = AllArmInteractionPointTypes.class.getDeclaredMethod("register", String.class, ArmInteractionPointType.class);
            registerMethod.setAccessible(true);
            // 注册 MeshTrapInteractionPointType
            registerMethod.invoke(null, "mesh_trap", new MeshTrapInteractionPointType());
            // 注册 TrapNozzleInteractionPointType
            registerMethod.invoke(null, "trap_nozzle", new TrapNozzleInteractionPointType());

            registerMethod.invoke(null, "smart_mesh", new SmartMeshInteractionPointType());
        } catch (Exception e) {
            // 记录异常以便调试，但不抛出以避免崩溃
            System.err.println("Failed to register interaction point types: " + e.getMessage());
        }
    }
}