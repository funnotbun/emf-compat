package strm.createFlyEmfCompat.compat;

import net.minecraft.client.model.geom.ModelPart;
import strm.CreateFlyEmfCompatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.models.parts.EMFModelPart;
import traben.entity_model_features.models.parts.EMFModelPartCustom;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Gives Create's hat layer a part it can still measure.
 *
 * <p>Create places the engineer's and the logistics hat by reading the head part's box: it lifts the
 * hat by the box's height onto the top of the head and scales it by the box's widest side
 * ({@code HatFeatureRenderer#submit}), and it does that only {@code if (!lastChild.isEmpty())} —
 * that is, only while the part still has cubes.</p>
 *
 * <p>Under EMF a part a resource pack replaced has none. {@code EMFModelPartRoot} clears the vanilla
 * part ({@code cubes = List.of()}) and hangs the pack's boxes off an {@code EMF_*} child with a
 * compensating pivot; the part keeps its animated position and rotation, so everything that only
 * hangs off it still follows, but everything that measures it reads an empty list. Create's
 * {@code if} then never runs and the hat stays at the head's pivot — the neck — a full head too low:
 * measured with Fresh Animations, 10/16 of a block on a villager and 8/16 on a zombie or player.</p>
 *
 * <p>So while the pack's model is in place, the layer is handed the <em>vanilla</em> part instead.
 * EMF keeps the untouched vanilla tree in {@code EMFModelPartRoot.vanillaRoot}, and the EMF tree is
 * built from it with the same names, so the counterpart is the same path walked on the other root.
 * Only the box's extent is read — {@code minY - maxY} for the lift, the widest of X and Z for the
 * scale — so nothing about the pivot matters, and Create's own per-entity offsets (the
 * {@code hat_offsets} data files) keep meaning what they were authored against.</p>
 *
 * <p>A pack may also <em>re-parent</em> the head instead of just re-drawing it, and then measuring
 * is not enough: the part the layer walked to is not merely empty, it is unused. Fresh Animations
 * does this on every animal — the head is drawn as a {@code head2} submodel of <b>body</b>, and the
 * vanilla {@code head} part keeps nothing at all, so a hat hung on it floats beside the mob
 * (measured on a chicken and a parrot). On humanoids the same pack hangs {@code head2} off
 * {@code headwear}, which is a child of the head, so there the head still carries it. {@link #chain}
 * therefore checks whether the pack drew anything under the part at all, and if it did not, follows
 * the part the pack moved the geometry into — the custom part named after it, {@code head2} for
 * {@code head} — and hands the layer the path to that instead.</p>
 */
public final class HatGeometry {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMF Compat");

    /** A part's cubes and children, found by their generic type: the names are mapping-specific. */
    private static final Field CUBES = partField(List.class, ModelPart.Cube.class);
    private static final Field CHILDREN = partField(Map.class, ModelPart.class);

    /** Render thread only. Weak: a model reload drops the parts and takes its entries with them. */
    private static final Map<ModelPart, ModelPart> VANILLA_TWINS = new WeakHashMap<>();

    /** Set once if EMF is not the shape this expects, so the lookup is not retried every frame. */
    private static boolean unavailable = CUBES == null || CHILDREN == null;

    private HatGeometry() {
    }

    /**
     * The part whose boxes describe the head: {@code part} itself whenever it still has any, and
     * otherwise its vanilla counterpart from before the pack replaced it.
     */
    public static ModelPart measured(ModelPart part) {
        if (part == null || !part.isEmpty() || unavailable || !CreateFlyEmfCompatClient.isEnabled()
                || !CreateFlyEmfCompatClient.isHats()) {
            return part;
        }
        try {
            if (!(part instanceof EMFModelPart emf)) {
                return part;
            }
            ModelPart known = VANILLA_TWINS.get(part);
            if (known != null) {
                return known;
            }
            ModelPart twin = vanillaTwin(emf);
            if (twin == null || twin.isEmpty()) {
                return part;
            }
            VANILLA_TWINS.put(part, twin);
            return twin;
        } catch (Throwable t) {
            unavailable = true;
            LOGGER.warn("[EMF Compat] could not read the vanilla head geometry for Create's hat", t);
            return part;
        }
    }

    /**
     * The parts the hat layer should walk down to reach the head: its own chain while the pack still
     * draws something under it, and otherwise the path to wherever the pack moved that geometry.
     *
     * <p>The returned list always starts at the model root, which is where every caller starts from
     * — Create either applies the whole list or applies the root itself and then the rest.</p>
     */
    public static List<ModelPart> chain(String infoPart, ModelPart root, String defaultPart) {
        List<ModelPart> parts = walk(infoPart, root, defaultPart);
        if (parts.isEmpty() || unavailable || !CreateFlyEmfCompatClient.isEnabled()
                || !CreateFlyEmfCompatClient.isHats()) {
            return parts;
        }
        ModelPart last = parts.get(parts.size() - 1);
        if (!last.isEmpty() || packDraws(last)) {
            return parts;  // vanilla geometry, or the pack put its own under this very part
        }
        try {
            if (!(last instanceof EMFModelPart emf)) {
                return parts;
            }
            EMFModelPartRoot emfRoot = emf.getRoot();
            List<String> path = new ArrayList<>();
            if (emfRoot == null || !pathTo(emfRoot, last, path) || path.isEmpty()) {
                return parts;
            }
            List<ModelPart> moved = new ArrayList<>();
            return findMoved(emfRoot, path.get(path.size() - 1), moved) ? moved : parts;
        } catch (Throwable t) {
            unavailable = true;
            LOGGER.warn("[EMF Compat] could not follow the part Create's hat hangs on", t);
            return parts;
        }
    }

    /**
     * The chain Create itself would have built — {@code TrainHatInfo.getAdjustedPart}, rewritten here
     * because on Fabric that method cannot be called: Create Fly is an unremapped {@code compileOnly}
     * dependency, so its signature names Minecraft types this module does not know under those names.
     */
    private static List<ModelPart> walk(String infoPart, ModelPart root, String defaultPart) {
        List<ModelPart> parts = new ArrayList<>();
        parts.add(root);
        if (infoPart != null && !infoPart.isEmpty() && !infoPart.equals(defaultPart)) {
            ModelPart parent = root;
            for (String name : infoPart.split("/")) {
                ModelPart child = children(parent).get(name);
                if (child != null) {
                    parts.add(child);
                    parent = child;
                }
            }
        } else {
            ModelPart child = children(root).get(defaultPart);
            if (child != null) {
                parts.add(child);
            }
        }
        return parts;
    }

    /** Whether the pack drew anything of its own under this part. */
    private static boolean packDraws(ModelPart part) {
        for (ModelPart child : children(part).values()) {
            if (child instanceof EMFModelPartCustom && !cubes(child).isEmpty()) {
                return true;
            }
            if (packDraws(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Depth-first search for the custom part a pack moved {@code name} into, collecting the path to
     * it. Matched by name with the trailing digits and EMF's own prefix and duplicate marks taken
     * off, which is how packs name a re-parented copy — Fresh Animations writes {@code head2}.
     */
    private static boolean findMoved(ModelPart from, String name, List<ModelPart> path) {
        path.add(from);
        for (Map.Entry<String, ModelPart> child : children(from).entrySet()) {
            ModelPart part = child.getValue();
            if (part instanceof EMFModelPartCustom && plainName(child.getKey()).equals(name)
                    && (!cubes(part).isEmpty() || packDraws(part))) {
                path.add(part);
                return true;
            }
            if (findMoved(part, name, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    /** {@code EMF_head2##} -> {@code head}. */
    private static String plainName(String key) {
        String name = key.startsWith("EMF_") ? key.substring(4) : key;
        int end = name.length();
        while (end > 0 && (Character.isDigit(name.charAt(end - 1)) || name.charAt(end - 1) == '#')) {
            end--;
        }
        return name.substring(0, end);
    }

    /** The cubes of a part, or an empty list where they cannot be read. */
    public static List<ModelPart.Cube> cubes(ModelPart part) {
        return field(CUBES, part, List.of());
    }

    /** The same part in the vanilla tree EMF kept, found by the path that leads to it. */
    private static ModelPart vanillaTwin(EMFModelPart part) {
        EMFModelPartRoot root = part.getRoot();
        if (root == null || root.vanillaRoot == null) {
            return null;
        }
        List<String> path = new ArrayList<>();
        if (!pathTo(root, part, path)) {
            return null;
        }
        ModelPart vanilla = root.vanillaRoot;
        for (String name : path) {
            vanilla = children(vanilla).get(name);
            if (vanilla == null) {
                return null;
            }
        }
        return vanilla;
    }

    /** Names leading from {@code from} down to {@code target}, or false if it is not below it. */
    private static boolean pathTo(ModelPart from, ModelPart target, List<String> path) {
        if (from == target) {
            return true;
        }
        for (Map.Entry<String, ModelPart> child : children(from).entrySet()) {
            path.add(child.getKey());
            if (pathTo(child.getValue(), target, path)) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        return false;
    }

    private static Map<String, ModelPart> children(ModelPart part) {
        return field(CHILDREN, part, Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Field field, ModelPart part, T fallback) {
        if (field == null || part == null) {
            return fallback;
        }
        try {
            Object value = field.get(part);
            return value == null ? fallback : (T) value;
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    private static Field partField(Class<?> raw, Class<?> element) {
        for (Field field : ModelPart.class.getDeclaredFields()) {
            if (!raw.isAssignableFrom(field.getType())
                    || !(field.getGenericType() instanceof ParameterizedType parameterized)) {
                continue;
            }
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length > 0 && arguments[arguments.length - 1] == element) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }
}
