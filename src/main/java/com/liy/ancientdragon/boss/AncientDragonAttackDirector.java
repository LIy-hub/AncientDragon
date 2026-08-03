package com.liy.ancientdragon.boss;

import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Attack;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.AttackDomain;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Phase;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Deterministic scored attack selection with a two-action anti-repeat history. */
public final class AncientDragonAttackDirector {
    private static final int HISTORY_SIZE = 2;
    private final ArrayDeque<Attack> history = new ArrayDeque<>(HISTORY_SIZE);

    public Decision choose(Context context) {
        List<Attack> candidates = candidates(context);
        EnumMap<Attack, Double> scores = new EnumMap<>(Attack.class);
        for (Attack attack : candidates) {
            scores.put(attack, score(attack, context) + repeatPenalty(attack));
        }
        Attack selected = candidates.stream()
                .max(Comparator.<Attack>comparingDouble(scores::get)
                        .thenComparingInt(attack -> -attack.ordinal()))
                .orElseThrow();
        remember(selected);
        return new Decision(selected, Map.copyOf(scores));
    }

    public List<Attack> history() {
        return List.copyOf(history);
    }

    public void restoreHistory(List<Attack> restored) {
        history.clear();
        restored.stream().limit(HISTORY_SIZE).forEach(history::addLast);
    }

    private static List<Attack> candidates(Context context) {
        if (context.domain() == AttackDomain.GROUND) {
            List<Attack> ground = new ArrayList<>();
            // Physical attacks are only valid when at least one participant can be reached.
            if (context.closePlayers() > 0) {
                ground.add(Attack.BITE_HEAVY);
                ground.add(Attack.WING_SLAM);
            }
            ground.add(Attack.RIFT_CLAW);
            if (context.phase() == Phase.STORM) {
                ground.add(Attack.GROUND_STORM_BURST);
            } else if (context.phase() == Phase.SOLAR) {
                ground.add(Attack.GROUND_SOLAR_BREATH);
            }
            return List.copyOf(ground);
        }
        return switch (context.phase()) {
            case MOUNTAIN -> List.of(Attack.DIVE_STRIKE, Attack.TAIL_SWEEP, Attack.WIND_BLADE);
            case STORM -> List.of(
                    Attack.STORM_BURST,
                    Attack.LIGHTNING_CHAIN,
                    Attack.STORM_CAGE,
                    Attack.DIVE_STRIKE,
                    Attack.TAIL_SWEEP,
                    Attack.WIND_BLADE);
            case SOLAR -> List.of(
                    Attack.SOLAR_BREATH,
                    Attack.STORM_CAGE,
                    Attack.WIND_BLADE,
                    Attack.STORM_BURST,
                    Attack.DIVE_STRIKE);
        };
    }

    private static double score(Attack attack, Context context) {
        return switch (attack) {
            case DIVE_STRIKE -> 48.0D + context.exposedPlayers() * 8.0D
                    + context.meanTargetDistance() * 0.08D - context.closePlayers() * 3.0D;
            case TAIL_SWEEP -> 42.0D + context.rearPlayers() * 12.0D + context.closePlayers() * 7.0D;
            case STORM_BURST -> 44.0D + context.clusteredPlayers() * 13.0D
                    + context.exposedPlayers() * 2.0D;
            case SOLAR_BREATH -> 46.0D + context.exposedPlayers() * 7.0D
                    + context.meanTargetDistance() * 0.06D;
            case LIGHTNING_CHAIN -> 43.0D + context.exposedPlayers() * 9.0D
                    + context.meanTargetDistance() * 0.07D;
            case WIND_BLADE -> 44.0D + context.exposedPlayers() * 3.0D
                    + context.meanTargetDistance() * 0.15D - context.closePlayers() * 4.0D;
            case STORM_CAGE -> 45.0D + context.clusteredPlayers() * 11.0D
                    + context.closePlayers() * 5.0D;
            case BITE_HEAVY -> 52.0D + context.closePlayers() * 10.0D;
            case WING_SLAM -> 48.0D + context.clusteredPlayers() * 9.0D
                    + context.rearPlayers() * 4.0D;
            case RIFT_CLAW -> 48.0D + context.exposedPlayers() * 4.0D
                    + context.meanTargetDistance() * 0.12D - context.closePlayers() * 2.0D;
            case GROUND_STORM_BURST -> 46.0D + context.clusteredPlayers() * 12.0D
                    + context.exposedPlayers() * 3.0D;
            case GROUND_SOLAR_BREATH -> 50.0D + context.clusteredPlayers() * 8.0D
                    + context.exposedPlayers() * 5.0D;
        };
    }

    private double repeatPenalty(Attack attack) {
        Attack[] previous = history.toArray(Attack[]::new);
        double penalty = 0.0D;
        if (previous.length >= 1 && previous[previous.length - 1] == attack) {
            penalty -= 100.0D;
        }
        if (previous.length >= 2 && previous[previous.length - 2] == attack) {
            penalty -= 25.0D;
        }
        return penalty;
    }

    private void remember(Attack selected) {
        if (history.size() == HISTORY_SIZE) {
            history.removeFirst();
        }
        history.addLast(selected);
    }

    public record Context(
            Phase phase,
            AttackDomain domain,
            int clusteredPlayers,
            int closePlayers,
            int rearPlayers,
            int exposedPlayers,
            double meanTargetDistance) {
        public Context {
            if (phase == null || domain == null || clusteredPlayers < 0 || closePlayers < 0
                    || rearPlayers < 0 || exposedPlayers < 0
                    || !Double.isFinite(meanTargetDistance) || meanTargetDistance < 0.0D) {
                throw new IllegalArgumentException("Invalid attack-director context");
            }
        }
    }

    public record Decision(Attack attack, Map<Attack, Double> scores) {
    }
}
