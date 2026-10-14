package ru.anton_flame.afanvilpotions.listeners;

import org.bukkit.Color;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.Material;
import ru.anton_flame.afanvilpotions.utils.ConfigManager;
import ru.anton_flame.afanvilpotions.utils.Hex;

import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

public final class Listeners implements Listener {

    private static final int LEFT_SLOT = 0;
    private static final int RIGHT_SLOT = 1;
    private static final int RESULT_SLOT = 2;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack left = inv.getItem(LEFT_SLOT);
        ItemStack right = inv.getItem(RIGHT_SLOT);
        event.setResult(null);

        if (!validPair(left, right)) return;
        PotionMeta leftMeta = (PotionMeta) left.getItemMeta();
        PotionMeta rightMeta = (PotionMeta) right.getItemMeta();
        PotionType leftType = leftMeta.getBasePotionType();
        PotionType rightType = rightMeta.getBasePotionType();
        if (leftType == null || rightType == null || leftType != rightType) return;

        boolean upgraded = isUpgradePotion(leftType);
        if (upgraded != isUpgradePotion(rightType)) return;

        boolean levelThree = upgraded;
        if (levelThree && !ConfigManager.enabledLevel3) return;
        if (!levelThree && !ConfigManager.enabledLevel2) return;

        ConfigurationSection potions = levelThree ? ConfigManager.potionsLevel3 : ConfigManager.potionsLevel2;
        String effectName = getConfiguredEffectName(leftType, potions);
        if (effectName == null) return;

        int amount = Math.min(left.getAmount(), right.getAmount());
        if (amount <= 0) return;

        ItemStack result = createResult(left, effectName, potions, levelThree ? 2 : 1);
        if (result == null) return;
        result.setAmount(amount);
        event.setResult(result);

