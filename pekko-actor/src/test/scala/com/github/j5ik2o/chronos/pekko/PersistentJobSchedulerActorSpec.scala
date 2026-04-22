package com.github.j5ik2o.chronos.pekko

import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.freespec.AnyFreeSpecLike

import java.time.{ Instant, ZoneId }
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._

object PersistentJobSchedulerActorSpec {
  val config: Config = ConfigFactory.parseString(s"""
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    pekko.persistence.journal.inmem.test-serialization = on
    pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
    pekko.persistence.snapshot-store.local.dir = "target/${getClass.getName}-${UUID.randomUUID().toString}"
    pekko.actor.serialization-bindings {
      "${classOf[CborSerializable].getName}" = jackson-cbor
    }
    """)
}

class PersistentJobSchedulerActorSpec
    extends ScalaTestWithActorTestKit(PersistentJobSchedulerActorSpec.config)
    with AnyFreeSpecLike {

  sealed trait TestMessage extends CborSerializable
  case object DoWork extends TestMessage

  "PersistentJobSchedulerActor" - {
    "job fires only on cron boundary" in {
      val zoneId               = ZoneId.of("UTC")
      val mockTime             = new AtomicReference[Instant](Instant.parse("2024-01-01T12:00:00Z"))
      val clock: () => Instant = () => mockTime.get()
      val id                   = UUID.randomUUID()
      val probe                = testKit.createTestProbe[TestMessage]()

      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id, clock = clock))
      val reply                = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()

      val job = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        sendTo = probe.ref,
        message = DoWork
      )
      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, reply.ref)
      reply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

      // Tick 1: initialize lastTick
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      probe.expectNoMessage(200.millis)

      // Tick 2: tickInterval elapsed, but no cron period passed
      mockTime.set(Instant.parse("2024-01-01T12:00:00.600Z"))
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      probe.expectNoMessage(200.millis)

      // Tick 3: past cron boundary
      mockTime.set(Instant.parse("2024-01-01T12:01:00.100Z"))
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      probe.expectMessage(DoWork)

      // Tick 4: not yet next cron boundary
      mockTime.set(Instant.parse("2024-01-01T12:01:00.700Z"))
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      probe.expectNoMessage(200.millis)

      // Tick 5: past next cron boundary
      mockTime.set(Instant.parse("2024-01-01T12:02:00.200Z"))
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
      probe.expectMessage(DoWork)
    }

    "add multiple jobs in JustState" in {
      val zoneId = ZoneId.systemDefault()
      val id     = UUID.randomUUID()
      val probe  = testKit.createTestProbe[TestMessage]()

      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id))
      val reply1               = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
      val reply2               = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()

      val job1 = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        sendTo = probe.ref,
        message = DoWork
      )
      val job2 = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        sendTo = probe.ref,
        message = DoWork
      )

      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job1, reply1.ref)
      reply1.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job2, reply2.ref)
      reply2.expectMessage(JobSchedulerProtocol.AddJobSucceeded)
    }

    "remove job in JustState" in {
      val zoneId = ZoneId.systemDefault()
      val id     = UUID.randomUUID()
      val probe  = testKit.createTestProbe[TestMessage]()

      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id))
      val addReply             = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
      val removeReply          = testKit.createTestProbe[JobSchedulerProtocol.RemoveJobReply]()

      val job = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        sendTo = probe.ref,
        message = DoWork
      )

      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, addReply.ref)
      addReply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

      jobSchedulerActorRef ! JobSchedulerProtocol.RemoveJob(id, job.id, removeReply.ref)
      removeReply.expectMessage(JobSchedulerProtocol.RemoveJobSucceeded)
    }

    "GetJobs returns empty in EmptyState" in {
      val id                   = UUID.randomUUID()
      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id))
      val getReply             = testKit.createTestProbe[JobSchedulerProtocol.GetJobsReply]()

      jobSchedulerActorRef ! JobSchedulerProtocol.GetJobs(id, getReply.ref)
      getReply.expectMessage(JobSchedulerProtocol.GetJobsResponse(Seq.empty))
    }

    "GetJobs returns added jobs in JustState" in {
      val zoneId               = ZoneId.systemDefault()
      val id                   = UUID.randomUUID()
      val probe                = testKit.createTestProbe[TestMessage]()
      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id))
      val addReply             = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
      val getReply             = testKit.createTestProbe[JobSchedulerProtocol.GetJobsReply]()

      val job = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        sendTo = probe.ref,
        message = DoWork
      )

      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, addReply.ref)
      addReply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

      jobSchedulerActorRef ! JobSchedulerProtocol.GetJobs(id, getReply.ref)
      val response = getReply.expectMessageType[JobSchedulerProtocol.GetJobsResponse]
      assert(response.jobs.map(_.id) == Seq(job.id))
    }

    "shutdown in JustState" in {
      val zoneId = ZoneId.systemDefault()
      val id     = UUID.randomUUID()
      val probe  = testKit.createTestProbe[TestMessage]()

      val jobSchedulerActorRef = testKit.spawn(PersistentJobSchedulerActor(id))
      val addReply             = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
      val shutdownReply        = testKit.createTestProbe[JobSchedulerProtocol.ShutdownCompleted.type]()

      val job = Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        tickInterval = 500.millis,
        sendTo = probe.ref,
        message = DoWork
      )

      jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, addReply.ref)
      addReply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

      jobSchedulerActorRef ! JobSchedulerProtocol.Shutdown(id, shutdownReply.ref)
      shutdownReply.expectMessage(JobSchedulerProtocol.ShutdownCompleted)

      testKit.createTestProbe().expectTerminated(jobSchedulerActorRef)
    }
  }
}
