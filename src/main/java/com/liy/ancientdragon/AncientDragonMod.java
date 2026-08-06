package com.liy.ancientdragon;

import com.liy.ancientdragon.advancement.AncientDragonCriteria;
import com.liy.ancientdragon.atmosphere.DragonAtmosphereNetworking;
import com.liy.ancientdragon.block.AncientDragonBlocks;
import com.liy.ancientdragon.block.AncientCityGatewayActivation;
import com.liy.ancientdragon.boss.AncientDragonCorpseRewardService;
import com.liy.ancientdragon.chronicle.DragonChronicleService;
import com.liy.ancientdragon.command.AncientDragonCommands;
import com.liy.ancientdragon.entity.AncientDragonEntities;
import com.liy.ancientdragon.flight.AncientDragonFlightManager;
import com.liy.ancientdragon.item.AncientDragonItems;
import com.liy.ancientdragon.inventory.AncientDragonMenus;
import com.liy.ancientdragon.worldgen.SacredMountainBuildService;
import com.liy.ancientdragon.worldgen.AncientDragonWorldgen;
import com.liy.ancientdragon.worldgen.SacredMountainNaturalDragonSpawner;
import com.liy.ancientdragon.worldgen.SacredMountainPlacementService;
import com.liy.ancientdragon.worldgen.SacredMountainNaturalRepairService;
import com.liy.ancientdragon.worldgen.SacredMountainSculkAtmosphere;
import com.liy.ancientdragon.worldgen.SacredMountainEnvironmentService;
import com.liy.ancientdragon.worldgen.SacredMountainSoulFireService;
import net.fabricmc.api.ModInitializer;

/** Common entrypoint for the private Ancient Dragon vertical slice. */
public final class AncientDragonMod implements ModInitializer {
    public static final String MOD_ID = "ancient_dragon";
    private static final System.Logger LOGGER = System.getLogger("Ancient Dragon");

    @Override
    public void onInitialize() {
        DragonAtmosphereNetworking.initialize();
        AncientDragonCriteria.initialize();
        AncientDragonBlocks.initialize();
        AncientCityGatewayActivation.register();
        DragonChronicleService.register();
        AncientDragonCorpseRewardService.register();
        AncientDragonItems.initialize();
        AncientDragonMenus.initialize();
        AncientDragonFlightManager.initialize();
        AncientDragonEntities.initialize();
        AncientDragonWorldgen.initialize();
        SacredMountainNaturalDragonSpawner.register();
        SacredMountainEnvironmentService.register();
        SacredMountainSculkAtmosphere.register();
        SacredMountainSoulFireService.register();
        SacredMountainBuildService.register();
        SacredMountainPlacementService.register();
        SacredMountainNaturalRepairService.register();
        AncientDragonCommands.register();
        LOGGER.log(System.Logger.Level.INFO, "Ancient Dragon vertical slice initialized");
    }
}
