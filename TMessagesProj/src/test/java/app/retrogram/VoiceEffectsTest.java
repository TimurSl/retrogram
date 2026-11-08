// SPDX-License-Identifier: GPL-3.0-or-later
package app.retrogram;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import app.retrogram.graph.VoiceEffects;

import org.junit.Test;

public class VoiceEffectsTest {

    private static final int RATE = 16000;

    private static short[] sine(double hz, int samples, double amp) {
        final short[] s = new short[samples];
        for (int i = 0; i < samples; i++) {
            s[i] = (short) Math.round(amp * Math.sin(2 * Math.PI * hz * i / RATE));
        }
        return s;
    }

    /** Goertzel magnitude of one frequency, normalised by length. */
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
    public void echoOfImpulseHasDecayingCopies() {
        final short[] in = new short[100];
        in[0] = 10000;
        final short[] out = VoiceEffects.echo(in, 1000, 20, 0.5, 2); // 20 samples at 1 kHz
        assertEquals(100 + 40, out.length);
        assertEquals(10000, out[0]);
        assertEquals(5000, out[20]);
        assertEquals(2500, out[40]);
        assertEquals(0, out[10]);
    }

    @Test
    public void echoClipsInsteadOfWrapping() {
        final short[] in = {Short.MAX_VALUE, Short.MAX_VALUE};
        final short[] out = VoiceEffects.echo(in, 1000, 1, 1.0, 1);
        assertEquals(Short.MAX_VALUE, out[1]);
    }

    @Test
    public void ringModulationMovesEnergyToSumAndDifferenceFrequencies() {
        final short[] in = sine(1000, 16000, 10000);
        final short[] out = VoiceEffects.ringModulate(in, RATE, 300);
        assertTrue(power(out, 1300) > 1000);
        assertTrue(power(out, 700) > 1000);
        assertTrue("original tone is gone", power(out, 1000) < 100);
    }

    @Test
    public void pitchShiftUpMovesTheToneAndKeepsLength() {
        final short[] in = sine(400, 16000, 10000);
        final short[] up = VoiceEffects.pitchShift(in, RATE, 1.5);
        assertEquals(in.length, up.length);
        final double at600 = power(up, 600);
        final double at400 = power(up, 400);
        assertTrue("tone near 600 Hz (" + at600 + " vs " + at400 + ")", at600 > 3 * at400);
        assertTrue(at600 > 1500);
    }

    @Test
    public void pitchShiftDownMovesTheTone() {
        final short[] in = sine(600, 16000, 10000);
        final short[] down = VoiceEffects.pitchShift(in, RATE, 0.75);
        assertEquals(in.length, down.length);
        assertTrue(power(down, 450) > 3 * power(down, 600));
    }

    @Test
    public void neutralFactorAndEmptyInputAreIdentity() {
        final short[] in = sine(300, 800, 5000);
        assertArrayEquals(in, VoiceEffects.pitchShift(in, RATE, 1.0));
        assertEquals(0, VoiceEffects.pitchShift(new short[0], RATE, 1.5).length);
    }

    @Test
    public void inputIsNeverModified() {
        final short[] in = sine(500, 2000, 8000);
        final short[] copy = in.clone();
        VoiceEffects.pitchShift(in, RATE, 1.3);
        VoiceEffects.ringModulate(in, RATE, 200);
        VoiceEffects.echo(in, RATE, 50, 0.4, 2);
        assertArrayEquals(copy, in);
    }

    @Test
    public void applyOffIsIdentityAndEchoOnlyExtendsTheFinalSegment() {
        final short[] in = sine(500, 4000, 6000);
        assertArrayEquals(in, VoiceEffects.apply(VoiceEffects.EFFECT_OFF, in, RATE, true, 0));
        assertEquals(in.length, VoiceEffects.apply(VoiceEffects.EFFECT_ECHO, in, RATE, false, 0).length);
        assertTrue(VoiceEffects.apply(VoiceEffects.EFFECT_ECHO, in, RATE, true, 0).length > in.length);
    }

    @Test
    public void robotVoiceIsContinuousAcrossSegments() {
        final short[] in = sine(700, 3200, 8000);
        final short[] whole = VoiceEffects.apply(VoiceEffects.EFFECT_ROBOT, in, RATE, true, 0);
        final short[] a = VoiceEffects.apply(VoiceEffects.EFFECT_ROBOT, java.util.Arrays.copyOfRange(in, 0, 1000), RATE, false, 0);
        final short[] b = VoiceEffects.apply(VoiceEffects.EFFECT_ROBOT, java.util.Arrays.copyOfRange(in, 1000, 3200), RATE, true, 1000);
        final short[] joined = new short[whole.length];
        System.arraycopy(a, 0, joined, 0, a.length);
        System.arraycopy(b, 0, joined, a.length, b.length);
        assertArrayEquals(whole, joined);
    }

    @Test
    public void higherAndDeeperVoicesKeepLength() {
        final short[] in = sine(400, 8000, 8000);
        assertEquals(in.length, VoiceEffects.apply(VoiceEffects.EFFECT_HIGHER, in, RATE, true, 0).length);
        assertEquals(in.length, VoiceEffects.apply(VoiceEffects.EFFECT_DEEPER, in, RATE, true, 0).length);
    }
}
