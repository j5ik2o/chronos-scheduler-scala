package com.github.j5ik2o.chronos.akka

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import com.github.j5ik2o.chronos.akka.JobSchedulerActor.Protocol.{
  AddJob,
  AddJobSucceeded,
  RemoveJob,
  RemoveJobSucceeded,
  Stop,
  Stopped,
  Tick
}
import com.github.j5ik2o.chronos.core.{ Job, JobScheduler }

import java.util.UUID
import scala.concurrent.duration._

object JobSchedulerActor {

  object Protocol {
    sealed trait Command
    case class AddJob(schedulerId: UUID, job: Job, replyTo: ActorRef[AddJobReply]) extends Command
    sealed trait AddJobReply
    case class AddJobSucceeded()           extends AddJobReply
    case class AddJobFailed(ex: Throwable) extends AddJobReply

    case class RemoveJob(schedulerId: UUID, jobId: UUID, replyTo: ActorRef[RemoveJobReply]) extends Command
    sealed trait RemoveJobReply
    case class RemoveJobSucceeded()           extends RemoveJobReply
    case class RemoveJobFailed(ex: Throwable) extends RemoveJobReply

    case class Tick(schedulerId: UUID)                                  extends Command
    case class Stop(schedulerId: UUID, replyTo: ActorRef[Stopped.type]) extends Command
    case object Stopped                                                 extends Command
  }

  private def running(
      schedulerId: UUID,
      jobRefs: Map[UUID, ActorRef[JobProtocol.Command]]
  ): Behavior[Protocol.Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial {
        case AddJob(sid, job, replyTo) if schedulerId == sid =>
          val ref        = ctx.spawn(JobActor(job), job.id.toString)
          val newJobRefs = jobRefs + (job.id -> ref)
          replyTo ! AddJobSucceeded()
          running(schedulerId, newJobRefs)
        case RemoveJob(sid, jobId, replyTo) if schedulerId == sid =>
          val jobRef = jobRefs.get(jobId)
          jobRef.foreach(ref => ctx.stop(ref))
          val newJobRefs = jobRefs - jobId
          replyTo ! RemoveJobSucceeded()
          running(schedulerId, newJobRefs)
        case Stop(sid, replyTo) if schedulerId == sid =>
          replyTo ! Stopped
          Behaviors.stopped
        case Tick(sid) if schedulerId == sid =>
          jobRefs.foreach { case (_, jobRef) =>
            jobRef ! JobProtocol.Tick()
          }
          Behaviors.same
      }
    }
  }

  def apply(id: UUID, tickInterval: Option[FiniteDuration] = None): Behavior[Protocol.Command] = {
    Behaviors.setup[Protocol.Command] { _ =>
      Behaviors.withTimers { timer =>
        tickInterval.foreach(d => timer.startTimerAtFixedRate(Tick(id), d))
        running(id, Map.empty)
      }
    }
  }
}
