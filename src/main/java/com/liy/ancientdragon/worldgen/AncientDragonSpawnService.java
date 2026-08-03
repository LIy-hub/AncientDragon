package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.entity.AncientDragonEntities;
import com.liy.ancientdragon.entity.AncientDragonEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Shared idempotent creation path for manual and naturally generated Sacred Mountains. */
final class AncientDragonSpawnService {
    private AncientDragonSpawnService() {
    }

    static UUID spawnOrAdopt(ServerLevel level, BlockPos rest, float yaw, int quarterTurns) {
        Vec3 position = new Vec3(rest.getX(), rest.getY(), rest.getZ());
        for (var entity : level.getAllEntities()) {
            if (entity instanceof AncientDragonEntity dragon
                    && dragon.position().distanceToSqr(position) <= 4.0D) {
                dragon.setEncounterOrigin(position);
                dragon.setEncounterRotation(quarterTurns);
                return dragon.getUUID();
            }
        }

        AncientDragonEntity dragon = new AncientDragonEntity(AncientDragonEntities.ANCIENT_DRAGON, level);
        dragon.setPos(position.x, position.y, position.z);
        dragon.setYRot(yaw);
        dragon.setNoGravity(true);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.setEncounterOrigin(position);
        dragon.setEncounterRotation(quarterTurns);
        if (!level.addFreshEntity(dragon)) {
            throw new IllegalStateException("the target level rejected the Ancient Dragon entity");
        }
        return dragon.getUUID();
    }

    static void configureBoundDragon(
            ServerLevel level, UUID dragonUuid, BlockPos rest, int quarterTurns) {
        if (level.getEntity(dragonUuid) instanceof AncientDragonEntity dragon) {
            dragon.setEncounterOrigin(new Vec3(rest.getX(), rest.getY(), rest.getZ()));
            dragon.setEncounterRotation(quarterTurns);
        }
    }
}
