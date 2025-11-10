package com.adonis.createfisheryindustry.block.common;

/**
 * 流体环境类型枚举
 */
public enum FluidEnvironment {
    /**
     * 水下（上方也有水）
     */
    UNDERWATER,
    
    /**
     * 水面（当前在水中，但上方是空气）
     */
    WATER_SURFACE,
    
    /**
     * 岩浆中
     */
    LAVA,
    
    /**
     * 干燥环境（空气中）
     */
    DRY
}