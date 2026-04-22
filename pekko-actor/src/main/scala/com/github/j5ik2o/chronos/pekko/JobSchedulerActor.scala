package com.github.j5ik2o.chronos.pekko

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ ActorRef, Behavior }

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._

object JobSchedulerProtocol {
  sealed trait Command
  case class AddJob(schedulerId: UUID, job: Job[_], replyTo: ActorRef[AddJobReply]) extends Command
  sealed trait AddJobReply
  case object AddJobSucceeded                       extends AddJobReply
  case class AddJobFailed(ex: Throwable)            extends AddJobReply

  case class RemoveJob(schedulerId: UUID, jobId: UUID, replyTo: ActorRef[RemoveJobReply]) extends Command
  sealed trait RemoveJobReply
  case object RemoveJobSucceeded                    extends RemoveJobReply
  case class RemoveJobFailed(ex: Throwable)         extends RemoveJobReply

  case class GetJobs(schedulerId: UUID, replyTo: ActorRef[GetJobsReply]) extends Command
  sealed trait GetJobsReply
  case class GetJobsResponse(jobs: Seq[Job[_]])     extends GetJobsReply

  case class Tick(schedulerId: UUID)                                                      extends Command
  case class Shutdown(schedulerId: UUID, replyTo: ActorRef[ShutdownCompleted.type])       extends Command
  case object ShutdownCompleted                     extends Command
}

object JobSchedulerActor {

  private def running(
      schedulerId: UUID,
      jobs: Map[UUID, (Job[_], ActorRef[JobProtocol.Command])],
      clock: () => Instant
  ): Behavior[JobSchedulerProtocol.Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial {
        case JobSchedulerProtocol.AddJob(sid, job, replyTo) if schedulerId == sid =>
          val jobRef  = ctx.spawn(JobActor(job, clock), job.id.toString)
          val newJobs = jobs + (job.id -> (job, jobRef))
          replyTo ! JobSchedulerProtocol.AddJobSucceeded
          running(schedulerId, newJobs, clock)
        case JobSchedulerProtocol.RemoveJob(sid, jobId, replyTo) if schedulerId == sid =>
          jobs.get(jobId).foreach { case (_, ref) => ctx.stop(ref) }
          val newJobs = jobs - jobId
          replyTo ! JobSchedulerProtocol.RemoveJobSucceeded
          running(schedulerId, newJobs, clock)
        case JobSchedulerProtocol.GetJobs(sid, replyTo) if schedulerId == sid =>
          replyTo ! JobSchedulerProtocol.GetJobsResponse(jobs.values.map(_._1).toSeq)
          Behaviors.same
        case JobSchedulerProtocol.Shutdown(sid, replyTo) if schedulerId == sid =>
          replyTo ! JobSchedulerProtocol.ShutdownCompleted
          Behaviors.stopped
        case JobSchedulerProtocol.Tick(sid) if schedulerId == sid =>
          jobs.foreach { case (_, (_, jobRef)) =>
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
