package com.liy.ancientdragon.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Fixed authoring blueprint for the 768-block Sacred Mountain.
 *
 * <p>The macro silhouette, encounter shelf, approach route and negative spaces are authored in a
 * stable local coordinate system: +X is the dragon's eastern takeoff direction and the route
 * enters from the south-west. The seed only perturbs erosion and material patches.</p>
 */
public final class SacredMountainShape {
    public static final long DEFAULT_SEED = 984_221L;
    public static final int VERSION = 3;
    public static final int DIAMETER = 768;
    public static final int MIN_COORDINATE = -384;
    public static final int MAX_COORDINATE = 383;
    public static final int OUTER_RADIUS = 384;
    public static final int EDGE_BLEND_WIDTH = 48;
    public static final int NEST_CENTER_X = 38;
    public static final int NEST_CENTER_Z = 0;
    public static final int NEST_HEIGHT = 209;
    public static final int DRAGON_ANCHOR_LIFT = 15;
    public static final int NEST_CLEARANCE_WIDTH = 220;
    public static final int NEST_CLEARANCE_DEPTH = 210;
    public static final int NEST_CLEARANCE_HEIGHT = 105;
    public static final int MAX_RELATIVE_HEIGHT = 288;

    private static final int GRID_STEP = 2;
    private static final int GRID_RADIUS = OUTER_RADIUS / GRID_STEP;
    private static final int GRID_SIZE = (GRID_RADIUS * 2) + 1;
    private static final double THERMAL_TALUS = 4.25D;
    private static final int THERMAL_PASSES = 12;
    private static final RouteNode[] ROUTE = {
            new RouteNode(-326, 178, 7),
            new RouteNode(-305, 190, 22),
            new RouteNode(-276, 164, 40),
            new RouteNode(-246, 139, 61),
            new RouteNode(-218, 123, 80),
            new RouteNode(-190, 100, 101),
            new RouteNode(-168, 75, 120),
            new RouteNode(-145, 58, 137),
            new RouteNode(-121, 45, 151),
            new RouteNode(-96, 29, 165),
            new RouteNode(-73, 23, 176),
            new RouteNode(-52, 8, 188),
            new RouteNode(-25, 14, 198),
            new RouteNode(4, 4, 205),
            new RouteNode(NEST_CENTER_X, NEST_CENTER_Z, NEST_HEIGHT)
    };
    private static final CrestPath[] CREST_PATHS = {
            // Storm blade: narrow fault face on one side, broad talus on the other.
            path(32, 86, 0.60D, 1.18D, 38,
                    node(-142, -218, 238), node(-110, -190, 268), node(-88, -168, 282),
                    node(-60, -139, 271), node(-78, -108, 252), node(-96, -76, 238),
                    node(-116, -30, 232)),

            // Broken Crown western and eastern splays flank the authored ascent gorge.
            path(24, 76, 0.56D, 1.16D, 42,
                    node(-116, -30, 232), node(-186, 25, 229), node(-174, 72, 268),
                    node(-146, 119, 247), node(-112, 148, 218)),
            path(76, 24, 1.16D, 0.56D, 42,
                    node(-116, -30, 232), node(-135, 15, 249), node(-110, 62, 246),
                    node(-84, 108, 225)),

            // Solar spur is deliberately long: one late high point instead of a radial summit.
            path(30, 68, 0.58D, 1.14D, 44,
                    node(-84, 108, 225), node(-40, 121, 213), node(6, 128, 220),
                    node(50, 148, 233), node(88, 176, 241), node(132, 210, 214)),

            // Six low spurs break the circular footprint and tie the massif into its foothills.
            path(50, 105, 0.74D, 1.24D, 55,
                    node(-110, -190, 268), node(-225, -270, 138), node(-342, -300, 18)),
            path(105, 50, 1.24D, 0.74D, 55,
                    node(-60, -139, 271), node(38, -230, 150), node(165, -328, 15)),
            path(50, 105, 0.74D, 1.24D, 55,
                    node(-174, 72, 268), node(-275, 65, 128), node(-365, 35, 12)),
            path(105, 50, 1.24D, 0.74D, 55,
                    node(-146, 119, 247), node(-245, 205, 105), node(-342, 238, 12)),
            path(50, 105, 0.74D, 1.24D, 55,
                    node(50, 148, 233), node(72, 270, 108), node(40, 360, 12)),
            path(105, 50, 1.24D, 0.74D, 55,
                    node(88, 176, 241), node(218, 252, 112), node(335, 286, 12))
    };
    private static final CrestSegment[] CREST_SEGMENTS = Arrays.stream(CREST_PATHS)
            .flatMap(path -> Arrays.stream(path.segments()))
            .toArray(CrestSegment[]::new);
    private static final double[][] NEST_POLYGON = {
            {-64.0D, -96.0D},
            {50.0D, -108.0D},
            {124.0D, -86.0D},
            {151.0D, -38.0D},
            {147.0D, 53.0D},
            {118.0D, 101.0D},
            {-22.0D, 104.0D},
            {-66.0D, 65.0D},
            {-72.0D, -24.0D}
    };
    private static final Gully[] GULLIES = {
            new Gully(-103, -150, -310, -337, 16.0D, 15.0D),
            new Gully(-62, -116, 170, -349, 15.0D, 14.0D),
            new Gully(-183, 78, -370, 70, 17.0D, 16.0D),
            new Gully(-120, 135, -280, 315, 18.0D, 17.0D),
            new Gully(25, 155, 30, 372, 15.0D, 15.0D),
            new Gully(100, 180, 345, 320, 17.0D, 16.0D),
            new Gully(145, 82, 365, 130, 14.0D, 17.0D),
            new Gully(-120, -92, -168, -280, 13.0D, 13.0D)
    };

    private final long seed;
    private final double[] heights;

    public SacredMountainShape(long seed) {
        this.seed = seed;
        this.heights = buildHeightField();
    }

    public long seed() {
        return seed;
    }

