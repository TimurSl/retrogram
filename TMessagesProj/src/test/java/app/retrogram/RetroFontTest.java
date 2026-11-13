package app.retrogram;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RetroFontTest {

    @Test
    public void sansStylesMapToWeightAndItalic() {
        assertArrayEquals(new int[]{400, 0}, RetroFont.styleFor("fonts/rregular.ttf"));
        assertArrayEquals(new int[]{700, 0}, RetroFont.styleFor("fonts/rmedium.ttf"));
        assertArrayEquals(new int[]{700, 1}, RetroFont.styleFor("fonts/rmediumitalic.ttf"));
        assertArrayEquals(new int[]{800, 0}, RetroFont.styleFor("fonts/rextrabold.ttf"));
        assertArrayEquals(new int[]{400, 1}, RetroFont.styleFor("fonts/ritalic.ttf"));
    }

    @Test
    public void monoCondensedAndSymbolFontsAreLeftAlone() {
        assertNull(RetroFont.styleFor("fonts/rmono.ttf"));
        assertNull(RetroFont.styleFor("fonts/rcondensedbold.ttf"));
        assertNull(RetroFont.styleFor("fonts/mw_bold.ttf"));
        assertNull(RetroFont.styleFor("fonts/courier_new_bold.ttf"));
        assertNull(RetroFont.styleFor("fonts/num.otf"));
        assertNull(RetroFont.styleFor(null));
    }

    @Test
    public void fontConfigDefaultsToBundledFont() {
        RetroConfig.loadGif((key, def) -> def, (key, def) -> def);
        assertEquals("", RetroConfig.fontFile.String());
        assertEquals("", RetroConfig.fontName.String());

        RetroConfig.loadGif((key, def) -> def,
            (key, def) -> key.equals("RetroFontFile") ? "font_1.ttf" : key.equals("RetroFontName") ? "Lora.ttf" : def);
        assertEquals("font_1.ttf", RetroConfig.fontFile.String());
        assertEquals("Lora.ttf", RetroConfig.fontName.String());
    }
}
