package com.github.j5ik2o.chronos.akka

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.github.j5ik2o.chronos.core.Job
import com.github.j5ik2o.cron.CronSchedule
import org.scalatest.funsuite.AnyFunSuite

import java.time.ZoneId
import java.util.UUID
import scala.concurrent.duration._

class JobSchedulerActorSpec extends AnyFunSuite {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt

  val testKit: ActorTestKit = ActorTestKit()

  test("job") {
    val zoneId               = ZoneId.systemDefault()
    var counter              = 0
    val id                   = UUID.randomUUID()
    val jobSchedulerActorRef = testKit.spawn(JobSchedulerActor(id))

    val reply = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
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
    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(
      id,
      job,
      reply.ref
    )

    reply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

    jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
    Thread.sleep(job.tickInterval.toMillis * testTimeFactor)
    jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
    Thread.sleep(job.tickInterval.toMillis * testTimeFactor)
    jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
    Thread.sleep(job.tickInterval.toMillis * testTimeFactor)

    assert(counter == 2)
  }
}