    /** Returns the solid surface height relative to the detected superflat surface. */
    public int heightAt(int localX, int localZ) {
        if (!insideBounds(localX, localZ)) {
            return 0;
        }
        double gridX = (localX - MIN_COORDINATE) / (double) GRID_STEP;
        double gridZ = (localZ - MIN_COORDINATE) / (double) GRID_STEP;
        int x0 = Math.clamp((int) Math.floor(gridX), 0, GRID_SIZE - 2);
        int z0 = Math.clamp((int) Math.floor(gridZ), 0, GRID_SIZE - 2);
        double tx = gridX - x0;
        double tz = gridZ - z0;
        RouteSample route = nearestRoute(localX, localZ);
        if (route.distance() <= routeHalfWidth(route.progress())) {
            return Math.max(0, (int) Math.round(route.height()));
        }
        double a = heights[index(x0, z0)];
        double b = heights[index(x0 + 1, z0)];
        double c = heights[index(x0, z0 + 1)];
        double d = heights[index(x0 + 1, z0 + 1)];
        return Math.max(0, (int) Math.round(lerp(lerp(a, b, tx), lerp(c, d, tx), tz)));
    }

    /** Highest Y that can contain authored rock in this column, relative to the flat surface. */
    public int maximumSolidHeightAt(int localX, int localZ) {
        int maximum = heightAt(localX, localZ);
        if (insideWindArchMass(localX, localZ)) {
            maximum = Math.max(maximum, 214);
        }
        if (insideEastOverhangPlan(localX, localZ)) {
            maximum = Math.max(maximum, 224);
        }
        return Math.min(MAX_RELATIVE_HEIGHT, maximum);
    }

    /** Highest occupied voxel after three-dimensional carving and the encounter clearance cut. */
    public int topSolidHeightAt(int localX, int localZ) {
        int maximum = maximumSolidHeightAt(localX, localZ);
        if (isDragonClearanceFootprint(localX, localZ)) {
            maximum = Math.min(maximum, NEST_HEIGHT);
        }
        for (int relativeY = maximum; relativeY > 0; relativeY--) {
            if (isSolid(localX, relativeY, localZ)) {
                return relativeY;
            }
        }
        return 0;
    }

    /** Final three-dimensional occupancy after unions and authored negative-space subtraction. */
    public boolean isSolid(int localX, int relativeY, int localZ) {
        if (relativeY <= 0 || relativeY > MAX_RELATIVE_HEIGHT || !insideBounds(localX, localZ)) {
            return false;
        }
        boolean solid = relativeY <= heightAt(localX, localZ)
                || insideWindArchMass(localX, relativeY, localZ)
                || insideEastOverhangMass(localX, relativeY, localZ);
        if (!solid) {
            return false;
        }
        if (insideWindArchOpening(localX, relativeY, localZ)
                || insideEastUndercroft(localX, relativeY, localZ)
                || insideNorthCleft(localX, relativeY, localZ)
                || isTakeoffClearance(localX, relativeY, localZ)) {
            return false;
        }
        RouteSample route = nearestRoute(localX, localZ);
        int routeFloor = (int) Math.round(route.height());
        if (route.distance() <= routeHalfWidth(route.progress())
                && relativeY > routeFloor
                && relativeY <= routeFloor + 6) {
            return false;
        }
        return !isDragonClearance(localX, relativeY, localZ);
    }

    public boolean isRoute(int localX, int localZ) {
        RouteSample route = nearestRoute(localX, localZ);
        return route.distance() <= routeHalfWidth(route.progress());
    }

    public int routeHeightAt(int localX, int localZ) {
        return (int) Math.round(nearestRoute(localX, localZ).height());
    }

    public boolean isDragonClearanceFootprint(int localX, int localZ) {
        return Math.abs(localX - NEST_CENTER_X) <= NEST_CLEARANCE_WIDTH / 2
                && Math.abs(localZ - NEST_CENTER_Z) <= NEST_CLEARANCE_DEPTH / 2;
    }

    private static boolean isDragonClearanceFootprint(double localX, double localZ) {
        return Math.abs(localX - NEST_CENTER_X) <= NEST_CLEARANCE_WIDTH / 2.0D
                && Math.abs(localZ - NEST_CENTER_Z) <= NEST_CLEARANCE_DEPTH / 2.0D;
    }

    public boolean isDragonClearance(int localX, int relativeY, int localZ) {
        return isDragonClearanceFootprint(localX, localZ)
                && relativeY > NEST_HEIGHT
                && relativeY <= NEST_HEIGHT + NEST_CLEARANCE_HEIGHT;
    }

    public boolean isTakeoffMarker(int localX, int localZ) {
        return Math.abs(localZ - NEST_CENTER_Z) <= 2
                && localX >= NEST_CENTER_X
                && localX <= NEST_CENTER_X + 92;
    }

    private static boolean isTakeoffClearance(int localX, int relativeY, int localZ) {
        return localX >= 124
                && localX <= MAX_COORDINATE
                && Math.abs(localZ) <= 48
                && relativeY > NEST_HEIGHT
                && relativeY <= NEST_HEIGHT + NEST_CLEARANCE_HEIGHT;
    }

    public List<RouteNode> routeNodes() {
        return List.of(ROUTE);
    }

    public static List<RouteNode> fixedRouteNodes() {
        return List.of(ROUTE);
    }

    public SurfaceMaterial materialAt(int localX, int relativeY, int localZ) {
        if (!isSolid(localX, relativeY, localZ)) {
            throw new IllegalArgumentException("Material requested for air");
        }
        return materialAtKnownSolid(localX, relativeY, localZ);
    }

    SurfaceMaterial materialAtKnownSolid(int localX, int relativeY, int localZ) {
        if (isDeepHeightfieldInterior(localX, relativeY, localZ)
                && !nearThreeDimensionalCarving(localX, relativeY, localZ)) {
            return SurfaceMaterial.STONE;
        }
        return materialAtKnownSolidReference(localX, relativeY, localZ);
    }

