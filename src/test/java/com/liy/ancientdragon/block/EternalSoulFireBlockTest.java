package com.liy.ancientdragon.block;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class EternalSoulFireBlockTest {
    @Test
    void survivesWithoutASoulSoilSupportBlock() {
        assertTrue(EternalSoulFireBlock.permitsUnsupportedPlacement());
    }
}
