package gigaherz.eyes.client.objentity;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.IModelConfiguration;
import net.minecraftforge.client.model.geometry.IModelGeometryPart;
import net.minecraftforge.client.model.obj.MaterialLibrary;
import net.minecraftforge.client.model.obj.OBJModel;

import javax.annotation.Nullable;
import java.util.List;

public class OBJEntityModel<T extends Entity> extends EntityModel<T>
{
    public static <T extends Entity> OBJEntityModel<T> fromObjModel(OBJModel model)
    {
        OBJEntityModel<T> eModel = new OBJEntityModel<>();

        for(IModelGeometryPart part : model.getParts())
        {
            String name = part.name();

            //ModelBuilder b = new ModelBuilder

            //part.addQuads();
        }

        return eModel;
    }


    @Override
    public void setupAnim(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {

    }

    @Override
    public void renderToBuffer(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn, float red, float green, float blue, float alpha)
    {

    }

    private static class Piece extends ModelRenderer
    {
        private final List<BakedQuad> quads;
        private final MaterialLibrary.Material material;

        public Piece(Model model, List<BakedQuad> quads, MaterialLibrary.Material material)
        {
            super(model);
            this.quads = quads;
            this.material = material;
        }
    }

    private static class ModelBuilder<T extends Entity> implements IModelBuilder<ModelBuilder<T>>
    {
        private final MaterialLibrary.Material material;
        private final OBJEntityModel<T> model;
        private final List<BakedQuad> quads = Lists.newArrayList();

        private ModelBuilder(OBJEntityModel<T> model, MaterialLibrary.Material material)
        {
            this.model = model;
            this.material = material;
        }

        @Override
        public ModelBuilder<T> addFaceQuad(Direction facing, BakedQuad quad)
        {
            quads.add(quad);
            return this;
        }

        @Override
        public ModelBuilder<T> addGeneralQuad(BakedQuad quad)
        {
            quads.add(quad);
            return this;
        }

        @Override
        public IBakedModel build()
        {
            return null;
        }

        public Piece makePiece()
        {
            return new Piece(model, quads, material);
        }
    }

    private static class DummyConfiguration implements IModelConfiguration
    {
        public static final RenderMaterial BLANK_MATERIAL = new RenderMaterial(AtlasTexture.LOCATION_BLOCKS, new ResourceLocation("forge:white"));

        @Nullable
        @Override
        public IUnbakedModel getOwnerModel()
        {
            return null;
        }

        @Override
        public String getModelName()
        {
            return "dummy";
        }

        @Override
        public boolean isTexturePresent(String name)
        {
            return false;
        }

        @Override
        public RenderMaterial resolveTexture(String name)
        {
            return BLANK_MATERIAL;
        }

        @Override
        public boolean isShadedInGui()
        {
            return false;
        }

        @Override
        public boolean isSideLit()
        {
            return false;
        }

        @Override
        public boolean useSmoothLighting()
        {
            return false;
        }

        @Override
        public ItemCameraTransforms getCameraTransforms()
        {
            return ItemCameraTransforms.NO_TRANSFORMS;
        }

        @Override
        public IModelTransform getCombinedTransform()
        {
            return ModelRotation.X0_Y0;
        }
    }
}
