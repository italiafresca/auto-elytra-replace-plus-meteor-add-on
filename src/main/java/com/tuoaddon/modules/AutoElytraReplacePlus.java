package com.tuoaddon.modules;

import com.tuoaddon.AutoElytraAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AutoElytraReplacePlus extends Module {
    // Armor slot index for the chestplate in PlayerInventory.armor[]:
    // 0 = boots, 1 = leggings, 2 = chestplate/elytra, 3 = helmet
    private static final int CHEST_ARMOR_SLOT = 2;

    // Max elytra durability in vanilla Minecraft
    private static final int ELYTRA_MAX_DURABILITY = 432;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");

    private final Setting<Integer> replaceAt = sgGeneral.add(new IntSetting.Builder()
        .name("replace-at")
        .description("Durability at which the equipped elytra will be replaced.")
        .defaultValue(40)
        .range(1, 200)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> minReplacementDurability = sgGeneral.add(new IntSetting.Builder()
        .name("min-replacement-durability")
        .description("Minimum durability a spare elytra must have to be used as replacement. Must be higher than replace-at to avoid swap loops.")
        .defaultValue(100)
        .range(1, ELYTRA_MAX_DURABILITY)
        .sliderRange(1, ELYTRA_MAX_DURABILITY)
        .build()
    );

    private final Setting<Integer> swapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Delay in ticks between swap attempts. Prevents rapid repeated swaps.")
        .defaultValue(10)
        .range(1, 60)
        .sliderRange(1, 30)
        .build()
    );

    private final Setting<Boolean> ignoreScreens = sgSafety.add(new BoolSetting.Builder()
        .name("ignore-screens")
        .description("Does not swap while a container screen is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> warnWhenEmpty = sgSafety.add(new BoolSetting.Builder()
        .name("warn-when-empty")
        .description("Warns in chat when no valid spare elytra is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopWhenEmpty = sgSafety.add(new BoolSetting.Builder()
        .name("stop-when-empty")
        .description("Force-releases W/Jump/Sprint keys when no replacement elytra is available. Useful for auto-fly macros.")
        .defaultValue(true)
        .build()
    );

    private int timer;
    private boolean warned;

    public AutoElytraReplacePlus() {
        super(AutoElytraAddon.CATEGORY, "auto-elytra-replace-plus",
            "Automatically replaces low durability elytra with the best spare from your inventory.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        warned = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Countdown swap delay between swaps
        if (timer > 0) {
            timer--;
            return;
        }

        // Skip while GUI containers are open (e.g. chests, crafting)
        if (ignoreScreens.get() && mc.currentScreen instanceof HandledScreen<?>) return;

        // Check if currently wearing an elytra
        ItemStack equipped = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (equipped.isEmpty() || !equipped.isOf(Items.ELYTRA)) return;

        int currentDurability = getDurabilityLeft(equipped);

        // Elytra is fine – reset warning state and do nothing
        if (currentDurability > replaceAt.get()) {
            warned = false;
            return;
        }

        // Durability is low – search for a replacement
        int slot = findBestReplacementElytra();

        if (slot == -1) {
            // No valid replacement found
            if (warnWhenEmpty.get() && !warned) {
                warning("No spare elytra found! Current durability: " + currentDurability + "/" + ELYTRA_MAX_DURABILITY);
                warned = true;
            }

            // Force-release movement keys every tick so auto-fly stops
            if (stopWhenEmpty.get()) {
                mc.options.forwardKey.setPressed(false);
                mc.options.jumpKey.setPressed(false);
                mc.options.sprintKey.setPressed(false);
            }

            return;
        }

        // Swap: move best spare elytra from inventory to chest/elytra armor slot
        InvUtils.move()
            .from(slot)
            .toArmor(CHEST_ARMOR_SLOT);

        info("Swapped elytra (was " + currentDurability + " durability).");

        // Start cooldown to avoid rapid repeated swaps
        timer = swapDelay.get();
        warned = false;
    }

    /**
     * Scans inventory slots 0-35 (hotbar + main inventory) for the elytra
     * with the highest durability that meets the minimum threshold.
     * Returns the slot index, or -1 if none is found.
     */
    private int findBestReplacementElytra() {
        int bestSlot = -1;
        int bestDurability = -1;

        // Slots 0-8: hotbar, slots 9-35: main inventory
        // Slots 36-39 are armor slots (excluded intentionally)
        for (int slot = 0; slot <= 35; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);

            if (stack.isEmpty() || !stack.isOf(Items.ELYTRA)) continue;

            int durability = getDurabilityLeft(stack);

            // Reject spare elytras that are already too damaged
            // (also prevents swap-loop if minReplacementDurability <= replaceAt)
            if (durability <= replaceAt.get()) continue;
            if (durability < minReplacementDurability.get()) continue;

            if (durability > bestDurability) {
                bestDurability = durability;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private int getDurabilityLeft(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (!stack.isDamageable()) return 0;
        return stack.getMaxDamage() - stack.getDamage();
    }
}
