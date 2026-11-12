package gorefest.tools

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Flow metrics and Monte Carlo forecasts from the milestone tables in docs/.
 *
 * Reads every docs/milestone-*.md table with Arrived, Started and Done columns (see docs/backlog.md, "Flow metrics").
 * Days are calendar days. Cycle time counts a story started and done on the same day as 1 day.
 */
object Flow {
    private const val HELP = """Flow metrics and Monte Carlo forecasts from the milestone tables in docs/.

    ./gradlew -q flow                                       cycle time, lead time, throughput, work in progress
    ./gradlew -q flow --args="--when 8"                     when will 8 more stories be done? (default: everything not done)
    ./gradlew -q flow --args="--how-many-by 2026-12-01"     how many stories will be done by then?
    ./gradlew -q flow --args="--since 2026-10-01"           sample throughput from this day on (default: the last 28 days)

Other options: --today YYYY-MM-DD, --runs N (default 10000), --seed N (for repeatable forecasts).
Days are calendar days. Cycle time counts a story started and done on the same day as 1 day."""

    private val OPEN = setOf("📋", "⏭", "🚧")
    private const val DONE = "✅"
    private const val DOING = "🚧"
    private const val SAMPLE_DAYS = 28L
    private const val GIVE_UP_AFTER_DAYS = 3650
    private const val DEFAULT_RUNS = 10_000
    private val CERTAINTIES = listOf(50, 85, 95)

    data class Options(
        val today: LocalDate,
        val since: LocalDate? = null,
        /** null: no forecast; 0: everything not done yet. */
        val `when`: Int? = null,
        val howManyBy: LocalDate? = null,
        val runs: Int = DEFAULT_RUNS,
        val seed: Int? = null,
    )

    fun run(args: List<String>, clock: Clock) {
        val options = parse(args, LocalDate.now(clock))
        val random = options.seed?.let { Random(it) } ?: Random.Default
        val stories = Milestones.stories(root.resolve("docs"))
        report(stories, options.today)
        if (options.`when` == null && options.howManyBy == null) return

        val since = options.since ?: options.today.minusDays(SAMPLE_DAYS - 1)
        val samples = dailyThroughput(stories.delivered(), since, options.today)
        println("\nThroughput sample: ${samples.sum()} stories over ${samples.size} days from $since")
        if (samples.none { it > 0 }) {
            println("Nothing was done in that window; widen it with --since.")
            return
        }
        options.`when`?.let { asked ->
            val count = if (asked > 0) asked else stories.count { it.status in OPEN }
            if (count == 0) println("\nNothing left to forecast: pass --when N.")
            else forecastWhen(samples, count, options.today, options.runs, random)
        }
        options.howManyBy?.let { forecastHowMany(samples, it, options.today, options.runs, random) }
    }

    fun parse(args: List<String>, today: LocalDate): Options {
        var options = Options(today)
        var i = 0
        fun value(): String = args.getOrNull(++i) ?: usage("${args[i - 1]} needs a value")
        while (i < args.size) {
            when (val arg = args[i]) {
                "-h", "--help" -> { println(HELP); exitProcess(0) }
                "--today" -> options = options.copy(today = LocalDate.parse(value()))
                "--since" -> options = options.copy(since = LocalDate.parse(value()))
                "--how-many-by" -> options = options.copy(howManyBy = LocalDate.parse(value()))
                "--runs" -> options = options.copy(runs = value().toInt())
                "--seed" -> options = options.copy(seed = value().toInt())
                "--when" -> {
                    val count = args.getOrNull(i + 1)?.toIntOrNull()
                    if (count != null) i++
                    options = options.copy(`when` = count ?: 0)
                }
                else -> usage("unknown argument $arg")
            }
            i++
        }
        return options
    }

    private fun usage(problem: String): Nothing {
        System.err.println("flow: $problem\n\n$HELP")
        exitProcess(2)
    }

    private fun List<Story>.delivered() = filter { it.status == DONE && it.done != null }

    fun days(first: LocalDate, last: LocalDate): Int = ChronoUnit.DAYS.between(first, last).toInt() + 1

    fun percentile(values: List<Int>, p: Int): Int {
        val ordered = values.sorted()
        return ordered[minOf(ordered.size - 1, (p / 100.0 * ordered.size).toInt())]
    }

    private fun spread(label: String, values: List<Int>) {
        if (values.isEmpty()) {
            println("$label: no data")
            return
        }
        println(
            "$label (n=${values.size}): p50 ${percentile(values, 50)}d · p85 ${percentile(values, 85)}d · " +
                "p95 ${percentile(values, 95)}d · max ${values.max()}d",
        )
    }

    fun dailyThroughput(delivered: List<Story>, since: LocalDate, today: LocalDate): List<Int> {
        val perDay = delivered.groupingBy { it.done }.eachCount()
        return (0 until days(since, today)).map { perDay[since.plusDays(it.toLong())] ?: 0 }
    }

    private fun report(stories: List<Story>, today: LocalDate) {
        val delivered = stories.delivered()
        spread("Cycle time (started → done)", delivered.mapNotNull { s -> s.started?.let { days(it, s.done!!) } })
        spread("Lead time (arrived → done)", delivered.mapNotNull { s -> s.arrived?.let { days(it, s.done!!) } })

        println("\nThroughput per week (✅ only):")
        val weeks = delivered.groupingBy { it.done!!.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.eachCount()
        var monday = weeks.keys.minOrNull()
        while (monday != null && monday <= today) {
            val count = weeks[monday] ?: 0
            println("  week of $monday: ${"#".repeat(count)} $count")
            monday = monday.plusDays(7)
        }

        val doing = stories.filter { it.status == DOING }
        println("\nWork in progress: ${doing.size}")
        for (s in doing) {
            val age = s.started?.let { "${days(it, today)}d old" } ?: "no Started date"
            println("  ${s.id} (${s.milestone}): $age")
        }
        println("Not done yet (📋 ⏭ 🚧): ${stories.count { it.status in OPEN }}")
    }

    private fun forecastWhen(samples: List<Int>, count: Int, today: LocalDate, runs: Int, random: Random) {
        val results = List(runs) {
            var done = 0
            var day = 0
            while (done < count && day <= GIVE_UP_AFTER_DAYS) {
                day++
                done += samples.random(random)
            }
            day.takeIf { done >= count }
        }
        if (null in results) {
            println("Throughput sample is too thin to forecast; widen it with --since.")
            return
        }
        println("\nWhen will $count more stories be done? ($runs runs)")
        for (p in CERTAINTIES) println("  $p% sure by ${today.plusDays(percentile(results.filterNotNull(), p).toLong())}")
    }

    private fun forecastHowMany(samples: List<Int>, until: LocalDate, today: LocalDate, runs: Int, random: Random) {
        val horizon = ChronoUnit.DAYS.between(today, until).toInt()
        val results = List(runs) { (0 until horizon).sumOf { samples.random(random) } }
        println("\nHow many stories done from tomorrow to $until? ($runs runs)")
        for (p in CERTAINTIES) println("  $p% sure of at least ${percentile(results, 100 - p)}")
    }
}
