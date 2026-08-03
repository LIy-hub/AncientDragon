package com.liy.ancientdragon.animation.inertia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AncientDragonInertiaProfileTest {
    @Test
    void coversEveryStructuralV011JointExactlyOnce() {
        var bones = AncientDragonInertiaProfile.bones();
        Set<String> names = new HashSet<>();
        bones.forEach(bone -> assertTrue(names.add(bone.name()), bone.name()));

        assertEquals(81, bones.size());
        for (String required : Set.of(
                "root", "pelvis", "spine_01", "chest",
                "neck_01", "neck_02", "neck_03", "neck_04", "head",
                "tail_01", "tail_08",
                "wing_main_01.L", "wing_main_vein_C_04.R",
                "wing_secondary_01.R", "wing_secondary_vein_C_04.L",
                "thigh.L", "shin.R", "foot.L", "toe.R")) {
            assertTrue(names.contains(required), required);
        }

        for (String authoredOnly : Set.of(
                "jaw", "eye.L", "eye.R", "eyelid_upper.L", "eyelid_lower.L",
                "eyelid_upper.R", "eyelid_lower.R", "solar_core")) {
            assertFalse(names.contains(authoredOnly), authoredOnly);
        }
    }

    @Test
    void distalTailIsSofterAndMoreDelayedThanItsRoot() {
        var tailRoot = AncientDragonInertiaProfile.bones().stream()
                .filter(bone -> bone.name().equals("tail_01"))
                .findFirst()
                .orElseThrow();
        var tailTip = AncientDragonInertiaProfile.bones().stream()
                .filter(bone -> bone.name().equals("tail_08"))
                .findFirst()
                .orElseThrow();

        assertTrue(tailTip.frequencyHertz() < tailRoot.frequencyHertz());
        assertTrue(tailTip.dampingRatio() < tailRoot.dampingRatio());
        assertTrue(tailTip.yawResponse() > tailRoot.yawResponse());
    }

    @Test
    void turningProfileBuildsAProgressiveTorsoAndTailCurve() {
        var root = bone("root");
        var pelvis = bone("pelvis");
        var spine = bone("spine_01");
        var chest = bone("chest");

        assertTrue(root.yawResponse() < pelvis.yawResponse());
        assertTrue(pelvis.yawResponse() < spine.yawResponse());
        assertTrue(spine.yawResponse() < chest.yawResponse());
        assertTrue(root.maximumRadians() < pelvis.maximumRadians());
        assertTrue(pelvis.maximumRadians() < spine.maximumRadians());
        assertTrue(spine.maximumRadians() < chest.maximumRadians());
        assertTrue(root.frequencyHertz() > chest.frequencyHertz());

        double torsoTravel = root.maximumRadians()
                + pelvis.maximumRadians()
                + spine.maximumRadians()
                + chest.maximumRadians();
        assertTrue(torsoTravel >= Math.toRadians(3.5));
        assertTrue(torsoTravel <= Math.toRadians(4.0));

        double tailTravel = AncientDragonInertiaProfile.bones().stream()
                .filter(bone -> bone.group() == AncientDragonInertiaProfile.Group.TAIL)
                .mapToDouble(AncientDragonInertiaProfile.Bone::maximumRadians)
                .sum();
        assertTrue(tailTravel >= Math.toRadians(4.0));
        assertTrue(tailTravel <= Math.toRadians(4.5));
        assertTrue(bone("head").yawResponse() < 0.0);
    }

    @Test
    void everyConfiguredBoneExistsInTheShippedGlb() throws IOException {
        byte[] glb;
        try (var stream = AncientDragonInertiaProfileTest.class.getResourceAsStream(
                "/assets/ancient_dragon/models3d/entity/ancient_dragon.glb")) {
            if (stream == null) {
                throw new AssertionError("Ancient Dragon GLB resource is missing");
            }
            glb = stream.readAllBytes();
        }

        ByteBuffer buffer = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x46546C67, buffer.getInt());
        assertEquals(2, buffer.getInt());
        assertEquals(glb.length, buffer.getInt());
        String json = null;
        while (buffer.remaining() >= 8) {
            int length = buffer.getInt();
            int type = buffer.getInt();
            byte[] chunk = new byte[length];
            buffer.get(chunk);
            if (type == 0x4E4F534A) {
                json = new String(chunk, StandardCharsets.UTF_8);
                break;
            }
        }
        if (json == null) {
            throw new AssertionError("Ancient Dragon GLB has no JSON chunk");
        }

        Set<String> nodeNames = new HashSet<>();
        Matcher names = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        while (names.find()) {
            nodeNames.add(names.group(1));
        }
        AncientDragonInertiaProfile.bones().forEach(
                bone -> assertTrue(nodeNames.contains(bone.name()), bone.name()));
    }

    private static AncientDragonInertiaProfile.Bone bone(String name) {
        return AncientDragonInertiaProfile.bones().stream()
                .filter(bone -> bone.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
