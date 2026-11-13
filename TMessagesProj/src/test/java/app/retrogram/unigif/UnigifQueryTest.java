package app.retrogram.unigif;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UnigifQueryTest {

    @Test
    public void normalizeKeepsCaseAndOneTrailingSpace() {
        assertEquals("", UnigifQuery.normalize(null));
        assertEquals("", UnigifQuery.normalize(""));
        assertEquals("", UnigifQuery.normalize("  \t "));
        assertEquals("Cat", UnigifQuery.normalize("Cat"));
        assertEquals("Cat", UnigifQuery.normalize("  Cat"));
        assertEquals("cat ", UnigifQuery.normalize("cat "));
        assertEquals("cat ", UnigifQuery.normalize(" cat   "));
        assertEquals("cat funny ", UnigifQuery.normalize("cat funny "));
        assertEquals("cat  -dog", UnigifQuery.normalize("cat  -dog"));
    }

    @Test
    public void lastWord() {
        assertEquals("", UnigifQuery.lastWord(null));
        assertEquals("", UnigifQuery.lastWord(""));
        assertEquals("ca", UnigifQuery.lastWord("ca"));
        assertEquals("fu", UnigifQuery.lastWord("cat fu"));
        assertEquals("", UnigifQuery.lastWord("cat "));
        assertEquals("do", UnigifQuery.lastWord("cat -do"));
        assertEquals("", UnigifQuery.lastWord("cat -"));
        assertEquals("do", UnigifQuery.lastWord("#do"));
        assertEquals("do", UnigifQuery.lastWord("-#do"));
        assertEquals("b-c", UnigifQuery.lastWord("a b-c"));
    }

    @Test
    public void replaceLastWord() {
        assertEquals("cat ", UnigifQuery.replaceLastWord("ca", "cat"));
        assertEquals("cat funny ", UnigifQuery.replaceLastWord("cat fu", "funny"));
        assertEquals("cat -dog ", UnigifQuery.replaceLastWord("cat -do", "dog"));
        assertEquals("cat funny ", UnigifQuery.replaceLastWord("cat ", "funny"));
        assertEquals("cat ", UnigifQuery.replaceLastWord("", "cat"));
        assertEquals("cat ", UnigifQuery.replaceLastWord(null, "#cat"));
        assertEquals("  cat ", UnigifQuery.replaceLastWord("  c", " cat "));
    }

    @Test
    public void chipFlowFromThePlan() {
        String text = UnigifQuery.replaceLastWord("ca", "cat");
        text = UnigifQuery.replaceLastWord(text + "fu", "funny");
        assertEquals("cat funny ", UnigifQuery.normalize(text));
    }
}
