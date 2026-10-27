# Dark Web & iPhone Duo — shared engineering notes

Project: `/home/claude/darkweb_duo` — Minecraft **1.20.1**, **Forge 47.2.0**, **Java 17**, **official (Mojang) mappings**, mod id `darkweb_duo`,
root package `com.darkwebduo`. NO external libraries (no GeckoLib, no Mixins). Do not change versions.

## HARD CONSTRAINT: nothing can be compiled in this sandbox
The Forge/Minecraft jars are unreachable here, so **every API call you write must be one you are certain exists in 1.20.1 / Forge 47.2.0**.
When you are not sure a method/class/signature exists, use a simpler well-known alternative instead of guessing. Re-read every file you write once, hunting for:
missing imports, wrong package names, wrong method signatures, unbalanced braces, checked exceptions, generics errors, client classes used on the server.

To catch *syntax* errors we run `javalang` over every file (`pip install javalang --break-system-packages`; `tools/syntax_check.py <files...>`).
So write **Java 8–11 style syntax only**: NO records, NO switch expressions / arrow-switch, NO `instanceof Foo f` patterns, NO text blocks, NO `var`, NO sealed classes.
(Lambdas, method refs, generics, diamond, enhanced-for, old-style switch, nested/inner/anonymous classes are all fine.)

## Known 1.20.1 facts (get these right)
- `Entity#level()` is a METHOD (`level()`), not a public field. `Entity#position()`, `getX()`, `getEyePosition()`, `blockPosition()`.
- Damage: `target.hurt(level().damageSources().mobAttack(this), amount)`; magic: `damageSources().indirectMagic(this, this)`; generic: `damageSources().generic()`; explosion: `damageSources().explosion(this, this)`.
  Multiply boss damage by `Config.damageMult()`.
- Server-only logic guarded by `if (!level().isClientSide)` / `level() instanceof ServerLevel sl` → write as `if (level() instanceof ServerLevel)` then cast.
- Boss bar: `new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.X, BossEvent.BossBarOverlay.Y)`; add/remove players in `startSeenByPlayer/stopSeenByPlayer`; `setProgress(getHealth()/getMaxHealth())` each tick.
- Mod entities: override `public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }` (net.minecraftforge.network.NetworkHooks).
- Synched data: `private static final EntityDataAccessor<Integer> X = SynchedEntityData.defineId(MyEntity.class, EntityDataSerializers.INT);` + `defineSynchedData()` override with `this.entityData.define(X, 0)`.
- Entity model code (client): `LayerDefinition.create(MeshDefinition, texW, texH)`, `PartDefinition.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u,v).addBox(...), PartPose.offset(...))`; `EntityModel#setupAnim(T, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)`; `renderToBuffer(PoseStack, VertexConsumer, int light, int overlay, float r, float g, float b, float a)`. `ModelLayerLocation(new ResourceLocation(MODID, "name"), "main")`.
- Rendering: `com.mojang.math.Axis.YP.rotationDegrees(f)`; math types are `org.joml.Vector3f/Matrix4f/Quaternionf`. Emissive: `RenderType.eyes(tex)` or `RenderType.entityTranslucentEmissive(tex)`; beams: `RenderType.lightning()` (POSITION_COLOR, additive) or `RenderType.entityTranslucent`.
- Items in BER: `Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack, buffer, level, seed)`.
- GUI: `GuiGraphics` (`graphics.fill(x1,y1,x2,y2,argb)`, `graphics.drawString(font, text, x, y, color, shadow)`, `graphics.blit(ResourceLocation, x, y, u, v, w, h, texW, texH)`, `graphics.pose()`, `graphics.enableScissor/disableScissor`, `graphics.drawCenteredString`). `Screen#render(GuiGraphics, int mx, int my, float pt)`.
- Block entities: `load(CompoundTag)` / `saveAdditional(CompoundTag)`; sync with `getUpdateTag()` → `saveWithoutMetadata()` and `getUpdatePacket()` → `ClientboundBlockEntityDataPacket.create(this)`; push with `level.sendBlockUpdated(pos, state, state, 3)`; mark dirty `setChanged()`.
- Ticking BEs: block implements `EntityBlock`, `getTicker(Level, BlockState, BlockEntityType<T>)` → `BaseEntityBlock.createTickerHelper(type, MY_TYPE.get(), MyBE::tick)` (static `tick(Level, BlockPos, BlockState, MyBE)`).
- Blocks: `use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` returns `InteractionResult`; `BaseEntityBlock#getRenderShape` returns `RenderShape.INVISIBLE` by default -> **override to `RenderShape.MODEL`** when you use a JSON model. `Properties.of()` (no arg) in 1.20.
- Particles: `ParticleTypes.END_ROD, ELECTRIC_SPARK, SOUL_FIRE_FLAME, FLAME, SMOKE, LARGE_SMOKE, CLOUD, FLASH, EXPLOSION, EXPLOSION_EMITTER, SONIC_BOOM, ENCHANT, GLOW, REVERSE_PORTAL, PORTAL, DRAGON_BREATH, WITCH, SCULK_SOUL, SCULK_CHARGE_POP, GLOW_SQUID_INK, SQUID_INK, CRIT, ENCHANTED_HIT, TOTEM_OF_UNDYING, FIREWORK, WAX_ON, ELECTRIC_SPARK, SHRIEK...`. Dust: `Fx.dust(0xRRGGBB, size)`, `Fx.dustFade(from, to, size)` (DustColorTransitionOptions). Use `Fx.*` helpers (server) — they are visible from 120 blocks away.
- Client-only code lives in `com.darkwebduo.client.*`. Never reference client classes from common classes except inside `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientClass.method())`.
- Mob goals: bosses should NOT rely on vanilla goal AI for their attacks; run your own tick-driven state machine in `aiStep()`/`customServerAiStep()`, using `getNavigation()`, `setDeltaMovement`, `hurtMarked = true` for knockback, `LivingEntity#knockback`, or direct `teleportTo`.
- Config values: ALWAYS through `Config.cipherHealth()`, `Config.omegaHealth()`, `Config.damageMult()`, `Config.cipherEmeralds()`... (never `.get()` directly).
- Cutscenes: `CutsceneServer.start(level, origin, radius, CutsceneIds.X, arg, bossId)` and the beat table in `CutsceneIds` javadoc. The camera/HUD are done client side by the core module — you only run the server timeline.
- Camera shake / flash: `ModNetwork.shake(level, pos, radius, intensity, ticks)`, `ModNetwork.flash(level, pos, radius, 0xAARRGGBB, ticks)`.
- Sounds: `ModSounds.X.get()`; `level.playSound(null, x, y, z, sound, SoundSource.HOSTILE, vol, pitch)` / `Fx.sound(...)`.

