package com.github.j5ik2o.chronos.akka

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.github.j5ik2o.chronos.core.Job
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.freespec.AnyFreeSpecLike

import java.time.{ Instant, ZoneId }
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
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

  "PersistentJobSchedulerActor" - {
    "job fires only on cron boundary" in {
      val zoneId               = ZoneId.of("UTC")
      val mockTime             = new AtomicReference[Instant](Instant.parse("2024-01-01T12:00:00Z"))
      val clock: () => Instant = () => mockTime.get()
      var counter              = 0
      val id                   = UUID.randomUUID()

      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id, clock = clock))
      val reply                = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()

      val job = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        run = { () => counter += 1 }
      )
      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, reply.ref)
      reply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

      // Tick 1: initialize lastTick
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      Thread.sleep(200)
      assert(counter == 0)

      // Tick 2: tickInterval elapsed, but no cron period passed
      mockTime.set(Instant.parse("2024-01-01T12:00:00.600Z"))
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      Thread.sleep(200)
      assert(counter == 0)

      // Tick 3: past cron boundary
      mockTime.set(Instant.parse("2024-01-01T12:01:00.100Z"))
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      Thread.sleep(200)
      assert(counter == 1)

      // Tick 4: not yet next cron boundary
      mockTime.set(Instant.parse("2024-01-01T12:01:00.700Z"))
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      Thread.sleep(200)
      assert(counter == 1)

      // Tick 5: past next cron boundary
      mockTime.set(Instant.parse("2024-01-01T12:02:00.200Z"))
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      Thread.sleep(200)
      assert(counter == 2)
    }

    "add multiple jobs in JustState" in {
      val zoneId = ZoneId.systemDefault()
      val id     = UUID.randomUUID()

      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id))
      val reply1               = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
      val reply2               = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()

      val job1 = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        run = { () => () }
      )
      val job2 = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        run = { () => () }
      )

      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job1, reply1.ref)
      reply1.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job2, reply2.ref)
      reply2.expectMessage(JobSchedulerProtocol.AddJobSucceeded)
    }

    "remove job in JustState" in {
      val zoneId = ZoneId.systemDefault()
      val id     = UUID.randomUUID()

      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id))
      val addReply             = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
      val removeReply          = testKit.createTestProbe[JobSchedulerProtocol.RemoveJobReply]()

      val job = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        run = { () => () }
      )

      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, addReply.ref)
      addReply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

      jobSchedulerActorRef ! JobSchedulerProtocol.RemoveJob(id, job.id, removeReply.ref)
      removeReply.expectMessage(JobSchedulerProtocol.RemoveJobSucceeded)
    }

    "shutdown in JustState" in {
      val zoneId = ZoneId.systemDefault()
      val id     = UUID.randomUUID()

      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id))
      val addReply             = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
      val shutdownReply        = testKit.createTestProbe[JobSchedulerProtocol.ShutdownCompleted.type]()

      val job = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        run = { () => () }
      )

      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, addReply.ref)
      addReply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

      jobSchedulerActorRef ! JobSchedulerProtocol.Shutdown(id, shutdownReply.ref)
      shutdownReply.expectMessage(JobSchedulerProtocol.ShutdownCompleted)

      testKit.createTestProbe().expectTerminated(jobSchedulerActorRef)
    }
  }
}
