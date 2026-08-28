package com.moranpcy.networkchaos.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosConfigTest {
    @Test
    void rejectsInvalidRatesTimesAndPatterns() {
        assertThrows(IllegalArgumentException.class,
                () -> new LinkProfile(-0.1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new LinkProfile(0, -1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new LinkProfile(0, 0, 0, 1.1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ChaosConfig.clear().withPacketFilter("[", "(?!)"));
    }

    @Test
    void copyMethodsChangeOnlyTheirDeclaredField() {
        LinkProfile c2s = new LinkProfile(0.1, 10, 2, 0, 0, 0);
        ChaosConfig config = ChaosConfig.clear()
                .withClientToServer(c2s)
                .withSeed(42)
                .withPacketFilter(".*BlockUpdate.*", ".*KeepAlive.*");

        assertEquals(c2s, config.clientToServer());
        assertEquals(LinkProfile.CLEAR, config.serverToClient());
        assertEquals(42, config.seed());
        assertEquals(".*BlockUpdate.*", config.includePacketRegex());
        assertTrue(config.protectControlPackets());
    }

    @Test
    void lifecycleFacadeIsExplicitAndResettable() {
        NetworkChaos.reset();
        ChaosConfig config = ChaosPresets.lossy(7);
        NetworkChaos.enable(config);
        assertTrue(NetworkChaos.isEnabled());
        assertEquals(config, NetworkChaos.config());

        NetworkChaos.reset();
        assertTrue(!NetworkChaos.isEnabled());
        assertEquals(ChaosConfig.clear(), NetworkChaos.config());
    }
}
