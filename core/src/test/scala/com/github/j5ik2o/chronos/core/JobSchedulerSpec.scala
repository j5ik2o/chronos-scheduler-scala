package com.github.j5ik2o.chronos.core

import org.scalatest.funsuite.AnyFunSuite

import java.time.{ Instant, ZoneId }
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._

class JobSchedulerSpec extends AnyFunSuite {

  test("job fires when cron occurrence passes") {
    val zoneId   = ZoneId.of("UTC")
    val mockTime = new AtomicReference[Instant](Instant.parse("2024-01-01T12:00:00Z"))
    var counter  = 0
    val job      = Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      tickInterval = 500.millis,
      run = { () => counter += 1 }
    )
    job.clock = () => mockTime.get()
    val jobScheduler = JobScheduler(UUID.randomUUID()).addJob(job)

    // Tick 1: initialize lastTick
    jobScheduler.tick()
    assert(counter == 0)

    // Tick 2: 600ms later, tickInterval elapsed but no cron boundary crossed
    mockTime.set(Instant.parse("2024-01-01T12:00:00.600Z"))
    jobScheduler.tick()
    assert(counter == 0)

    // Tick 3: past 12:01:00, cron boundary crossed
    mockTime.set(Instant.parse("2024-01-01T12:01:00.100Z"))
    jobScheduler.tick()
    assert(counter == 1)

    // Tick 4: 600ms after last evaluation, still before 12:02:00
    mockTime.set(Instant.parse("2024-01-01T12:01:00.700Z"))
    jobScheduler.tick()
    assert(counter == 1)

    // Tick 5: past 12:02:00
    mockTime.set(Instant.parse("2024-01-01T12:02:00.100Z"))
    jobScheduler.tick()
    assert(counter == 2)
  }

  test("job does not fire when limitMissedRuns exceeded") {
    val zoneId   = ZoneId.of("UTC")
    val mockTime = new AtomicReference[Instant](Instant.parse("2024-01-01T12:00:00Z"))
    var counter  = 0
    val job      = Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      limitMissedRuns = 3,
      tickInterval = 500.millis,
      run = { () => counter += 1 }
    )
    job.clock = () => mockTime.get()
    val jobScheduler = JobScheduler(UUID.randomUUID()).addJob(job)

    // Tick 1: initialize
    jobScheduler.tick()

    // Tick 2: jump 10 minutes ahead (missed more than 3 runs)
    mockTime.set(Instant.parse("2024-01-01T12:10:00.100Z"))
    jobScheduler.tick()
    assert(counter == 0)
  }

  test("job fires when missed runs within limit") {
    val zoneId   = ZoneId.of("UTC")
    val mockTime = new AtomicReference[Instant](Instant.parse("2024-01-01T12:00:00Z"))
    var counter  = 0
    val job      = Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      limitMissedRuns = 5,
      tickInterval = 500.millis,
      run = { () => counter += 1 }
    )
    job.clock = () => mockTime.get()
    val jobScheduler = JobScheduler(UUID.randomUUID()).addJob(job)

    // Tick 1: initialize
    jobScheduler.tick()

    // Tick 2: jump 3 minutes ahead (missed 3 runs, within limit of 5)
    mockTime.set(Instant.parse("2024-01-01T12:03:00.100Z"))
    jobScheduler.tick()
    assert(counter == 1)
  }
}
