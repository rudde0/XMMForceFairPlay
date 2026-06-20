package dev.zerek.XMMForceFairPlay;

import com.cjcrafter.foliascheduler.FoliaCompatibility;
import com.cjcrafter.foliascheduler.ServerImplementation;
import dev.zerek.XMMForceFairPlay.listeners.PlayerChangedWorldListener;
import dev.zerek.XMMForceFairPlay.listeners.PlayerJoinListener;
import dev.zerek.XMMForceFairPlay.managers.PacketManager;
import dev.zerek.XMMForceFairPlay.managers.ModeManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class XMMForceFairPlay extends JavaPlugin {

    private PacketManager packetManager;
    private ModeManager modeManager;
    private boolean xaeroOnly;
    private List<String> fairPlayWorlds;
    public ServerImplementation scheduler = new FoliaCompatibility(this).getServerImplementation();

    @Override
    public void onLoad() {
        packetManager = new PacketManager(this);
        packetManager.load();
    }

    @Override
    public void onEnable() {

        saveDefaultConfig();
        String mode = this.getConfig().getString("MODE", "FAIR-PLAY");
        modeManager = new ModeManager(this, mode);
        this.xaeroOnly = this.getConfig().getBoolean("XAERO-ONLY", false);
        this.fairPlayWorlds = this.getConfig().getStringList("WORLDS");

        packetManager.init();

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChangedWorldListener(this), this);

        // Re-apply to players who are already online (e.g. after a plugin reload).
        for (Player player : getServer().getOnlinePlayers()) {
            sendControlTo(player);
        }

        getLogger().info("XMMForceFairPlay enabled with mode: " + modeManager.getMode()
                + (xaeroOnly ? " (Xaero-only)" : "")
                + (fairPlayWorlds.isEmpty() ? "" : " (worlds: " + fairPlayWorlds + ")"));
    }

    @Override
    public void onDisable() {
        packetManager.terminate();
    }

    /**
     * Sends the fair-play control string to a player after a short delay.
     * Skips players with the bypass permission, players whose current world is
     * not in the configured WORLDS list, and - when XAERO-ONLY is enabled -
     * players without a detected Xaero map mod, so vanilla players never receive
     * the (otherwise empty) control message. The checks run after the delay so the
     * player has settled into the destination world and the client has announced
     * its plugin channels.
     * @param player the player to send to
     */
    public void sendControlTo(Player player) {
        if (player.hasPermission("fairplay.bypass")) return;

        String string = modeManager.getString();
        if (string == null) return;

        scheduler.entity(player).runDelayed(() -> {
            if (!player.isOnline()) return;
            if (!isFairPlayWorld(player.getWorld().getName())) return;
            if (xaeroOnly && !hasXaeroChannel(player)) return;
            packetManager.sendString(player, string);
        }, 10L);
    }

    /**
     * Whether the fair-play message should be enforced in the given world.
     * When the WORLDS list is empty, all worlds are enforced.
     * @param worldName the world name to check
     * @return true if the world is enforced
     */
    private boolean isFairPlayWorld(String worldName) {
        if (fairPlayWorlds.isEmpty()) return true;
        for (String world : fairPlayWorlds) {
            if (world.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    /**
     * Checks whether a player has announced any Xaero plugin channel
     * (e.g. xaerominimap:main, xaeroworldmap:main), indicating a Xaero map mod is installed.
     * @param player the player to check
     * @return true if the client registered a Xaero channel
     */
    public static boolean hasXaeroChannel(Player player) {
        for (String channel : player.getListeningPluginChannels()) {
            if (channel.toLowerCase().startsWith("xaero")) return true;
        }
        return false;
    }

    /**
     * Gets the mode manager instance.
     * @return the mode manager
     */
    public ModeManager getModeManager() {
        return modeManager;
    }

    /**
     * Gets the packet manager instance.
     * @return the packet manager
     */
    public PacketManager getPacketManager() {
        return packetManager;
    }

    /**
     * Whether the plugin only targets players with a detected Xaero map mod.
     * @return true if XAERO-ONLY is enabled
     */
    public boolean isXaeroOnly() {
        return xaeroOnly;
    }

}
