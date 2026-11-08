package app.retrogram.graph;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Stateful, segment-by-segment version of {@link VoiceEffects} (WP 6.4), so a long recording can be processed in pieces without seams.
 * Pure Java, no Android dependencies.
 *
 * Usage: call {@link #push} with each piece of PCM in order; whatever it returns is ready to be encoded (it may be shorter than the input
 * because some samples are held back, or empty); call {@link #finish} once at the end for the rest. The concatenated output has the length of the
 * input, plus the echo tail for {@link VoiceEffects#EFFECT_ECHO}.
 *
 * - robot: the carrier phase continues across pieces (exactly the same as processing everything at once);
 * - echo: the history of the last delay x repeats samples is kept, so the output equals the whole-signal echo exactly, tail included;
 * - higher / deeper voice: each piece is processed together with the last 60 ms of the previous one as context, and the overlapping
 *   region is crossfaded, which removes the click and the phase jump a cut would cause.
 */
public final class VoiceEffectStream {

    private static final int ECHO_DELAY_MS = 220;
    private static final double ECHO_DECAY = 0.45;
    private static final int ECHO_REPEATS = 2;

    private final int effect;
    private final int rate;
    private final double pitchFactor;

    private long samplesDone;           // robot phase
    private short[] echoHistory;        // last delay*repeats input samples (zeros before the start)
    private final int overlap;          // pitch context and crossfade length in samples
    private short[] pitchTailRaw = new short[0];    // last `overlap` raw input samples of the previous piece
    private short[] pitchPending = new short[0];    // processed version of those samples, held back until the next piece
    private short[] pitchHeld = new short[0];       // raw samples not processed yet (piece too short)
    private boolean pitchStarted;
    private boolean finished;

    public VoiceEffectStream(int effect, int sampleRate) {
        this.effect = effect;
        this.rate = sampleRate;
        this.pitchFactor = effect == VoiceEffects.EFFECT_HIGHER ? 1.35 : 0.75;
        this.overlap = 3 * Math.max(64, sampleRate / 50);
        final int echoLen = Math.max(1, ECHO_DELAY_MS * sampleRate / 1000) * ECHO_REPEATS;
        this.echoHistory = new short[echoLen];
    }

    private static short clip(double v) {
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) Math.round(v);
    }

    public short[] push(short[] piece) {
        if (piece.length == 0) {
            return new short[0];
        }
        switch (effect) {
            case VoiceEffects.EFFECT_ROBOT: {
                final short[] out = VoiceEffects.ringModulate(piece, rate, 55, samplesDone);
                samplesDone += piece.length;
                return out;
            }
            case VoiceEffects.EFFECT_ECHO:
                return pushEcho(piece);
            case VoiceEffects.EFFECT_HIGHER:
            case VoiceEffects.EFFECT_DEEPER:
                return pushPitch(piece);
            default:
                return piece.clone();
        }
    }

    /** The samples still held back, and for echo the tail. Call once, after the last {@link #push}. */
    public short[] finish() {
        if (finished) {
            return new short[0]; // the pause path and the final stop may both flush; the tail must only be written once
        }
        finished = true;
        switch (effect) {
            case VoiceEffects.EFFECT_ECHO:
                return finishEcho();
            case VoiceEffects.EFFECT_HIGHER:
            case VoiceEffects.EFFECT_DEEPER:
                return finishPitch();
            default:
                return new short[0];
        }
    }

    // ---- echo ----

    private short[] pushEcho(short[] piece) {
        final int d = echoHistory.length / ECHO_REPEATS;
        final int h = echoHistory.length;
        final short[] combined = new short[h + piece.length];
        System.arraycopy(echoHistory, 0, combined, 0, h);
        System.arraycopy(piece, 0, combined, h, piece.length);
        final short[] out = new short[piece.length];
        for (int i = 0; i < piece.length; i++) {
            double sum = piece[i];
            double gain = 1;
            for (int r = 1; r <= ECHO_REPEATS; r++) {
                gain *= ECHO_DECAY;
                sum += combined[h + i - r * d] * gain;
            }
            out[i] = clip(sum);
        }
        echoHistory = Arrays.copyOfRange(combined, combined.length - h, combined.length);
        return out;
    }

    private short[] finishEcho() {
        final int h = echoHistory.length;
        final int d = h / ECHO_REPEATS;
        final short[] tail = new short[h];
        for (int m = 0; m < h; m++) {
            double sum = 0;
            double gain = 1;
            for (int r = 1; r <= ECHO_REPEATS; r++) {
                gain *= ECHO_DECAY;
                if (m < r * d) {
                    sum += echoHistory[m - r * d + h] * gain;
                }
            }
            tail[m] = clip(sum);
        }
        return tail;
    }

    // ---- pitch ----

    private static short[] concat(short[] a, short[] b) {
        final short[] out = new short[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static short[] crossfade(short[] earlier, short[] later, int n) {
        final short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            final double w = (i + 0.5) / n; // the later computation has more context towards the end of the region
            out[i] = clip(earlier[i] * (1 - w) + later[i] * w);
        }
        return out;
    }

    private short[] pushPitch(short[] piece) {
        final short[] fresh = concat(pitchHeld, piece);
        if (fresh.length < 2 * overlap) {
            pitchHeld = fresh; // too short to process on its own: wait for more
            return new short[0];
        }
        pitchHeld = new short[0];
        final int c = pitchTailRaw.length;
        final short[] input = concat(pitchTailRaw, fresh);
        final short[] processed = VoiceEffects.pitchShift(input, rate, pitchFactor);
        final int n = input.length;
        final ArrayList<short[]> parts = new ArrayList<>(2);
        if (!pitchStarted) {
            parts.add(Arrays.copyOfRange(processed, 0, n - overlap));
            pitchStarted = true;
        } else {
            parts.add(crossfade(pitchPending, Arrays.copyOfRange(processed, 0, c), c));
            parts.add(Arrays.copyOfRange(processed, c, n - overlap));
        }
        pitchPending = Arrays.copyOfRange(processed, n - overlap, n);
        pitchTailRaw = Arrays.copyOfRange(input, n - overlap, n);
        return joinParts(parts);
    }

    private static short[] joinParts(ArrayList<short[]> parts) {
        int total = 0;
        for (short[] p : parts) {
            total += p.length;
        }
        final short[] out = new short[total];
        int pos = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private short[] finishPitch() {
        if (!pitchStarted) {
            // never processed anything: the whole recording is shorter than two overlaps
            return pitchHeld.length == 0 ? new short[0] : VoiceEffects.pitchShift(pitchHeld, rate, pitchFactor);
        }
        final int c = pitchTailRaw.length;
        final short[] input = concat(pitchTailRaw, pitchHeld);
        final short[] processed = VoiceEffects.pitchShift(input, rate, pitchFactor);
        final ArrayList<short[]> parts = new ArrayList<>(2);
        parts.add(crossfade(pitchPending, Arrays.copyOfRange(processed, 0, c), c));
        parts.add(Arrays.copyOfRange(processed, c, input.length));
        pitchHeld = new short[0];
        pitchStarted = false;
        return joinParts(parts);
    }
}
