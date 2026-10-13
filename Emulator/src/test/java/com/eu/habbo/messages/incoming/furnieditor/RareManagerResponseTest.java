package com.eu.habbo.messages.incoming.furnieditor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RareManagerResponseTest {
    @Test void largeMultilingualCatalogueResponsesFitHabboStringLengthsAndRoundTrip() {
        String response="\"\\\n🙂Categorie 日本語".repeat(10000);
        var parts=RareEconomyCommandEvent.splitResponse(response);
        assertEquals(response,String.join("",parts));
        for(int index=0;index<parts.size();index++) {
            String packet=new com.google.gson.Gson().toJson(Map.of("action","admin-categories","requestId","abc",
                "chunk",Map.of("index",index,"total",parts.size(),"payload",parts.get(index))));
            assertTrue(packet.getBytes(StandardCharsets.UTF_8).length<65536);
            assertFalse(Character.isHighSurrogate(parts.get(index).charAt(parts.get(index).length()-1)));
        }
    }
}
