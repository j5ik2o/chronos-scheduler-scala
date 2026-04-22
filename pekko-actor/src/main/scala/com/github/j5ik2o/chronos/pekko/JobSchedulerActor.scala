package com.github.j5ik2o.chronos.pekko

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ ActorRef, Behavior }
import com.github.j5ik2o.chronos.core.Job

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._

object JobSchedulerProtocol {
  sealed trait Command
  case class AddJob(schedulerId: UUID, job: Job, replyTo: ActorRef[AddJobReply]) extends Command
  sealed trait AddJobReply
  case object AddJobSucceeded extends AddJobReply
  case class AddJobFailed(ex: Throwable) extends AddJobReply

  case class RemoveJob(schedulerId: UUID, jobId: UUID, replyTo: ActorRef[RemoveJobReply]) extends Command
  sealed trait RemoveJobReply
  case object RemoveJobSucceeded extends RemoveJobReply
  case class RemoveJobFailed(ex: Throwable) extends RemoveJobReply

  case class Tick(schedulerId: UUID) extends Command
  case class Shutdown(schedulerId: UUID, replyTo: ActorRef[ShutdownCompleted.type]) extends Command
  case object ShutdownCompleted extends Command
}

object JobSchedulerActor {

  private def running(
      schedulerId: UUID,
      jobRefs: Map[UUID, ActorRef[JobProtocol.Command]],
      clock: () => Instant
  ): Behavior[JobSchedulerProtocol.Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial {
        case JobSchedulerProtocol.AddJob(sid, job, replyTo) if schedulerId == sid =>
          val jobRef     = ctx.spawn(JobActor(job, clock), job.id.toString)
          val newJobRefs = jobRefs + (job.id -> jobRef)
          replyTo ! JobSchedulerProtocol.AddJobSucceeded
          running(schedulerId, newJobRefs, clock)
        case JobSchedulerProtocol.RemoveJob(sid, jobId, replyTo) if schedulerId == sid =>
          val jobRef = jobRefs.get(jobId)
          jobRef.foreach(ref => ctx.stop(ref))
          val newJobRefs = jobRefs - jobId
          replyTo ! JobSchedulerProtocol.RemoveJobSucceeded
          running(schedulerId, newJobRefs, clock)
        case JobSchedulerProtocol.Shutdown(sid, replyTo) if schedulerId == sid =>
          replyTo ! JobSchedulerProtocol.ShutdownCompleted
          Behaviors.stopped
        case JobSchedulerProtocol.Tick(sid) if schedulerId == sid =>
          jobRefs.foreach { case (_, jobRef) =>
            jobRef ! JobProtocol.Tick
          }
          Behaviors.same
      }
    }
  }

  def apply(
      id: UUID,
      tickInterval: Option[FiniteDuration] = None,
      clock: () => Instant = () => Instant.now()
  ): Behavior[JobSchedulerProtocol.Command] = {
    Behaviors.setup[JobSchedulerProtocol.Command] { _ =>
      Behaviors.withTimers { timer =>
        tickInterval.foreach(d => timer.startTimerAtFixedRate(JobSchedulerProtocol.Tick(id), d))
        running(id, Map.empty, clock)
      }
    }
  }
}
