package us.dingl.VowSMPPlugin.Gui;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class StateBinding<T> {

    private final Supplier<T> state;
    private final Function<T, ItemStack> renderer;

    private StateBinding(Supplier<T> state, Function<T, ItemStack> renderer) {
        this.state = state;
        this.renderer = renderer;
    }

    /** General case: any state type, any mapping logic. */
    public static <T> StateBinding<T> of(Supplier<T> state, Function<T, ItemStack> renderer) {
        return new StateBinding<>(state, renderer);
    }

    /** var1 ? item1 : item2 */
    public static StateBinding<Boolean> bool(Supplier<Boolean> state, ItemStack whenTrue, ItemStack whenFalse) {
        return new StateBinding<>(state, value -> Boolean.TRUE.equals(value) ? whenTrue : whenFalse);
    }

    /** For more than two states, e.g. an enum - falls back to fallback if the value has no entry. */
    public static <T> StateBinding<T> map(Supplier<T> state, Map<T, ItemStack> items, ItemStack fallback) {
        return new StateBinding<>(state, value -> items.getOrDefault(value, fallback));
    }

    public Supplier<ItemStack> asSupplier() {
        return () -> renderer.apply(state.get());
    }
}