    SurfaceMaterial materialAtKnownSolidReference(int localX, int relativeY, int localZ) {
        boolean exposedTop = !isSolid(localX, relativeY + 1, localZ);
        boolean exposedSide = !isSolid(localX + 1, relativeY, localZ)
                || !isSolid(localX - 1, relativeY, localZ)
                || !isSolid(localX, relativeY, localZ + 1)
                || !isSolid(localX, relativeY, localZ - 1);
        int slope = surfaceSlope(localX, localZ);
        double patch = valueNoise(localX, localZ, 34.0D, 0xA881L);
        SegmentSample solarBand = segment(localX, localZ, 6.0D, 128.0D, 218.0D, 252.0D);
        boolean solarScar = solarBand.along() >= 0.0D
                && solarBand.along() <= 1.0D
                && solarBand.distance() <= 62.0D;
        if ((exposedTop || exposedSide) && solarScar) {
            return patch > 0.08D ? SurfaceMaterial.BASALT : SurfaceMaterial.BLACKSTONE;
        }
        if (exposedTop && relativeY >= 246 && !isRoute(localX, localZ)) {
            return SurfaceMaterial.SNOW;
        }
        double stratum = positiveModulo(relativeY
                + (int) Math.round(valueNoise(localX, localZ, 76.0D, 0xCA1CL) * 8.0D), 43);
        if (exposedSide && stratum <= 3.0D && relativeY >= 82) {
            return SurfaceMaterial.CALCITE;
        }
        if (exposedTop && slope >= 5 && relativeY < 174 && patch < 0.34D) {
            return SurfaceMaterial.GRAVEL;
        }
        if (exposedSide && (slope >= 7 || relativeY >= 218)) {
            return patch < -0.18D ? SurfaceMaterial.DEEPSLATE : SurfaceMaterial.TUFF;
        }
        if ((exposedTop || exposedSide) && patch > 0.28D) {
            return SurfaceMaterial.ANDESITE;
        }
        return SurfaceMaterial.STONE;
    }

    int guaranteedStoneHeightAt(int localX, int localZ) {
        if (!insideBounds(localX, localZ)) {
            return 0;
        }
        int minimumNeighborHeight = Math.min(
                heightAt(localX, localZ),
                Math.min(
                        Math.min(heightAt(localX - 1, localZ), heightAt(localX + 1, localZ)),
                        Math.min(heightAt(localX, localZ - 1), heightAt(localX, localZ + 1))));
        int guaranteedHeight = Math.max(0, minimumNeighborHeight - 1);
        boolean windArch = localX >= -205 && localX <= -25
                && localZ >= -45 && localZ <= 125;
        if (windArch) {
            guaranteedHeight = Math.min(guaranteedHeight, 29);
        }
        boolean eastUndercroft = localX >= 68 && localX <= 242
                && Math.abs(localZ) <= 96;
        if (eastUndercroft) {
            guaranteedHeight = Math.min(guaranteedHeight, 77);
        }
        boolean northCleft = localX >= -160 && localX <= -58
                && localZ >= -232 && localZ <= -90;
        if (northCleft) {
            guaranteedHeight = Math.min(guaranteedHeight, 61);
        }
        boolean dragonClearance = Math.abs(localX - NEST_CENTER_X) <= (NEST_CLEARANCE_WIDTH / 2) + 1
                && Math.abs(localZ - NEST_CENTER_Z) <= (NEST_CLEARANCE_DEPTH / 2) + 1;
        if (dragonClearance) {
            guaranteedHeight = Math.min(guaranteedHeight, NEST_HEIGHT);
        }
        if (localX >= 123 && Math.abs(localZ) <= 49) {
            guaranteedHeight = Math.min(guaranteedHeight, NEST_HEIGHT);
        }
        return guaranteedHeight;
    }

    private boolean isDeepHeightfieldInterior(int localX, int relativeY, int localZ) {
        int minimumNeighborHeight = Math.min(
                heightAt(localX, localZ),
                Math.min(
                        Math.min(heightAt(localX - 1, localZ), heightAt(localX + 1, localZ)),
                        Math.min(heightAt(localX, localZ - 1), heightAt(localX, localZ + 1))));
        return relativeY < minimumNeighborHeight;
    }

    private static boolean nearThreeDimensionalCarving(int localX, int relativeY, int localZ) {
        boolean windArch = localX >= -205 && localX <= -25
                && localZ >= -45 && localZ <= 125
                && relativeY >= 30 && relativeY <= 225;
        boolean eastUndercroft = localX >= 68 && localX <= 242
                && Math.abs(localZ) <= 96
                && relativeY >= 78 && relativeY <= 222;
        boolean northCleft = localX >= -160 && localX <= -58
                && localZ >= -232 && localZ <= -90
                && relativeY >= 62;
        boolean takeoff = localX >= 123
                && Math.abs(localZ) <= 49
                && relativeY >= NEST_HEIGHT;
        return windArch || eastUndercroft || northCleft || takeoff;
    }

    public int surfaceSlope(int localX, int localZ) {
        int center = heightAt(localX, localZ);
        int slope = Math.abs(center - heightAt(localX + 1, localZ));
        slope = Math.max(slope, Math.abs(center - heightAt(localX - 1, localZ)));
        slope = Math.max(slope, Math.abs(center - heightAt(localX, localZ + 1)));
        return Math.max(slope, Math.abs(center - heightAt(localX, localZ - 1)));
    }

    public boolean insideWindArchOpening(int localX, int relativeY, int localZ) {
        OrientedPoint point = oriented(localX, localZ, -121.0D, 45.0D, -0.49D);
        double floor = Math.round(nearestRoute(localX, localZ).height());
        if (Math.abs(point.along()) > 60.0D || relativeY <= floor) {
            return false;
        }
        double lateral = point.across() / 25.0D;
        double vertical = (relativeY - (floor + 16.0D)) / 17.5D;
        return (lateral * lateral) + (vertical * vertical) <= 1.0D;
    }

    public boolean insideEastUndercroft(int localX, int relativeY, int localZ) {
        double x = (localX - 153.0D) / 83.0D;
        double y = (relativeY - 157.0D) / 58.0D;
        double z = localZ / 84.0D;
        return localX >= 78 && relativeY >= 85 && (x * x) + (y * y) + (z * z) <= 1.0D;
    }

