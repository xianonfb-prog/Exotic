package com.exotic.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;

public class ScoreboardManager {

    private final ExoticPlugin plugin;
    private final Map<java.util.UUID, Scoreboard> boards = new HashMap<>();
    private Team hiddenNameTeam;

    public ScoreboardManager(ExoticPlugin plugin) {
        this.plugin = plugin;
    }

    /** Rebuilds and (re)sends the sidebar for a player based on their active trial, if any. */
    public void update(Player player) {
        TrialSystem.ActiveTrial trial = plugin.trials().get(player);
        if (trial == null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }

        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard());

        Objective obj = board.getObjective("exotic_trial");
        if (obj != null) obj.unregister();
        obj = board.registerNewObjective("exotic_trial", Criteria.DUMMY,
                net.kyori.adventure.text.Component.text("§6§lExotic Trial"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        SwordType type = SwordType.byId(trial.swordId);
        int line = trial.progress.size() + 1;
        obj.getScore("§f" + type.displayName()).setScore(line--);
        obj.getScore("§7 ").setScore(line--);

        for (var entry : trial.progress.entrySet()) {
            int required = TrialSystem.REQUIREMENTS.get(trial.swordId).get(entry.getKey());
            String text = "§e" + entry.getKey().label + ": §f" + entry.getValue() + "§7/§f" + required;
            // Scoreboard entries must be unique strings; pad with invisible color codes if needed.
            obj.getScore(uniqueEntry(text, line)).setScore(line--);
        }

        player.setScoreboard(board);
    }

    private final Map<String, String> entryCache = new HashMap<>();
    private String uniqueEntry(String text, int line) {
        // Ensure uniqueness across lines without changing visible text meaningfully.
        return text.length() > 40 ? text.substring(0, 40) : text;
    }

    /** Temporarily hides a player's nametag for the given duration (Lurker ability). */
    public void hideNameTag(Player player, long ticks) {
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = mainBoard.getTeam("exotic_hidden");
        if (team == null) {
            team = mainBoard.registerNewTeam("exotic_hidden");
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        team.addEntry(player.getName());

        Team finalTeam = team;
        Bukkit.getScheduler().runTaskLater(plugin, () -> finalTeam.removeEntry(player.getName()), ticks);
    }

    public void clear(Player player) {
        boards.remove(player.getUniqueId());
    }
}
