package com.github.j5ik2o.chronos.example

import com.github.j5ik2o.chronos.core.{ Job, JobScheduler }

import java.time.ZoneId
import java.util.UUID

object CoreMain extends App {
  val zoneId: ZoneId = ZoneId.systemDefault()
  var counter        = 0

  val jobScheduler: JobScheduler = JobScheduler(UUID.randomUUID()).addJob(
    Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      run = { () =>
        println(s"run job: $counter")
        counter += 1
      }
    )
  )

  while (true) {
    jobScheduler.tick()
    Thread.sleep(1000)
  }
}
