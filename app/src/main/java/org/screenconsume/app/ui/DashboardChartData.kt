package org.screenconsume.app.ui

import org.screenconsume.app.domain.model.DailyAppUsage

internal data class ChartSegment(val name: String, val seconds: Long, val packageName: String? = null)
internal data class UsageBucket(val label: String, val segments: List<ChartSegment>, val description: String = label) {
    val total: Long = segments.sumOf { it.seconds }
}

internal fun rankedSegments(values: List<Pair<DailyAppUsage, Long>>, otherLabel: String, highlighted: String?): List<ChartSegment> {
    val ranked = values.groupBy { it.first.packageName }.map { (packageName, rows) ->
        ChartSegment(rows.first().first.displayName, rows.sumOf { it.second }, packageName)
    }.filter { it.seconds > 0 }.sortedByDescending { it.seconds }
    val highlightedSegment = ranked.firstOrNull { it.packageName == highlighted }
    val top = if (highlightedSegment == null) ranked.take(3)
        else listOf(highlightedSegment) + ranked.filter { it.packageName != highlighted }.take(2)
    val other = ranked.filter { candidate -> top.none { it.packageName == candidate.packageName } }.sumOf { it.seconds }
    return top + listOfNotNull(ChartSegment(otherLabel, other).takeIf { other > 0 })
}

