package com.github.j5ik2o.chronos.core

import com.github.j5ik2o.cron.CronSchedule
import org.scalatest.funsuite.AnyFunSuite

import java.time.ZoneId
import java.util.UUID
import scala.concurrent.duration._

class JobSchedulerSpec extends AnyFunSuite {
  test("job") {
    val zoneId  = ZoneId.systemDefault()
    var counter = 0
    val job = Job(
      id = UUID.randomUUID(),
      schedule = CronSchedule("*/1 * * * *", zoneId),
      tickInterval = 1.seconds,
      run = { () =>
        println(s"run job: $counter")
        counter += 1
      }
    )
    val jobScheduler = JobScheduler(UUID.randomUUID()).addJob(job)

    jobScheduler.tick()
    Thread.sleep(job.tickInterval.toMillis)
    jobScheduler.tick()
    Thread.sleep(job.tickInterval.toMillis)
    jobScheduler.tick()

    assert(counter == 2)
  }
}
