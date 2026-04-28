package com.tuoaddon;

import com.tuoaddon.modules.AutoElytraReplacePlus;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AutoElytraAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("AutoElytra");

    @Override
    public void onInitialize() {
        LOG.info("Initializing AutoElytraReplacePlus addon");
        Modules.get().add(new AutoElytraReplacePlus());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.tuoaddon";
    }
}
