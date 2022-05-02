package com.github.j5ik2o.chronos.akka

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import com.github.j5ik2o.chronos.akka.JobSchedulerProtocol.{ AddJobReply, RemoveJobReply }

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

object JobSchedulerEvents {
  sealed trait Event
  case class JobAdded(schedulerId: UUID, jobRef: (UUID, ActorRef[JobProtocol.Command]), replyTo: ActorRef[AddJobReply])
      extends Event
  case class JobRemoved(schedulerId: UUID, jobID: UUID, replyTo: ActorRef[RemoveJobReply]) extends Event
}

class PersistentJobSchedulerActor {

  sealed trait State
  case object EmptyState extends State
  case class JustState(schedulerId: UUID, jobRefs: Map[UUID, ActorRef[JobProtocol.Command]]) extends State

  def apply(id: UUID, tickInterval: Option[FiniteDuration] = None): Behavior[JobSchedulerProtocol.Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timer =>
        tickInterval.foreach(d => timer.startTimerAtFixedRate(JobSchedulerProtocol.Tick(id), d))
        EventSourcedBehavior.withEnforcedReplies[JobSchedulerProtocol.Command, JobSchedulerEvents.Event, State](
          persistenceId = PersistenceId.ofUniqueId(id.toString),
          emptyState = EmptyState,
          commandHandler = {
            case (EmptyState, JobSchedulerProtocol.AddJob(sid, job, replyTo)) =>
              val jobRef = ctx.spawn(JobActor(job), job.id.toString)
              Effect
                .persist(JobSchedulerEvents.JobAdded(sid, job.id -> jobRef, replyTo)).thenReply(replyTo) { case _ =>
                  JobSchedulerProtocol.AddJobSucceeded
                }
            case (EmptyState, JobSchedulerProtocol.RemoveJob(sid, jobId, replyTo)) =>
              Effect
                .persist(JobSchedulerEvents.JobRemoved(sid, jobId, replyTo)).thenReply(replyTo) { _ =>
                  JobSchedulerProtocol.RemoveJobSucceeded
                }
            case (JustState(schedulerId, _), JobSchedulerProtocol.Stop(sid, replyTo)) if schedulerId == sid =>
              Effect.reply(replyTo)(JobSchedulerProtocol.Stopped)
            case (JustState(schedulerId, jobRefs), JobSchedulerProtocol.Tick(sid)) if schedulerId == sid =>
              jobRefs.foreach { case (_, jobRef) =>
                jobRef ! JobProtocol.Tick
              }
              Effect.noReply
          },
          eventHandler = {
            case (JustState(schedulerId, jobRefs), JobSchedulerEvents.JobAdded(_, entry, _)) =>
              JustState(schedulerId, jobRefs + entry)
            case (JustState(schedulerId, jobRefs), JobSchedulerEvents.JobRemoved(_, jobId, _)) =>
              JustState(schedulerId, jobRefs - jobId)
          }
        )

      }
    }
  }
}
