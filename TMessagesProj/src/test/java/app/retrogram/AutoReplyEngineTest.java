// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import app.retrogram.graph.AutoReplyEngine;
import app.retrogram.graph.AutoReplyEngine.ChatKind;
import app.retrogram.graph.AutoReplyEngine.Incoming;
import app.retrogram.graph.AutoReplyEngine.Rule;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AutoReplyEngineTest {

    private static final long H = 3_600_000L;

    private static Rule rule(String id, boolean groups, int start, int end, int cooldownH, String... keywords) {
        return new Rule(id, "reply " + id, true, true, groups, Arrays.asList(keywords), start, end, cooldownH);
    }

    private static Incoming msg(ChatKind kind, long sender, String text, long nowMs, int minute) {
        return new Incoming(kind, sender, text, nowMs, minute, false, false);
    }

    private static AutoReplyEngine engine(Rule... rules) {
        return new AutoReplyEngine(Arrays.asList(rules), 100, H, false);
    }

    @Test
    public void repliesOncePerSenderPerCooldown() {
        Rule r = rule("a", false, 0, 0, 6);
        AutoReplyEngine e = engine(r);
        assertSame(r, e.decide(msg(ChatKind.PRIVATE, 1, "hi", 0, 600), false));
        assertNull(e.decide(msg(ChatKind.PRIVATE, 1, "hi again", 5 * H, 600), false));
        assertSame(r, e.decide(msg(ChatKind.PRIVATE, 1, "hello", 6 * H, 600), false));
        assertSame("another sender is independent", r, e.decide(msg(ChatKind.PRIVATE, 2, "hi", 6 * H, 600), false));
    }

    @Test
    public void groupsOnlyWhenExplicitlyEnabled() {
        AutoReplyEngine off = engine(rule("a", false, 0, 0, 1));
        assertNull(off.decide(msg(ChatKind.GROUP, 1, "hi", 0, 0), false));
        Rule on = rule("b", true, 0, 0, 1);
        assertSame(on, engine(on).decide(msg(ChatKind.GROUP, 1, "hi", 0, 0), false));
    }

    @Test
    public void scheduleWindowIncludingMidnightWrap() {
        Rule night = rule("n", false, 22 * 60, 7 * 60, 1);
        AutoReplyEngine e = engine(night);
        assertSame(night, e.decide(msg(ChatKind.PRIVATE, 1, "x", 0, 23 * 60), false));
        assertSame(night, e.decide(msg(ChatKind.PRIVATE, 2, "x", 0, 3 * 60), false));
        assertNull(e.decide(msg(ChatKind.PRIVATE, 3, "x", 0, 12 * 60), false));
        assertNull("end minute is exclusive", e.decide(msg(ChatKind.PRIVATE, 4, "x", 0, 7 * 60), false));
    }

    @Test
    public void keywordsAreCaseInsensitiveAndFirstMatchingRuleWins() {
        Rule price = rule("price", false, 0, 0, 1, "Price", "cost");
        Rule fallback = rule("all", false, 0, 0, 1);
        AutoReplyEngine e = engine(price, fallback);
        assertSame(price, e.decide(msg(ChatKind.PRIVATE, 1, "What is the PRICE?", 0, 0), false));
        assertSame(fallback, e.decide(msg(ChatKind.PRIVATE, 2, "hello", 0, 0), false));
    }

    @Test
    public void neverRepliesToSelfBotsOrWhenNotAway() {
        Rule r = rule("a", true, 0, 0, 1);
        AutoReplyEngine e = new AutoReplyEngine(Collections.singletonList(r), 100, H, true);
        assertNull(e.decide(msg(ChatKind.PRIVATE, 1, "hi", 0, 0), false));
        assertNull(e.decide(new Incoming(ChatKind.PRIVATE, 1, "hi", 0, 0, true, false), true));
        assertNull(e.decide(new Incoming(ChatKind.PRIVATE, 1, "hi", 0, 0, false, true), true));
        assertSame(r, e.decide(msg(ChatKind.PRIVATE, 1, "hi", 0, 0), true));
    }

    @Test
    public void globalRateLimitStopsReplyStorms() {
        Rule r = rule("a", false, 0, 0, 0);
        AutoReplyEngine e = new AutoReplyEngine(Collections.singletonList(r), 2, H, false);
        assertSame(r, e.decide(msg(ChatKind.PRIVATE, 1, "x", 0, 0), false));
        assertSame(r, e.decide(msg(ChatKind.PRIVATE, 2, "x", 1000, 0), false));
        assertNull(e.decide(msg(ChatKind.PRIVATE, 3, "x", 2000, 0), false));
        assertSame("window slides", r, e.decide(msg(ChatKind.PRIVATE, 4, "x", H + 1, 0), false));
    }

    @Test
    public void disabledRuleIsSkipped() {
        Rule off = new Rule("d", "r", false, true, true, Collections.<String>emptyList(), 0, 0, 1);
        assertEquals(null, engine(off).decide(msg(ChatKind.PRIVATE, 1, "x", 0, 0), false));
    }
}
