package com.github.j5ik2o.chronos.akka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.j5ik2o.chronos.core.Job

import java.time.{ Duration, Instant }
import scala.concurrent.duration.DurationInt

object JobProtocol {
  sealed trait Command
  case object Tick extends Command
  case object Run  extends Command

}

object JobActor {

  private def started(job: Job)(
      lastTick: Option[Instant]
  ): Behavior[JobProtocol.Command] = {
    Behaviors.setup[JobProtocol.Command] { _ =>
      Behaviors.receiveMessagePartial {
        case JobProtocol.Tick =>
          Behaviors.same
        case JobProtocol.Run =>
          job.run()
          notStarted(job)(lastTick)
      }
    }
  }

  private def notStarted(job: Job)(
      lastTick: Option[Instant]
  ): Behavior[JobProtocol.Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial { case JobProtocol.Tick =>
        val now = Instant.now()
        lastTick match {
          case None =>
            notStarted(job)(Some(now))
          case Some(lt) if lt.plus(Duration.ofMillis(job.tickInterval.toMillis)).toEpochMilli <= now.toEpochMilli =>
            if (job.limitMissedRuns > 0)
              if (
                job.schedule.upcoming(lastTick.get).take(job.limitMissedRuns).exists(_.toEpochMilli > now.toEpochMilli)
              ) {
                ctx.self ! JobProtocol.Run
                started(job)(Some(now))
              } else
                notStarted(job)(Some(now))
            else {
              if (job.schedule.upcoming(lastTick.get).exists(_.toEpochMilli > now.toEpochMilli)) {
                ctx.self ! JobProtocol.Run
                started(job)(Some(now))
              } else
                notStarted(job)(Some(now))
            }
          case _ =>
            Behaviors.same
        }
      }
    }
  }

  def apply(
      job: Job
  ): Behavior[JobProtocol.Command] = {
    notStarted(job)(None)
  }

}
