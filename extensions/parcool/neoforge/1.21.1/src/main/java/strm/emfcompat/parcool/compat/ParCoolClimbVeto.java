package strm.emfcompat.parcool.compat;

import com.alrex.parcool.api.action.ParCoolActionEvent;
import net.neoforged.neoforge.common.NeoForge;
import strm.emfcompat.parcool.EMFCompatParCoolMod;

import java.util.Set;

/**
 * A player carrying a block cannot hang by their hands.
 *
 * <p>Both hands are on the load, so there is no honest animation for it - whatever is drawn, the
 * block ends up somewhere it cannot be. Rather than invent a pose for an impossible move, the move
 * is refused: ParCool asks before it starts an action ({@code ParCoolActionEvent.TryToStart}, which
 * is cancellable), and this says no to a ledge grab, to the climb off one and to a bar while the
 * hands are full. Put the block down and all three work as they always did.</p>
 *
 * <p>A bar looks at first as though it needs no refusing, since ParCool's hang key shares its
 * physical button with {@code use} and Carry On puts the block down on that press. It only does so
 * with somewhere to put it, though: aimed at open air the block stays in hand and the hang starts
 * as normal, which is exactly the case this covers.</p>
 *
 * <p>Only those three. Everything else ParCool does is left alone, and so is a hang that has
 * already started.</p>
 */
public final class ParCoolClimbVeto {

    /** ParCool's own class names for the moves that hold the player up by both arms. */
    private static final Set<String> TWO_HANDED_HOLDS = Set.of("HangOn", "ClimbUp", "HangDown");

    private ParCoolClimbVeto() {
    }

    /** Subscribes to ParCool's event. Only called when ParCool's action API is on the classpath. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ParCoolClimbVeto::onTryToStart);
    }

    private static void onTryToStart(ParCoolActionEvent.TryToStart event) {
        if (!EMFCompatParCoolMod.isEnabled()) return;
        if (!TWO_HANDED_HOLDS.contains(event.getAction().getClass().getSimpleName())) return;
        if (ParCoolHandsFull.handsFull(event.getPlayer())) {
            event.setCanceled(true);
        }
    }
}
