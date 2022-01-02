package com.tom.cpm.client;

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.tom.cpm.CommonProxy;
import com.tom.cpm.mixinplugin.OFDetector;
import com.tom.cpm.shared.config.ConfigKeys;
import com.tom.cpm.shared.config.ModConfig;
import com.tom.cpm.shared.config.Player;
import com.tom.cpm.shared.definition.ModelDefinition;
import com.tom.cpm.shared.editor.gui.EditorGui;
import com.tom.cpm.shared.gui.GestureGui;
import com.tom.cpm.shared.gui.SettingsGui;
import com.tom.cpm.shared.model.RenderManager;
import com.tom.cpm.shared.network.NetHandler;
import com.tom.cpm.shared.util.Log;

import io.netty.buffer.Unpooled;

public class ClientProxy extends CommonProxy {
	public static final ResourceLocation DEFAULT_CAPE = new ResourceLocation("cpm:textures/template/cape.png");
	public static boolean optifineLoaded;
	public static ClientProxy INSTANCE = null;
	public static MinecraftObject mc;
	private Minecraft minecraft;
	public RenderManager<GameProfile, net.minecraft.world.entity.player.Player, Model, MultiBufferSource> manager;
	public NetHandler<ResourceLocation, CompoundTag, net.minecraft.world.entity.player.Player, FriendlyByteBuf, ClientPacketListener> netHandler;

