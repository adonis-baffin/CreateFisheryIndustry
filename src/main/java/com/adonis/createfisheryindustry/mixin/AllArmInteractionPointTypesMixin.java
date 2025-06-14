package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapInteractionPointType;
import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveInteractionPointType;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshInteractionPointType;
import com.adonis.createfisheryindustry.block.SmartNozzle.SmartNozzleInteractionPointType;
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

            registerMethod.invoke(null, "smart_beehive", new SmartBeehiveInteractionPointType());

            registerMethod.invoke(null, "smart_nozzle", new SmartNozzleInteractionPointType());

        } catch (Exception e) {
        }
    }
}