package com.github.j5ik2o.chronos.akka

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import com.github.j5ik2o.chronos.akka.JobSchedulerProtocol.{ AddJobReply, RemoveJobReply }
import com.github.j5ik2o.chronos.core.Job

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

object JobSchedulerEvents {
  sealed trait Event extends CborSerializable
  case class JobAdded(schedulerId: UUID, job: Job, replyTo: ActorRef[AddJobReply]) extends Event
  case class JobRemoved(schedulerId: UUID, jobID: UUID, replyTo: ActorRef[RemoveJobReply]) extends Event
}

object PersistentJobSchedulerActor {

  sealed trait State
  case object EmptyState extends State
  case class JustState(schedulerId: UUID, jobs: Map[UUID, Job]) extends State

  def apply(id: UUID, tickInterval: Option[FiniteDuration] = None): Behavior[JobSchedulerProtocol.Command] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timer =>
        tickInterval.foreach(d => timer.startTimerAtFixedRate(JobSchedulerProtocol.Tick(id), d))
        EventSourcedBehavior.withEnforcedReplies[JobSchedulerProtocol.Command, JobSchedulerEvents.Event, State](
          persistenceId = PersistenceId.ofUniqueId(id.toString),
          emptyState = EmptyState,
          commandHandler = {
            case (EmptyState, JobSchedulerProtocol.AddJob(sid, job, replyTo)) =>
              Effect
                .persist(JobSchedulerEvents.JobAdded(sid, job, replyTo)).thenReply(replyTo) { _ =>
                  JobSchedulerProtocol.AddJobSucceeded
                }
            case (EmptyState, JobSchedulerProtocol.RemoveJob(sid, jobId, replyTo)) =>
              Effect
                .persist(JobSchedulerEvents.JobRemoved(sid, jobId, replyTo)).thenReply(replyTo) { _ =>
                  JobSchedulerProtocol.RemoveJobSucceeded
                }
            case (JustState(schedulerId, _), JobSchedulerProtocol.Stop(sid, replyTo)) if schedulerId == sid =>
              Effect.reply(replyTo)(JobSchedulerProtocol.Stopped)
            case (JustState(schedulerId, jobs), JobSchedulerProtocol.Tick(sid)) if schedulerId == sid =>
              jobs.foreach { case (_, job) =>
                val jobRef = ctx.child(job.id.toString) match {
                  case Some(ref) => ref.unsafeUpcast[JobProtocol.Command]
                  case None      => ctx.spawn(JobActor(job), job.id.toString)
                }
                jobRef ! JobProtocol.Tick
              }
              Effect.noReply
          },
          eventHandler = {
            case (EmptyState, JobSchedulerEvents.JobAdded(_, job, _)) =>
              JustState(id, Map(job.id -> job))
            case (JustState(schedulerId, jobs), JobSchedulerEvents.JobAdded(sid, job, _)) if schedulerId == sid =>
              JustState(id, jobs + (job.id -> job))
            case (JustState(schedulerId, jobs), JobSchedulerEvents.JobRemoved(sid, jobId, _)) if schedulerId == sid =>
              JustState(schedulerId, jobs - jobId)
          }
        )

      }
    }
  }
}
