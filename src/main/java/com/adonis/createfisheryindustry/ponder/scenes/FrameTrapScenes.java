package com.adonis.createfisheryindustry.ponder.scenes;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class FrameTrapScenes {

    public static void frameTrap(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("frame_trap", "Using the Frame Trap");
        scene.configureBasePlate(0, 0, 7);

        // 先显示基础平台
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // 定义各个选择区域
        Selection frameTrapSelection = util.select().fromTo(4, 1, 3, 2, 2, 3);
        Selection chassisSelection = util.select().fromTo(2, 3, 3, 4, 3, 3);
        Selection casingsSelection = util.select().position(2, 1, 2)
                .add(util.select().position(4, 1, 2));
        Selection barrelSelection = util.select().position(4, 4, 3);

        // 显示除了Frame Trap之外的所有方块（不包括安山机壳，因为还没设置）
        Selection everythingElse = util.select()
                .layers(1, 5)
                .substract(frameTrapSelection)
                .substract(casingsSelection);  // 排除安山机壳位置

        scene.world().showSection(everythingElse, Direction.DOWN);
        scene.idle(10);

        // 逐个显示Frame Trap方块
        // 底层3个
        scene.world().showSection(util.select().position(4, 1, 3), Direction.DOWN);
        scene.idle(3);
        scene.world().showSection(util.select().position(3, 1, 3), Direction.DOWN);
        scene.idle(3);
        scene.world().showSection(util.select().position(2, 1, 3), Direction.DOWN);
        scene.idle(5);

        // 上层3个
        scene.world().showSection(util.select().position(4, 2, 3), Direction.DOWN);
        scene.idle(3);
        scene.world().showSection(util.select().position(3, 2, 3), Direction.DOWN);
        scene.idle(3);
        scene.world().showSection(util.select().position(2, 2, 3), Direction.DOWN);
        scene.idle(10);

        // 高亮显示Frame Trap区域
        scene.overlay().showOutline(PonderPalette.GREEN, "frame_trap_outline", frameTrapSelection, 90);
        scene.idle(10);

        // Text 1: 像风帆一样自行依附
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Like sails, Frame Traps will attach themselves to moving contraptions")
                .pointAt(util.vector().blockSurface(util.grid().at(2, 2, 3), Direction.NORTH))
                .placeNearTarget();
        scene.idle(110);

        // Text 2: 当作为动态结构一部分移动时 - 指向Frame Trap位置(2,2,3)
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("When Frame Traps move as part of a contraption...")
                .pointAt(util.vector().centerOf(2, 2, 3))
                .placeNearTarget();
        scene.idle(60);

        // 在Text2显示之后添加安山机壳
        scene.world().setBlock(util.grid().at(2, 1, 2), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        scene.world().setBlock(util.grid().at(4, 1, 2), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        scene.world().showSection(casingsSelection, Direction.DOWN);
        scene.idle(10);

        // 创建掉落物（羽毛）在安山机壳上方
        ElementLink<EntityElement> featherEntity = scene.world().createItemEntity(
                util.vector().centerOf(2, 2, 2),
                Vec3.ZERO,
                new ItemStack(Items.FEATHER)
        );
        scene.idle(10);

        // 创建鳕鱼实体
        ElementLink<EntityElement> codEntity = scene.world().createEntity(world -> {
            Cod cod = new Cod(EntityType.COD, world);
            cod.setPos(util.vector().centerOf(4, 2, 2));
            return cod;
        });
        scene.idle(20);

        // 创建包含Frame Trap和其他部件的动态结构
        Selection movingParts = frameTrapSelection
                .add(chassisSelection)
                .add(barrelSelection);

        ElementLink<WorldSectionElement> contraption = scene.world().makeSectionIndependent(movingParts);
        scene.world().configureCenterOfRotation(contraption, util.vector().centerOf(3, 3, 3));

        // 开始旋转动态结构
        scene.world().rotateSection(contraption, 360, 0, 0, 80);

        // 在旋转过程中，让掉落物和鳕鱼消失（模拟被收集）
        scene.idle(4);
        scene.world().modifyEntity(featherEntity, entity -> entity.discard());
        scene.world().modifyEntity(codEntity, entity -> entity.discard());
        scene.idle(15);

        // Text 3: 收集掉落物和捕捉生物
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("It will collect items it touches and capture small creatures in its path")
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 2), Direction.NORTH))
                .placeNearTarget();

        scene.idle(50); // 等待旋转完成
        scene.idle(60);

        // 高亮木桶
        scene.idle(10);

        // Text 4: 自动存储到容器（不显示物品）
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("And automatically store them in connected containers")
                .pointAt(util.vector().blockSurface(util.grid().at(4, 4, 3), Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.markAsFinished();
    }
}