package me.arboriginal.SimpleCompass.trackers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import me.arboriginal.SimpleCompass.plugin.AbstractTracker;
import me.arboriginal.SimpleCompass.plugin.SimpleCompass;
import me.arboriginal.SimpleCompass.utils.CacheUtil;

public class PlayerTracker extends AbstractTracker implements Listener {
  private HashMap<UUID, HashMap<UUID, Long>> requests;

  // ----------------------------------------------------------------------------------------------
  // Constructor methods
  // ----------------------------------------------------------------------------------------------

  public PlayerTracker(SimpleCompass plugin) {
    super(plugin);
    requests = new HashMap<UUID, HashMap<UUID, Long>>();
  }

  // ----------------------------------------------------------------------------------------------
  // Tracker methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public String github() {
    return "arboriginal/SCT-PlayerTracker";
  }

  @Override
  public String trackerID() {
    return "PLAYER";
  }

  @Override
  public String version() {
    return "7";
  }

  // ----------------------------------------------------------------------------------------------
  // Listener methods
  // ----------------------------------------------------------------------------------------------

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    cleanPlayerTrackerRequests(event.getPlayer(), true);
  }

  // ----------------------------------------------------------------------------------------------
  // Actions methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<TrackingActions> getActionsAvailable(Player player, boolean keepUnavailable) {
    List<TrackingActions> list = super.getActionsAvailable(player, keepUnavailable);

    if (player.hasPermission("scompass.track." + trackerID() + ".request"))
      list.add(TrackingActions.ASK);

    if (player.hasPermission("scompass.track." + trackerID() + ".silently")
        && (keepUnavailable || !availableTargets(player, "").isEmpty()))
      list.add(TrackingActions.START);

    if (player.hasPermission("scompass.track." + trackerID() + ".accept")
        && getPlayerTrackerRequests(player) != null) {
      list.add(TrackingActions.ACCEPT);
      list.add(TrackingActions.DENY);
    }

    if (keepUnavailable || !list(player, null, "").isEmpty()) list.add(TrackingActions.STOP);

    return list;
  }

  // ----------------------------------------------------------------------------------------------
  // Targets methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<String> availableTargets(Player player, String startWith) {
    List<String> list = new ArrayList<String>();

    sc.getServer().getOnlinePlayers().forEach(candidate -> {
      String name = candidate.getName();
      if (!name.equals(player.getName()))
        if (startWith.isEmpty() || name.toLowerCase().startsWith(startWith.toLowerCase())) list.add(name);
    });

    return list;
  }

  @Override
  public boolean del(Player player, String name) {
    return false;
  }

  @Override
  public double[] get(Player player, String name) {
    for (Player target : sc.getServer().getOnlinePlayers())
      if (target.getName().equalsIgnoreCase(name))
        return new double[] { target.getLocation().getX(), target.getLocation().getZ() };

    return null;
  }

  @Override
  public List<String> list(Player player, TrackingActions action, String startWith) {
    if (action == null) return activeTargets(player, startWith);
    List<String> list;

    switch (action) {
      case ACCEPT:
      case DENY:
        list = new ArrayList<String>();

        UUID uid = player.getUniqueId();
        if (!requests.containsKey(uid)) break;

        requests.get(uid).forEach((seekerUID, until) -> {
          if (CacheUtil.now() > until)
            requests.get(uid).remove(seekerUID);
          else {
            Player seeker = sc.getServer().getPlayer(seekerUID);
            if (seeker.isOnline()) list.add(seeker.getName());
          }
        });
        break;

      case STOP:
        list = new ArrayList<String>();
        for (OfflinePlayer candidate : sc.getServer().getOfflinePlayers()) {
          String name = candidate.getName();
          if (!name.equals(player.getName()))
            if (startWith.isEmpty() || name.toLowerCase().startsWith(startWith.toLowerCase())) list.add(name);
        }
        break;

      default:
        list = super.list(player, action, startWith);
    }

    return list;
  }

  @Override
  public boolean set(Player player, String name, double[] coords) {
    return false;
  }

  // ----------------------------------------------------------------------------------------------
  // Command methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public List<String> commandSuggestions(Player player, String[] args, HashMap<String, Object> parsed) {
    if (args.length == 3 && parsed.get("action") != null && !parsed.get("action").equals(TrackingActions.STOP))
      return availableTargets(player, args[2]);

    return super.commandSuggestions(player, args, parsed);
  }

  @Override
  public void parseArguments(Player player, String[] args, HashMap<String, Object> parsed) {
    super.parseArguments(player, args, parsed);

    if (parsed.get("target") != null || parsed.get("action") == null
        || parsed.get("action").equals(TrackingActions.STOP) || args.length < 3)
      return;

    for (OfflinePlayer candidate : sc.getServer().getOfflinePlayers())
      if (candidate.getName().equalsIgnoreCase(args[2])) {
        parsed.put("target", candidate.getName());
        return;
      }
  }

  @Override
  public boolean perform(Player player, String command, TrackingActions action, String target, String[] args) {
    if (target == null) {
      sendMessage(player, "missing_target");
      return true;
    }

    switch (action) {
      case ACCEPT:
        return respondPlayerTrackerRequest(player, target, true);

      case DENY:
        return respondPlayerTrackerRequest(player, target, false);

      case ASK:
        if (limitReached(player, TrackingActions.START, true, null)) break;

        Player targetPlayer = sc.getServer().getPlayer(target);
        if (targetPlayer == null)
          sendMessage(player, "target_not_found", ImmutableMap.of("target", target));
        else {
          sendPlayerTrackerRequest(player, targetPlayer);
          sendMessage(player, "ASK", ImmutableMap.of("target", target));
        }
        break;

      case START:
        if (!activate(player, args[2], true)) break;
        sendMessage(player, "START", ImmutableMap.of("target", args[2]));
        break;

      case STOP:
        disable(player, args[2]);
        sendMessage(player, "STOP", ImmutableMap.of("target", target));
        break;

      default:
        return false;
    }

    return true;
  }

  // ----------------------------------------------------------------------------------------------
  // Specific methods
  // ----------------------------------------------------------------------------------------------

  public void cleanPlayerTrackerRequests(Player player) {
    cleanPlayerTrackerRequests(player, false);
  }

  public void cleanPlayerTrackerRequests(Player player, boolean delete) {
    UUID uid = player.getUniqueId();

    if (!requests.containsKey(uid)) return;
    if (delete)
      requests.remove(uid);
    else {
      requests.get(uid).forEach((seeker, until) -> {
        if (CacheUtil.now() > until) requests.get(uid).remove(seeker);
      });
    }
  }

  public Set<UUID> getPlayerTrackerRequests(Player player) {
    UUID uid = player.getUniqueId();
    return requests.containsKey(uid) ? requests.get(uid).keySet() : null;
  }

  public long getPlayerTrackerRequest(Player player, Player seeker) {
    UUID pUid = player.getUniqueId(), sUid = seeker.getUniqueId();
    cleanPlayerTrackerRequests(player);
    if (requests.containsKey(pUid) && requests.get(pUid).containsKey(sUid)) return requests.get(pUid).get(sUid);
    return 0;
  }

  public void removePlayerTrackerRequest(Player player, Player seeker) {
    UUID pUid = player.getUniqueId(), sUid = seeker.getUniqueId();
    if (requests.containsKey(pUid) && requests.get(pUid).containsKey(sUid)) requests.get(pUid).remove(sUid);
  }

  public boolean respondPlayerTrackerRequest(Player player, String seekerName, boolean accept) {
    Player seeker = sc.getServer().getPlayer(seekerName);
    if (seeker == null || !seeker.isOnline()) return true;

    Long request = getPlayerTrackerRequest(player, seeker);
    if (request == 0) {
      sendMessage(player, "request.expired", ImmutableMap.of("player", seekerName));
      return true;
    }

    removePlayerTrackerRequest(player, seeker);
    ImmutableMap<String, String> placeholders = ImmutableMap.of("player", seekerName, "target", player.getName());

    if (accept) {
      String[] keys = { "request.accepted.target", "request.accepted.player" };
      if (!activate(seeker, player.getName(), false)) for (int i = 0; i < keys.length; i++) keys[i] += "_limit_reached";

      sendMessage(player, keys[0], placeholders);
      sendMessage(seeker, keys[1], placeholders);
    }
    else {
      sendMessage(player, "request.refused.target", placeholders);
      sendMessage(seeker, "request.refused.player", placeholders);
    }

    return true;
  }

  public void sendPlayerTrackerRequest(Player hunter, Player target) {
    Map<String, Map<String, String>> commands = new HashMap<String, Map<String, String>>();

    for (TrackingActions action : ImmutableList.of(TrackingActions.ACCEPT, TrackingActions.DENY)) {
      String command = "/sctrack " + trackerName() + " " + getActionName(action) + " " + hunter.getName();
      commands.put("{" + action + "}", ImmutableMap.of(
          "text", prepareMessage("request." + action),
          "click", command,
          "hover", prepareMessage("request." + action + "_hover", ImmutableMap.of("command", command))));
    }

    setPlayerTrackerRequest(hunter, target);
    target.spigot().sendMessage(sc.createClickableMessage(
        prepareMessage("request.message", ImmutableMap.of("player", hunter.getName())), commands));
  }

  public void setPlayerTrackerRequest(Player hunter, Player target) {
    UUID hUid = hunter.getUniqueId(), tUid = target.getUniqueId();

    if (!requests.containsKey(tUid))
      requests.put(tUid, new HashMap<UUID, Long>());
    else
      cleanPlayerTrackerRequests(target);

    requests.get(tUid).put(hUid, CacheUtil.now() + settings.getInt("settings.request_duration") * 1000);
  }
}
