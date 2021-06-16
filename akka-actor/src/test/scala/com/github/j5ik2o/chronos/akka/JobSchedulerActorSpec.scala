package com.github.j5ik2o.chronos.akka

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.github.j5ik2o.chronos.core.Job
import com.github.j5ik2o.cron.CronSchedule
import org.scalatest.funsuite.AnyFunSuite

import java.time.ZoneId
import java.util.UUID

class JobSchedulerActorSpec extends AnyFunSuite {
  val testKit: ActorTestKit = ActorTestKit()

  test("job") {
    val zoneId               = ZoneId.systemDefault()
    var counter              = 0
    val id                   = UUID.randomUUID()
    val jobSchedulerActorRef = testKit.spawn(JobSchedulerActor(id))

    val reply = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(
      id,
      Job(
        id = UUID.randomUUID(),
        schedule = CronSchedule("*/1 * * * *", zoneId),
        run = { () =>
          println(s"run job: $counter")
          counter += 1
        }
      ),
      reply.ref
    )

    reply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

    jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
    Thread.sleep(1000)
    jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
    Thread.sleep(1000)
    jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
    Thread.sleep(1000)

    assert(counter == 2)
  }
}
