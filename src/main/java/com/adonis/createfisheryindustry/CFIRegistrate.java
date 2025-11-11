package com.adonis.createfisheryindustry;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.api.registry.registrate.SimpleBuilder;
import com.simibubi.create.foundation.data.CreateBlockEntityBuilder;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.BlockEntityBuilder;
import com.tterrag.registrate.builders.Builder;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class CFIRegistrate extends AbstractRegistrate<CFIRegistrate> {
    protected final Logger logger;
    protected final Map<Holder<?>, Holder<CreativeModeTab>> creativeModeTabLookup = new HashMap<>();
    protected @Nullable Holder<CreativeModeTab> creativeModeTab;
    protected @Nullable Function<Item, com.simibubi.create.foundation.item.TooltipModifier> tooltipModifier;

    protected CFIRegistrate(String modId) {
        super(modId);
        this.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
        this.logger = LoggerFactory.getLogger(this.getClass().getSimpleName() + "[" + modId + "]");
    }

    public static CFIRegistrate create(String modId) {
        return new CFIRegistrate(modId);
    }

    public CFIRegistrate setCreativeModeTab(@Nullable Holder<CreativeModeTab> creativeModeTab) {
        this.creativeModeTab = creativeModeTab;
        return this;
    }

    public CFIRegistrate setTooltipModifier(@Nullable Function<Item, com.simibubi.create.foundation.item.TooltipModifier> tooltipModifier) {
        this.tooltipModifier = tooltipModifier;
        return this;
    }

    @Override
    protected <R, T extends R> RegistryEntry<R, T> accept(String name, ResourceKey<? extends Registry<R>> type, Builder<R, T, ?, ?> builder, NonNullSupplier<? extends T> creator, NonNullFunction<DeferredHolder<R, T>, ? extends RegistryEntry<R, T>> entryFactory) {
        RegistryEntry<R, T> entry = super.accept(name, type, builder, creator, entryFactory);
        if (type.equals(net.minecraft.core.registries.Registries.ITEM) && this.tooltipModifier != null) {
            Function<Item, com.simibubi.create.foundation.item.TooltipModifier> modifier = this.tooltipModifier;
            this.addRegisterCallback(name, net.minecraft.core.registries.Registries.ITEM, item -> {
                com.simibubi.create.foundation.item.TooltipModifier modifierInstance = modifier.apply(item);
                com.simibubi.create.foundation.item.TooltipModifier.REGISTRY.register(item, modifierInstance);
            });
        }
        if (this.creativeModeTab != null) {
            this.creativeModeTabLookup.put(entry, this.creativeModeTab);
        }
        return entry;
    }

    @Override
    public <T extends BlockEntity> CreateBlockEntityBuilder<T, CFIRegistrate> blockEntity(String name, BlockEntityBuilder.BlockEntityFactory<T> factory) {
        return blockEntity(self(), name, factory);
    }

    @Override
    public <T extends BlockEntity, P> CreateBlockEntityBuilder<T, P> blockEntity(P parent, String name, BlockEntityBuilder.BlockEntityFactory<T> factory) {
        return (CreateBlockEntityBuilder<T, P>) entry(name, callback -> CreateBlockEntityBuilder.create(this, parent, name, callback, factory));
    }

    /**
     * 注册动态物品存储类型
     */
    public <T extends MountedItemStorageType<?>> SimpleBuilder<MountedItemStorageType<?>, T, CFIRegistrate> mountedItemStorage(String name, Supplier<T> supplier) {
        return this.entry(name, callback -> {
            SimpleBuilder<MountedItemStorageType<?>, T, CFIRegistrate> builder =
                    new SimpleBuilder<>(this, this, name, callback, CreateRegistries.MOUNTED_ITEM_STORAGE_TYPE, supplier);

            // 确保调用 byBlock 方法
            builder.byBlock(MountedItemStorageType.REGISTRY);

            return builder;
        });
    }

    public ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(getModid(), path);
    }
}