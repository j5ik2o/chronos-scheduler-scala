package com.github.j5ik2o.chronos.akka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.j5ik2o.chronos.core.Job

import java.time.{ Duration, Instant }

object JobProtocol {
  sealed trait Command
  case object Tick extends Command
  case object Run extends Command

}

object JobActor {

  private def started(job: Job, clock: () => Instant)(
      lastTick: Option[Instant]
  ): Behavior[JobProtocol.Command] = {
    Behaviors.setup[JobProtocol.Command] { _ =>
      Behaviors.receiveMessagePartial {
        case JobProtocol.Tick =>
          Behaviors.same
        case JobProtocol.Run =>
          job.run()
          notStarted(job, clock)(lastTick)
      }
    }
  }

  private def notStarted(job: Job, clock: () => Instant)(
      lastTick: Option[Instant]
  ): Behavior[JobProtocol.Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial { case JobProtocol.Tick =>
        val now = clock()
        lastTick match {
          case None =>
            notStarted(job, clock)(Some(now))
          case Some(lt) if lt.plus(Duration.ofMillis(job.tickInterval.toMillis)).toEpochMilli <= now.toEpochMilli =>
            val nextOccurrences = job.schedule.upcoming(lt).drop(1)
            val hasDue          = nextOccurrences.head.toEpochMilli <= now.toEpochMilli
            if (hasDue) {
              if (job.limitMissedRuns > 0) {
                if (nextOccurrences.take(job.limitMissedRuns).exists(_.toEpochMilli > now.toEpochMilli)) {
                  ctx.self ! JobProtocol.Run
                  started(job, clock)(Some(now))
                } else
                  notStarted(job, clock)(Some(now))
              } else {
                ctx.self ! JobProtocol.Run
                started(job, clock)(Some(now))
              }
            } else
              Behaviors.same
          case _ =>
            Behaviors.same
        }
      }
    }
  }

  def apply(
      job: Job,
      clock: () => Instant = () => Instant.now()
  ): Behavior[JobProtocol.Command] = {
    notStarted(job, clock)(None)
  }

}