## Asset generation convention
Each module ships a **deterministic Python generator** `tools/gen_<module>.py` that writes straight into `src/main/resources/...` (compute
`ROOT = Path(__file__).resolve().parent.parent`), using only `numpy`, `Pillow` (and `ffmpeg` binary for audio). It must run standalone in < 3 minutes and be idempotent.
Language entries go into a JSON fragment `tools/lang/<module>.json` (flat `{ "key": "value" }`); the core generator merges all fragments into `en_us.json`.
Textures must look *great*: no flat colour fills — use gradients, noise, panel lines, bevels, glow, dithering, emissive details. 16x16 block textures are fine but crisp;
entity/large textures may be 128–512 px. Models are hand-authored JSON (1.20.1 format, `display` transforms included where relevant).

## Package / ownership map
core (lead): registry/*, util/Fx, net/*, cutscene/*, client/cutscene/*, block/{GiantMonitor,GiantKeyboard,GiantMouse,DeliveryBox,KeycardDoor}*, item/KeycardItem, command/*, client/ClientSetup, build_mod.py
audio:  tools/gen_audio.py, sounds.json
cipher: entity/CipherWardenEntity (+entity/cipher/*), client/render/CipherWarden{Model,Renderer}, world/CipherArena, tools/gen_cipher.py
omega:  entity/OmegaRootEntity (+entity/omega/*), client/render/OmegaRoot{Model,Renderer}, block/OmegaTerminalBlock, block/entity/OmegaTerminalBlockEntity, client/render/OmegaTerminalRenderer, world/OmegaArena, tools/gen_omega.py
iphone: block/IPhoneDuoBlock, block/entity/IPhoneDuoBlockEntity, item/IPhoneDuoItem, client/render/IPhoneDuoRenderer, client/screen/**, tools/gen_iphone.py
