package com.github.j5ik2o.chronos.pekko

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.funsuite.AnyFunSuite

import java.time.{ Instant, ZoneId }
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._

class JobSchedulerActorSpec extends AnyFunSuite {

  val testKit: ActorTestKit = ActorTestKit()

  sealed trait TestMessage extends CborSerializable
  case object DoWork extends TestMessage

  test("job fires only on cron boundary") {
    val zoneId               = ZoneId.of("UTC")
    val mockTime             = new AtomicReference[Instant](Instant.parse("2024-01-01T12:00:00Z"))
    val clock: () => Instant = () => mockTime.get()
    val id                   = UUID.randomUUID()
    val probe                = testKit.createTestProbe[TestMessage]()
    val jobSchedulerActorRef = testKit.spawn(JobSchedulerActor(id, clock = clock))

    val reply = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
    val job   = Job(
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

    // Tick 3: past cron boundary (12:01:00)
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

  test("job does not fire when limitMissedRuns exceeded") {
    val zoneId               = ZoneId.of("UTC")
    val mockTime             = new AtomicReference[Instant](Instant.parse("2024-01-01T12:00:00Z"))
    val clock: () => Instant = () => mockTime.get()
    val id                   = UUID.randomUUID()
    val probe                = testKit.createTestProbe[TestMessage]()
    val jobSchedulerActorRef = testKit.spawn(JobSchedulerActor(id, clock = clock))

    val reply = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
    val job   = Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      limitMissedRuns = 3,
      tickInterval = 500.millis,
      sendTo = probe.ref,
      message = DoWork
    )
    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, reply.ref)
    reply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

    // Tick 1: initialize
    jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
    probe.expectNoMessage(200.millis)

    // Tick 2: jump 10 minutes ahead (missed more than 3 runs)
    mockTime.set(Instant.parse("2024-01-01T12:10:00.100Z"))
    jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
    probe.expectNoMessage(200.millis)
  }

  test("GetJobs returns added jobs") {
    val zoneId               = ZoneId.of("UTC")
    val id                   = UUID.randomUUID()
    val probe                = testKit.createTestProbe[TestMessage]()
    val jobSchedulerActorRef = testKit.spawn(JobSchedulerActor(id))
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

    jobSchedulerActorRef ! JobSchedulerProtocol.GetJobs(id, getReply.ref)
    getReply.expectMessage(JobSchedulerProtocol.GetJobsResponse(Seq.empty))

    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, addReply.ref)
    addReply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

    jobSchedulerActorRef ! JobSchedulerProtocol.GetJobs(id, getReply.ref)
    val response = getReply.expectMessageType[JobSchedulerProtocol.GetJobsResponse]
    assert(response.jobs.map(_.id) == Seq(job.id))
  }

  test("rapid ticks do not cause errors") {
    val zoneId               = ZoneId.of("UTC")
    val mockTime             = new AtomicReference[Instant](Instant.parse("2024-01-01T12:00:00Z"))
    val clock: () => Instant = () => mockTime.get()
    val id                   = UUID.randomUUID()
    val probe                = testKit.createTestProbe[TestMessage]()
    val jobSchedulerActorRef = testKit.spawn(JobSchedulerActor(id, clock = clock))

    val reply = testKit.createTestProbe[JobSchedulerProtocol.AddJobReply]()
    val job   = Job(
      id = UUID.randomUUID(),
      cronExpression = "*/1 * * * *",
      zoneId,
      tickInterval = 500.millis,
      sendTo = probe.ref,
      message = DoWork
    )
    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(id, job, reply.ref)
    reply.expectMessage(JobSchedulerProtocol.AddJobSucceeded)

    // Send many ticks without advancing time
    for (_ <- 1 to 10) {
      jobSchedulerActorRef ! JobSchedulerProtocol.Tick(id)
    }
    probe.expectNoMessage(200.millis)
  }
}
