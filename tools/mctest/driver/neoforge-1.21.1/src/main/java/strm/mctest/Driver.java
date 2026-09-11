package strm.mctest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Runs scripts that tools/mctest drops into {@code <gameDir>/mctest/inbox}, one step at a time on
 * the client thread, and answers in {@code outbox}. Also keeps {@code status.json} fresh so the
 * launcher can tell when the world is up.
 *
 * <p>Steps run back to back within a tick until a {@code wait}. A screenshot captures the most
 * recently rendered frame, so give a change at least one tick before shooting it.</p>
 */
public final class Driver {

    private static final Logger LOG = LoggerFactory.getLogger("mctest");
    private static final Gson GSON = new Gson();

    private static Path dir;
    private static Script current;
    private static final Set<KeyMapping> HELD = new LinkedHashSet<>();
    private static int statusCountdown;

    private Driver() {
    }

    public static boolean enabled() {
        return System.getProperty("mctest") != null;
    }

    /** Once per client tick, after the game's own tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (dir == null) {
            dir = mc.gameDirectory.toPath().resolve("mctest");
            LOG.info("mctest driver active, watching {}", dir);
        }
        // Held keys are re-asserted every tick: opening a screen releases every mapping.
        for (KeyMapping key : HELD) {
            key.setDown(true);
        }
        if (--statusCountdown <= 0) {
            statusCountdown = 10;
            writeStatus(mc);
        }
        try {
            if (current == null) {
                current = poll();
            }
            if (current != null) {
                current.advance(mc);
                if (current.done()) {
                    write(dir.resolve("outbox").resolve(current.id + ".json"), current.answer());
                    current = null;
                }
            }
        } catch (Throwable t) {
            LOG.error("mctest driver failed", t);
            current = null;
        }
    }

    private static Script poll() throws IOException {
        Path inbox = dir.resolve("inbox");
        if (!Files.isDirectory(inbox)) {
            return null;
        }
        Path next;
        try (Stream<Path> files = Files.list(inbox)) {
            next = files.filter(p -> p.toString().endsWith(".json")).min(Comparator.naturalOrder()).orElse(null);
        }
        if (next == null) {
            return null;
        }
        JsonObject json = GSON.fromJson(Files.readString(next, StandardCharsets.UTF_8), JsonObject.class);
        Files.delete(next);
        return new Script(json.get("id").getAsString(), json.getAsJsonArray("steps"));
    }

    private static void write(Path target, JsonObject json) {
        try {
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("mctest: could not write {}", target, e);
        }
    }

    private static void writeStatus(Minecraft mc) {
        JsonObject s = new JsonObject();
        s.addProperty("inWorld", mc.level != null && mc.player != null);
        s.addProperty("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        s.addProperty("fps", mc.getFps());
        s.addProperty("paused", mc.isPaused());
        s.addProperty("busy", current != null);
        write(dir.resolve("status.json"), s);
    }

    // ---------------------------------------------------------------------------------------

    private static final class Script {
        final String id;
        final JsonArray steps;
        final JsonArray results = new JsonArray();
        int index;
        int waiting;

        Script(String id, JsonArray steps) {
            this.id = id;
            this.steps = steps;
        }

        boolean done() {
            return index >= steps.size() && waiting <= 0;
        }

        JsonObject answer() {
            JsonObject a = new JsonObject();
            a.addProperty("id", id);
            a.add("results", results);
            return a;
        }

        void advance(Minecraft mc) {
            if (waiting > 0 && --waiting > 0) {
                return;
            }
            while (index < steps.size()) {
                JsonObject step = steps.get(index++).getAsJsonObject();
                JsonObject result = new JsonObject();
                result.addProperty("step", index - 1);
                try {
                    int wait = run(mc, step, result);
                    results.add(result);
                    if (wait > 0) {
                        waiting = wait;
                        return;
                    }
                } catch (Throwable t) {
                    result.addProperty("error", t.toString());
                    results.add(result);
                }
            }
        }
    }

    /** Runs one step; returns how many ticks to wait before the next. */
    private static int run(Minecraft mc, JsonObject step, JsonObject result) throws ReflectiveOperationException {
        LocalPlayer player = mc.player;
        for (Map.Entry<String, JsonElement> e : step.entrySet()) {
            String kind = e.getKey();
            JsonElement v = e.getValue();
            switch (kind) {
                case "wait" -> {
                    return Math.max(1, v.getAsInt());
                }
                case "cmd" -> {
                    String cmd = v.getAsString();
                    requirePlayer(player).connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                }
                case "chat" -> requirePlayer(player).connection.sendChat(v.getAsString());
                case "hold" -> press(mc, key(mc, v.getAsString()), true);
                case "release" -> press(mc, key(mc, v.getAsString()), false);
                case "releaseAll" -> {
                    HELD.forEach(k -> k.setDown(false));
                    HELD.clear();
                }
                // getKey() is gone on newer versions; the saved binding names the same key everywhere.
                case "click" -> KeyMapping.click(InputConstants.getKey(key(mc, v.getAsString()).saveString()));
                case "slot" -> requirePlayer(player).getInventory().selected = v.getAsInt();
                case "camera" -> mc.options.setCameraType(switch (v.getAsString()) {
                    case "first" -> CameraType.FIRST_PERSON;
                    case "back" -> CameraType.THIRD_PERSON_BACK;
                    case "front" -> CameraType.THIRD_PERSON_FRONT;
                    default -> throw new IllegalArgumentException("camera: first, back or front");
                });
                case "look" -> {
                    JsonArray a = v.getAsJsonArray();
                    float yaw = a.get(0).getAsFloat();
                    float pitch = a.get(1).getAsFloat();
                    LocalPlayer p = requirePlayer(player);
                    p.setYRot(yaw);
                    p.setXRot(pitch);
                    p.yRotO = yaw;
                    p.xRotO = pitch;
                    p.setYHeadRot(yaw);
                    p.yBodyRot = yaw;
                    p.yBodyRotO = yaw;
                }
                case "hideGui" -> mc.options.hideGui = v.getAsBoolean();
                case "closeScreen" -> mc.setScreen(null);
                case "screenshot" -> {
                    String name = v.getAsString().replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
                    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
                    result.addProperty("screenshot",
                            mc.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath().toString());
                }
                case "state" -> result.add("state", state(mc));
                case "config" -> config(v.getAsJsonObject());
                case "fade" -> result.add("fade", fade(mc));
                case "log" -> LOG.info("[mctest] {}", v.getAsString());
                default -> throw new IllegalArgumentException("unknown step: " + kind);
            }
        }
        return 0;
    }