        int pricePerPotion = Math.max(0, potions.getInt(effectName + ".exp-price", 0));
        long totalCost = (long) pricePerPotion * amount;
        inv.setRepairCost((int) Math.min(Integer.MAX_VALUE, totalCost));
    }

    /**
     * Bukkit/Paper 1.21.4 PotionType does not expose isUpgrade().
     * Strong vanilla potion variants are represented by STRONG_* enum constants.
     */
    private boolean isUpgradePotion(PotionType type) {
        return type.name().startsWith("STRONG_");
    }

    private boolean validPair(ItemStack left, ItemStack right) {
        if (left == null || right == null || left.getType().isAir() || right.getType().isAir()) return false;
        if (left.getType() != right.getType()) return false;
        if (!(left.getItemMeta() instanceof PotionMeta) || !(right.getItemMeta() instanceof PotionMeta)) return false;
        return left.getType() == Material.POTION || left.getType() == Material.SPLASH_POTION
                || left.getType() == Material.LINGERING_POTION;
    }

    private String getConfiguredEffectName(PotionType type, ConfigurationSection potions) {
        if (potions == null) return null;
        for (String configuredName : potions.getKeys(false)) {
            PotionEffectType configuredType = PotionEffectType.getByName(configuredName.toUpperCase(Locale.ROOT));
            if (configuredType == null) continue;
            for (PotionEffect effect : type.getPotionEffects()) {
                if (effect.getType().equals(configuredType)) return configuredName;
            }
        }
        return null;
    }

    private ItemStack createResult(ItemStack input, String potionName, ConfigurationSection potions, int amplifier) {
        PotionEffectType effectType = PotionEffectType.getByName(potionName.toUpperCase(Locale.ROOT));
        if (effectType == null) return null;
        String colorString = potions.getString(potionName + ".potion-color", "255,255,255");
        String[] rgb = colorString.split(",");
        if (rgb.length != 3) return null;
        try {
            int r = clamp(Integer.parseInt(rgb[0].trim()));
            int g = clamp(Integer.parseInt(rgb[1].trim()));
            int b = clamp(Integer.parseInt(rgb[2].trim()));
            ItemStack result = new ItemStack(input.getType(), 1);
            if (!(result.getItemMeta() instanceof PotionMeta)) return null;
            PotionMeta meta = (PotionMeta) result.getItemMeta();
            int duration = Math.max(1, potions.getInt(potionName + ".duration", 1)) * 20;
            meta.addCustomEffect(new PotionEffect(effectType, duration, amplifier), true);
            meta.setDisplayName(Hex.color(potions.getString(potionName + ".potion-name", "Upgraded Potion")));
            meta.setColor(Color.fromRGB(r, g, b));
            result.setItemMeta(meta);
            Integer stackLimit = input.getData(DataComponentTypes.MAX_STACK_SIZE);
            if (stackLimit != null) {
                result.setData(DataComponentTypes.MAX_STACK_SIZE, stackLimit);
            }
            return result;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * Handle the result as a single transaction. Never keep pending leftovers in a
     * Map keyed by mutable ItemStacks: that design can duplicate or lose items when
     * the result is recalculated, shift-clicked, or anvil viewers change.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResultClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        if (event.getRawSlot() != RESULT_SLOT || event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (!(event.getWhoClicked() instanceof Player)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        AnvilInventory anvil = (AnvilInventory) event.getInventory();
        ItemStack left = anvil.getItem(LEFT_SLOT);
        ItemStack right = anvil.getItem(RIGHT_SLOT);
        ItemStack shownResult = anvil.getItem(RESULT_SLOT);

        // Revalidate current inputs and recompute output rather than trusting a stale preview.
        if (!validPair(left, right) || shownResult == null || shownResult.getType().isAir()) return;
        if (left.getAmount() < shownResult.getAmount() || right.getAmount() < shownResult.getAmount()) return;
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getLevel() < anvil.getRepairCost()) return;

        ItemStack output = shownResult.clone();
        int outputAmount = output.getAmount();
        boolean shift = event.isShiftClick();
        // Recompute permission before any inventory mutation.
        if (!hasUpgradePermission(player, left)) {
            anvil.setItem(RESULT_SLOT, null);
            return;
        }

        if (shift) {
            if (!hasInventoryRoom(player.getInventory(), output)) return;
        } else {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                if (!cursor.isSimilar(output) || cursor.getAmount() + outputAmount > cursor.getMaxStackSize()) return;
            }
        }

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            player.setLevel(player.getLevel() - anvil.getRepairCost());
        }

        if (!shift) {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType().isAir()) {
                event.setCursor(output);
            } else {
                cursor.setAmount(cursor.getAmount() + outputAmount);
                event.setCursor(cursor);
            }
        }

        consume(anvil, LEFT_SLOT, outputAmount);
        consume(anvil, RIGHT_SLOT, outputAmount);
        anvil.setItem(RESULT_SLOT, null);
        anvil.setRepairCost(0);
        if (shift) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(output);
            // Capacity was checked before consuming, so this should never occur.
            // If another plugin mutates inventory synchronously, do not silently discard leftovers.
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.updateInventory();
    }


    private boolean hasInventoryRoom(Inventory inventory, ItemStack output) {
        int remaining = output.getAmount();
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                remaining -= output.getMaxStackSize();
            } else if (item.isSimilar(output)) {
                remaining -= Math.max(0, item.getMaxStackSize() - item.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private boolean hasUpgradePermission(Player player, ItemStack left) {
        if (left == null || !(left.getItemMeta() instanceof PotionMeta)) return false;
        PotionType type = ((PotionMeta) left.getItemMeta()).getBasePotionType();
        if (type == null) return false;
        boolean levelThree = isUpgradePotion(type);
        if (levelThree) {
            return !ConfigManager.checkPermissionLevel3 || player.hasPermission("afanvilpotions.upgrade.level.3");
        }
        return !ConfigManager.checkPermissionLevel2 || player.hasPermission("afanvilpotions.upgrade.level.2");
    }

    private void consume(AnvilInventory inv, int slot, int amount) {
        ItemStack stack = inv.getItem(slot);
        if (stack == null) return;
        int remaining = stack.getAmount() - amount;
        if (remaining <= 0) inv.setItem(slot, null);
        else {
            stack.setAmount(remaining);
            inv.setItem(slot, stack);
        }
    }
}
