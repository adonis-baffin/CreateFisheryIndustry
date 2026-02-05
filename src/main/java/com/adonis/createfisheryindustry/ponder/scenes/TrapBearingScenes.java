package com.adonis.createfisheryindustry.ponder.scenes;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
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

public class TrapBearingScenes {

    public static void trapBearing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("trap_bearing", "createfisheryindustry.ponder.trap_bearing.header");
        scene.configureBasePlate(0, 0, 5);

        // 显示基础平台
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // 定义位置
        BlockPos frameTrap1Pos = util.grid().at(2, 1, 2);
        BlockPos frameTrap2Pos = util.grid().at(1, 1, 2);
        BlockPos trapBearingPos = util.grid().at(3, 1, 2);
        BlockPos brassFunnelPos = util.grid().at(3, 1, 1);
        BlockPos shaftPos = util.grid().at(4, 1, 2);
        BlockPos cogwheelPos = util.grid().at(5, 1, 2);
        BlockPos largeCogwheelPos = util.grid().at(5, 0, 1);

        // 显示捕鱼轴承
        scene.world().showSection(util.select().position(trapBearingPos), Direction.DOWN);
        scene.idle(10);

        // 第一句话：捕鱼轴承可为玩家自动获取钓鱼战利品
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.trap_bearing.text_1")
                .pointAt(util.vector().blockSurface(trapBearingPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 显示传动杆、齿轮、大齿轮
        scene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        scene.world().showSection(util.select().position(cogwheelPos), Direction.DOWN);
        scene.idle(10);

        // 第二句话：捕鱼轴承本身是一种特殊的动力轴承，需要持续的机械动力驱动
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.trap_bearing.text_2")
                .pointAt(util.vector().blockSurface(trapBearingPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 显示两个捕鱼框
        scene.world().showSection(util.select().position(frameTrap1Pos), Direction.DOWN);
        scene.world().showSection(util.select().position(frameTrap2Pos), Direction.DOWN);
        scene.idle(5);

        // 高亮两个捕鱼框（白色框选）
        Selection frameTrapSelection = util.select().position(frameTrap1Pos).add(util.select().position(frameTrap2Pos));
        scene.overlay().showOutline(PonderPalette.WHITE, "frame_traps", frameTrapSelection, 100);

        // 第三句话：捕鱼轴承必须配合水中的捕鱼框使用，否则与普通动力轴承无异
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.trap_bearing.text_3")
                .pointAt(util.vector().blockSurface(frameTrap1Pos, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        // 第四句话：每8个位于水源中的捕鱼框可提升一级捕鱼等级
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.trap_bearing.text_4")
                .pointAt(util.vector().centerOf(frameTrap1Pos).add(util.vector().centerOf(frameTrap2Pos)).scale(0.5))
                .placeNearTarget();
        scene.idle(90);

        // 第五句话：至少需要一级捕鱼等级（8个捕鱼框）才能开始获取钓鱼战利品，等级越高，获取速度越快
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.trap_bearing.text_5")
                .pointAt(util.vector().blockSurface(trapBearingPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        // 第六句话：捕鱼等级达到四级及以上时，有机会获取宝藏类战利品
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("createfisheryindustry.ponder.trap_bearing.text_6")
                .pointAt(util.vector().blockSurface(trapBearingPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 第七句话：捕鱼等级最高为八级，超过64个捕鱼框不会再提升效率
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.trap_bearing.text_7")
                .pointAt(util.vector().blockSurface(trapBearingPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 显示黄铜漏斗
        scene.world().showSection(util.select().position(brassFunnelPos), Direction.SOUTH);
        scene.idle(10);

        // 第八句话：捕鱼战利品会存储在捕鱼轴承内部，可以通过漏斗或管道直接提取
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.trap_bearing.text_8")
                .pointAt(util.vector().blockSurface(brassFunnelPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(50);

        // 对黄铜漏斗显示右键标志+鳕鱼（表示提取）
        scene.overlay().showControls(util.vector().centerOf(brassFunnelPos).add(0, 1, 0), Pointing.DOWN, 60)
                .rightClick()
                .withItem(new ItemStack(Items.COD));
        scene.idle(70);

        // 第九句话：与普通钓鱼不同，捕鱼轴承可获取专属的额外战利品，例如破损的鱼叉
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("createfisheryindustry.ponder.trap_bearing.text_9")
                .pointAt(util.vector().blockSurface(brassFunnelPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 第十句话：在海洋群系中使用捕鱼轴承，还会额外获得贻贝等海洋特有战利品
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text("createfisheryindustry.ponder.trap_bearing.text_10")
                .pointAt(util.vector().blockSurface(trapBearingPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        scene.markAsFinished();
    }
}