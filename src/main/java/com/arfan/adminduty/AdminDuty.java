package com.arfan.adminduty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class AdminDuty extends JavaPlugin implements Listener {

    // Disimpan di memori saja: restart server / logout = semua otomatis OFF DUTY.
    private final Set<UUID> onDuty = new HashSet<>();
    private final Set<String> adminPlugins = new HashSet<>();
    private final Set<String> alwaysAllowed = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLists();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadLists() {
        adminPlugins.clear();
        alwaysAllowed.clear();
        for (String s : getConfig().getStringList("admin-plugins")) {
            adminPlugins.add(s.toLowerCase(Locale.ROOT));
        }
        for (String s : getConfig().getStringList("always-allowed")) {
            alwaysAllowed.add(s.toLowerCase(Locale.ROOT));
        }
    }

    private String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "&6[AdminDuty] ");
        String text = getConfig().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', prefix + text);
    }

    // ---------------- /adminduty ----------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("console"));
            return true;
        }
        // Pengaman ekstra: hanya OP.
        if (!player.isOp()) {
            return true;
        }
        UUID id = player.getUniqueId();
        if (onDuty.remove(id)) {
            player.sendMessage(msg("off-duty"));
        } else {
            onDuty.add(id);
            player.sendMessage(msg("on-duty"));
        }
        return true;
    }

    // ---------------- Blokir command admin kalau belum on duty ----------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandUse(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();

        // Player biasa (bukan OP): plugin ini tidak ikut campur sama sekali, tidak ada notif.
        if (!p.isOp()) {
            onDuty.remove(p.getUniqueId());
            return;
        }
        if (onDuty.contains(p.getUniqueId())) {
            return;
        }

        String raw = e.getMessage();
        if (raw.length() < 2) return;
        String label = raw.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (label.isEmpty()) return;

        if (isAdminCommand(label)) {
            e.setCancelled(true);
            p.sendMessage(msg("not-on-duty"));
        }
    }

    private boolean isAdminCommand(String label) {
        // "essentials:heal" -> "heal"
        String shortLabel = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        if (alwaysAllowed.contains(label) || alwaysAllowed.contains(shortLabel)) {
            return false;
        }

        Command cmd = Bukkit.getCommandMap().getCommand(label);
        if (cmd == null) {
            return false; // bukan command yang dikenal, biarkan server yang menangani
        }

        // 1) Command yang punya permission default OP (termasuk command vanilla) = admin.
        String perm = cmd.getPermission();
        if (perm != null && !perm.isBlank()) {
            boolean publicCmd = false;
            for (String part : perm.split(";")) {
                Permission pr = Bukkit.getPluginManager().getPermission(part.trim());
                PermissionDefault def = (pr == null) ? PermissionDefault.OP : pr.getDefault();
                if (def == PermissionDefault.TRUE || def == PermissionDefault.NOT_OP) {
                    publicCmd = true;
                    break;
                }
            }
            if (!publicCmd) return true;
            return false;
        }

        // 2) Tidak ada permission di command-nya (contoh EssentialsX): lihat plugin pemiliknya.
        if (cmd instanceof PluginIdentifiableCommand pic) {
            String owner = pic.getPlugin().getName().toLowerCase(Locale.ROOT);
            return adminPlugins.contains(owner);
        }
        return false;
    }

    // ---------------- Logout = otomatis OFF DUTY (tanpa pesan, join juga tanpa pesan) ----------------
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        onDuty.remove(e.getPlayer().getUniqueId());
    }
}
