package com.adonis.createfisheryindustry.ponder.scenes;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class MeshTrapScenes {

    public static void meshTrap(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("mesh_trap", "createfisheryindustry.ponder.mesh_trap.header");
        scene.configureBasePlate(0, 0, 5);

        // 显示基础平台
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // 定义位置
        BlockPos meshTrapPos = util.grid().at(2, 1, 2);
        BlockPos barrelPos = util.grid().at(1, 1, 2);

        // 第一部分：演示手持右键捕获
        // 在中心位置创建兔子
        ElementLink<EntityElement> firstRabbit = scene.world().createEntity(world -> {
            Rabbit rabbit = new Rabbit(EntityType.RABBIT, world);
            rabbit.setPos(util.vector().centerOf(2, 1, 2));
            return rabbit;
        });
        scene.idle(10);

        // 在兔子上方显示右键和MeshTrap图标
        scene.overlay().showControls(util.vector().centerOf(2, 2, 2), Pointing.DOWN, 60)
                .rightClick()
                .withItem(new ItemStack(CreateFisheryBlocks.MESH_TRAP));
        scene.idle(30);

        // 显示文本1：手持右键捕获
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.mesh_trap.text_1")
                .pointAt(util.vector().centerOf(2, 1, 2))
                .placeNearTarget();
        scene.idle(100);

        // 移除兔子
        scene.world().modifyEntity(firstRabbit, entity -> entity.discard());
        scene.idle(20);

        // 第二部分：放置MeshTrap并演示自动功能
        // 显示MeshTrap
        scene.world().showSection(util.select().position(meshTrapPos), Direction.DOWN);
        scene.idle(10);

        // 在附近创建掉落物（羽毛）
        ElementLink<EntityElement> featherEntity = scene.world().createItemEntity(
                util.vector().centerOf(2, 2, 1),
                Vec3.ZERO,
                new ItemStack(Items.FEATHER)
        );
        scene.idle(5);

        // 在附近创建兔子
        ElementLink<EntityElement> secondRabbit = scene.world().createEntity(world -> {
            Rabbit rabbit = new Rabbit(EntityType.RABBIT, world);
            rabbit.setPos(util.vector().centerOf(3, 1, 2));
            return rabbit;
        });
        scene.idle(15);

        // 模拟被捕获：移除掉落物和兔子
        scene.world().modifyEntity(featherEntity, entity -> entity.discard());
        scene.world().modifyEntity(secondRabbit, entity -> entity.discard());
        scene.idle(10);

        // 高亮MeshTrap并显示文本2
        Selection meshTrapSelection = util.select().position(meshTrapPos);
//        scene.overlay().showOutline(PonderPalette.GREEN, "mesh_trap_outline", meshTrapSelection, 80);
        
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("createfisheryindustry.ponder.mesh_trap.text_2")
                .pointAt(util.vector().blockSurface(meshTrapPos, Direction.NORTH))
                .placeNearTarget();
        scene.idle(60);

        // 在MeshTrap上方显示右键和兔子脚图标
        scene.overlay().showControls(util.vector().centerOf(2, 2, 2), Pointing.DOWN, 60)
                .rightClick()
                .withItem(new ItemStack(Items.RABBIT_FOOT));
        scene.idle(60);

        // 第三部分：演示自动输出到容器
        // 放置木桶
        scene.world().setBlock(barrelPos, AllBlocks.ITEM_VAULT.getDefaultState(), false);
        scene.world().showSection(util.select().position(barrelPos), Direction.DOWN);
        scene.idle(15);

        // 高亮MeshTrap并显示文本3
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text("createfisheryindustry.ponder.mesh_trap.text_3")
                .pointAt(util.vector().blockSurface(meshTrapPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(70);

        // 在木桶上方显示右键和兔子脚图标（表示物品被输出到容器）
        scene.overlay().showControls(util.vector().centerOf(1, 2, 2), Pointing.DOWN, 60)
                .withItem(new ItemStack(Items.RABBIT_FOOT));
        scene.idle(80);

        scene.markAsFinished();
    }
}