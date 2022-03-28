package net.coderbot.iris.compat.sodium.impl.vertex_format.terrain_xhfp;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.gfx.api.array.attribute.VertexAttributeFormat;
import net.caffeinemc.gfx.api.array.attribute.VertexFormat;
import net.caffeinemc.sodium.render.terrain.format.TerrainMeshAttribute;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexSink;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.caffeinemc.sodium.render.vertex.VertexSink;
import net.caffeinemc.sodium.render.vertex.buffer.VertexBufferView;
import net.caffeinemc.sodium.render.vertex.type.BlittableVertexType;
import net.coderbot.iris.compat.sodium.impl.vertex_format.IrisChunkMeshAttributes;

/**
 * Like HFPModelVertexType, but extended to support Iris. The extensions aren't particularly efficient right now.
 */
public class XHFPModelVertexType implements TerrainVertexType {
	static final int STRIDE = 36;

	public static final VertexFormat<TerrainMeshAttribute> VERTEX_FORMAT = VertexFormat.builder(TerrainMeshAttribute.class, STRIDE)
		.addElement(TerrainMeshAttribute.POSITION, 0, VertexAttributeFormat.UNSIGNED_SHORT, 3, false, false)
		.addElement(TerrainMeshAttribute.COLOR, 8, VertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
		.addElement(TerrainMeshAttribute.BLOCK_TEXTURE, 12, VertexAttributeFormat.UNSIGNED_SHORT, 2, false, false)
		.addElement(TerrainMeshAttribute.LIGHT_TEXTURE, 16, VertexAttributeFormat.UNSIGNED_SHORT, 2, true, false)
		.addElement(IrisChunkMeshAttributes.MID_TEX_COORD, 20, VertexAttributeFormat.UNSIGNED_SHORT, 2, true, false)
			.addElement(IrisChunkMeshAttributes.TANGENT, 24, VertexAttributeFormat.BYTE, 4, true, false)
			.addElement(IrisChunkMeshAttributes.NORMAL, 28, VertexAttributeFormat.BYTE, 3, true, false)
			.addElement(IrisChunkMeshAttributes.BLOCK_ID, 32, VertexAttributeFormat.UNSIGNED_SHORT, 2, false, false)
			.build();

	private static final int POSITION_MAX_VALUE = 65536;
	private static final int TEXTURE_MAX_VALUE = 65536;

	private static final float MODEL_ORIGIN = 8.0f;
	private static final float MODEL_RANGE = 32.0f;
	private static final float MODEL_SCALE = MODEL_RANGE / POSITION_MAX_VALUE;

	private static final float MODEL_SCALE_INV = POSITION_MAX_VALUE / MODEL_RANGE;

	private static final float TEXTURE_SCALE = (1.0f / TEXTURE_MAX_VALUE);

	@Override
	public TerrainVertexSink createFallbackWriter(VertexConsumer consumer) {
		throw new UnsupportedOperationException();
	}

    @Override
    public TerrainVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
        return direct ? new XHFPModelVertexBufferWriterUnsafe(buffer) : new XHFPModelVertexBufferWriterNio(buffer);
    }

	@Override
	public BlittableVertexType<TerrainVertexSink> asBlittable() {
		return this;
	}

	@Override
	public VertexFormat<TerrainMeshAttribute> getCustomVertexFormat() {
		return VERTEX_FORMAT;
	}

	@Override
	public float getTextureScale() {
		return TEXTURE_SCALE;
	}

	@Override
	public float getPositionScale() {
		return MODEL_SCALE;
	}

	@Override
	public float getPositionOffset() {
		return -MODEL_ORIGIN;
	}

	static short encodeBlockTexture(float value) {
		return (short) (value * TEXTURE_MAX_VALUE);
	}

	static float decodeBlockTexture(short raw) {
		return (raw & 0xFFFF) * TEXTURE_SCALE;
	}

	static short encodePosition(float v) {
		return (short) ((MODEL_ORIGIN + v) * MODEL_SCALE_INV);
	}

	static float decodePosition(short raw) {
		return (raw & 0xFFFF) * MODEL_SCALE - MODEL_ORIGIN;
	}

	static int encodeLightMapTexCoord(int light) {
		int r = light;

		// Mask off coordinate values outside 0..255
		r &= 0x00FF_00FF;

		// Light coordinates are normalized values, so upcasting requires a shift
		// Scale the coordinates from the range of 0..255 (unsigned byte) into 0..65535 (unsigned short)
		r <<= 8;

		// Add a half-texel offset to each coordinate so we sample from the center of each texel
		r += 0x0800_0800;

		return r;
	}
}
