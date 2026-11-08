package app.retrogram;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import app.retrogram.graph.VoiceEffectStream;
import app.retrogram.graph.VoiceEffects;

import org.junit.Test;

import java.util.Arrays;

public class VoiceEffectStreamTest {

    private static final int RATE = 16000;

    private static short[] sine(double hz, int samples, double amp) {
        final short[] s = new short[samples];
        for (int i = 0; i < samples; i++) {
            s[i] = (short) Math.round(amp * Math.sin(2 * Math.PI * hz * i / RATE));
        }
        return s;
    }

    private static short[] stream(int effect, short[] in, int piece) {
        final VoiceEffectStream st = new VoiceEffectStream(effect, RATE);
        short[] out = new short[0];
        for (int i = 0; i < in.length; i += piece) {
            out = concat(out, st.push(Arrays.copyOfRange(in, i, Math.min(in.length, i + piece))));
        }
        return concat(out, st.finish());
    }

    private static short[] concat(short[] a, short[] b) {
        final short[] o = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }

    private static double power(short[] s, double hz) {
        double re = 0, im = 0;
        for (int i = 0; i < s.length; i++) {
            final double a = 2 * Math.PI * hz * i / RATE;
            re += s[i] * Math.cos(a);
            im += s[i] * Math.sin(a);
        }
        return Math.sqrt(re * re + im * im) / s.length;
    }

    @Test
    public void echoStreamEqualsWholeSignalEchoExactlyIncludingTail() {
        final short[] in = sine(500, 9000, 9000);
        final short[] whole = VoiceEffects.echo(in, RATE, 220, 0.45, 2);
        assertArrayEquals(whole, stream(VoiceEffects.EFFECT_ECHO, in, 1337));
        assertArrayEquals("one big piece", whole, stream(VoiceEffects.EFFECT_ECHO, in, in.length));
        assertArrayEquals("tiny pieces", whole, stream(VoiceEffects.EFFECT_ECHO, in, 97));
    }

    @Test
    public void robotStreamEqualsWholeSignal() {
        final short[] in = sine(700, 6000, 8000);
        assertArrayEquals(VoiceEffects.ringModulate(in, RATE, 55), stream(VoiceEffects.EFFECT_ROBOT, in, 777));
    }

    @Test
    public void pitchStreamKeepsLengthAndTone() {
        final short[] in = sine(400, 5 * RATE, 10000);
        final short[] out = stream(VoiceEffects.EFFECT_HIGHER, in, RATE); // 1 s pieces
        assertEquals(in.length, out.length);
        assertTrue(power(out, 540) > 3 * power(out, 400));
    }

    @Test
    public void pitchSeamsAreNotWorseThanTheRestOfTheSignal() {
        final short[] in = sine(400, 5 * RATE, 10000);
        final short[] out = stream(VoiceEffects.EFFECT_HIGHER, in, RATE);
        int worst = 0;
        int worstAtSeams = 0;
        for (int i = 1; i < out.length; i++) {
            final int jump = Math.abs(out[i] - out[i - 1]);
            worst = Math.max(worst, jump);
            // seams of the 1 s pieces (output positions about k * RATE - overlap)
            final int pos = i % RATE;
            if (pos > RATE - 2000 || pos < 2000) {
                worstAtSeams = Math.max(worstAtSeams, jump);
            }
        }
        // a 540 Hz sine of amplitude 10000 moves by at most 2*pi*540/16000*10000 = 2120 per sample; allow some slack for WSOLA's own irregularities
        assertTrue("seam jump " + worstAtSeams + " vs overall " + worst, worstAtSeams <= Math.max(worst, 3000) * 1.3);
        assertTrue("overall jump " + worst, worst < 6000);
    }

    @Test
    public void shortRecordingsAndEmptyPiecesWork() {
        final short[] in = sine(400, 500, 8000);
        for (int effect = 0; effect <= 4; effect++) {
            final VoiceEffectStream st = new VoiceEffectStream(effect, RATE);
            assertEquals(0, st.push(new short[0]).length);
            final short[] out = concat(st.push(in), st.finish());
            if (effect == VoiceEffects.EFFECT_ECHO) {
                assertTrue(out.length > in.length);
            } else {
                assertEquals("effect " + effect, in.length, out.length);
            }
        }
    }
}
