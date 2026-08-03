package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SacredMountainShapeTest {
    private static final SacredMountainShape SHAPE =
            new SacredMountainShape(SacredMountainShape.DEFAULT_SEED);

    @Test
    void shapeIsDeterministicAndFeathersInsideTheDeclaredBounds() {
        SacredMountainShape second = new SacredMountainShape(SacredMountainShape.DEFAULT_SEED);
        for (int x = -380; x <= 380; x += 13) {
            for (int z = -380; z <= 380; z += 13) {
                assertEquals(SHAPE.heightAt(x, z), second.heightAt(x, z));
            }
        }
        assertEquals(0, SHAPE.heightAt(-385, 0));
        assertEquals(0, SHAPE.heightAt(384, 0));
        assertEquals(0, SHAPE.heightAt(0, -385));
        assertEquals(0, SHAPE.heightAt(0, 384));
        for (int coordinate = -380; coordinate <= 380; coordinate += 20) {
            assertTrue(SHAPE.heightAt(coordinate, -380) <= 8);
            assertTrue(SHAPE.heightAt(coordinate, 380) <= 8);
            assertTrue(SHAPE.heightAt(-380, coordinate) <= 8);
            assertTrue(SHAPE.heightAt(380, coordinate) <= 8);
        }
    }

    @Test
    void fixedFaultGraphHasNestAndFourReadableSilhouetteLayers() {
        assertTrue(between(SHAPE.heightAt(38, 0), 207, 213));
        assertTrue(SHAPE.heightAt(-88, -168) >= 276, "storm blade lost its central tooth");
        assertTrue(regionalMaximum(-220, -70, -20, 165) >= 248, "broken crown lost height");
        assertTrue(SHAPE.heightAt(88, 176) >= 238, "solar ridge lost its late high point");
        assertTrue(SHAPE.heightAt(-116, -30) >= 224, "storm/crown saddle was severed");

        int maximum = 0;
        int maximumX = 0;
        int maximumZ = 0;
        for (int x = -200; x <= 150; x += 2) {
            for (int z = -220; z <= 220; z += 2) {
                int height = SHAPE.heightAt(x, z);
                if (height > maximum) {
                    maximum = height;
                    maximumX = x;
                    maximumZ = z;
                }
            }
        }
        assertTrue(maximum >= 279 && maximum <= SacredMountainShape.MAX_RELATIVE_HEIGHT);
        assertTrue(Math.hypot(maximumX + 88.0D, maximumZ + 168.0D) <= 8.0D,
                "highest point moved away from the fixed storm peak");
    }

    @Test
    void highContoursAreElongatedFaultRidgesInsteadOfCircularCones() {
        ContourMetrics storm = contourMetrics(-235, 20, -285, -45, 214);
        ContourMetrics crown = contourMetrics(-235, -58, -45, 175, 194);
        ContourMetrics solar = contourMetrics(-125, 175, 82, 252, 194);

        assertTrue(storm.circularity() < 0.72D, "storm circularity=" + storm.circularity());
        assertTrue(crown.circularity() < 0.72D, "crown circularity=" + crown.circularity());
        assertTrue(solar.circularity() < 0.72D, "solar circularity=" + solar.circularity());
        assertTrue(storm.aspectRatio() >= 1.35D, "storm aspect=" + storm.aspectRatio());
        assertTrue(crown.aspectRatio() >= 1.35D, "crown aspect=" + crown.aspectRatio());
        assertTrue(solar.aspectRatio() >= 2.0D, "solar aspect=" + solar.aspectRatio());

        assertFalse(hasNearCircularCliffRing(-88, -168, 90));
        assertFalse(hasNearCircularCliffRing(-162, 112, 90));
        assertFalse(hasNearCircularCliffRing(88, 176, 96));
        assertTrue(connectedAbove(-110, -190, -60, -139, 220),
                "storm teeth no longer share a high blade");
        assertTrue(connectedAbove(-88, -168, 88, 176, 176),
                "main high ridge and its authored saddles became isolated mesas");
    }

    @Test
    void seedChangesErosionButNotAuthoredLayout() {
        SacredMountainShape alternate = new SacredMountainShape(42L);
        assertEquals(SHAPE.routeNodes(), alternate.routeNodes());
        assertTrue(Math.abs(SHAPE.heightAt(-88, -168) - alternate.heightAt(-88, -168)) <= 3);
        assertTrue(Math.abs(SHAPE.heightAt(38, 0) - alternate.heightAt(38, 0)) <= 3);
        int differingSamples = 0;
        for (int x = -320; x <= 320; x += 20) {
            for (int z = -320; z <= 320; z += 20) {
                if (SHAPE.heightAt(x, z) != alternate.heightAt(x, z)) {
                    differingSamples++;
                }
            }
        }
        assertTrue(differingSamples > 150, "seed no longer affects erosion detail");
        assertNotEquals(
                SHAPE.materialAt(78, SHAPE.heightAt(78, 159), 159),
                alternate.materialAt(78, alternate.heightAt(78, 159), 159));
    }

    @Test
    void fastInteriorMaterialPathMatchesFullBoundaryClassification() {
        for (int x = -360; x <= 360; x += 17) {
            for (int z = -360; z <= 360; z += 19) {
                int maximumY = SHAPE.maximumSolidHeightAt(x, z);
                for (int y = 1; y <= maximumY; y += 7) {
                    if (SHAPE.isSolid(x, y, z)) {
                        assertEquals(
                                SHAPE.materialAtKnownSolidReference(x, y, z),
                                SHAPE.materialAtKnownSolid(x, y, z),
                                "material shortcut changed " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void guaranteedStoneColumnsContainNoCarvingOrNonStoneBoundary() {
        for (int x = -380; x <= 380; x += 13) {
            for (int z = -380; z <= 380; z += 13) {
                int guaranteedHeight = SHAPE.guaranteedStoneHeightAt(x, z);
                for (int y = 1; y <= guaranteedHeight; y += 5) {
                    assertTrue(SHAPE.isSolid(x, y, z),
                            "guaranteed stone crossed air at " + x + "," + y + "," + z);
                    assertEquals(SacredMountainShape.SurfaceMaterial.STONE,
                            SHAPE.materialAtKnownSolidReference(x, y, z),
                            "guaranteed stone crossed a material boundary at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void authoredRouteIsContinuousWalkableAndHasHeadroom() {
        List<SacredMountainShape.RouteNode> nodes = SHAPE.routeNodes();
        Integer previousHeight = null;
        int sampled = 0;
        for (int nodeIndex = 0; nodeIndex < nodes.size() - 1; nodeIndex++) {
            var start = nodes.get(nodeIndex);
            var end = nodes.get(nodeIndex + 1);
            int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
            for (int step = 0; step <= steps; step++) {
                double amount = step / (double) steps;
                int x = (int) Math.round(start.x() + ((end.x() - start.x()) * amount));
                int z = (int) Math.round(start.z() + ((end.z() - start.z()) * amount));
                int height = SHAPE.heightAt(x, z);
                assertTrue(SHAPE.isRoute(x, z));
                assertTrue(SHAPE.isSolid(x, height, z), "route floor missing at " + x + " " + z);
                for (int y = height + 1; y <= height + 4; y++) {
                    assertFalse(SHAPE.isSolid(x, y, z), "route headroom blocked at " + x + " " + y + " " + z);
                }
                if (previousHeight != null) {
                    assertTrue(Math.abs(height - previousHeight) <= 1,
                            "route rises by more than one block at " + x + " " + z);
                }
                previousHeight = height;
                sampled++;
            }
        }
        assertTrue(sampled > 350);
        assertTrue(SHAPE.heightAt(nodes.getFirst().x(), nodes.getFirst().z()) <= 8);
        assertEquals(SacredMountainShape.NEST_HEIGHT,
                SHAPE.heightAt(nodes.getLast().x(), nodes.getLast().z()));
        assertTrue(routeSearchReachesSummit(nodes.getFirst(), nodes.getLast()));

        for (int index = 0; index < nodes.size() - 1; index++) {
            var start = nodes.get(index);
            var end = nodes.get(index + 1);
            double centerX = (start.x() + end.x()) * 0.5D;
            double centerZ = (start.z() + end.z()) * 0.5D;
            double dx = end.x() - start.x();
            double dz = end.z() - start.z();
            double length = Math.hypot(dx, dz);
            double normalX = -dz / length;
            double normalZ = dx / length;
            int crossSection = 0;
            for (int offset = -12; offset <= 12; offset++) {
                int x = (int) Math.round(centerX + (normalX * offset));
                int z = (int) Math.round(centerZ + (normalZ * offset));
                if (SHAPE.isRoute(x, z)) {
                    crossSection++;
                }
            }
            assertTrue(crossSection >= 10 && crossSection <= 16,
                    "route width=" + crossSection + " at segment " + index);
        }
    }

    @Test
    void largeNegativeSpacesHaveSolidConnectedShells() {
        assertTrue(SHAPE.insideWindArchOpening(-121, 167, 45));
        assertFalse(SHAPE.isSolid(-121, 167, 45));
        assertTrue(SHAPE.isSolid(-121, 205, 45), "wind arch lost its crown");
        assertTrue(SHAPE.insideWindArchOpening(-112, 167, 63));
        assertTrue(SHAPE.insideWindArchOpening(-130, 167, 27));

        assertTrue(SHAPE.insideEastUndercroft(153, 157, 0));
        assertFalse(SHAPE.isSolid(153, 157, 0));
        assertTrue(SHAPE.isSolid(77, 200, 0), "east overhang disconnected from the mountain");

        assertTrue(SHAPE.insideNorthCleft(-100, 170, -151));
        assertFalse(SHAPE.isSolid(-100, 170, -151));
        assertTrue(SHAPE.isSolid(-65, 170, -151) || SHAPE.isSolid(-140, 170, -151));
    }

    @Test
    void reservedEncounterVolumeContainsNoAuthoredRock() {
        for (int x = SacredMountainShape.NEST_CENTER_X - SacredMountainShape.NEST_CLEARANCE_WIDTH / 2;
                x <= SacredMountainShape.NEST_CENTER_X + SacredMountainShape.NEST_CLEARANCE_WIDTH / 2;
                x += 5) {
            for (int z = SacredMountainShape.NEST_CENTER_Z - SacredMountainShape.NEST_CLEARANCE_DEPTH / 2;
                    z <= SacredMountainShape.NEST_CENTER_Z + SacredMountainShape.NEST_CLEARANCE_DEPTH / 2;
                    z += 5) {
                for (int y = SacredMountainShape.NEST_HEIGHT + 1;
                        y <= SacredMountainShape.NEST_HEIGHT + SacredMountainShape.NEST_CLEARANCE_HEIGHT;
                        y += 5) {
                    assertFalse(SHAPE.isSolid(x, y, z));
                }
            }
        }
    }

    @Test
    void easternTakeoffCorridorRemainsOpenAboveTheCliffLip() {
        for (int x = 124; x <= 360; x += 4) {
            for (int z = -48; z <= 48; z += 4) {
                for (int y = SacredMountainShape.NEST_HEIGHT + 1;
                        y <= SacredMountainShape.NEST_HEIGHT + SacredMountainShape.NEST_CLEARANCE_HEIGHT;
                        y += 5) {
                    assertFalse(SHAPE.isSolid(x, y, z),
                            "takeoff corridor blocked at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void coarseVoxelAuditFindsNoFloatingRockComponents() {
        int sampleStep = 4;
        int sampleOffset = sampleStep / 2;
        int horizontalSize = SacredMountainShape.DIAMETER / sampleStep;
        int verticalSize = SacredMountainShape.MAX_RELATIVE_HEIGHT / sampleStep;
        int layerSize = horizontalSize * horizontalSize;
        boolean[] solid = new boolean[layerSize * verticalSize];
        int solidCount = 0;
        for (int yIndex = 0; yIndex < verticalSize; yIndex++) {
            int y = sampleOffset + (yIndex * sampleStep);
            for (int zIndex = 0; zIndex < horizontalSize; zIndex++) {
                int z = SacredMountainShape.MIN_COORDINATE + sampleOffset + (zIndex * sampleStep);
                for (int xIndex = 0; xIndex < horizontalSize; xIndex++) {
                    int x = SacredMountainShape.MIN_COORDINATE + sampleOffset + (xIndex * sampleStep);
                    int index = (yIndex * layerSize) + (zIndex * horizontalSize) + xIndex;
                    solid[index] = SHAPE.isSolid(x, y, z);
                    if (solid[index]) {
                        solidCount++;
                    }
                }
            }
        }

        boolean[] visited = new boolean[solid.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < layerSize; index++) {
            if (solid[index]) {
                visited[index] = true;
                queue.addLast(index);
            }
        }
        int visitedCount = 0;
        int[] horizontal = {-1, 1, -horizontalSize, horizontalSize};
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            visitedCount++;
            int local = current % layerSize;
            int x = local % horizontalSize;
            int z = local / horizontalSize;
            for (int offset : horizontal) {
                int next = current + offset;
                if (next < 0 || next >= solid.length || visited[next] || !solid[next]) {
                    continue;
                }
                int nextLocal = next % layerSize;
                int nextX = nextLocal % horizontalSize;
                int nextZ = nextLocal / horizontalSize;
                if (Math.abs(nextX - x) + Math.abs(nextZ - z) != 1) {
                    continue;
                }
                visited[next] = true;
                queue.addLast(next);
            }
            for (int offset : new int[] {-layerSize, layerSize}) {
                int next = current + offset;
                if (next >= 0 && next < solid.length && solid[next] && !visited[next]) {
                    visited[next] = true;
                    queue.addLast(next);
                }
            }
        }
        int firstFloating = -1;
        for (int index = 0; index < solid.length; index++) {
            if (solid[index] && !visited[index]) {
                firstFloating = index;
                break;
            }
        }
        int floatingIndex = firstFloating;
        assertEquals(solidCount, visitedCount, () -> {
            int yIndex = floatingIndex / layerSize;
            int local = floatingIndex % layerSize;
            int xIndex = local % horizontalSize;
            int zIndex = local / horizontalSize;
            return "coarse audit found floating rock near "
                    + (SacredMountainShape.MIN_COORDINATE + sampleOffset + (xIndex * sampleStep)) + ","
                    + (sampleOffset + (yIndex * sampleStep)) + ","
                    + (SacredMountainShape.MIN_COORDINATE + sampleOffset + (zIndex * sampleStep));
        });
    }

    private static int regionalMaximum(int minimumX, int maximumX, int minimumZ, int maximumZ) {
        int maximum = 0;
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                maximum = Math.max(maximum, SHAPE.heightAt(x, z));
            }
        }
        return maximum;
    }

    private static ContourMetrics contourMetrics(
            int minimumX,
            int maximumX,
            int minimumZ,
            int maximumZ,
            int threshold) {
        long area = 0;
        long perimeter = 0;
        double sumX = 0.0D;
        double sumZ = 0.0D;
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                if (SHAPE.heightAt(x, z) < threshold) {
                    continue;
                }
                area++;
                sumX += x;
                sumZ += z;
                for (int[] direction : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                    int neighborX = x + direction[0];
                    int neighborZ = z + direction[1];
                    if (neighborX < minimumX || neighborX > maximumX
                            || neighborZ < minimumZ || neighborZ > maximumZ
                            || SHAPE.heightAt(neighborX, neighborZ) < threshold) {
                        perimeter++;
                    }
                }
            }
        }
        assertTrue(area > 100, "contour region unexpectedly empty at Y=" + threshold);
        double centerX = sumX / area;
        double centerZ = sumZ / area;
        double covarianceXX = 0.0D;
        double covarianceXZ = 0.0D;
        double covarianceZZ = 0.0D;
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                if (SHAPE.heightAt(x, z) < threshold) {
                    continue;
                }
                double dx = x - centerX;
                double dz = z - centerZ;
                covarianceXX += dx * dx;
                covarianceXZ += dx * dz;
                covarianceZZ += dz * dz;
            }
        }
        covarianceXX /= area;
        covarianceXZ /= area;
        covarianceZZ /= area;
        double trace = covarianceXX + covarianceZZ;
        double discriminant = Math.sqrt(
                Math.max(0.0D, ((covarianceXX - covarianceZZ) * (covarianceXX - covarianceZZ))
                        + (4.0D * covarianceXZ * covarianceXZ)));
        double major = (trace + discriminant) * 0.5D;
        double minor = Math.max(1.0e-9D, (trace - discriminant) * 0.5D);
        double circularity = (4.0D * Math.PI * area) / (perimeter * (double) perimeter);
        return new ContourMetrics(circularity, Math.sqrt(major / minor));
    }

    private static boolean hasNearCircularCliffRing(int centerX, int centerZ, int maximumRadius) {
        int[] distances = new int[24];
        int found = 0;
        for (int direction = 0; direction < distances.length; direction++) {
            double angle = direction * (Math.PI * 2.0D / distances.length);
            for (int radius = 8; radius <= maximumRadius; radius++) {
                int innerX = (int) Math.round(centerX + (Math.cos(angle) * (radius - 8)));
                int innerZ = (int) Math.round(centerZ + (Math.sin(angle) * (radius - 8)));
                int outerX = (int) Math.round(centerX + (Math.cos(angle) * radius));
                int outerZ = (int) Math.round(centerZ + (Math.sin(angle) * radius));
                if (SHAPE.heightAt(innerX, innerZ) - SHAPE.heightAt(outerX, outerZ) > 35) {
                    distances[found++] = radius;
                    break;
                }
            }
        }
        if (found < 22) {
            return false;
        }
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        double sum = 0.0D;
        for (int index = 0; index < found; index++) {
            minimum = Math.min(minimum, distances[index]);
            maximum = Math.max(maximum, distances[index]);
            sum += distances[index];
        }
        double mean = sum / found;
        double variance = 0.0D;
        for (int index = 0; index < found; index++) {
            double difference = distances[index] - mean;
            variance += difference * difference;
        }
        double coefficientOfVariation = Math.sqrt(variance / found) / mean;
        return maximum - minimum <= 14 && coefficientOfVariation < 0.14D;
    }

    private static boolean connectedAbove(
            int startX, int startZ, int endX, int endZ, int minimumHeight) {
        int size = SacredMountainShape.DIAMETER;
        boolean[] visited = new boolean[size * size];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        if (SHAPE.heightAt(startX, startZ) < minimumHeight
                || SHAPE.heightAt(endX, endZ) < minimumHeight) {
            return false;
        }
        int startIndex = routeIndex(startX, startZ);
        int endIndex = routeIndex(endX, endZ);
        visited[startIndex] = true;
        queue.addLast(startIndex);
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            if (current == endIndex) {
                return true;
            }
            int x = (current % size) + SacredMountainShape.MIN_COORDINATE;
            int z = (current / size) + SacredMountainShape.MIN_COORDINATE;
            for (int[] direction : directions) {
                int nextX = x + direction[0];
                int nextZ = z + direction[1];
                if (nextX < SacredMountainShape.MIN_COORDINATE
                        || nextX > SacredMountainShape.MAX_COORDINATE
                        || nextZ < SacredMountainShape.MIN_COORDINATE
                        || nextZ > SacredMountainShape.MAX_COORDINATE
                        || SHAPE.heightAt(nextX, nextZ) < minimumHeight) {
                    continue;
                }
                int next = routeIndex(nextX, nextZ);
                if (!visited[next]) {
                    visited[next] = true;
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    private static boolean routeSearchReachesSummit(
            SacredMountainShape.RouteNode start, SacredMountainShape.RouteNode end) {
        int size = SacredMountainShape.DIAMETER;
        boolean[] visited = new boolean[size * size];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int startIndex = routeIndex(start.x(), start.z());
        int endIndex = routeIndex(end.x(), end.z());
        visited[startIndex] = true;
        queue.add(startIndex);
        int[][] directions = {
                {-1, -1}, {0, -1}, {1, -1},
                {-1, 0}, {1, 0},
                {-1, 1}, {0, 1}, {1, 1}
        };
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            if (current == endIndex) {
                return true;
            }
            int x = (current % size) + SacredMountainShape.MIN_COORDINATE;
            int z = (current / size) + SacredMountainShape.MIN_COORDINATE;
            int height = SHAPE.heightAt(x, z);
            for (int[] direction : directions) {
                int nextX = x + direction[0];
                int nextZ = z + direction[1];
                if (nextX < SacredMountainShape.MIN_COORDINATE
                        || nextX > SacredMountainShape.MAX_COORDINATE
                        || nextZ < SacredMountainShape.MIN_COORDINATE
                        || nextZ > SacredMountainShape.MAX_COORDINATE
                        || !SHAPE.isRoute(nextX, nextZ)) {
                    continue;
                }
                int nextIndex = routeIndex(nextX, nextZ);
                if (!visited[nextIndex] && Math.abs(SHAPE.heightAt(nextX, nextZ) - height) <= 1) {
                    visited[nextIndex] = true;
                    queue.addLast(nextIndex);
                }
            }
        }
        return false;
    }

    private static int routeIndex(int x, int z) {
        return ((z - SacredMountainShape.MIN_COORDINATE) * SacredMountainShape.DIAMETER)
                + (x - SacredMountainShape.MIN_COORDINATE);
    }

    private static boolean between(int value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }

    private record ContourMetrics(double circularity, double aspectRatio) {
    }
}
