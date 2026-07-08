package land.webgui.compat;

import java.util.function.Predicate;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import land.webgui.WebGUIMod;

/**
 * Версионный адаптер API (Minecraft 1.21.1 / NeoForge 21.1) — ТОЛЬКО общие
 * (server-safe) классы: грузится и на dedicated-сервере при регистрации
 * пейлоадов. Всё клиентское — в {@link ClientCompat}.
 */
public final class Compat {
    private Compat() {}

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, path));
    }

    /** Требование уровня прав "оператор" (OP 2) для команд. */
    public static Predicate<CommandSourceStack> opRequirement() {
        return s -> s.hasPermission(2);
    }

    public static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }
}
