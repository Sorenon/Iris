package net.coderbot.iris.mixin.texture.pbr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.PngInfo;
import com.mojang.datafixers.util.Pair;

import net.coderbot.iris.Iris;
import net.coderbot.iris.mixin.texture.TextureAtlasSpriteAccessor;
import net.coderbot.iris.texture.pbr.PBRAtlasTextureHolder;
import net.coderbot.iris.texture.pbr.PBRAtlasSpriteHolder;
import net.coderbot.iris.texture.pbr.PBRType;
import net.coderbot.iris.texture.pbr.TextureAtlasExtension;
import net.coderbot.iris.texture.pbr.TextureAtlasSpriteExtension;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

@Mixin(TextureAtlas.class)
public abstract class MixinTextureAtlas extends AbstractTexture implements TextureAtlasExtension {
	@Unique
	private PBRAtlasTextureHolder pbrHolder;

	@Inject(method = "prepareToStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/stream/Stream;Lnet/minecraft/util/profiling/ProfilerFiller;I)Lnet/minecraft/client/renderer/texture/TextureAtlas$Preparations;", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=loading"))
	private void beforeLoadingSprites(ResourceManager resourceManager, Stream<ResourceLocation> spriteIds, ProfilerFiller profiler, int mipLevel, CallbackInfoReturnable<TextureAtlas.Preparations> cir) {
		if (pbrHolder != null) {
			pbrHolder.clear();
		}
	}

	@Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite$Info;IIIII)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;", at = @At(value = "RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
	private void onReturnLoad(ResourceManager resourceManager, TextureAtlasSprite.Info spriteInfo, int atlasWidth, int atlasHeight, int maxLevel, int x, int y, CallbackInfoReturnable<TextureAtlasSprite> cir, ResourceLocation imageLocation) {
		TextureAtlasSprite sprite = cir.getReturnValue();
		if (sprite == null) {
			return;
		}

		createPBRSprite(sprite, imageLocation, resourceManager, spriteInfo, atlasWidth, atlasHeight, maxLevel, x, y, PBRType.NORMAL);
		createPBRSprite(sprite, imageLocation, resourceManager, spriteInfo, atlasWidth, atlasHeight, maxLevel, x, y, PBRType.SPECULAR);
	}

	private void createPBRSprite(TextureAtlasSprite sprite, ResourceLocation imageLocation, ResourceManager resourceManager, TextureAtlasSprite.Info spriteInfo, int atlasWidth, int atlasHeight, int maxLevel, int x, int y, PBRType pbrType) {
		TextureAtlas self = (TextureAtlas) (Object) this;

		ResourceLocation spriteId = spriteInfo.name();
		ResourceLocation pbrSpriteId = new ResourceLocation(spriteId.getNamespace(), spriteId.getPath() + pbrType.getSuffix());
		ResourceLocation pbrImageLocation = pbrType.appendToFileLocation(imageLocation);

		TextureAtlasSprite pbrSprite = null;
		try {
			Resource resource = resourceManager.getResource(pbrImageLocation);

			try {
				InputStream inputStream = resource.getInputStream();
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				int length;
				while ((length = inputStream.read(buf)) > 0) {
					byteStream.write(buf, 0, length);
				}

				PngInfo pngInfo = new PngInfo(resource.toString(), new ByteArrayInputStream(byteStream.toByteArray()));
				AnimationMetadataSection animationMetadata = resource.getMetadata(AnimationMetadataSection.SERIALIZER);
				if (animationMetadata == null) {
					animationMetadata = AnimationMetadataSection.EMPTY;
				}

				Pair<Integer, Integer> pair = animationMetadata.getFrameSize(pngInfo.width, pngInfo.height);
				TextureAtlasSprite.Info pbrSpriteInfo = new TextureAtlasSprite.Info(pbrSpriteId, pair.getFirst(), pair.getSecond(), animationMetadata);

				NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(byteStream.toByteArray()));
				pbrSprite = TextureAtlasSpriteAccessor.callInit(self, pbrSpriteInfo, maxLevel, atlasWidth, atlasHeight, x, y, nativeImage);
			} catch (Throwable t) {
				if (resource != null) {
					try {
						resource.close();
					} catch (Throwable t1) {
						t.addSuppressed(t1);
					}
				}

				throw t;
			}

			if (resource != null) {
				resource.close();
			}
		} catch (FileNotFoundException e) {
			//
		} catch (RuntimeException e) {
			Iris.logger.error("Unable to parse metadata from {} : {}", pbrImageLocation, e);
		} catch (IOException e) {
			Iris.logger.error("Unable to load {} : {}", pbrImageLocation, e);
		}

		if (pbrSprite != null) {
			if (pbrHolder == null) {
				pbrHolder = new PBRAtlasTextureHolder(self);
			}
			PBRAtlasSpriteHolder pbrSpriteHolder = ((TextureAtlasSpriteExtension) sprite).getOrCreatePBRSpriteHolder();

			switch (pbrType) {
			case NORMAL:
				pbrHolder.getOrCreateNormalTexture().addSprite(pbrSprite);
				pbrSpriteHolder.setNormalSprite(pbrSprite);
				break;
			case SPECULAR:
				pbrHolder.getOrCreateSpecularTexture().addSprite(pbrSprite);
				pbrSpriteHolder.setSpecularSprite(pbrSprite);
				break;
			}
		}
	}

	@Inject(method = "reload(Lnet/minecraft/client/renderer/texture/TextureAtlas$Preparations;)V", at = @At("TAIL"))
	private void onTailReload(TextureAtlas.Preparations preparations, CallbackInfo ci) {
		if (pbrHolder != null) {
			pbrHolder.reload(preparations);
			GlStateManager._bindTexture(getId()); // Restore state
		}
	}

	@Inject(method = "cycleAnimationFrames()V", at = @At("TAIL"))
	private void onTailCycleAnimationFrames(CallbackInfo ci) {
		if (pbrHolder != null) {
			pbrHolder.cycleAnimationFrames();
		}
	}

	@Override
	public void releaseId() {
		super.releaseId();
		if (pbrHolder != null) {
			pbrHolder.releaseIds();
		}
	}

	@Override
	public void close() {
		super.close();
		if (pbrHolder != null) {
			pbrHolder.close();
		}
	}

//	@Inject(method = "releaseId()V", at = @At("TAIL"))
//	private void onTailReleaseId(CallbackInfo ci) {
//		if (pbrHolder != null) {
//			pbrHolder.releaseIds();
//		}
//	}
//
//	@Inject(method = "close()V", at = @At("TAIL"), remap = false)
//	private void onTailClose(CallbackInfo ci) {
//		if (pbrHolder != null) {
//			pbrHolder.close();
//		}
//	}

	@Override
	public boolean hasPBRHolder() {
		return pbrHolder != null;
	}

	@Override
	public PBRAtlasTextureHolder getPBRHolder() {
		return pbrHolder;
	}
}