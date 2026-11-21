package com.kingdomunique.trade;

import com.kingdomunique.building.BuildingType;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class TradeDisplayOffers {
    public record Entry(TradeOffer offer, List<Set<BuildingType>> alternatives) {
        public boolean matches(Set<BuildingType> completed) {
            return alternatives.stream().anyMatch(completed::containsAll);
        }
    }

    private TradeDisplayOffers() {
    }

    public static List<Entry> build(String role) {
        Set<BuildingType> queried = EnumSet.noneOf(BuildingType.class);
        KingdomTradeRegistry.buildOffers(role, type -> {
            queried.add(type);
            return false;
        });
        List<BuildingType> dependencies = List.copyOf(queried);
        List<Entry> entries = new ArrayList<>();
        for (int mask = 0; mask < (1 << dependencies.size()); mask++) {
            Set<BuildingType> completed = EnumSet.noneOf(BuildingType.class);
            for (int bit = 0; bit < dependencies.size(); bit++) {
                if ((mask & (1 << bit)) != 0) {
                    completed.add(dependencies.get(bit));
                }
            }
            for (TradeOffer offer : KingdomTradeRegistry.buildOffers(role, completed::contains)) {
                Entry entry = entries.stream().filter(e -> sameOffer(e.offer(), offer)).findFirst().orElse(null);
                if (entry == null) {
                    entry = new Entry(offer, new ArrayList<>());
                    entries.add(entry);
                }
                if (entry.alternatives().stream().noneMatch(completed::containsAll)) {
                    entry.alternatives().removeIf(requirement -> requirement.containsAll(completed));
                    entry.alternatives().add(Set.copyOf(completed));
                }
            }
        }
        return entries.stream().map(e -> new Entry(e.offer(), List.copyOf(e.alternatives()))).toList();
    }

    public static boolean sameOffer(TradeOffer first, TradeOffer second) {
        return sameStack(first.getDisplayedFirstBuyItem(), second.getDisplayedFirstBuyItem())
                && sameStack(first.getDisplayedSecondBuyItem(), second.getDisplayedSecondBuyItem())
                && sameStack(first.getSellItem(), second.getSellItem());
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.areItemsAndComponentsEqual(first, second);
    }
}
