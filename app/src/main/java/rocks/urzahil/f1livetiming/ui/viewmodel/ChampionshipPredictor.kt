package rocks.urzahil.f1livetiming.ui.viewmodel

import rocks.urzahil.f1livetiming.data.model.SeasonRaceItem
import rocks.urzahil.f1livetiming.data.model.scheduleUtcMillis

/** Grands Prix and sprints of the season whose points are not yet in the standings. */
data class RemainingSessions(val races: Int, val sprints: Int) {
    val driverPoints: Int get() = races * ChampionshipPredictor.DRIVER_RACE_MAX + sprints * ChampionshipPredictor.DRIVER_SPRINT_MAX
    val constructorPoints: Int get() = races * ChampionshipPredictor.CONSTRUCTOR_RACE_MAX + sprints * ChampionshipPredictor.CONSTRUCTOR_SPRINT_MAX
    val isSeasonOver: Boolean get() = races == 0 && sprints == 0
}

/** A driver or constructor row from the standings, reduced to what the predictor needs. */
data class StandingEntry(val id: String, val label: String, val colorKey: String?, val points: Float)

/** One driver or constructor in the predictor: points now and the most they can still finish on. */
data class TitleContender(
    val id: String,
    val label: String,
    val colorKey: String?,
    val points: Float,
    val maxPoints: Float,
    val inContention: Boolean
)

/**
 * Works out who can still mathematically win the championship.
 *
 * A contender stays in the fight while their maximum possible total (current points plus every
 * remaining point at the maximum per session) reaches at least the leader's current points.
 * A tie on points is decided by countback, so an exact tie still counts as in contention.
 */
object ChampionshipPredictor {
    // Fastest-lap bonus point was dropped from 2025, so a win is the maximum per session.
    const val DRIVER_RACE_MAX = 25
    const val DRIVER_SPRINT_MAX = 8

    // Constructors can score a one-two: 25 + 18 in a Grand Prix, 8 + 7 in a sprint.
    const val CONSTRUCTOR_RACE_MAX = 43
    const val CONSTRUCTOR_SPRINT_MAX = 15

    // Race (up to the 3 hour red-flag limit) plus time for the results API to publish the points.
    const val RACE_SCORING_WINDOW_MILLIS = 6L * 60 * 60 * 1000

    /**
     * Counts the sessions still to be added to the standings.
     *
     * Rounds after [standingsRound] count in full. The standings round itself only has a Grand Prix still
     * to score on a sprint weekend, where the standings advance to that round after Saturday's sprint. That
     * Grand Prix keeps counting until [RACE_SCORING_WINDOW_MILLIS] after its scheduled start, so Sunday's
     * points do not vanish when the lights go out, before the standings have been updated. On any other
     * weekend the standings only reach the round once its race has been scored.
     */
    fun remainingSessions(calendar: List<SeasonRaceItem>, standingsRound: Int?, nowMillis: Long): RemainingSessions {
        var races = 0
        var sprints = 0
        for (race in calendar) {
            val round = race.round?.toIntOrNull() ?: continue
            when {
                standingsRound == null || round > standingsRound -> {
                    races++
                    if (race.sprint != null) sprints++
                }
                round == standingsRound -> {
                    if (race.sprint != null && !isRaceScored(race.date, race.time, nowMillis)) races++
                }
            }
        }
        return RemainingSessions(races, sprints)
    }

    /** Builds contenders sorted by points, flagging who can still reach the leader. */
    fun contenders(entries: List<StandingEntry>, pointsAvailable: Int): List<TitleContender> {
        val leaderPoints = entries.maxOfOrNull { it.points } ?: 0f
        return entries
            .map { entry ->
                val maxPoints = entry.points + pointsAvailable
                TitleContender(
                    id = entry.id,
                    label = entry.label,
                    colorKey = entry.colorKey,
                    points = entry.points,
                    maxPoints = maxPoints,
                    inContention = maxPoints >= leaderPoints
                )
            }
            .sortedByDescending { it.points }
    }

    /** The leader once no one else can reach their points; null while the title is still open. */
    fun clinchedChampion(contenders: List<TitleContender>): TitleContender? {
        val leader = contenders.firstOrNull() ?: return null
        return leader.takeIf { contenders.count { it.inContention } == 1 }
    }

    /** Whether the Grand Prix is old enough (even after a long red flag) to be in the standings. */
    private fun isRaceScored(dateStr: String?, timeStr: String?, nowMillis: Long): Boolean =
        scheduleUtcMillis(dateStr, timeStr)?.let { it + RACE_SCORING_WINDOW_MILLIS <= nowMillis } ?: false
}
