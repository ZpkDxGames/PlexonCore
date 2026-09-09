package com.zpkdxgames.plexoncore.origin;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockOriginPackingTest {
    @Test void packedCoordinatesRemainUniqueAcrossRepresentativeWorldPositions() {
        int[][] positions = {
            {0, 64, 0}, {1, 64, 0}, {0, 65, 0}, {0, 64, 1},
            {-1, 64, 0}, {0, -64, 0}, {0, 320, -1},
            {30_000_000, 100, 30_000_000}, {-30_000_000, -64, -30_000_000}
        };
        Set<Long> packed = new HashSet<>();
        for (int[] position : positions) packed.add(BlockOriginService.pack(position[0], position[1], position[2]));
        assertEquals(positions.length, packed.size());
    }
}