    private static LocalPlayer requirePlayer(LocalPlayer player) {
        if (player == null) {
            throw new IllegalStateException("not in a world");
        }
        return player;
    }

    private static KeyMapping key(Minecraft mc, String name) {
        return switch (name) {
            case "forward" -> mc.options.keyUp;
            case "back" -> mc.options.keyDown;
            case "left" -> mc.options.keyLeft;
            case "right" -> mc.options.keyRight;
            case "jump" -> mc.options.keyJump;
            case "sneak" -> mc.options.keyShift;
            case "sprint" -> mc.options.keySprint;
            case "attack" -> mc.options.keyAttack;
            case "use" -> mc.options.keyUse;
            case "drop" -> mc.options.keyDrop;
            case "swap" -> mc.options.keySwapOffhand;
            case "inventory" -> mc.options.keyInventory;
            default -> {
                // Any other mapping by its translation key, e.g. Carry On's "key.carry.desc".
                for (KeyMapping k : mc.options.keyMappings) {
                    if (k.getName().equals(name)) {
                        yield k;
                    }
                }
                throw new IllegalArgumentException("unknown key: " + name);
            }
        };
    }

    /**
     * Presses or releases every mapping bound to the same physical key as {@code base}, as a real
     * key press would. Mods keep their own mappings on shared keys — Carry On picks up with its
     * "Carry" mapping on shift, which the vanilla sneak mapping alone never triggers.
     */
    private static void press(Minecraft mc, KeyMapping base, boolean down) {
        String bound = base.saveString();
        for (KeyMapping k : mc.options.keyMappings) {
            if (k == base || k.saveString().equals(bound)) {
                if (down) {
                    HELD.add(k);
                } else {
                    HELD.remove(k);
                }
                k.setDown(down);
            }
        }
    }

