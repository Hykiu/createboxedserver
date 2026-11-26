package fr.hykiuidustries.createboxed.team;

import fr.hykiuidustries.createboxed.Keys.Lang;
import fr.hykiuidustries.createboxed.plot.data.PlotData;
import fr.hykiuidustries.createboxed.plot.data.PlotEntry;
import fr.hykiuidustries.createboxed.plot.data.PlotStatus;
import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import fr.hykiuidustries.createboxed.team.data.Team;
import fr.hykiuidustries.createboxed.team.data.TeamMember;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Builds the rich information messages shared by /slot team info and /slot admin.
 */
@NullMarked
public final class CommandInfoView {
    private static final int BAR_WIDTH = 10;
    private static final String SEPARATOR = "------------------------------";

    private CommandInfoView() {
    }

    public static Component header(int teamCount) {
        return Component.translatable(Lang.ADMIN_LIST_HEADER, teamCount)
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    public static List<Component> details(PlotData storage, Team team) {
        Component memberLabel = Component.translatable(Lang.INFO_LABEL_MEMBERS)
            .append(Component.literal(" (" + team.size() + ")"));
        return List.of(
            Component.literal(SEPARATOR).withStyle(ChatFormatting.DARK_GRAY),
            labelled(Component.translatable(Lang.INFO_LABEL_TEAM), teamName(team)),
            labelled(Component.translatable(Lang.INFO_LABEL_LEADER), playerName(leaderName(team))),
            labelled(memberLabel, members(team)),
            labelled(
                Component.translatable(Lang.INFO_LABEL_SLOT),
                slotStatus(findPlot(storage, team.slotIndex()))
            )
        );
    }

    public static Component compact(PlotData storage, Team team) {
        return Component.empty()
            .append(teamName(team))
            .append(separator())
            .append(labelled(
                Component.translatable(Lang.INFO_LABEL_LEADER), playerName(leaderName(team))
            ))
            .append(separator())
            .append(labelled(
                Component.translatable(Lang.INFO_LABEL_MEMBERS),
                Component.literal(String.valueOf(team.size())).withStyle(ChatFormatting.WHITE)
            ))
            .append(separator())
            .append(labelled(
                Component.translatable(Lang.INFO_LABEL_SLOT),
                slotStatus(findPlot(storage, team.slotIndex()))
            ));
    }

    private static Component separator() {
        return Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static MutableComponent labelled(Component label, Component value) {
        return label.copy()
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
            .append(value);
    }

    private static String leaderName(Team team) {
        return team.findMember(team.leader()).map(TeamMember::name).orElse("?");
    }

    private static Component members(Team team) {
        MutableComponent result = Component.empty();
        boolean first = true;
        for (TeamMember member : team.members()) {
            if (!first) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
            }
            result.append(playerName(member.name()));
            first = false;
        }
        return result;
    }

    private static Component teamName(Team team) {
        return Component.literal(team.name()).withStyle(
            Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withBold(true)
                .withClickEvent(new ClickEvent(
                    ClickEvent.Action.SUGGEST_COMMAND, "/slot visit " + team.name()
                ))
                .withHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    Component.translatable(Lang.INFO_HOVER_VISIT, team.name())
                ))
        );
    }

    private static Component playerName(String name) {
        return Component.literal(name).withStyle(
            Style.EMPTY
                .withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent(
                    ClickEvent.Action.SUGGEST_COMMAND, "/msg " + name + " "
                ))
                .withHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    Component.translatable(Lang.INFO_HOVER_MESSAGE, name)
                ))
        );
    }

    /** Compact bar used by the action bar while a slot is being generated. */
    public static Component progressBar(int done, int total) {
        return progressBar(done, total, ChatFormatting.GREEN);
    }

    private static Component progressBar(int done, int total, ChatFormatting color) {
        int limit = Math.max(total, 1);
        int clamped = Math.min(Math.max(done, 0), limit);
        int filled = clamped * BAR_WIDTH / limit;
        return Component.literal("[")
            .append(Component.literal("#".repeat(filled)).withStyle(color))
            .append(Component.literal("-".repeat(BAR_WIDTH - filled))
                .withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("]"));
    }

    private static Component slotStatus(@Nullable PlotEntry plot) {
        if (plot == null) {
            return Component.translatable(Lang.SLOT_STATUS_NONE).withStyle(ChatFormatting.GRAY);
        }
        int total = PlotGeometry.CHUNKS_PER_PLOT;
        int done = Math.min(Math.max(plot.nextChunk(), 0), total);
        ChatFormatting color = switch (plot.status()) {
            case DONE -> ChatFormatting.GREEN;
            case GENERATING -> ChatFormatting.YELLOW;
            case DELETING -> ChatFormatting.RED;
        };
        return Component.translatable(Lang.SLOT_STATUS_INDEX, plot.index())
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal(" "))
            .append(progressBar(done, total, color))
            .append(Component.literal(" "))
            .append(Component.translatable(Lang.INFO_PROGRESS, done, total)
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" "))
            .append(Component.translatable(statusKey(plot.status())).withStyle(color));
    }

    private static String statusKey(PlotStatus status) {
        return switch (status) {
            case GENERATING -> Lang.INFO_STATUS_GENERATING;
            case DONE -> Lang.INFO_STATUS_DONE;
            case DELETING -> Lang.INFO_STATUS_DELETING;
        };
    }

    private static @Nullable PlotEntry findPlot(PlotData storage, @Nullable Integer index) {
        if (index == null) {
            return null;
        }
        return storage.plots().stream()
            .filter(plot -> plot.index() == index)
            .findFirst()
            .orElse(null);
    }
}
