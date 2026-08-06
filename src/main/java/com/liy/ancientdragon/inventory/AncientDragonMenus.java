package com.liy.ancientdragon.inventory;

import com.liy.ancientdragon.AncientDragonMod;
import com.liy.ancientdragon.block.SunheartAltarMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** Menu registrations shared by the dedicated server and client. */
public final class AncientDragonMenus {
    public static final Identifier SUNHEART_ALTAR_ID =
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "sunheart_altar");
    public static final MenuType<SunheartAltarMenu> SUNHEART_ALTAR = Registry.register(
            BuiltInRegistries.MENU,
            SUNHEART_ALTAR_ID,
            new MenuType<>(SunheartAltarMenu::new, FeatureFlags.VANILLA_SET));

    private AncientDragonMenus() {
    }

    public static void initialize() {
        // Class loading performs registration.
    }
}
