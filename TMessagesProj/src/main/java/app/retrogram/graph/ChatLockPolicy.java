// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lock/unlock state machine of the per-chat lock (WP 6.1, docs/plans/06-graph-features.md).
 *
 * Pure logic, no Android dependencies. It decides whether opening a chat needs the passcode/biometric gate,
 * and when an unlocked chat locks again (timeout, or the app going to the background). Persistence (the
 * Keystore-backed store of locked dialog ids), the gate UI (`PasscodeView`) and the hooks in
 * `LaunchActivity.presentFragment`, notifications, share targets and widgets are separate work and not done yet.
 * It does not encrypt message storage, and the UI must say so.
 *
 * Designed from the plan, not from Graph Messenger; options will be reconciled with `docs/graph-spec/chat-lock.md`.
 */
public final class ChatLockPolicy {

    private final Set<Long> locked = new HashSet<>();
    private final Map<Long, Long> unlockedAtMs = new HashMap<>();
    private final long relockTimeoutMs;
    private final boolean relockOnBackground;

    /** @param relockTimeoutMs 0 means "lock again as soon as the chat is left" (checked via {@link #onChatClosed}) */
    public ChatLockPolicy(long relockTimeoutMs, boolean relockOnBackground) {
        this.relockTimeoutMs = Math.max(relockTimeoutMs, 0);
        this.relockOnBackground = relockOnBackground;
    }

    public void lock(long dialogId) {
        locked.add(dialogId);
        unlockedAtMs.remove(dialogId);
    }

    /** Removing the lock requires a successful gate first; the caller checks that. */
    public void removeLock(long dialogId) {
        locked.remove(dialogId);
        unlockedAtMs.remove(dialogId);
    }

    public boolean isLocked(long dialogId) {
        return locked.contains(dialogId);
    }

    /** Whether opening this dialog (or revealing its notification text) must show the gate now. */
    public boolean needsGate(long dialogId, long nowMs) {
        if (!locked.contains(dialogId)) {
            return false;
        }
        final Long at = unlockedAtMs.get(dialogId);
        if (at == null) {
            return true;
        }
        if (relockTimeoutMs == 0 || nowMs - at >= relockTimeoutMs) {
            unlockedAtMs.remove(dialogId);
            return true;
        }
        return false;
    }

    /** Call after the gate succeeded. Unlocking a chat that is not locked does nothing. */
    public void onUnlocked(long dialogId, long nowMs) {
        if (locked.contains(dialogId)) {
            unlockedAtMs.put(dialogId, nowMs);
        }
    }

    public void onChatClosed(long dialogId) {
        if (relockTimeoutMs == 0) {
            unlockedAtMs.remove(dialogId);
        }
    }

    public void onAppBackgrounded() {
        if (relockOnBackground) {
            unlockedAtMs.clear();
        }
    }

    /** Notification text, share-target and widget previews of a locked chat are redacted whenever this is true. */
    public boolean shouldRedactPreview(long dialogId) {
        return locked.contains(dialogId);
    }
}