	@Override
	public void init() {
		super.init();
		INSTANCE = this;
		minecraft = Minecraft.getInstance();
		mc = new MinecraftObject(minecraft);
		optifineLoaded = OFDetector.doApply();
		if(optifineLoaded)Log.info("Optifine detected, enabling optifine compatibility");
		MinecraftForge.EVENT_BUS.register(this);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerShaders);
		KeyBindings.init();
		manager = new RenderManager<>(mc.getPlayerRenderManager(), mc.getDefinitionLoader(), net.minecraft.world.entity.player.Player::getGameProfile);
		netHandler = new NetHandler<>(ResourceLocation::new);
		netHandler.setNewNbt(CompoundTag::new);
		netHandler.setNewPacketBuffer(() -> new FriendlyByteBuf(Unpooled.buffer()));
		netHandler.setWriteCompound(FriendlyByteBuf::writeNbt, FriendlyByteBuf::readNbt);
		netHandler.setNBTSetters(CompoundTag::putBoolean, CompoundTag::putByteArray, CompoundTag::putFloat);
		netHandler.setNBTGetters(CompoundTag::getBoolean, CompoundTag::getByteArray, CompoundTag::getFloat);
		netHandler.setContains(CompoundTag::contains);
		netHandler.setExecutor(() -> minecraft);
		netHandler.setSendPacket((c, rl, pb) -> c.send(new ServerboundCustomPayloadPacket(rl, pb)), null);
		netHandler.setPlayerToLoader(net.minecraft.world.entity.player.Player::getGameProfile);
		netHandler.setReadPlayerId(FriendlyByteBuf::readVarInt, id -> {
			Entity ent = Minecraft.getInstance().level.getEntity(id);
			if(ent instanceof net.minecraft.world.entity.player.Player) {
				return (net.minecraft.world.entity.player.Player) ent;
			}
			return null;
		});
		netHandler.setGetClient(() -> minecraft.player);
		netHandler.setGetNet(c -> ((LocalPlayer)c).connection);
		ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class, () -> new ConfigGuiHandler.ConfigGuiFactory((mc, scr) -> new GuiImpl(SettingsGui::new, scr)));
	}

	@SubscribeEvent
	public void playerRenderPre(RenderPlayerEvent.Pre event) {
		manager.bindPlayer(event.getPlayer(), event.getMultiBufferSource());
	}

	@SubscribeEvent
	public void playerRenderPost(RenderPlayerEvent.Post event) {
		manager.tryUnbind();
	}

	@SubscribeEvent
	public void initGui(ScreenEvent.InitScreenEvent.Post evt) {
		if((evt.getScreen() instanceof TitleScreen && ModConfig.getCommonConfig().getSetBoolean(ConfigKeys.TITLE_SCREEN_BUTTON, true)) ||
				evt.getScreen() instanceof SkinCustomizationScreen) {
			Button btn = new Button(0, 0, () -> Minecraft.getInstance().setScreen(new GuiImpl(EditorGui::new, evt.getScreen())));
			evt.addListener(btn);
			((List) evt.getScreen().children()).add(btn);
		}
	}

	public void renderHand(MultiBufferSource buffer) {
		manager.bindHand(Minecraft.getInstance().player, buffer);
	}

	public void renderSkull(Model skullModel, GameProfile profile, MultiBufferSource buffer) {
		manager.bindSkull(profile, buffer, skullModel);
	}

	public void renderElytra(net.minecraft.world.entity.player.Player player, MultiBufferSource buffer, ElytraModel<LivingEntity> model) {
		manager.bindElytra(player, buffer, model);
	}

	public void renderArmor(HumanoidModel<LivingEntity> modelArmor, HumanoidModel<LivingEntity> modelLeggings,
			net.minecraft.world.entity.player.Player player, MultiBufferSource bufferIn) {
		manager.bindArmor(player, bufferIn, modelArmor, 1);
		manager.bindArmor(player, bufferIn, modelLeggings, 2);
	}

	@SubscribeEvent
	public void renderTick(RenderTickEvent evt) {
		if(evt.phase == Phase.START) {
			mc.getPlayerRenderManager().getAnimationEngine().update(evt.renderTickTime);
		}
	}

	@SubscribeEvent
	public void clientTick(ClientTickEvent evt) {
		if(evt.phase == Phase.START && !minecraft.isPaused()) {
			mc.getPlayerRenderManager().getAnimationEngine().tick();
		}
		if (minecraft.player == null || evt.phase == Phase.START)
			return;

		if(KeyBindings.gestureMenuBinding.consumeClick()) {
			Minecraft.getInstance().setScreen(new GuiImpl(GestureGui::new, null));
		}

		if(KeyBindings.renderToggleBinding.consumeClick()) {
			Player.setEnableRendering(!Player.isEnableRendering());
		}

		for (Entry<Integer, KeyMapping> e : KeyBindings.quickAccess.entrySet()) {
			if(e.getValue().consumeClick()) {
				mc.getPlayerRenderManager().getAnimationEngine().onKeybind(e.getKey());
			}
		}
	}

	@SubscribeEvent
	public void openGui(ScreenOpenEvent openGui) {
		if(openGui.getScreen() == null && minecraft.screen instanceof GuiImpl.Overlay) {
			openGui.setScreen(((GuiImpl.Overlay) minecraft.screen).getGui());
		}
		if(openGui.getScreen() instanceof TitleScreen && EditorGui.doOpenEditor()) {
			openGui.setScreen(new GuiImpl(EditorGui::new, openGui.getScreen()));
		}
	}

	private void registerShaders(RegisterShadersEvent evt) {
		try {
			evt.registerShader(new ShaderInstance(evt.getResourceManager(), new ResourceLocation("cpm", "rendertype_entity_translucent_cull_no_light"), DefaultVertexFormat.NEW_ENTITY), s -> CustomRenderTypes.entityTranslucentCullNoLightShaderProgram = s);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static class Button extends net.minecraft.client.gui.components.Button {

		public Button(int x, int y, Runnable r) {
			super(x, y, 100, 20, new TranslatableComponent("button.cpm.open_editor"), b -> r.run());
		}

	}

	@SubscribeEvent
	public void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent evt) {
		mc.getDefinitionLoader().clearServerData();
	}

	public void unbind(Model model) {
		manager.tryUnbind(model);
	}

	//Copy from CapeLayer
	public static void renderCape(PoseStack matrixStackIn, VertexConsumer buffer, int packedLightIn,
			AbstractClientPlayer playerIn, float partialTicks, PlayerModel<AbstractClientPlayer> model,
			ModelDefinition modelDefinition) {
		matrixStackIn.pushPose();

		float f1, f2, f3;

		if(playerIn != null) {
			double d0 = Mth.lerp(partialTicks, playerIn.xCloakO,
					playerIn.xCloak)
					- Mth.lerp(partialTicks, playerIn.xo, playerIn.getX());
			double d1 = Mth.lerp(partialTicks, playerIn.yCloakO,
					playerIn.yCloak)
					- Mth.lerp(partialTicks, playerIn.yo, playerIn.getY());
			double d2 = Mth.lerp(partialTicks, playerIn.zCloakO,
					playerIn.zCloak)
					- Mth.lerp(partialTicks, playerIn.zo, playerIn.getZ());
			float f = playerIn.yBodyRotO
					+ (playerIn.yBodyRot - playerIn.yBodyRotO);
			double d3 = Mth.sin(f * 0.017453292F);
			double d4 = (-Mth.cos(f * 0.017453292F));
			f1 = (float) d1 * 10.0F;
			f1 = Mth.clamp(f1, -6.0F, 32.0F);
			f2 = (float) (d0 * d3 + d2 * d4) * 100.0F;
			f2 = Mth.clamp(f2, 0.0F, 150.0F);
			f3 = (float) (d0 * d4 - d2 * d3) * 100.0F;
			f3 = Mth.clamp(f3, -20.0F, 20.0F);
			if (f2 < 0.0F) {
				f2 = 0.0F;
			}

			float f4 = Mth.lerp(partialTicks, playerIn.oBob, playerIn.bob);
			f1 += Mth.sin(Mth.lerp(partialTicks, playerIn.walkDistO,
					playerIn.walkDist) * 6.0F) * 32.0F * f4;
			if (playerIn.isCrouching()) {
				f1 += 25.0F;
			}
			if (playerIn.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
				if (playerIn.isCrouching()) {
					model.cloak.z = 1.4F + 0.125F * 3;
					model.cloak.y = 1.85F + 1 - 0.125F * 4;
				} else {
					model.cloak.z = 0.0F + 0.125F * 16f;
					model.cloak.y = 0.0F;
				}
			} else if (playerIn.isCrouching()) {
				model.cloak.z = 0.3F + 0.125F * 16f;
				model.cloak.y = 0.8F + 0.3f;
			} else {
				model.cloak.z = -1.1F + 0.125F * 32f;
				model.cloak.y = -0.85F + 1;
			}
		} else {
			f1 = 0;
			f2 = 0;
			f3 = 0;
		}

		model.cloak.xRot = (float) -Math.toRadians(6.0F + f2 / 2.0F + f1);
		model.cloak.yRot = (float) Math.toRadians(180.0F - f3 / 2.0F);
		model.cloak.zRot = (float) Math.toRadians(f3 / 2.0F);
		mc.getPlayerRenderManager().setModelPose(model);
		model.cloak.xRot = 0;
		model.cloak.yRot = 0;
		model.cloak.zRot = 0;
		model.renderCloak(matrixStackIn, buffer, packedLightIn, OverlayTexture.NO_OVERLAY);
		matrixStackIn.popPose();
	}
}