    private static JsonObject state(Minecraft mc) {
        JsonObject s = new JsonObject();
        s.addProperty("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        s.addProperty("camera", mc.options.getCameraType().name());
        s.addProperty("fps", mc.getFps());
        List<String> held = new ArrayList<>();
        HELD.forEach(k -> held.add(k.getName()));
        s.add("heldKeys", GSON.toJsonTree(held));
        LocalPlayer p = mc.player;
        if (p == null) {
            return s;
        }
        s.addProperty("name", p.getName().getString());
        s.addProperty("dimension", p.level().dimension().location().toString());
        s.add("pos", GSON.toJsonTree(new double[]{p.getX(), p.getY(), p.getZ()}));
        s.add("rot", GSON.toJsonTree(new float[]{p.getYRot(), p.getXRot()}));
        s.addProperty("pose", p.getPose().name());
        s.addProperty("crouching", p.isCrouching());
        s.addProperty("onGround", p.onGround());
        s.addProperty("usingItem", p.isUsingItem());
        s.addProperty("slot", p.getInventory().selected);
        s.addProperty("mainHand", item(p.getMainHandItem()));
        s.addProperty("offHand", item(p.getOffhandItem()));
        s.addProperty("gameMode", mc.gameMode == null ? null : mc.gameMode.getPlayerMode().getName());
        s.add("target", target(mc, p));
        s.addProperty("dayTime", p.level().getDayTime());
        return s;
    }

    /** What the crosshair is on — Better Combat, for one, will not swing at a block it could mine. */
    private static JsonObject target(Minecraft mc, LocalPlayer p) {
        HitResult hit = mc.hitResult;
        if (hit == null) {
            return null;
        }
        JsonObject t = new JsonObject();
        t.addProperty("type", hit.getType().name());
        t.addProperty("distance", Math.round(Math.sqrt(hit.getLocation().distanceToSqr(p.getEyePosition())) * 100) / 100.0);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block) {
            t.addProperty("block", String.valueOf(BuiltInRegistries.BLOCK.getKey(
                    p.level().getBlockState(block.getBlockPos()).getBlock())));
        } else if (hit instanceof EntityHitResult entity) {
            t.addProperty("entity", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getEntity().getType())));
        }
        return t;
    }

    // --- Probes into the EMF Compat core, by reflection so the driver never depends on it. ---

    private static final String CORE = "strm.emfcompat.core.";

    /** Sets core config options in memory (not saved), e.g. {"core.smoothPoseTransitions": false}. */
    private static void config(JsonObject values) throws ReflectiveOperationException {
        Method set = Class.forName(CORE + "EMFCompatConfig").getMethod("setBoolean", String.class, boolean.class);
        for (Map.Entry<String, JsonElement> e : values.entrySet()) {
            set.invoke(null, e.getKey(), e.getValue().getAsBoolean());
        }
    }

    /**
     * Who poses the local player and how far each part's fade has got: {"sources": [...],
     * "parts": {"right_arm": "0.62 in"}} — "in" while a source holds the part, "out" while it is
     * being faded back into the pack's animation. No "parts" means nothing is fading.
     */
    private static JsonObject fade(Minecraft mc) throws ReflectiveOperationException {
        UUID uuid = requirePlayer(mc.player).getUUID();
        JsonObject out = new JsonObject();
        Class<?> pm = Class.forName(CORE + "PoseManager");
        JsonArray sources = new JsonArray();
        if (((Map<?, ?>) pm.getField("entitySavedPoses").get(null)).containsKey(uuid)) {
            sources.add("default");
        }
        Map<?, ?> bySource = (Map<?, ?>) ((Map<?, ?>) pm.getField("entitySavedPosesBySource").get(null)).get(uuid);
        if (bySource != null) {
            bySource.keySet().forEach(k -> sources.add(String.valueOf(k)));
        }
        out.add("sources", sources);
        Field statesField = Class.forName(CORE + "PoseInterpolator").getDeclaredField("STATES");
        statesField.setAccessible(true);
        Map<?, ?> mine = (Map<?, ?>) ((Map<?, ?>) statesField.get(null)).get(uuid);
        if (mine != null && !mine.isEmpty()) {
            JsonObject parts = new JsonObject();
            for (Map.Entry<?, ?> e : mine.entrySet()) {
                Object f = e.getValue();
                Field weight = f.getClass().getDeclaredField("weight");
                Field posed = f.getClass().getDeclaredField("posed");
                weight.setAccessible(true);
                posed.setAccessible(true);
                parts.addProperty(String.valueOf(e.getKey()),
                        String.format(java.util.Locale.ROOT, "%.2f %s", weight.getFloat(f), posed.getBoolean(f) ? "in" : "out"));
            }
            out.add("parts", parts);
        }
        return out;
    }

    private static String item(ItemStack stack) {
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount();
    }
}
