package us.dingl.VowSMPPlugin.Gui;

import java.util.HashSet;
import java.util.Set;

public record GuiZone(String id, Set<Integer> slots, SlotPermission permission) {

    public boolean contains(int slot) {
        return slots.contains(slot);
    }

    /** Helper for building a contiguous slot range, e.g. range(19, 25). */
    public static Set<Integer> range(int from, int to) {
        Set<Integer> result = new HashSet<>();
        for (int i = from; i <= to; i++) {
            result.add(i);
        }
        return result;
    }
}