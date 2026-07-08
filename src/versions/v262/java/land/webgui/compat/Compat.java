package land.webgui.compat;

import java.util.function.Predicate;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import land.webgui.WebGUIMod;

/**
 * Версионный адаптер API (Minecraft 26.2 / NeoForge 26.2) — ТОЛЬКО общие
 * (server-safe) классы: грузится и на dedicated-сервере при регистрации
 * пейлоадов. Всё клиентское — в {@link ClientCompat}.
 */
public final class Compat {
    private Compat() {}

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, path));
    }

    /** Требование уровня прав "оператор" (эквивалент OP 2) для команд. */
    public static Predicate<CommandSourceStack> opRequirement() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

    public static String dimensionId(Level level) {
        return level.dimension().identifier().toString();
    }
}
