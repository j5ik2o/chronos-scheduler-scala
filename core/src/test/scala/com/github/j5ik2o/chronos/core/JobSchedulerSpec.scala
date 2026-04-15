package com.github.j5ik2o.chronos.core

import org.scalatest.funsuite.AnyFunSuite

import java.time.{ Instant, ZoneId }
import java.util.UUID
import scala.concurrent.duration._

class JobSchedulerSpec extends AnyFunSuite {

  private def tickAt(job: Job, time: String): Unit =
    Job.JobTicker.tickWithClock(job, Instant.parse(time))

  test("job fires when cron occurrence passes") {
    val zoneId  = ZoneId.of("UTC")
    var counter = 0
    val job     = Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      tickInterval = 500.millis,
      run = { () => counter += 1 }
    )

    // Tick 1: initialize lastTick
    tickAt(job, "2024-01-01T12:00:00Z")
    assert(counter == 0)

    // Tick 2: 600ms later, tickInterval elapsed but no cron period passed
    tickAt(job, "2024-01-01T12:00:00.600Z")
    assert(counter == 0)

    // Tick 3: past cron boundary
    tickAt(job, "2024-01-01T12:01:00.100Z")
    assert(counter == 1)

    // Tick 4: 600ms after last fire, still before next cron period
    tickAt(job, "2024-01-01T12:01:00.700Z")
    assert(counter == 1)

    // Tick 5: past next cron boundary
    tickAt(job, "2024-01-01T12:02:00.200Z")
    assert(counter == 2)
  }

  test("job does not fire when limitMissedRuns exceeded") {
    val zoneId  = ZoneId.of("UTC")
    var counter = 0
    val job     = Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      limitMissedRuns = 3,
      tickInterval = 500.millis,
      run = { () => counter += 1 }
    )

    // Tick 1: initialize
    tickAt(job, "2024-01-01T12:00:00Z")

    // Tick 2: jump 10 minutes ahead (missed more than 3 runs)
    tickAt(job, "2024-01-01T12:10:00.100Z")
    assert(counter == 0)
  }

  test("job fires when missed runs within limit") {
    val zoneId  = ZoneId.of("UTC")
    var counter = 0
    val job     = Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      limitMissedRuns = 5,
      tickInterval = 500.millis,
      run = { () => counter += 1 }
    )

    // Tick 1: initialize
    tickAt(job, "2024-01-01T12:00:00Z")

    // Tick 2: jump 3 minutes ahead (missed 3 runs, within limit of 5)
    tickAt(job, "2024-01-01T12:03:00.100Z")
    assert(counter == 1)
  }
}
