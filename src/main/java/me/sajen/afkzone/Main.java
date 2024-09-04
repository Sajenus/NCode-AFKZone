package me.sajen.afkzone;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class Main extends JavaPlugin implements Listener {

    Map<UUID, Long> afkPlayers = new HashMap<>();

    String messageType;
    String rewardTitle;
    String rewardSubtitle;
    String rewardActionBar;
    String rewardMessage;
    String afkRegion;
    long rewardTime;
    List<String> commandRewards;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();

        reload();

        afkPlayersTimer();
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkRegion(player);
        }

    }

    @Override
    public void onDisable() {
        getLogger().info("AfkZone wyłączony");
    }

    private void afkPlayersTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Long> data : afkPlayers.entrySet()) {
                    Player player = getServer().getPlayer(data.getKey());
                    if (player == null) {
                        afkPlayers.remove(data.getKey());
                    }

                    long secondsRemain = (data.getValue() - System.currentTimeMillis()) / 1000;
                    if (secondsRemain > 0) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(secondsRemain + " second(s) remain until penalty"));
                    } else {
                        for(String command : commandRewards) {
                            command = command.replace("{PLAYER}", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                        }
                        switch (messageType) {
                            case "title":
                                player.sendTitle(format(rewardTitle), format(rewardSubtitle), 10, 70, 20);
                                break;
                            case "actionbar":
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(format(rewardActionBar)));
                                break;
                            case "message":
                                player.sendMessage(format(rewardMessage));
                                break;
                        }
                        data.setValue(System.currentTimeMillis() + rewardTime * 1000);
                        reload();
                    }

                    if (player.isDead()) {
                        afkPlayers.remove(data.getKey());
                    }


                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    public static String format(String str) {
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    public static void wait(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public void reload() {
        messageType = getConfig().getString("afk.afk-messages.message-type");
        rewardTitle = getConfig().getString("afk.afk-messages.reward-give-title");
        rewardSubtitle = getConfig().getString("afk.afk-messages.reward-give-subtitle");
        rewardActionBar = getConfig().getString("afk.afk-messages.reward-give-actionbar");
        rewardMessage = getConfig().getString("afk.afk-messages.reward-give-message");

        afkRegion = getConfig().getString("afk.afk-other-things.afk-region");
        rewardTime = getConfig().getLong("afk.afk-other-things.afk-reward-time");
        commandRewards = getConfig().getStringList("afk.afk-other-things.afk-command-rewards");

        reloadConfig();
    }

    private Set<Player> playersInRegion = new HashSet<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(player.getWorld()));
        Location locationOfEvent = event.getTo();
        ApplicableRegionSet set = regionManager.getApplicableRegions(
                BlockVector3.at(locationOfEvent.getX(), locationOfEvent.getY(), locationOfEvent.getZ()));
        boolean isAfkRegion = false;
        for (ProtectedRegion region : set) {
            if (region.getId().equalsIgnoreCase(afkRegion)) {
                isAfkRegion = true;
                break;
            }
        }
        if (isAfkRegion) {
            if (!playersInRegion.contains(player)) {
                playersInRegion.add(player);
                if (!afkPlayers.containsKey(player.getUniqueId())) {
                    afkPlayers.put(player.getUniqueId(), System.currentTimeMillis() + rewardTime * 1000);
                }
            }
        } else {
            if (playersInRegion.contains(player)) {
                playersInRegion.remove(player);
                afkPlayers.remove(player.getUniqueId());
            }
        }
    }

    public void checkRegion(Player player) {
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(player.getWorld()));
        Location location = player.getLocation();
        ApplicableRegionSet set = regionManager.getApplicableRegions(BlockVector3.at(location.getX(), location.getY(), location.getZ()));
        for (ProtectedRegion region : set) {
            if (region.getId().equalsIgnoreCase(afkRegion)) {
                if (!afkPlayers.containsKey(player.getUniqueId())) {
                    afkPlayers.put(player.getUniqueId(), System.currentTimeMillis() + rewardTime * 1000);
                }
            } else {
                afkPlayers.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        checkRegion(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        checkRegion(event.getPlayer());
    }
}
