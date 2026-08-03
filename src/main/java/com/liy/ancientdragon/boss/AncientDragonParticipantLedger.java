package com.liy.ancientdragon.boss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistent lifetime contribution plus short-lived combat threat. */
public final class AncientDragonParticipantLedger {
    private static final double HEAD_THREAT_MULTIPLIER = 1.35D;
    private static final double CHANNEL_INTERRUPT_THREAT = 30.0D;
    private static final double THREAT_HALF_LIFE_TICKS = 100.0D;

    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private UUID lockedTarget;
    private long targetLockUntilTick = Long.MIN_VALUE;
    private int maxParticipantsSeen = 1;
    private int emptyTicks;

    public void tick(Collection<UUID> activePlayers, long tick) {
        Set<UUID> active = Set.copyOf(activePlayers);
        if (active.isEmpty()) {
            emptyTicks++;
        } else {
            emptyTicks = 0;
            maxParticipantsSeen = Math.max(
                    maxParticipantsSeen,
                    AncientDragonEncounterRules.normalizedParticipants(active.size()));
        }
        for (UUID playerId : active) {
            Participant current = participant(playerId);
            participants.put(playerId, current.withActiveTicks(current.activeTicks() + 1));
        }
        if (lockedTarget != null && (!active.contains(lockedTarget) || tick >= targetLockUntilTick)) {
            lockedTarget = null;
            targetLockUntilTick = Long.MIN_VALUE;
        }
    }

    public void recordAcceptedDamage(UUID playerId, double acceptedDamage, long tick, boolean headHit) {
        if (playerId == null || !(acceptedDamage > 0.0D)) {
            return;
        }
        Participant current = participant(playerId);
        double recent = decayedThreat(current, tick)
                + acceptedDamage * (headHit ? HEAD_THREAT_MULTIPLIER : 1.0D);
        participants.put(playerId, new Participant(
                current.lifetimeDamage() + acceptedDamage,
                current.activeTicks(),
                recent,
                tick,
                tick));
    }

    public void recordChannelInterrupt(UUID playerId, long tick) {
        if (playerId == null) {
            return;
        }
        Participant current = participant(playerId);
        participants.put(playerId, new Participant(
                current.lifetimeDamage(),
                current.activeTicks(),
                decayedThreat(current, tick) + CHANNEL_INTERRUPT_THREAT,
                tick,
                current.reachedAtTick()));
    }

    public Optional<UUID> selectTarget(Collection<UUID> activePlayers, long tick) {
        Set<UUID> active = Set.copyOf(activePlayers);
        if (lockedTarget != null && active.contains(lockedTarget) && tick < targetLockUntilTick) {
            return Optional.of(lockedTarget);
        }
        Optional<UUID> selected = active.stream().min(Comparator
                .<UUID>comparingDouble(playerId -> decayedThreat(participant(playerId), tick))
                .reversed()
                .thenComparing(Comparator
                        .<UUID>comparingDouble(playerId -> participant(playerId).lifetimeDamage())
                        .reversed())
                .thenComparingLong(playerId -> participant(playerId).reachedAtTick())
                .thenComparing(UUID::compareTo));
        selected.ifPresent(playerId -> {
            lockedTarget = playerId;
            targetLockUntilTick = tick + AncientDragonEncounterRules.TARGET_LOCK_TICKS;
        });
        return selected;
    }

    public double lifetimeDamage(UUID playerId) {
        return participant(playerId).lifetimeDamage();
    }

    public double recentThreat(UUID playerId, long tick) {
        return decayedThreat(participant(playerId), tick);
    }

    public boolean eligibleForReward(UUID playerId, double encounterMaximumHealth) {
        Participant participant = participant(playerId);
        return participant.activeTicks() >= AncientDragonEncounterRules.PARTICIPATION_TIME_TICKS
                || participant.lifetimeDamage()
                        >= encounterMaximumHealth * AncientDragonEncounterRules.PARTICIPATION_DAMAGE_FRACTION;
    }

    public List<UUID> eligiblePlayers(double encounterMaximumHealth) {
        return participants.keySet().stream()
                .filter(playerId -> eligibleForReward(playerId, encounterMaximumHealth))
                .sorted()
                .toList();
    }

    public int maxParticipantsSeen() {
        return maxParticipantsSeen;
    }

    public int emptyTicks() {
        return emptyTicks;
    }

    public Optional<UUID> lockedTarget() {
        return Optional.ofNullable(lockedTarget);
    }

    public long targetLockUntilTick() {
        return targetLockUntilTick;
    }

    public List<SavedParticipant> savedParticipants() {
        List<SavedParticipant> saved = new ArrayList<>(participants.size());
        participants.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Participant value = entry.getValue();
            saved.add(new SavedParticipant(
                    entry.getKey(),
                    value.lifetimeDamage(),
                    value.activeTicks(),
                    value.recentThreat(),
                    value.recentThreatAtTick(),
                    value.reachedAtTick()));
        });
        return List.copyOf(saved);
    }

    public void restore(
            Collection<SavedParticipant> saved,
            int restoredMaxParticipantsSeen,
            int restoredEmptyTicks,
            UUID restoredLockedTarget,
            long restoredTargetLockUntilTick) {
        participants.clear();
        for (SavedParticipant value : saved) {
            participants.put(value.playerId(), new Participant(
                    Math.max(0.0D, value.lifetimeDamage()),
                    Math.max(0, value.activeTicks()),
                    Math.max(0.0D, value.recentThreat()),
                    value.recentThreatAtTick(),
                    value.reachedAtTick()));
        }
        maxParticipantsSeen = AncientDragonEncounterRules.normalizedParticipants(restoredMaxParticipantsSeen);
        emptyTicks = Math.clamp(restoredEmptyTicks, 0, AncientDragonEncounterRules.RETURN_TO_ROOST_DELAY_TICKS);
        lockedTarget = restoredLockedTarget;
        targetLockUntilTick = restoredTargetLockUntilTick;
    }

    public void clearForDormancy() {
        participants.clear();
        lockedTarget = null;
        targetLockUntilTick = Long.MIN_VALUE;
        maxParticipantsSeen = 1;
        emptyTicks = 0;
    }

    private Participant participant(UUID playerId) {
        return participants.getOrDefault(playerId, Participant.ZERO);
    }

    private static double decayedThreat(Participant participant, long tick) {
        if (!(participant.recentThreat() > 0.0D)) {
            return 0.0D;
        }
        long age = Math.max(0L, tick - participant.recentThreatAtTick());
        if (age >= AncientDragonEncounterRules.RECENT_THREAT_WINDOW_TICKS) {
            return 0.0D;
        }
        return participant.recentThreat() * Math.pow(0.5D, age / THREAT_HALF_LIFE_TICKS);
    }

    public record SavedParticipant(
            UUID playerId,
            double lifetimeDamage,
            int activeTicks,
            double recentThreat,
            long recentThreatAtTick,
            long reachedAtTick) {
    }

    private record Participant(
            double lifetimeDamage,
            int activeTicks,
            double recentThreat,
            long recentThreatAtTick,
            long reachedAtTick) {
        private static final Participant ZERO =
                new Participant(0.0D, 0, 0.0D, Long.MIN_VALUE, Long.MAX_VALUE);

        Participant withActiveTicks(int value) {
            return new Participant(lifetimeDamage, value, recentThreat, recentThreatAtTick, reachedAtTick);
        }
    }
}
