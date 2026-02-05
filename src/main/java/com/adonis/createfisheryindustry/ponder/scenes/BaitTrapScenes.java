package com.adonis.createfisheryindustry.ponder.scenes;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BaitTrapScenes {

    public static void baitTrap(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("bait_trap", "createfisheryindustry.ponder.bait_trap.header");
        scene.configureBasePlate(0, 0, 5);

        // 显示基础平台
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // 定义位置
        BlockPos baitTrapPos = util.grid().at(2, 1, 2);
        BlockPos beltStart = util.grid().at(1, 1, 3);
        BlockPos beltMiddle = util.grid().at(2, 1, 3);
        BlockPos beltEnd = util.grid().at(3, 1, 3);
        BlockPos shaft1Pos = util.grid().at(3, 1, 4);
        BlockPos cogwheel1Pos = util.grid().at(3, 1, 5);
        BlockPos deployerPos = util.grid().at(4, 1, 2);
        BlockPos cogwheel2Pos = util.grid().at(5, 1, 2);

        // 显示诱捕网
        scene.world().showSection(util.select().position(baitTrapPos), Direction.DOWN);
        scene.idle(10);

        // 第一句话：诱捕网是一种被动捕获装置，可用于自动化生产某些生物的战利品
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.bait_trap.text_1")
                .pointAt(util.vector().blockSurface(baitTrapPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 对诱捕网显示右键标志+胡萝卜
        scene.overlay().showControls(util.vector().centerOf(baitTrapPos).add(0, 1, 0), Pointing.DOWN, 60)
                .rightClick()
                .withItem(new ItemStack(Items.CARROT));
        scene.idle(30);

        // 第二句话：使用对应诱饵潜行右键诱捕网即可放入
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.bait_trap.text_2")
                .pointAt(util.vector().blockSurface(baitTrapPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 对诱捕网显示兔子皮图标
        scene.overlay().showControls(util.vector().centerOf(baitTrapPos).add(0, 1, 0), Pointing.DOWN, 60)
                .withItem(new ItemStack(Items.RABBIT_HIDE));
        scene.idle(30);

        // 第三句话：在合适的环境条件下，诱捕网会定期生成对应战利品
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("createfisheryindustry.ponder.bait_trap.text_3")
                .pointAt(util.vector().blockSurface(baitTrapPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 显示传送带、齿轮1和传动杆，并接入应力
        scene.world().showSection(util.select().fromTo(beltStart, beltEnd), Direction.DOWN);
        scene.world().showSection(util.select().position(shaft1Pos), Direction.DOWN);
        scene.world().showSection(util.select().position(cogwheel1Pos), Direction.DOWN);
        scene.idle(5);

        // 接入应力
        Selection beltSelection = util.select().fromTo(beltStart, beltEnd);
        scene.world().setKineticSpeed(beltSelection, 32);
        scene.world().setKineticSpeed(util.select().position(shaft1Pos), 32);
        scene.world().setKineticSpeed(util.select().position(cogwheel1Pos), 32);
        scene.effects().rotationDirectionIndicator(cogwheel1Pos);
        scene.idle(10);

        // 第四句话：诱捕网会自动将物品输出到临近的容器或传送带内
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.bait_trap.text_4")
                .pointAt(util.vector().blockSurface(baitTrapPos, Direction.SOUTH))
                .placeNearTarget();
        scene.idle(90);

        // 第五句话：诱饵会随使用逐渐消耗，耗尽后捕获停止
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .text("createfisheryindustry.ponder.bait_trap.text_5")
                .pointAt(util.vector().blockSurface(baitTrapPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 显示动力臂和齿轮2
        scene.world().showSection(util.select().position(deployerPos), Direction.WEST);
        scene.world().showSection(util.select().position(cogwheel2Pos), Direction.WEST);
        scene.idle(5);

        // 接入应力
        scene.world().setKineticSpeed(util.select().position(deployerPos), -16);
        scene.world().setKineticSpeed(util.select().position(cogwheel2Pos), -16);
        scene.effects().rotationDirectionIndicator(cogwheel2Pos);
        scene.idle(10);

        // 第六句话：诱饵会自然消耗，可以用动力臂来填充它们
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text("createfisheryindustry.ponder.bait_trap.text_6")
                .pointAt(util.vector().blockSurface(deployerPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        scene.markAsFinished();
    }
}