    public boolean insideNorthCleft(int localX, int relativeY, int localZ) {
        SegmentSample cleft = segment(localX, localZ, -79.0D, -111.0D, -135.0D, -218.0D);
        double halfWidth = lerp(10.0D, 18.0D, cleft.along());
        double floor = lerp(155.0D, 72.0D, cleft.along());
        return cleft.along() >= 0.0D && cleft.along() <= 1.0D
                && cleft.distance() <= halfWidth
                && relativeY >= floor;
    }

    public enum SurfaceMaterial {
        STONE,
        ANDESITE,
        TUFF,
        DEEPSLATE,
        CALCITE,
        GRAVEL,
        SNOW,
        BASALT,
        BLACKSTONE
    }

    public record RouteNode(int x, int z, int height) {
    }

    private double[] buildHeightField() {
        double[] field = new double[GRID_SIZE * GRID_SIZE];
        for (int gridZ = 0; gridZ < GRID_SIZE; gridZ++) {
            int localZ = MIN_COORDINATE + (gridZ * GRID_STEP);
            for (int gridX = 0; gridX < GRID_SIZE; gridX++) {
                int localX = MIN_COORDINATE + (gridX * GRID_STEP);
                field[index(gridX, gridZ)] = initialHeight(localX, localZ);
            }
        }
        thermalErode(field);
        flowErode(field);
        for (int gridZ = 0; gridZ < GRID_SIZE; gridZ++) {
            int localZ = MIN_COORDINATE + (gridZ * GRID_STEP);
            for (int gridX = 0; gridX < GRID_SIZE; gridX++) {
                int localX = MIN_COORDINATE + (gridX * GRID_STEP);
                int index = index(gridX, gridZ);
                field[index] = applyFixedFeatures(localX, localZ, field[index]);
            }
        }
        return field;
    }

    private double initialHeight(double x, double z) {
        double radius = Math.hypot(x, z);
        double angle = Math.atan2(z, x);
        double boundary = boundaryRadius(angle);
        if (radius >= boundary) {
            return 0.0D;
        }
        double height = 0.0D;
        for (CrestPath crest : CREST_PATHS) {
            double ridge = crestPathHeight(x, z, crest);
            if (ridge > 0.0D) {
                height = height <= 0.0D ? ridge : smoothMax(height, ridge, 12.0D);
            }
        }

        for (Gully gully : GULLIES) {
            SegmentSample sample = segment(x, z, gully.startX(), gully.startZ(), gully.endX(), gully.endZ());
            if (sample.along() >= 0.0D && sample.along() <= 1.0D) {
                double width = lerp(gully.width() * 0.55D, gully.width(), sample.along());
                double cut = gully.depth() * sample.along()
                        * Math.exp(-Math.pow(sample.distance() / width, 2.0D));
                height -= cut;
            }
        }

        double erosionNoise = valueNoise(x, z, 61.0D, 0x77E1L) * 3.2D
                + valueNoise(x, z, 27.0D, 0xB04FL) * 1.6D;
        double detailWeight = smoothstep(5.0D, 46.0D, height);
        return Math.max(0.0D, height + (erosionNoise * detailWeight));
    }

    private double applyFixedFeatures(double x, double z, double erodedHeight) {
        double radius = Math.hypot(x, z);
        double angle = Math.atan2(z, x);
        double boundary = boundaryRadius(angle);
        double height = erodedHeight
                * (1.0D - smoothstep(boundary - EDGE_BLEND_WIDTH, boundary, radius));
        height = applySummitNotches(x, z, height);
        height = applyNestTerrace(x, z, height);

        RouteSample route = nearestRoute(x, z);
        double routeHalfWidth = routeHalfWidth(route.progress());
        double routeWeight = 1.0D - smoothstep(routeHalfWidth, routeHalfWidth + 23.0D, route.distance());
        if (routeWeight > 0.0D) {
            double bankRise = Math.max(0.0D, route.distance() - routeHalfWidth) * 0.78D;
            double valleyTarget = route.height() + Math.min(34.0D, bankRise);
            height = lerp(height, Math.min(height, valleyTarget), routeWeight);
        }

        double cliffEdge = 148.0D
                + (7.0D * Math.sin((z + 19.0D) / 31.0D))
                + (3.0D * Math.sin((z - 11.0D) / 13.0D));
        double cliffBand = 1.0D - smoothstep(76.0D, 108.0D, Math.abs(z));
        double cliffWeight = smoothstep(cliffEdge - 10.0D, cliffEdge + 22.0D, x) * cliffBand;
        if (cliffWeight > 0.0D) {
            double lowerShoulder = 54.0D
                    + (22.0D * Math.max(0.0D, 1.0D - Math.abs(z) / 146.0D))
                    + (valueNoise(x, z, 45.0D, 0xE457L) * 3.0D);
            height = lerp(height, Math.min(height, lowerShoulder), cliffWeight);
        }

        return Math.clamp(height, 0.0D, MAX_RELATIVE_HEIGHT);
    }

