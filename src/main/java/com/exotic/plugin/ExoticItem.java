package com.exotic.plugin;

import org.bukkit.inventory.ItemStack;

public interface ExoticItem {
    String id();
    String displayName();
    String announcement();
    ItemStack build();

    static ExoticItem byId(String id) {
        SwordType sword = SwordType.byId(id);
        if (sword != null) return sword;
        return TomeType.byId(id);
    }
}
