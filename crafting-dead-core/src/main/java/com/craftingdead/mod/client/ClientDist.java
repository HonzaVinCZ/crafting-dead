package com.craftingdead.mod.client;

import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import org.lwjgl.glfw.GLFW;
import com.craftingdead.mod.CraftingDead;
import com.craftingdead.mod.IModDist;
import com.craftingdead.mod.capability.ModCapabilities;
import com.craftingdead.mod.capability.SerializableProvider;
import com.craftingdead.mod.capability.player.ClientPlayer;
import com.craftingdead.mod.capability.player.DefaultPlayer;
import com.craftingdead.mod.capability.player.IPlayer;
import com.craftingdead.mod.client.crosshair.CrosshairManager;
import com.craftingdead.mod.client.gui.IngameGui;
import com.craftingdead.mod.client.gui.screen.inventory.ModInventoryScreen;
import com.craftingdead.mod.client.model.GunModel;
import com.craftingdead.mod.client.model.PerspectiveAwareModel;
import com.craftingdead.mod.client.renderer.entity.AdvancedZombieRenderer;
import com.craftingdead.mod.client.renderer.entity.CorpseRenderer;
import com.craftingdead.mod.client.renderer.entity.GrenadeRenderer;
import com.craftingdead.mod.client.renderer.entity.SupplyDropRenderer;
import com.craftingdead.mod.client.renderer.entity.player.CustomPlayerRenderer;
import com.craftingdead.mod.client.renderer.entity.player.layer.ClothingLayer;
import com.craftingdead.mod.client.renderer.entity.player.layer.EquipmentLayer;
import com.craftingdead.mod.entity.ModEntityTypes;
import com.craftingdead.mod.inventory.InventorySlotType;
import com.craftingdead.mod.inventory.container.ModContainerTypes;
import com.craftingdead.mod.item.AttachmentItem;
import com.craftingdead.mod.item.Color;
import com.craftingdead.mod.item.GunItem;
import com.craftingdead.mod.item.ModItems;
import com.craftingdead.mod.item.PaintItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.entity.model.BipedModel.ArmPose;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class ClientDist implements IModDist {

  public static final KeyBinding RELOAD =
      new KeyBinding("key.reload", GLFW.GLFW_KEY_R, "key.categories.gameplay");
  public static final KeyBinding TOGGLE_FIRE_MODE =
      new KeyBinding("key.toggle_fire_mode", GLFW.GLFW_KEY_M, "key.categories.gameplay");
  public static final KeyBinding CROUCH =
      new KeyBinding("key.crouch", GLFW.GLFW_KEY_BACKSLASH, "key.categories.gameplay");
  public static final KeyBinding OPEN_PLAYER_CONTAINER =
      new KeyBinding("key.player", GLFW.GLFW_KEY_X, "key.categories.inventory");

  private static final Minecraft minecraft = Minecraft.getInstance();

  /**
   * Random.
   */
  private static final Random random = new Random();

  private final CrosshairManager crosshairManager = new CrosshairManager();

  /**
   * Current camera velocity.
   */
  private Vec2f rotationVelocity = Vec2f.ZERO;

  /**
   * Camera velocity of last tick.
   */
  private Vec2f prevRotationVelocity = this.rotationVelocity;

  private long rollStartTime = 0;

  private float roll;

  private IngameGui ingameGui;

  public ClientDist() {
    FMLJavaModLoadingContext.get().getModEventBus().register(this);
    MinecraftForge.EVENT_BUS.register(this);

    ((IReloadableResourceManager) minecraft.getResourceManager())
        .addReloadListener(this.crosshairManager);

    this.ingameGui = new IngameGui(minecraft, this, CrosshairManager.DEFAULT_CROSSHAIR);
  }

  public void joltCamera(float accuracy) {
    float amount = ((1.0F - accuracy) * 100) / 2.5F;
    float randomAmount = random.nextBoolean() ? amount : -amount;
    this.rotationVelocity =
        new Vec2f(this.rotationVelocity.x + randomAmount, this.rotationVelocity.y - amount);
    this.rollStartTime = Util.milliTime();
    this.roll = randomAmount;
  }

  public CrosshairManager getCrosshairManager() {
    return this.crosshairManager;
  }

  public LazyOptional<ClientPlayer> getPlayer() {
    return minecraft.player != null
        ? minecraft.player.getCapability(ModCapabilities.PLAYER, null).cast()
        : LazyOptional.empty();
  }

  public IngameGui getIngameGui() {
    return this.ingameGui;
  }

  // ================================================================================
  // Mod Events
  // ================================================================================

  @SubscribeEvent
  public void handleClientSetup(FMLClientSetupEvent event) {
    ScreenManager.registerFactory(ModContainerTypes.PLAYER.get(), ModInventoryScreen::new);

    ModelLoaderRegistry
        .registerLoader(new ResourceLocation(CraftingDead.ID, "gun"), GunModel.Loader.INSTANCE);
    ModelLoaderRegistry
        .registerLoader(new ResourceLocation(CraftingDead.ID, "perspective_aware"),
            PerspectiveAwareModel.Loader.INSTANCE);

    ClientRegistry.registerKeyBinding(TOGGLE_FIRE_MODE);
    ClientRegistry.registerKeyBinding(RELOAD);
    ClientRegistry.registerKeyBinding(CROUCH);

    RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.corpse, CorpseRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.corpse, CorpseRenderer::new);
    RenderingRegistry
        .registerEntityRenderingHandler(ModEntityTypes.advancedZombie, AdvancedZombieRenderer::new);
    RenderingRegistry
        .registerEntityRenderingHandler(ModEntityTypes.fastZombie, AdvancedZombieRenderer::new);
    RenderingRegistry
        .registerEntityRenderingHandler(ModEntityTypes.tankZombie, AdvancedZombieRenderer::new);
    RenderingRegistry
        .registerEntityRenderingHandler(ModEntityTypes.weakZombie, AdvancedZombieRenderer::new);
    RenderingRegistry
        .registerEntityRenderingHandler(ModEntityTypes.supplyDrop, SupplyDropRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(ModEntityTypes.grenade, GrenadeRenderer::new);

    StartupMessageManager.addModMessage("Injecting CustomPlayerRenderer");

    try {
      CustomPlayerRenderer.inject();
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject CustomPlayerRenderer", e);
    }

    StartupMessageManager.addModMessage("Loading model layers");

    this.registerPlayerLayer(ClothingLayer::new);
    this
        .registerPlayerLayer(renderer -> new EquipmentLayer.Builder()
            .withRenderer(renderer)
            .withItemStackGetter(
                player -> player.getInventory().getStackInSlot(InventorySlotType.MELEE.getIndex()))
            .withCrouchingOrientation(true)
            .build());
    this
        .registerPlayerLayer(renderer -> new EquipmentLayer.Builder()
            .withRenderer(renderer)
            .withItemStackGetter(
                player -> player.getInventory().getStackInSlot(InventorySlotType.VEST.getIndex()))
            .withCrouchingOrientation(true)
            .build());
    this
        .registerPlayerLayer(renderer -> new EquipmentLayer.Builder()
            .withRenderer(renderer)
            .withItemStackGetter(
                player -> player.getInventory().getStackInSlot(InventorySlotType.HAT.getIndex()))
            .withHeadOrientation(true)

            // Inverts X and Y rotation. This is from Mojang, based on HeadLayer.class.
            // TODO Find a reason to not remove this line. Also, if you remove it, you will
            // need to change the json file of every helmet since the scale affects positions.
            .withArbitraryTransformation(matrix -> matrix.scale(-1F, -1F, 1F))

            .build());
    this
        .registerPlayerLayer(renderer -> new EquipmentLayer.Builder()
            .withRenderer(renderer)
            .withItemStackGetter(
                player -> player.getInventory().getStackInSlot(InventorySlotType.GUN.getIndex()))
            .withCrouchingOrientation(true)
            .build());
    this
        .registerPlayerLayer(renderer -> new EquipmentLayer.Builder()
            .withRenderer(renderer)
            .withItemStackGetter(player -> player
                .getInventory()
                .getStackInSlot(InventorySlotType.BACKPACK.getIndex()))
            .withCrouchingOrientation(true)
            .build());
  }

  /**
   * Registers a layer into {@link PlayerRenderer}. Can be used normally during
   * {@link FMLClientSetupEvent}.
   *
   * @param function - {@link Function} with a {@link PlayerRenderer} as input and a
   *        {@link LayerRenderer} as output.
   */
  public void registerPlayerLayer(
      Function<PlayerRenderer, LayerRenderer<AbstractClientPlayerEntity, PlayerModel<AbstractClientPlayerEntity>>> function) {
    // A little dirty way, blame Mojang
    minecraft.getRenderManager().getSkinMap().forEach((skin, renderer) -> {
      renderer.addLayer(function.apply(renderer));
    });
  }

  // ================================================================================
  // Forge Events
  // ================================================================================

  @SubscribeEvent
  public void handleClientTick(TickEvent.ClientTickEvent event) {
    switch (event.phase) {
      case START:
        if (minecraft.loadingGui == null
            && (minecraft.currentScreen == null || minecraft.currentScreen.passEvents)) {
          while (TOGGLE_FIRE_MODE.isPressed()) {
            minecraft.player
                .getCapability(ModCapabilities.PLAYER)
                .ifPresent(player -> player.toggleFireMode(true));
          }
          while (OPEN_PLAYER_CONTAINER.isPressed()) {
            minecraft.player
                .getCapability(ModCapabilities.PLAYER)
                .ifPresent(IPlayer::openPlayerContainer);
          }
        }
        break;
      default:
        break;
    }
  }

  @SubscribeEvent
  public void handleRawMouse(InputEvent.RawMouseEvent event) {
    if (minecraft.getConnection() != null && minecraft.currentScreen == null) {
      if (minecraft.gameSettings.keyBindAttack.matchesMouseKey(event.getButton())) {
        boolean triggerPressed = event.getAction() == GLFW.GLFW_PRESS;
        this.getPlayer().ifPresent(player -> {
          if (player.getEntity().getHeldItemMainhand().getItem() instanceof GunItem) {
            event.setCanceled(true);
            player.setTriggerPressed(triggerPressed, true);
          }
        });
      }
    }
  }

  @SubscribeEvent
  public void handleAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
    if (event.getObject() instanceof ClientPlayerEntity) {
      event
          .addCapability(new ResourceLocation(CraftingDead.ID, "player"),
              new SerializableProvider<>(new ClientPlayer((ClientPlayerEntity) event.getObject()),
                  ModCapabilities.PLAYER));
    } else if (event.getObject() instanceof AbstractClientPlayerEntity) {
      event
          .addCapability(new ResourceLocation(CraftingDead.ID, "player"),
              new SerializableProvider<>(
                  new DefaultPlayer<>((AbstractClientPlayerEntity) event.getObject()),
                  ModCapabilities.PLAYER));
    }
  }

  @SubscribeEvent
  public void handleRenderLiving(RenderLivingEvent.Pre<?, BipedModel<?>> event) {

    /*
     * Renders the ArmPose as BOW_AND_ARROW if the living entity is holding a gun.
     */

    ItemStack heldStack = event.getEntity().getHeldItemMainhand();
    if (event.getRenderer().getEntityModel() instanceof BipedModel
        && heldStack.getItem() instanceof GunItem) {
      BipedModel<?> model = event.getRenderer().getEntityModel();
      switch (event.getEntity().getPrimaryHand()) {
        case LEFT:
          model.leftArmPose = ArmPose.BOW_AND_ARROW;
          break;
        case RIGHT:
          model.rightArmPose = ArmPose.BOW_AND_ARROW;
          break;
        default:
          break;
      }
    }

  }

  @SubscribeEvent
  public void handleRenderGameOverlayPre(RenderGameOverlayEvent.Pre event) {
    switch (event.getType()) {
      case ALL:
        this.ingameGui
            .renderGameOverlay(event.getPartialTicks(), event.getWindow().getScaledWidth(),
                event.getWindow().getScaledHeight());
        break;
      case CROSSHAIRS:
        this.getPlayer().ifPresent(player -> {
          ClientPlayerEntity playerEntity = player.getEntity();
          ItemStack heldStack = playerEntity.getHeldItemMainhand();

          event
              .setCanceled(heldStack
                  .getCapability(ModCapabilities.ACTION)
                  .map(action -> action.isActive(playerEntity))
                  .orElse(false) || player.isAiming());

          if (!event.isCanceled()) {
            heldStack.getCapability(ModCapabilities.GUN_CONTROLLER).ifPresent(gunController -> {
              event.setCanceled(true);

              boolean showCrosshair =
                  gunController.getGun().map(gun -> gun.getCrosshairVisibility()).orElse(false);

              if (showCrosshair) {
                this.ingameGui
                    .renderCrosshairs(gunController.getAccuracy(playerEntity, heldStack),
                        event.getPartialTicks(), event.getWindow().getScaledWidth(),
                        event.getWindow().getScaledHeight());
              }
            });
          }
        });
        break;
      default:
        break;
    }
  }

  @SubscribeEvent
  public void handleCameraSetup(EntityViewRenderEvent.CameraSetup event) {
    float pct = MathHelper.clamp((Util.milliTime() - this.rollStartTime) / 1000.0F * 5, 0.0F, 1.0F);
    float roll = (float) Math.sin(Math.toRadians(180 * pct)) * this.roll / 20;
    if (pct == 1.0F) {
      this.roll = 0;
    }
    event.setRoll(roll);
  }

  @SubscribeEvent
  public void handeFOVUpdate(FOVUpdateEvent event) {
    ItemStack heldStack = minecraft.player.getHeldItemMainhand();
    if (this.getPlayer().map(IPlayer::isAiming).orElse(false)) {
      heldStack.getCapability(ModCapabilities.GUN_CONTROLLER).ifPresent(gunController -> {
        event
            .setNewfov(event.getFov()
                * gunController.getAttachmentMultiplier(AttachmentItem.MultiplierType.FOV));
      });
    }
  }

  @SubscribeEvent
  public void handleRenderTick(TickEvent.RenderTickEvent event) {
    switch (event.phase) {
      case START:
        if (minecraft.player != null) {
          float smoothYaw =
              MathHelper.lerp(0.25F, this.prevRotationVelocity.x, this.rotationVelocity.x);
          float smoothPitch =
              MathHelper.lerp(0.25F, this.prevRotationVelocity.y, this.rotationVelocity.y);
          this.rotationVelocity = Vec2f.ZERO;
          this.prevRotationVelocity = new Vec2f(smoothYaw, smoothPitch);
          minecraft.player.rotateTowards(smoothYaw, smoothPitch);
        }
        break;
      default:
        break;
    }
  }

  @SubscribeEvent
  public void handleItemColor(ColorHandlerEvent.Item event) {
    // Color for stacks with GUN_CONTROLLER capability
    IItemColor gunColor = (stack, tintIndex) -> {
      return stack
          .getCapability(ModCapabilities.GUN_CONTROLLER)
          .map(gunController -> gunController.getColor().map(Color::getMergedValues))
          .orElse(Optional.empty())
          .orElse(Integer.MAX_VALUE);
    };

    // Registers the color for every matching CD item
    ModItems.ITEMS
        .getEntries()
        .stream()
        .map(RegistryObject::get)
        .filter(item -> item instanceof GunItem)
        .forEach(item -> event.getItemColors().register(gunColor, () -> item));

    // Color for stacks with PAINT_COLOR capability
    IItemColor paintStackColor = (stack, tintIndex) -> {
      return stack
          .getCapability(ModCapabilities.PAINT_COLOR)
          .map(paintColorCap -> paintColorCap.getColor().map(Color::getMergedValues))
          .orElse(Optional.empty())
          .orElse(Integer.MAX_VALUE);
    };

    // Registers the color for every matching CD item
    ModItems.ITEMS
        .getEntries()
        .stream()
        .map(RegistryObject::get)
        .filter(item -> item instanceof PaintItem)
        .forEach(item -> event.getItemColors().register(paintStackColor, () -> item));
  }

  @SubscribeEvent
  public void handleTextureStitch(TextureStitchEvent.Pre event) {
    // Automatically adds every painted gun skins
    ModItems.ITEMS
        .getEntries()
        .stream()
        .map(RegistryObject::get)
        .filter(item -> item instanceof GunItem)
        .map(item -> (GunItem) item)
        .forEach(gun -> {
          gun.getAcceptedPaints().stream().filter(PaintItem::hasSkin).forEach(paint -> {
            event
                .addSprite(
                    // Example: "craftingdead:models/guns/m4a1_diamond_paint"
                    new ResourceLocation(gun.getRegistryName().getNamespace(),
                        "models/guns/" + gun.getRegistryName().getPath() + "_"
                            + paint.getRegistryName().getPath()));
          });
        });
  }
}
