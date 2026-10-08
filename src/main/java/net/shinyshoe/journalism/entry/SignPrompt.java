package net.shinyshoe.journalism.entry;

import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.shinyshoe.journalism.Journalism;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class SignPrompt implements Listener {

    private static final int BLOCKS_BELOW_FEET = 3;

    private final Journalism plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    SignPrompt(final Journalism plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // the sign is virtual
    void open(final Player player, final List<String> lines, @Nullable final Consumer<List<String>> onDone) {
        close(player);

        final Location location = player.getLocation().getBlock().getLocation();
        final int lowest = location.getWorld().getMinHeight();
        final int highest = location.getWorld().getMaxHeight() - 1;
        location.setY(Math.clamp(location.getBlockY() - BLOCKS_BELOW_FEET, lowest, highest));

        final BlockData data = Material.OAK_SIGN.createBlockData();
        final Sign sign = (Sign) data.createBlockState();
        for (int line = 0; line < EntryFiles.SIGN_LINES; line++) {
            // sign screen cant display minimessage, but can use the old color codes
            final Component text = MiniMessage.miniMessage().deserialize(line < lines.size() ? lines.get(line) : "");
            sign.getSide(Side.FRONT).line(line, Component.text(LegacyComponentSerializer.legacySection().serialize(text)));
        }
        player.sendBlockChange(location, data);
        player.sendBlockUpdate(location, sign);
        player.openVirtualSign(Position.block(location), Side.FRONT);
        sessions.put(player.getUniqueId(), new Session(location, onDone));
    }

    void closeAll() {
        for (final UUID id : List.copyOf(sessions.keySet())) {
            final Player player = plugin.getServer().getPlayer(id);
            if (player != null) close(player);
        }
        sessions.clear();
    }

    // shows the player the block that is really there again
    private void close(final Player player) {
        final Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        final Block block = session.location.getBlock();
        player.sendBlockChange(session.location, block.getBlockData());
        if (block.getState() instanceof TileState state) {
            player.sendBlockUpdate(session.location, state);
        }
    }

    @EventHandler
    public void onUncheckedSignChange(final UncheckedSignChangeEvent event) {
        final Player player = event.getPlayer();
        final Session session = sessions.get(player.getUniqueId());
        final BlockPosition edited = event.getEditedBlockPosition();
        if (session == null || edited.blockX() != session.location.getBlockX()
                || edited.blockY() != session.location.getBlockY() || edited.blockZ() != session.location.getBlockZ()) {
            return;
        }
        event.setCancelled(true);

        final List<String> lines = event.lines().stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacySection()
                        .deserialize(PlainTextComponentSerializer.plainText().serialize(line))))
                .toList();
        close(player);
        if (session.onDone != null) session.onDone.accept(lines);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private record Session(Location location, @Nullable Consumer<List<String>> onDone) {
    }
}
