package com.github.j5ik2o.chronos.akka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.j5ik2o.chronos.core.Job

import java.time.Instant

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
        if (lastTick.isEmpty) notStarted(job)(Some(now))
        else if (job.limitMissedRuns > 0)
          if (job.schedule.upcoming(lastTick.get).take(job.limitMissedRuns).exists(_.toEpochMilli > now.toEpochMilli)) {
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
      }
    }
  }

  def apply(
      job: Job
  ): Behavior[JobProtocol.Command] = {
    notStarted(job)(None)
  }

}
