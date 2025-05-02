//package com.adonis.createfisheryindustry.client.renderer;
//
//import net.minecraft.client.model.Model;
//import net.minecraft.client.model.geom.ModelLayerLocation;
//import net.minecraft.client.model.geom.ModelPart;
//import net.minecraft.client.model.geom.PartPose;
//import net.minecraft.client.model.geom.builders.CubeListBuilder;
//import net.minecraft.client.model.geom.builders.LayerDefinition;
//import net.minecraft.client.model.geom.builders.MeshDefinition;
//import net.minecraft.client.model.geom.builders.PartDefinition;
//import net.minecraft.client.renderer.RenderType;
//import net.minecraft.resources.ResourceLocation;
//
//public class HarpoonModel extends Model {
//    public static final ModelLayerLocation HARPOON_LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("createfisheryindustry", "harpoon"), "main");
//    private final ModelPart root;
//
//    public HarpoonModel(ModelPart root) {
//        super(RenderType::entityTranslucent);
//        this.root = root;
//    }
//
//    public static LayerDefinition createBodyLayer() {
//        MeshDefinition meshdefinition = new MeshDefinition();
//        PartDefinition partdefinition = meshdefinition.getRoot();
//
//
//
//        return LayerDefinition.create(meshdefinition, 32, 32);
//    }
//
//    @Override
//    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
//        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
//    }
//}