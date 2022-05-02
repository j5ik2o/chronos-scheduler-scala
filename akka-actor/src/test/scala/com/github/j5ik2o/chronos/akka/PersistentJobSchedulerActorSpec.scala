package com.github.j5ik2o.chronos.akka

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.github.j5ik2o.chronos.core.Job
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.freespec.AnyFreeSpecLike

import java.time.ZoneId
import java.util.UUID
import scala.concurrent.duration.DurationInt

object PersistentJobSchedulerActorSpec {
  val config: Config = ConfigFactory.parseString(s"""
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    akka.persistence.journal.inmem.test-serialization = on
    akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
    akka.persistence.snapshot-store.local.dir = "target/${getClass.getName}-${UUID.randomUUID().toString}"
    akka.actor.serialization-bindings {
      "${classOf[CborSerializable].getName}" = jackson-cbor
    }
    """)
}

class PersistentJobSchedulerActorSpec
    extends ScalaTestWithActorTestKit(PersistentJobSchedulerActorSpec.config)
    with AnyFreeSpecLike {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt

  "PersistentJobSchedulerActor" - {
    "job" in {
      val zoneId  = ZoneId.systemDefault()
      var counter = 0
      val id      = UUID.randomUUID()

      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id))
      val reply                = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()

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
}
