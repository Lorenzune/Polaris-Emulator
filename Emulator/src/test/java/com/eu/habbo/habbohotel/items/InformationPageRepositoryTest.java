package com.eu.habbo.habbohotel.items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InformationPageRepositoryTest {
    @Test void preservesFormattingAndImageLayoutWithoutAllowingArbitraryCss() {
        String html = InformationPageRepository.clean("<p style='font-size:24px;color:#123456;text-align:center;position:fixed;z-index:999'>Testo</p><img src='https://example.com/a.webp' style='width:320px;height:auto;margin-left:auto;display:block'>");
        assertTrue(html.contains("font-size: 24px")); assertTrue(html.contains("text-align: center"));
        assertTrue(html.contains("width: 320px")); assertTrue(html.contains("margin-left: auto"));
        assertFalse(html.contains("position")); assertFalse(html.contains("z-index"));
    }
    @Test void keepsRichContentAndRemovesExecutableMarkup() {
        String html = InformationPageRepository.clean("<h2>Titolo</h2><ul><li><b>Testo</b></li></ul><script>alert(1)</script><a href='javascript:alert(1)'>Link</a><img src='https://example.com/a.webp' onerror='alert(1)'><iframe src='https://example.com'></iframe>");
        assertTrue(html.contains("<h2>Titolo</h2>"));
        assertTrue(html.contains("https://example.com/a.webp"));
        assertFalse(html.contains("script")); assertFalse(html.contains("onerror")); assertFalse(html.contains("iframe"));
    }
}