    private void thermalErode(double[] field) {
        double[] delta = new double[field.length];
        for (int pass = 0; pass < THERMAL_PASSES; pass++) {
            Arrays.fill(delta, 0.0D);
            for (int z = 1; z < GRID_SIZE - 1; z++) {
                for (int x = 1; x < GRID_SIZE - 1; x++) {
                    double localX = MIN_COORDINATE + (x * GRID_STEP);
                    double localZ = MIN_COORDINATE + (z * GRID_STEP);
                    if (protectedHighCrest(localX, localZ)) {
                        continue;
                    }
                    int source = index(x, z);
                    double sourceHeight = field[source];
                    int receiver = source;
                    double greatestDifference = 0.0D;
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if ((dx == 0 && dz == 0) || (dx != 0 && dz != 0)) {
                                continue;
                            }
                            int neighbor = index(x + dx, z + dz);
                            double difference = sourceHeight - field[neighbor];
                            if (difference > greatestDifference) {
                                greatestDifference = difference;
                                receiver = neighbor;
                            }
                        }
                    }
                    if (receiver != source && greatestDifference > THERMAL_TALUS) {
                        double transfer = (greatestDifference - THERMAL_TALUS) * 0.11D;
                        delta[source] -= transfer;
                        delta[receiver] += transfer;
                    }
                }
            }
            for (int index = 0; index < field.length; index++) {
                field[index] = Math.max(0.0D, field[index] + delta[index]);
            }
        }
    }

    private void flowErode(double[] field) {
        Integer[] order = new Integer[field.length];
        for (int index = 0; index < order.length; index++) {
            order[index] = index;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer index) -> field[index]).reversed());
        double[] flow = new double[field.length];
        Arrays.fill(flow, 1.0D);
        int[] receiver = new int[field.length];
        Arrays.fill(receiver, -1);
        for (int packed : order) {
            int x = packed % GRID_SIZE;
            int z = packed / GRID_SIZE;
            if (x == 0 || z == 0 || x == GRID_SIZE - 1 || z == GRID_SIZE - 1) {
                continue;
            }
            double lowest = field[packed];
            int lowestIndex = -1;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int neighbor = index(x + dx, z + dz);
                    if (field[neighbor] < lowest) {
                        lowest = field[neighbor];
                        lowestIndex = neighbor;
                    }
                }
            }
            receiver[packed] = lowestIndex;
            if (lowestIndex >= 0) {
                flow[lowestIndex] += flow[packed];
            }
        }
        for (int packed = 0; packed < field.length; packed++) {
            int gridX = packed % GRID_SIZE;
            int gridZ = packed / GRID_SIZE;
            double localX = MIN_COORDINATE + (gridX * GRID_STEP);
            double localZ = MIN_COORDINATE + (gridZ * GRID_STEP);
            if (protectedFromErosion(localX, localZ) || receiver[packed] < 0) {
                continue;
            }
            double drop = field[packed] - field[receiver[packed]];
            double incision = Math.min(9.0D, Math.max(0.0D, Math.log1p(flow[packed]) - 2.3D) * 0.72D);
            field[packed] = Math.max(0.0D, field[packed] - incision * Math.min(1.0D, drop / 9.0D));
        }
    }

    private boolean protectedFromErosion(double x, double z) {
        return nearestRoute(x, z).distance() <= 34.0D
                || signedDistanceToNestPolygon(x, z) >= -16.0D
                || protectedHighCrest(x, z);
    }

    private boolean insideWindArchPlan(double x, double z) {
        OrientedPoint point = oriented(x, z, -121.0D, 45.0D, -0.49D);
        return Math.abs(point.along()) <= 66.0D && Math.abs(point.across()) <= 59.0D;
    }

    private boolean insideWindArchMass(int x, int z) {
        return insideWindArchPlan(x, z);
    }

    private boolean insideWindArchMass(int x, int y, int z) {
        OrientedPoint point = oriented(x, z, -121.0D, 45.0D, -0.49D);
        double floor = 151.0D + (point.along() * 0.18D);
        double across = point.across() / 59.0D;
        double vertical = (y - (floor + 27.0D)) / 45.0D;
        return Math.abs(point.along()) <= 60.0D
                && (across * across) + (vertical * vertical) <= 1.0D;
    }

    private boolean insideEastOverhangPlan(int x, int z) {
        double localX = (x - 137.0D) / 101.0D;
        double localZ = z / 105.0D;
        return (localX * localX) + (localZ * localZ) <= 1.0D;
    }

    private boolean insideEastOverhangMass(int x, int y, int z) {
        double localX = (x - 126.0D) / 112.0D;
        double localY = (y - 183.0D) / 43.0D;
        double localZ = z / 105.0D;
        return x >= 34 && (localX * localX) + (localY * localY) + (localZ * localZ) <= 1.0D;
    }

    private RouteSample nearestRoute(double x, double z) {
        RouteSample nearest = new RouteSample(Double.POSITIVE_INFINITY, NEST_HEIGHT, 1.0D);
        for (int index = 0; index < ROUTE.length - 1; index++) {
            RouteNode start = ROUTE[index];
            RouteNode end = ROUTE[index + 1];
            SegmentSample sample = segment(x, z, start.x(), start.z(), end.x(), end.z());
            double clampedAlong = Math.clamp(sample.along(), 0.0D, 1.0D);
            double nearestX = lerp(start.x(), end.x(), clampedAlong);
            double nearestZ = lerp(start.z(), end.z(), clampedAlong);
            double distance = Math.hypot(x - nearestX, z - nearestZ);
            if (distance < nearest.distance()) {
                nearest = new RouteSample(
                        distance,
                        lerp(start.height(), end.height(), clampedAlong),
                        (index + clampedAlong) / (ROUTE.length - 1.0D));
            }
        }
        return nearest;
    }

    private double valueNoise(double x, double z, double scale, long salt) {
        double gridX = x / scale;
        double gridZ = z / scale;
        int x0 = (int) Math.floor(gridX);
        int z0 = (int) Math.floor(gridZ);
        double tx = smoothstep(0.0D, 1.0D, gridX - x0);
        double tz = smoothstep(0.0D, 1.0D, gridZ - z0);
        double a = signed(hash(seed, x0, z0, salt));
        double b = signed(hash(seed, x0 + 1, z0, salt));
        double c = signed(hash(seed, x0, z0 + 1, salt));
        double d = signed(hash(seed, x0 + 1, z0 + 1, salt));
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private static CrestNode node(double x, double z, double height) {
        return new CrestNode(x, z, height);
    }

    private static CrestPath path(
            double leftWidth,
            double rightWidth,
            double leftPower,
            double rightPower,
            double talusWidth,
            CrestNode... nodes) {
        if (nodes.length < 2) {
            throw new IllegalArgumentException("A crest path needs at least two nodes");
        }
        List<CrestSegmentDraft> drafts = new ArrayList<>();
        for (int index = 0; index < nodes.length - 1; index++) {
            CrestNode previous = nodes[Math.max(0, index - 1)];
            CrestNode start = nodes[index];
            CrestNode end = nodes[index + 1];
            CrestNode next = nodes[Math.min(nodes.length - 1, index + 2)];
            int steps = Math.max(2, (int) Math.ceil(
                    Math.hypot(end.x() - start.x(), end.z() - start.z()) / 12.0D));
            for (int step = 0; step < steps; step++) {
                double startAmount = step / (double) steps;
                double endAmount = (step + 1.0D) / steps;
                drafts.add(new CrestSegmentDraft(
                        hermite(previous, start, end, next, startAmount),
                        hermite(previous, start, end, next, endAmount),
                        index,
                        startAmount,
                        endAmount));
            }
        }

        double totalLength = 0.0D;
        for (CrestSegmentDraft draft : drafts) {
            totalLength += Math.hypot(
                    draft.end().x() - draft.start().x(),
                    draft.end().z() - draft.start().z());
        }
        double warpPhase = (nodes[0].x() * 0.013D) + (nodes[0].z() * 0.009D);
        CrestSegment[] segments = new CrestSegment[drafts.size()];
        double pathDistance = 0.0D;
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < segments.length; index++) {
            CrestSegmentDraft draft = drafts.get(index);
            CrestNode start = draft.start();
            CrestNode end = draft.end();
            double bendPhase = warpPhase + (draft.authoredSegment() * 1.71D);
            double warpDirection = Math.sin(bendPhase) < 0.0D ? -1.0D : 1.0D;
            double warpAmplitude = Math.clamp(
                    10.5D + (2.5D * Math.sin(warpPhase + (draft.authoredSegment() * 1.23D))),
                    8.0D,
                    14.0D);
            segments[index] = new CrestSegment(
                    start,
                    end,
                    leftWidth,
                    rightWidth,
                    leftPower,
                    rightPower,
                    talusWidth,
                    pathDistance,
                    totalLength,
                    warpPhase,
                    draft.warpStart(),
                    draft.warpEnd(),
                    warpDirection,
                    warpAmplitude);
            pathDistance += Math.hypot(end.x() - start.x(), end.z() - start.z());
            minimumX = Math.min(minimumX, Math.min(start.x(), end.x()));
            minimumZ = Math.min(minimumZ, Math.min(start.z(), end.z()));
            maximumX = Math.max(maximumX, Math.max(start.x(), end.x()));
            maximumZ = Math.max(maximumZ, Math.max(start.z(), end.z()));
        }
        double margin = Math.max(leftWidth, rightWidth) + talusWidth + 72.0D;
        return new CrestPath(
                segments,
                minimumX - margin,
                minimumZ - margin,
                maximumX + margin,
                maximumZ + margin);
    }

    private static CrestNode hermite(
            CrestNode previous,
            CrestNode start,
            CrestNode end,
            CrestNode next,
            double amount) {
        double tangentScale = 0.36D;
        double x = cubicHermite(
                start.x(), end.x(),
                (end.x() - previous.x()) * tangentScale,
                (next.x() - start.x()) * tangentScale,
                amount);
        double z = cubicHermite(
                start.z(), end.z(),
                (end.z() - previous.z()) * tangentScale,
                (next.z() - start.z()) * tangentScale,
                amount);
        double height = cubicHermite(
                start.height(), end.height(),
                (end.height() - previous.height()) * tangentScale,
                (next.height() - start.height()) * tangentScale,
                amount);
        height = Math.clamp(
                height,
                Math.min(start.height(), end.height()),
                Math.max(start.height(), end.height()));
        return new CrestNode(x, z, height);
    }

    private static double cubicHermite(
            double start, double end, double startTangent, double endTangent, double amount) {
        double squared = amount * amount;
        double cubed = squared * amount;
        return ((2.0D * cubed) - (3.0D * squared) + 1.0D) * start
                + (cubed - (2.0D * squared) + amount) * startTangent
                + ((-2.0D * cubed) + (3.0D * squared)) * end
                + (cubed - squared) * endTangent;
    }

    /**
     * Sweeps one cross-section along a whole polyline. Only the nearest segment contributes, so
     * bends cannot expose the parallel rectangular fields that made the v2/v3-first-pass peaks
     * read as stacked cones.
     */
    private static double crestPathHeight(double x, double z, CrestPath path) {
        if (x < path.minimumX() || x > path.maximumX()
                || z < path.minimumZ() || z > path.maximumZ()) {
            return 0.0D;
        }
        CrestSegment selected = null;
        CrestProjection selectedProjection = null;
        double selectedDistance = Double.POSITIVE_INFINITY;
        for (CrestSegment segment : path.segments()) {
            CrestProjection projection = projectCrest(x, z, segment);
            if (projection.along() < -0.16D || projection.along() > 1.16D) {
                continue;
            }
            double endpointDistance = Math.max(0.0D, -projection.along())
                    + Math.max(0.0D, projection.along() - 1.0D);
            double distance = Math.hypot(
                    warpedSignedDistance(projection), endpointDistance * projection.length() * 1.7D);
            if (distance < selectedDistance) {
                selected = segment;
                selectedProjection = projection;
                selectedDistance = distance;
            }
        }
        if (selected == null) {
            return 0.0D;
        }

        double along = Math.clamp(selectedProjection.along(), 0.0D, 1.0D);
        double endpointDistance = Math.max(0.0D, -selectedProjection.along())
                + Math.max(0.0D, selectedProjection.along() - 1.0D);
        double longitudinalDistance = endpointDistance * selectedProjection.length() * 1.7D;
        double signedDistance = warpedSignedDistance(selectedProjection);
        boolean leftSide = signedDistance >= 0.0D;
        double pathProgress = pathProgress(selected, selectedProjection.along());
        double widthScale = Math.clamp(
                0.91D
                        + (0.11D * Math.sin((pathProgress * Math.PI * 3.0D) + selected.warpPhase()))
                        + (0.06D * Math.sin((pathProgress * Math.PI * 7.0D) - selected.warpPhase())),
                0.76D,
                1.08D);
        double width = (leftSide ? selected.leftWidth() : selected.rightWidth()) * widthScale;
        double power = leftSide ? selected.leftPower() : selected.rightPower();
        double distance = Math.hypot(Math.abs(signedDistance), longitudinalDistance);
        double crestTop = lerp(selected.start().height(), selected.end().height(), along);

        double shoulderFloor = Math.min(62.0D, crestTop * 0.23D);
        double core = 0.0D;
        if (distance < width) {
            double normalized = distance / width;
            core = shoulderFloor
                    + ((crestTop - shoulderFloor) * (1.0D - Math.pow(normalized, power)));
        }
        double talus = 0.0D;
        if (distance >= width && distance < width + selected.talusWidth()) {
            double talusProgress = (distance - width) / selected.talusWidth();
            talus = shoulderFloor * Math.pow(1.0D - talusProgress, 1.38D);
        }
        double supportWidth = width + selected.talusWidth() + 64.0D;
        double support = 0.0D;
        if (distance < supportWidth) {
            double supportTop = Math.min(42.0D, crestTop * 0.18D);
            support = supportTop * Math.pow(1.0D - (distance / supportWidth), 1.55D);
        }
        return Math.max(Math.max(core, talus), support);
    }

    private static double warpedSignedDistance(CrestProjection projection) {
        CrestSegment segment = projection.segment();
        double authoredAmount = lerp(
                segment.warpStart(),
                segment.warpEnd(),
                Math.clamp(projection.along(), 0.0D, 1.0D));
        double axisWarp = segment.warpDirection()
                * segment.warpAmplitude()
                * Math.sin(Math.PI * authoredAmount);
        return projection.signedDistance() - axisWarp;
    }

    private static double pathProgress(CrestSegment segment, double along) {
        double distance = segment.pathDistance()
                + (Math.clamp(along, 0.0D, 1.0D) * segmentLength(segment));
        return Math.clamp(distance / segment.totalPathLength(), 0.0D, 1.0D);
    }

    private static double segmentLength(CrestSegment segment) {
        return Math.hypot(
                segment.end().x() - segment.start().x(),
                segment.end().z() - segment.start().z());
    }

    private static CrestProjection projectCrest(double x, double z, CrestSegment crest) {
        double dx = crest.end().x() - crest.start().x();
        double dz = crest.end().z() - crest.start().z();
        double lengthSquared = (dx * dx) + (dz * dz);
        double length = Math.sqrt(lengthSquared);
        double along = ((x - crest.start().x()) * dx + (z - crest.start().z()) * dz) / lengthSquared;
        double clamped = Math.clamp(along, 0.0D, 1.0D);
        double nearestX = crest.start().x() + (dx * clamped);
        double nearestZ = crest.start().z() + (dz * clamped);
        double signedDistance = (((x - nearestX) * -dz) + ((z - nearestZ) * dx)) / length;
        return new CrestProjection(along, signedDistance, nearestX, nearestZ, length, crest);
    }

    private static boolean protectedHighCrest(double x, double z) {
        for (CrestSegment crest : CREST_SEGMENTS) {
            CrestProjection projection = projectCrest(x, z, crest);
            if (projection.along() < 0.0D || projection.along() > 1.0D) {
                continue;
            }
            double along = projection.along();
            double top = lerp(crest.start().height(), crest.end().height(), along);
            double signedDistance = warpedSignedDistance(projection);
            double width = signedDistance >= 0.0D ? crest.leftWidth() : crest.rightWidth();
            if (top >= 180.0D && Math.abs(signedDistance) <= width * 0.72D) {
                return true;
            }
        }
        return false;
    }

    private double applySummitNotches(double x, double z, double height) {
        height -= notchCut(x, z, -99.0D, -179.0D, 0.75D, 9.0D, 35.0D, 15.0D);
        height -= notchCut(x, z, -73.0D, -151.0D, 0.80D, 8.0D, 31.0D, 13.0D);
        height -= notchCut(x, z, -159.0D, 96.0D, 0.98D, 10.0D, 37.0D, 16.0D);
        height -= notchCut(x, z, -122.0D, 38.0D, 1.10D, 11.0D, 32.0D, 12.0D);
        return Math.max(0.0D, height);
    }

    private static double notchCut(
            double x,
            double z,
            double centerX,
            double centerZ,
            double radians,
            double alongRadius,
            double acrossRadius,
            double depth) {
        OrientedPoint point = oriented(x, z, centerX, centerZ, radians);
        double along = point.along() / alongRadius;
        double across = point.across() / acrossRadius;
        return depth * Math.exp(-((along * along) + (across * across)) * 1.7D);
    }

    private double applyNestTerrace(double x, double z, double originalHeight) {
        double signedDistance = signedDistanceToNestPolygon(x, z);
        double edgeWarp = (5.0D * Math.sin((x + (z * 0.37D)) / 41.0D))
                + (3.0D * Math.sin(((x * 0.31D) - z) / 27.0D))
                + (2.5D * Math.sin((x + (z * 2.3D)) / 19.0D));
        double warpedEdgeDistance = signedDistance + edgeWarp;
        double height = originalHeight;
        double easternCliff = smoothstep(82.0D, 132.0D, x)
                * (1.0D - smoothstep(72.0D, 112.0D, Math.abs(z)));
        double outsideFeather = lerp(82.0D, 24.0D, easternCliff);
        double insideFeather = lerp(38.0D, 14.0D, easternCliff);
        if (warpedEdgeDistance >= -outsideFeather) {
            // These benches run across the shelf instead of following its perimeter. Rounding the
            // gently tilted planes creates one-block walkable offsets without octagonal contour
            // rings around the polygon.
            double eastRise = 4.2D * smoothstep(-68.0D, 142.0D, x);
            double northBench = 2.1D * smoothstep(24.0D, 102.0D, -z);
            double southDrop = 3.1D * smoothstep(18.0D, 104.0D, z);
            double benchWarp = (0.85D * Math.sin((x + (z * 1.7D)) / 46.0D))
                    + (0.55D * Math.sin(((x * 1.9D) - z) / 33.0D));
            double outerBench = Math.clamp(
                    Math.rint(199.5D + eastRise + northBench - southDrop + benchWarp),
                    196.0D,
                    208.0D);

            double coreWarp = (3.6D * Math.sin((x + (z * 0.8D)) / 31.0D))
                    + (1.9D * Math.sin(((x * 0.6D) - z) / 21.0D));
            double coreDistance = roundedRectangleInteriorDistance(
                    x - NEST_CENTER_X, z - NEST_CENTER_Z, 75.0D, 52.0D, 22.0D) + coreWarp;
            double apronWidth = 14.0D
                    + (4.0D * smoothstep(-52.0D, 88.0D, x))
                    + (2.0D * Math.sin((x - z) / 37.0D));
            double coreApron = smoothstep(-apronWidth, 0.0D, coreDistance);
            double terrace = coreDistance >= 0.0D
                    ? NEST_HEIGHT
                    : lerp(outerBench, NEST_HEIGHT, coreApron);
            double shelfWeight = smoothstep(-outsideFeather, insideFeather, warpedEdgeDistance);
            height = lerp(height, terrace, shelfWeight);
        }

        if (isDragonClearanceFootprint(x, z) && signedDistance < 0.0D) {
            double cornerFloor = 188.0D
                    + (9.0D * smoothstep(-46.0D, 0.0D, warpedEdgeDistance));
            height = Math.min(height, cornerFloor);
        }
        return height;
    }

    private static double roundedRectangleInteriorDistance(
            double x, double z, double halfWidth, double halfDepth, double cornerRadius) {
        double qx = Math.abs(x) - (halfWidth - cornerRadius);
        double qz = Math.abs(z) - (halfDepth - cornerRadius);
        double outside = Math.hypot(Math.max(qx, 0.0D), Math.max(qz, 0.0D));
        double inside = Math.min(Math.max(qx, qz), 0.0D);
        return -((outside + inside) - cornerRadius);
    }

    private static double signedDistanceToNestPolygon(double x, double z) {
        boolean inside = false;
        double minimumDistance = Double.POSITIVE_INFINITY;
        for (int index = 0, previous = NEST_POLYGON.length - 1;
                index < NEST_POLYGON.length;
                previous = index++) {
            double ax = NEST_POLYGON[previous][0];
            double az = NEST_POLYGON[previous][1];
            double bx = NEST_POLYGON[index][0];
            double bz = NEST_POLYGON[index][1];
            minimumDistance = Math.min(minimumDistance, segment(x, z, ax, az, bx, bz).distance());
            boolean crosses = ((az > z) != (bz > z))
                    && x < ((bx - ax) * (z - az) / (bz - az)) + ax;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside ? minimumDistance : -minimumDistance;
    }

    private static double routeHalfWidth(double progress) {
        return 5.5D + (2.0D * (0.5D + (0.5D * Math.sin((progress * Math.PI * 5.0D) + 0.4D))));
    }

    private static double boundaryRadius(double angle) {
        return 350.0D
                + (14.0D * Math.cos((angle * 3.0D) + 0.35D))
                + (9.0D * Math.sin((angle * 5.0D) - 0.6D))
                + (6.0D * Math.cos((angle * 7.0D) + 1.1D));
    }

    private static double smoothMax(double first, double second, double radius) {
        if (radius <= 0.0D) {
            return Math.max(first, second);
        }
        double blend = Math.clamp(0.5D + (0.5D * (first - second) / radius), 0.0D, 1.0D);
        return lerp(second, first, blend) + (radius * blend * (1.0D - blend));
    }

    private static SegmentSample segment(
            double x, double z, double startX, double startZ, double endX, double endZ) {
        double dx = endX - startX;
        double dz = endZ - startZ;
        double lengthSquared = (dx * dx) + (dz * dz);
        double along = ((x - startX) * dx + (z - startZ) * dz) / lengthSquared;
        double clamped = Math.clamp(along, 0.0D, 1.0D);
        double nearestX = startX + (dx * clamped);
        double nearestZ = startZ + (dz * clamped);
        return new SegmentSample(Math.hypot(x - nearestX, z - nearestZ), along);
    }

    private static OrientedPoint oriented(
            double x, double z, double centerX, double centerZ, double radians) {
        double dx = x - centerX;
        double dz = z - centerZ;
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new OrientedPoint((dx * cosine) + (dz * sine), (-dx * sine) + (dz * cosine));
    }

    private static boolean insideBounds(int x, int z) {
        return x >= MIN_COORDINATE && x <= MAX_COORDINATE
                && z >= MIN_COORDINATE && z <= MAX_COORDINATE;
    }

    private static int index(int gridX, int gridZ) {
        return (gridZ * GRID_SIZE) + gridX;
    }

    private static long hash(long seed, int x, int z, long salt) {
        long value = seed ^ salt;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double signed(long value) {
        return (unit(value) * 2.0D) - 1.0D;
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 <= edge0) {
            throw new IllegalArgumentException("smoothstep edges must increase");
        }
        double normalized = Math.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return normalized * normalized * (3.0D - (2.0D * normalized));
    }

    private static double lerp(double a, double b, double weight) {
        return a + ((b - a) * weight);
    }

    private static int positiveModulo(int value, int divisor) {
        return Math.floorMod(value, divisor);
    }

    private record SegmentSample(double distance, double along) {
    }

    private record CrestNode(double x, double z, double height) {
    }

    private record CrestSegmentDraft(
            CrestNode start,
            CrestNode end,
            int authoredSegment,
            double warpStart,
            double warpEnd) {
    }

    private record CrestPath(
            CrestSegment[] segments,
            double minimumX,
            double minimumZ,
            double maximumX,
            double maximumZ) {
    }

    private record CrestSegment(
            CrestNode start,
            CrestNode end,
            double leftWidth,
            double rightWidth,
            double leftPower,
            double rightPower,
            double talusWidth,
            double pathDistance,
            double totalPathLength,
            double warpPhase,
            double warpStart,
            double warpEnd,
            double warpDirection,
            double warpAmplitude) {
    }

    private record CrestProjection(
            double along,
            double signedDistance,
            double nearestX,
            double nearestZ,
            double length,
            CrestSegment segment) {
    }

    private record RouteSample(double distance, double height, double progress) {
    }

    private record OrientedPoint(double along, double across) {
    }

    private record Gully(
            double startX, double startZ, double endX, double endZ, double width, double depth) {
    }
}
