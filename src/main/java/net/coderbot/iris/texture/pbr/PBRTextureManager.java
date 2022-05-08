package net.coderbot.iris.texture.pbr;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.rendertarget.NativeImageBackedSingleColorTexture;
import net.coderbot.iris.texture.TextureTracker;
import net.coderbot.iris.texture.pbr.loader.PBRTextureLoader;
import net.coderbot.iris.texture.pbr.loader.PBRTextureLoader.PBRTextureConsumer;
import net.coderbot.iris.texture.pbr.loader.PBRTextureLoaderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.NotNull;

public class PBRTextureManager {
	public static final PBRTextureManager INSTANCE = new PBRTextureManager();

	public static final boolean DEBUG = System.getProperty("iris.pbr.debug") != null;

	private final Int2ObjectMap<PBRTextureHolder> holders = new Int2ObjectOpenHashMap<>();
	private final PBRTextureConsumerImpl consumer = new PBRTextureConsumerImpl();

	private NativeImageBackedSingleColorTexture defaultNormalTexture;
	private NativeImageBackedSingleColorTexture defaultSpecularTexture;
	// Not PBRTextureHolderImpl to directly reference fields
	private final PBRTextureHolder defaultHolder = new PBRTextureHolder() {
		@Override
		public @NotNull AbstractTexture getNormalTexture() {
			return defaultNormalTexture;
		}

		@Override
		public @NotNull AbstractTexture getSpecularTexture() {
			return defaultSpecularTexture;
		}
	};

	private PBRTextureManager() {
	}

	public void init() {
		defaultNormalTexture = new NativeImageBackedSingleColorTexture(PBRType.NORMAL.getDefaultValue());
		defaultSpecularTexture = new NativeImageBackedSingleColorTexture(PBRType.SPECULAR.getDefaultValue());
	}

	public PBRTextureHolder getHolder(int id) {
		PBRTextureHolder holder = holders.get(id);
		if (holder == null) {
			return defaultHolder;
		}
		return holder;
	}

	public PBRTextureHolder getOrLoadHolder(int id) {
		PBRTextureHolder holder = holders.get(id);
		if (holder == null) {
			holder = loadHolder(id);
			holders.put(id, holder);
		}
		return holder;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private PBRTextureHolder loadHolder(int id) {
		try {
			AbstractTexture texture = TextureTracker.INSTANCE.getTexture(id);
			if (texture != null) {
				Class<? extends AbstractTexture> clazz = texture.getClass();
				PBRTextureLoader loader = PBRTextureLoaderRegistry.INSTANCE.getLoader(clazz);
				if (loader != null) {
					loader.load(texture, Minecraft.getInstance().getResourceManager(), consumer);

					AbstractTexture normalTexture = consumer.normalTexture;
					AbstractTexture specularTexture = consumer.specularTexture;
					if (normalTexture == null && specularTexture == null) {
						return defaultHolder;
					}
					if (normalTexture == null) {
						normalTexture = defaultNormalTexture;
					}
					if (specularTexture == null) {
						specularTexture = defaultSpecularTexture;
					}
					return new PBRTextureHolderImpl(normalTexture, specularTexture);
				}
			}
		} catch (Exception e) {
			Iris.logger.debug("Failed to load PBR textures for texture " + id, e);
		} finally {
			consumer.clear();
		}
		return defaultHolder;
	}

	public void onDeleteTexture(int id) {
		PBRTextureHolder holder = holders.remove(id);
		if (holder != null) {
			closeHolder(holder);
		}
	}

	public void clear() {
		for (PBRTextureHolder holder : holders.values()) {
			if (holder != defaultHolder) {
				closeHolder(holder);
			}
		}
		holders.clear();
	}

	public void close() {
		clear();
		defaultNormalTexture.close();
		defaultSpecularTexture.close();
	}

	private void closeHolder(PBRTextureHolder holder) {
		AbstractTexture normalTexture = holder.getNormalTexture();
		AbstractTexture specularTexture = holder.getSpecularTexture();
		if (normalTexture != defaultNormalTexture) {
			closeTexture(normalTexture);
		}
		if (specularTexture != defaultSpecularTexture) {
			closeTexture(specularTexture);
		}
	}

	private static void closeTexture(AbstractTexture texture) {
		try {
			texture.close();
		} catch (Exception e) {
			//
		}
		texture.releaseId();
	}

	private static class PBRTextureConsumerImpl implements PBRTextureConsumer {
		private AbstractTexture normalTexture;
		private AbstractTexture specularTexture;

		@Override
		public void acceptNormalTexture(AbstractTexture texture) {
			normalTexture = texture;
		}

		@Override
		public void acceptSpecularTexture(AbstractTexture texture) {
			specularTexture = texture;
		}

		private void clear() {
			normalTexture = null;
			specularTexture = null;
		}
	}

	private static class PBRTextureHolderImpl implements PBRTextureHolder {
		private final AbstractTexture normalTexture;
		private final AbstractTexture specularTexture;

		public PBRTextureHolderImpl(AbstractTexture normalTexture, AbstractTexture specularTexture) {
			this.normalTexture = normalTexture;
			this.specularTexture = specularTexture;
		}

		@Override
		public @NotNull AbstractTexture getNormalTexture() {
			return normalTexture;
		}

		@Override
		public @NotNull AbstractTexture getSpecularTexture() {
			return specularTexture;
		}
	}
}
