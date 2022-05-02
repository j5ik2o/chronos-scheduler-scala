package com.github.j5ik2o.chronos.core

import com.github.j5ik2o.cron.CronSchedule
import org.scalatest.funsuite.AnyFunSuite

import java.time.ZoneId
import java.util.UUID
import scala.concurrent.duration._

class JobSchedulerSpec extends AnyFunSuite {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt

  test("job") {
    val zoneId  = ZoneId.systemDefault()
    var counter = 0
    val job = Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      tickInterval = 500.millis,
      run = { () =>
        println(s"run job: $counter")
        counter += 1
      }
    )
    val jobScheduler = JobScheduler(UUID.randomUUID()).addJob(job)

    jobScheduler.tick()
    Thread.sleep(job.tickInterval.toMillis * testTimeFactor)
    jobScheduler.tick()
    Thread.sleep(job.tickInterval.toMillis * testTimeFactor)
    jobScheduler.tick()
    Thread.sleep(job.tickInterval.toMillis * testTimeFactor)

    assert(counter == 2)
  }
}
