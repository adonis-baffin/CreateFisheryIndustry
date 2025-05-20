package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;

import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.registry.*;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;


import com.adonis.createfisheryindustry.config.CreateFisheryStressConfig;

import net.neoforged.neoforge.common.ModConfigSpec; // 导入 ModConfigSpec


@Mod(CreateFisheryMod.ID)
public class CreateFisheryMod {
    public static final String ID = "createfisheryindustry";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CFIRegistrate REGISTRATE = CFIRegistrate.create(ID)
            .setTooltipModifier(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
            );

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER, ID);
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_TYPE, ID);

    // 实例化你的 StressConfig
    // 使其 public static final 以便在 CreateFisheryBlocks 中访问
    public static final CreateFisheryStressConfig STRESS_CONFIG = new CreateFisheryStressConfig(ID);

    // 为 StressConfig 持有 ModConfigSpec
    private static ModConfigSpec stressConfigSpec;


    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    public CreateFisheryMod(IEventBus bus, ModContainer modContainer) {
        LOGGER.info("Initializing CFIRegistrate: " + REGISTRATE);
        REGISTRATE.registerEventListeners(bus); // Registrate 需要在Blocks注册前监听事件

        // 确保方块注册发生在 StressConfig 的 setImpact 调用之后，
        // 并且 StressConfig 实例已经创建。
        // CreateFisheryBlocks.register() 应该在 STRESS_CONFIG 初始化后调用。
        // 实际上，Registrate 通常在构造函数之后，通过监听事件来完成注册。
        // 所以这里调用 .register() 可能是多余的，或者需要确保时机正确。
        // 如果 CreateFisheryBlocks.register() 只是一个空方法，那就没问题。
        // 否则，它内部的方块定义会用到 STRESS_CONFIG。
        CreateFisheryBlocks.register(); // 通常是空的，Registrate 会处理

        CreateFisheryBlockEntities.register(bus);
        CreateFisheryEntityTypes.register(bus);
        CreateFisheryItems.register(bus);
        CreateFisheryTabs.register(bus);
        CreateFisheryComponents.register(bus);

        SERIALIZERS.register(bus);
        TYPES.register(bus);
        CreateFisheryRecipeTypes.register(SERIALIZERS, TYPES);

        // 注册你的 Common 配置
        modContainer.registerConfig(ModConfig.Type.COMMON, CreateFisheryCommonConfig.CONFIG_SPEC);

        // --- 注册你的 Stress (Server) 配置 ---
        // Catnip 的 ConfigBase 通常期望在事件中构建其 ModConfigSpec
        // 我们将在 onModConfigEvent 方法中处理这个
        // 或者，如果你的 ConfigBase 设计为可以提前构建 spec，也可以在这里做：
        ModConfigSpec.Builder stressBuilder = new ModConfigSpec.Builder();
        STRESS_CONFIG.registerAll(stressBuilder); // 调用 registerAll 来定义配置项
        stressConfigSpec = stressBuilder.build(); // 构建 ModConfigSpec
        // 将其注册到 ModContainer
        modContainer.registerConfig(ModConfig.Type.SERVER, stressConfigSpec, STRESS_CONFIG.getName() + ".toml");
        // 注意：Create 的 CStress 是 SERVER 类型，所以我们也用 SERVER。
        // 如果你的应力值是客户端也需要的（虽然不太可能影响动力计算），则选 COMMON。
        // 但通常动力学应力是服务器端逻辑。

        // 旧的 CreateFisheryConfig(modContainer) 看起来是多余的，除非它做其他事情
        // new CreateFisheryConfig(modContainer); // 如果这个类负责加载其他配置，保留它

        bus.addListener(this::commonSetup);
        bus.addListener(this::onModConfigEvent); // 修改为监听通用的 ModConfigEvent
        // bus.addListener(this::onConfigLoad); // 这两个可以被 onModConfigEvent 替代
        // bus.addListener(this::onConfigReload);
        bus.register(CreateFisheryBlockEntities.class);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 可以在这里做一些需要在所有注册完成后执行的操作
    }

    // 监听通用的 ModConfigEvent，并在其中处理加载和重载
    private void onModConfigEvent(ModConfigEvent event) {
        ModConfig config = event.getConfig();
        // 处理 Common 配置
        if (config.getSpec() == CreateFisheryCommonConfig.CONFIG_SPEC) {
            if (event instanceof ModConfigEvent.Loading) {
                CreateFisheryCommonConfig.onLoad();
                LOGGER.info("Create Fishery Industry Common config loaded.");
            } else if (event instanceof ModConfigEvent.Reloading) {
                CreateFisheryCommonConfig.onReload();
                LOGGER.info("Create Fishery Industry Common config reloaded.");
            }
        }
        // 处理 Server (Stress) 配置
        // 注意：这里的 stressConfigSpec 必须是已经构建好的
        else if (stressConfigSpec != null && config.getSpec() == stressConfigSpec) {
            if (event instanceof ModConfigEvent.Loading) {
                // STRESS_CONFIG.onLoad(); // 如果你的 StressConfig 有 onLoad 逻辑
                LOGGER.info("Create Fishery Industry Stress (Server) config loaded: " + STRESS_CONFIG.getName());
            } else if (event instanceof ModConfigEvent.Reloading) {
                // STRESS_CONFIG.onReload(); // 如果你的 StressConfig 有 onReload 逻辑
                LOGGER.info("Create Fishery Industry Stress (Server) config reloaded: " + STRESS_CONFIG.getName());
            }
        }
    }

    // onConfigLoad 和 onConfigReload 方法现在被 onModConfigEvent 替代了，可以移除
    /*
    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == CreateFisheryCommonConfig.CONFIG_SPEC) {
            CreateFisheryCommonConfig.onLoad();
        }
        // 如果 StressConfig 有 onLoad 逻辑，也在这里处理
        // if (stressConfigSpec != null && event.getConfig().getSpec() == stressConfigSpec) {
        //     STRESS_CONFIG.onLoad();
        // }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == CreateFisheryCommonConfig.CONFIG_SPEC) {
            CreateFisheryCommonConfig.onReload();
        }
        // if (stressConfigSpec != null && event.getConfig().getSpec() == stressConfigSpec) {
        //     STRESS_CONFIG.onReload();
        // }
    }
    */

    private void onServerStarting(ServerStartingEvent event) {
        CreateFisheryCommonConfig.refreshCache();
        // 如果 StressConfig 需要在服务器启动时做些什么，在这里调用
    }

    private void onServerStopping(ServerStoppingEvent event) {
        // 清理逻辑
    }
}