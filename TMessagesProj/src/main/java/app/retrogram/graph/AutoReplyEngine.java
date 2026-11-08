// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decision core of the local auto-reply (WP 6.3, docs/plans/06-graph-features.md).
 *
 * Pure logic with no Android or Telegram dependencies, so it is unit-testable on the JVM. It only answers
 * "should this incoming message get a reply, and which one?". Sending goes through the normal
 * `SendMessagesHelper` path elsewhere; nothing here is wired to the app yet and the feature stays off by default.
 *
 * Designed from the plan, not from Graph Messenger. The exact options and defaults will be reconciled with
 * `docs/graph-spec/auto-reply.md` once that spec exists.
 */
public final class AutoReplyEngine {

    public enum ChatKind { PRIVATE, GROUP }

    /** One incoming message, reduced to what the rules need. */
    public static final class Incoming {
        public final ChatKind kind;
        public final long senderId;
        public final String text;
        /** Milliseconds since the epoch. */
        public final long nowMs;
        /** Minutes since local midnight (0..1439), already converted to the user's zone by the caller. */
        public final int minuteOfDay;
        public final boolean fromSelf;
        public final boolean isBotOrService;

        public Incoming(ChatKind kind, long senderId, String text, long nowMs, int minuteOfDay, boolean fromSelf, boolean isBotOrService) {
            this.kind = kind;
            this.senderId = senderId;
            this.text = text == null ? "" : text;
            this.nowMs = nowMs;
            this.minuteOfDay = minuteOfDay;
            this.fromSelf = fromSelf;
            this.isBotOrService = isBotOrService;
        }
    }

    public static final class Rule {
        public final String id;
        public final String replyText;
        public final boolean enabled;
        public final boolean privateChats;
        /** Groups are never answered unless this is explicitly true. */
        public final boolean groups;
        /** Case-insensitive; empty list matches every message. */
        public final List<String> keywords;
        /** Schedule window in minutes of the day; start == end means "always". The window may wrap midnight. */
        public final int startMinute;
        public final int endMinute;
        /** At most one reply per sender per this many hours. */
        public final int cooldownHours;

        public Rule(String id, String replyText, boolean enabled, boolean privateChats, boolean groups,
                    List<String> keywords, int startMinute, int endMinute, int cooldownHours) {
            this.id = id;
            this.replyText = replyText;
            this.enabled = enabled;
            this.privateChats = privateChats;
            this.groups = groups;
            this.keywords = keywords == null ? new ArrayList<>() : keywords;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.cooldownHours = Math.max(cooldownHours, 0);
        }

        boolean inWindow(int minute) {
            if (startMinute == endMinute) {
                return true;
            }
            if (startMinute < endMinute) {
                return minute >= startMinute && minute < endMinute;
            }
            return minute >= startMinute || minute < endMinute;
        }

        boolean matchesKeywords(String text) {
            if (keywords.isEmpty()) {
                return true;
            }
            final String lower = text.toLowerCase();
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isEmpty() && lower.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
    }

    private final List<Rule> rules;
    /** Global ceiling, protects the account from restriction if a rule loops with another auto-responder. */
    private final int maxRepliesPerWindow;
    private final long windowMs;
    private final boolean onlyWhenAway;

    private final Map<String, Long> lastReplyBySenderRule = new HashMap<>();
    private final ArrayList<Long> recentReplyTimes = new ArrayList<>();

    public AutoReplyEngine(List<Rule> rules, int maxRepliesPerWindow, long windowMs, boolean onlyWhenAway) {
        this.rules = rules;
        this.maxRepliesPerWindow = maxRepliesPerWindow;
        this.windowMs = windowMs;
        this.onlyWhenAway = onlyWhenAway;
    }

    /**
     * Returns the rule whose reply should be sent, or null. Records the reply (cooldown and global rate)
     * when it returns a rule, so call it exactly once per incoming message and send what it returns.
     *
     * @param userAway whether the user is currently away (offline / not recently active); only consulted when "only when away" is on
     */
    public Rule decide(Incoming in, boolean userAway) {
        if (in.fromSelf || in.isBotOrService) {
            return null;
        }
        if (onlyWhenAway && !userAway) {
            return null;
        }
        pruneRate(in.nowMs);
        if (recentReplyTimes.size() >= maxRepliesPerWindow) {
            return null;
        }
        for (Rule rule : rules) {
            if (!rule.enabled) {
                continue;
            }
            if (in.kind == ChatKind.PRIVATE ? !rule.privateChats : !rule.groups) {
                continue;
            }
            if (!rule.inWindow(in.minuteOfDay) || !rule.matchesKeywords(in.text)) {
                continue;
            }
            final String key = rule.id + ":" + in.senderId;
            final Long last = lastReplyBySenderRule.get(key);
            if (last != null && in.nowMs - last < rule.cooldownHours * 3_600_000L) {
                continue;
            }
            lastReplyBySenderRule.put(key, in.nowMs);
            recentReplyTimes.add(in.nowMs);
            return rule;
        }
        return null;
    }

    private void pruneRate(long nowMs) {
        while (!recentReplyTimes.isEmpty() && nowMs - recentReplyTimes.get(0) >= windowMs) {
            recentReplyTimes.remove(0);
        }
    }
}
