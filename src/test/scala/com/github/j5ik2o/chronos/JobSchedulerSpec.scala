package com.github.j5ik2o.chronos

import com.github.j5ik2o.cron.CronSchedule
import org.scalatest.funsuite.AnyFunSuite

import java.time.ZoneId
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class JobSchedulerSpec extends AnyFunSuite {
  test("job") {
    val zoneId  = ZoneId.systemDefault()
    var counter = 0

    val jobScheduler = JobScheduler(UUID.randomUUID()).addJob(
      Job(
        id = UUID.randomUUID(),
        schedule = CronSchedule("*/1 * * * *", zoneId),
        run = { () =>
          println(s"run job: $counter")
          counter += 1
        }
      )
    )

    jobScheduler.tick()
    Thread.sleep(1000)
    jobScheduler.tick()
    Thread.sleep(1000)
    jobScheduler.tick()
    Thread.sleep(1000)

    assert(counter == 2)
  }
}
