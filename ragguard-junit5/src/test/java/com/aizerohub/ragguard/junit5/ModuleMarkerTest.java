package com.aizerohub.ragguard.junit5;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ModuleMarkerTest {

    @Test
    void moduleCompiles() {
        assertNotNull(ModuleMarker.class);
    }
}
