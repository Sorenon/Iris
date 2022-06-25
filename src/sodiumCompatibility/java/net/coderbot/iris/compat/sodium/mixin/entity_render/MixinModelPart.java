package net.coderbot.iris.compat.sodium.mixin.entity_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.jellysquid.mods.sodium.client.model.ModelCuboidAccessor;
import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.VertexDrain;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.QuadVertexSink;
import me.jellysquid.mods.sodium.client.util.Norm3b;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.client.util.math.Matrix3fExtended;
import me.jellysquid.mods.sodium.client.util.math.Matrix4fExtended;
import me.jellysquid.mods.sodium.client.util.math.MatrixUtil;
import net.coderbot.iris.compat.sodium.impl.vertex_format.entity_xhfp.EntityVertexWriter;
import net.coderbot.iris.vertices.EntityVelocityInterface;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ModelPart.class)
public class MixinModelPart {
	private static final float NORM = 1.0F / 16.0F;

	@Shadow
	@Final
	private ObjectList<ModelPart.Cube> cubes;

	/**
	 * @author JellySquid
	 * @reason Use optimized vertex writer, avoid allocations, use quick matrix transformations
	 */
	@Overwrite
	private void compile(PoseStack.Pose pose, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha) {
		Matrix3fExtended normalExt = MatrixUtil.getExtendedMatrix(pose.normal());
		Matrix4fExtended modelExt = MatrixUtil.getExtendedMatrix(pose.pose());

		QuadVertexSink drain = VertexDrain.of(vertexConsumer).createSink(VanillaVertexTypes.QUADS);

		drain.ensureCapacity(this.cubes.size() * 6 * 4);

		int color = ColorABGR.pack(red, green, blue, alpha);

		for (ModelPart.Cube cuboid : this.cubes) {
			for (int polygonValue = 0; polygonValue < ((ModelCuboidAccessor) cuboid).getQuads().length; polygonValue++) {
				ModelPart.Polygon quad = ((ModelCuboidAccessor) cuboid).getQuads()[polygonValue];
				float normX = normalExt.transformVecX(quad.normal);
				float normY = normalExt.transformVecY(quad.normal);
				float normZ = normalExt.transformVecZ(quad.normal);

				int norm = Norm3b.pack(normX, normY, normZ);

				for (int vertexValue = 0; vertexValue < quad.vertices.length; vertexValue++) {
					ModelPart.Vertex vertex = quad.vertices[vertexValue];
					Vector3f pos = vertex.pos;

					float x1 = pos.x() * NORM;
					float y1 = pos.y() * NORM;
					float z1 = pos.z() * NORM;

					float x2 = modelExt.transformVecX(x1, y1, z1);
					float y2 = modelExt.transformVecY(x1, y1, z1);
					float z2 = modelExt.transformVecZ(x1, y1, z1);

					if (drain instanceof EntityVertexWriter) {
						((EntityVelocityInterface) cuboid).getEntityVelocity()[polygonValue][vertexValue].setPos(x1, y1, z1);
						((EntityVertexWriter) drain).setVelocity(((EntityVelocityInterface) cuboid).getEntityVelocity()[polygonValue][vertexValue]);
					}

					drain.writeQuad(x2, y2, z2, color, vertex.u, vertex.v, light, overlay, norm);
				}
			}
		}

		if (drain instanceof EntityVertexWriter) {
			((EntityVertexWriter) drain).resetVelocity();
		}

		drain.flush();
	}
}