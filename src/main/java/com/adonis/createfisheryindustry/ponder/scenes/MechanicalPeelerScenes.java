package com.adonis.createfisheryindustry.ponder.scenes;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class MechanicalPeelerScenes {

    public static void processing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("mechanical_peeler", "createfisheryindustry.ponder.mechanical_peeler.header");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);

        BlockPos shaftPos = util.grid().at(2, 1, 3);
        scene.world().setBlock(shaftPos, AllBlocks.SHAFT.getDefaultState()
                .setValue(ShaftBlock.AXIS, Axis.Z), false);

        BlockPos peelerPos = util.grid().at(2, 1, 2);
        BlockPos hopperPos = util.grid().at(2, 1, 1); // 漏斗位置
        Selection peelerSelect = util.select().position(peelerPos);

        scene.idle(5);
        scene.world().showSection(util.select().fromTo(2, 1, 3, 2, 1, 5), Direction.DOWN);
        scene.idle(10);
        scene.effects().rotationDirectionIndicator(shaftPos);
        scene.world().showSection(peelerSelect, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(50)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.mechanical_peeler.text_1")
                .pointAt(util.vector().blockSurface(peelerPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(55);

        // 演示处理生鳕鱼
        ItemStack rawCod = new ItemStack(Items.COD);
        ItemStack codSteak = CreateFisheryItems.COD_STEAK.asStack();

        Vec3 itemSpawn = util.vector().centerOf(peelerPos.above().west());
        ElementLink<EntityElement> codItem = scene.world().createItemEntity(itemSpawn, util.vector().of(0, 0, 0), rawCod);
        scene.idle(12);

        scene.overlay().showControls(itemSpawn, Pointing.DOWN, 20).withItem(rawCod);
        scene.idle(10);

        scene.world().modifyEntity(codItem, e -> e.setDeltaMovement(util.vector().of(0.05, 0.2, 0)));
        scene.idle(12);

        scene.world().modifyEntity(codItem, Entity::discard);
        scene.idle(20);

        codItem = scene.world().createItemEntity(util.vector().topOf(peelerPos).add(0.5, -.1, 0),
                util.vector().of(0.05, 0.18, 0), codSteak);
        scene.idle(12);
        scene.overlay().showControls(itemSpawn.add(2, 0, 0), Pointing.DOWN, 20).withItem(codSteak);
        scene.idle(30);

        scene.overlay().showText(60)
                .attachKeyFrame()
                .text("createfisheryindustry.ponder.mechanical_peeler.text_2")
                .pointAt(util.vector().blockSurface(peelerPos, Direction.UP))
                .placeNearTarget();
        scene.idle(70);

        scene.world().modifyEntity(codItem, Entity::discard);
        scene.idle(10);

        // 显示安山漏斗用于提取副产物（晚一点显示）
        scene.world().showSection(util.select().position(hopperPos), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("createfisheryindustry.ponder.mechanical_peeler.text_3")
                .pointAt(util.vector().blockSurface(hopperPos, Direction.NORTH))
                .placeNearTarget();
        scene.idle(50);

        // 显示副产物鱼皮被输出到漏斗
        scene.overlay().showControls(util.vector().centerOf(hopperPos), Pointing.UP, 60)
                .withItem(CreateFisheryItems.FISH_SKIN.asStack());
        scene.idle(80);

        scene.markAsFinished();
    }

    public static void treeProcessing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("mechanical_peeler_tree", "createfisheryindustry.ponder.mechanical_peeler_tree.header");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(.9f);
        scene.world().setBlock(util.grid().at(2, 0, 2), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        scene.world().showSection(util.select().layer(0)
                .substract(util.select().position(3, 1, 1))  // 不包含安山漏斗
                .add(util.select().position(1, 1, 2)), Direction.UP);

        scene.world().setKineticSpeed(util.select().position(5, 0, 1), -8);
        scene.world().setKineticSpeed(util.select().fromTo(3, 1, 2, 5, 1, 2), 16);

        BlockPos peelerPos = util.grid().at(3, 1, 2);
        BlockPos hopperPos = util.grid().at(3, 1, 1); // 安山漏斗位置 (z-1)

        scene.idle(5);
        scene.world().showSection(util.select().fromTo(4, 1, 2, 5, 1, 2), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(peelerPos), Direction.DOWN);
        scene.idle(10);

        // 在切削机之后显示安山漏斗
        scene.world().showSection(util.select().position(hopperPos), Direction.DOWN);
        scene.idle(10);

        // 显示整棵树
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 3, 2), Direction.UP);
        scene.world().showSection(util.select().layersFrom(4), Direction.UP);

        scene.idle(15);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(2, 2, 2), Direction.WEST))
                .text("createfisheryindustry.ponder.mechanical_peeler_tree.text_1");
        scene.idle(90);

        // 整棵树同时变成去皮原木（不破坏方块）
        Selection treeSelection = util.select().fromTo(2, 1, 2, 2, 6, 2);
        scene.world().replaceBlocks(treeSelection, Blocks.STRIPPED_OAK_LOG.defaultBlockState(), false);
        scene.idle(20);

        // 只清理树木场景，不移除切削机和漏斗
        scene.world().hideSection(util.select().fromTo(2, 1, 2, 2, 6, 2), Direction.UP);
        scene.world().hideSection(util.select().layersFrom(4), Direction.UP);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.idle(15);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text("createfisheryindustry.ponder.mechanical_peeler_tree.text_2")
                .pointAt(util.vector().blockSurface(peelerPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

// 演示给羊剪羊毛
        ElementLink<EntityElement> sheep = scene.world().createEntity(world -> {
            Sheep sheepEntity = new Sheep(EntityType.SHEEP, world);
            sheepEntity.setPos(util.vector().centerOf(2, 1, 2));
            return sheepEntity;
        });
        scene.idle(30);

// 羊毛从安山漏斗处出来
        scene.world().createItemEntity(util.vector().centerOf(hopperPos).add(0, 0.2, 0),
                util.vector().of(0, 0.2, -0.1), new ItemStack(Items.WHITE_WOOL));

// 将羊变成剪毛后的状态
        scene.world().modifyEntity(sheep, entity -> {
            if (entity instanceof Sheep) {
                ((Sheep) entity).setSheared(true);
            }
        });
        scene.idle(30);

// 移除羊
        scene.world().modifyEntity(sheep, Entity::discard);
        scene.idle(10);

// 演示乌龟
        ElementLink<EntityElement> turtle = scene.world().createEntity(world -> {
            Turtle turtleEntity = new Turtle(EntityType.TURTLE, world);
            turtleEntity.setPos(util.vector().centerOf(2, 1, 2));
            return turtleEntity;
        });
        scene.idle(30);

// 鳞甲从安山漏斗处出来
        scene.world().createItemEntity(util.vector().centerOf(hopperPos).add(0, 0.2, 0),
                util.vector().of(0, 0.2, -0.1), new ItemStack(Items.TURTLE_SCUTE));
        scene.idle(30);

// 移除乌龟
        scene.world().modifyEntity(turtle, Entity::discard);
        scene.idle(10);

// 演示犰狳
        ElementLink<EntityElement> armadillo = scene.world().createEntity(world -> {
            Armadillo armadilloEntity = new Armadillo(EntityType.ARMADILLO, world);
            armadilloEntity.setPos(util.vector().centerOf(2, 1, 2));
            return armadilloEntity;
        });
        scene.idle(30);

// 犰狳鳞甲从安山漏斗处出来
        scene.world().createItemEntity(util.vector().centerOf(hopperPos).add(0, 0.2, 0),
                util.vector().of(0, 0.2, -0.1), new ItemStack(Items.ARMADILLO_SCUTE));
        scene.idle(30);

// 移除犰狳
        scene.world().modifyEntity(armadillo, Entity::discard);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.idle(10);

// 演示紫水晶 - 使用最原始的方法
        BlockPos amethystPos = util.grid().at(1, 1, 2);
        BlockPos clusterPos = util.grid().at(2, 1, 2);

// 放置紫水晶母岩
        scene.world().setBlock(amethystPos, Blocks.BUDDING_AMETHYST.defaultBlockState(), false);
        scene.idle(30);

// 放置紫水晶簇 - 附着在母岩的东侧（WEST面朝向母岩）
        scene.world().setBlock(clusterPos, Blocks.AMETHYST_CLUSTER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.EAST), false);
        scene.idle(30);

// 在显示紫水晶后显示text_3
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("createfisheryindustry.ponder.mechanical_peeler_tree.text_3")
                .pointAt(util.vector().blockSurface(clusterPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

// 紫水晶碎片从安山漏斗处出来
        scene.world().createItemEntity(util.vector().centerOf(hopperPos).add(0, 0.2, 0),
                util.vector().of(0, 0.2, -0.1), new ItemStack(Items.AMETHYST_SHARD, 4));
        scene.idle(30);

// 清理紫水晶
        scene.world().setBlock(clusterPos, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(amethystPos, Blocks.AIR.defaultBlockState(), false);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.idle(10);
    }
}