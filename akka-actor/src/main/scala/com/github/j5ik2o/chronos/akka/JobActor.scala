package com.github.j5ik2o.chronos.akka

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import com.fasterxml.jackson.annotation.{ JsonIgnore, JsonTypeInfo }
import com.github.j5ik2o.cron.CronSchedule

import java.time.{ Duration, Instant, ZoneId }
import java.util.UUID
import scala.concurrent.duration._

case class Job[CMD](
    id: UUID,
    cronExpression: String,
    zoneId: ZoneId,
    limitMissedRuns: Int = 5,
    tickInterval: FiniteDuration = 1.minutes,
    sendTo: ActorRef[CMD],
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS) message: CMD
) extends CborSerializable {
  @JsonIgnore private[chronos] lazy val schedule: CronSchedule = CronSchedule(cronExpression, zoneId)
  @JsonIgnore private[chronos] def dispatch(): Unit             = sendTo ! message
}

object JobProtocol {
  sealed trait Command
  case object Tick extends Command
  case object Run  extends Command
}

object JobActor {

  private def started(job: Job[_], clock: () => Instant)(
      lastTick: Option[Instant]
  ): Behavior[JobProtocol.Command] = {
    Behaviors.setup[JobProtocol.Command] { _ =>
      Behaviors.receiveMessagePartial {
        case JobProtocol.Tick =>
          Behaviors.same
        case JobProtocol.Run =>
          job.dispatch()
          notStarted(job, clock)(lastTick)
      }
    }
  }

  private def notStarted(job: Job[_], clock: () => Instant)(
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
      job: Job[_],
      clock: () => Instant = () => Instant.now()
  ): Behavior[JobProtocol.Command] = {
    notStarted(job, clock)(None)
  }

}
