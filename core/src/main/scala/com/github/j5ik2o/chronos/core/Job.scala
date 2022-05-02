package com.github.j5ik2o.chronos.core

import com.github.j5ik2o.cron.CronSchedule

import java.time.{ Duration, Instant, ZoneId }
import java.util.UUID
import scala.concurrent.duration._

case class Job(
    id: UUID,
    cronExpression: String,
    zoneId: ZoneId,
    limitMissedRuns: Int = 5,
    tickInterval: FiniteDuration = 1.minutes,
    @transient run: () => Unit
) {

  @transient
  val schedule: CronSchedule = CronSchedule(cronExpression, zoneId)

  private var _lastTick: Option[Instant] = None

  def lastTick_=(value: Option[Instant]): Unit = {
    _lastTick = value
  }
  def lastTick: Option[Instant] = _lastTick

}

object Job {

  implicit object JobTicker extends Ticker[Job] {

    override def tick(self: Job): Unit = {
      val now = Instant.now()
      self.lastTick match {
        case None =>
          self.lastTick = Some(now)
        case Some(lt) if lt.plus(Duration.ofMillis(self.tickInterval.toMillis)).toEpochMilli <= now.toEpochMilli =>
          if (self.limitMissedRuns > 0) {
            if (self.schedule.upcoming(lt).take(self.limitMissedRuns).exists(_.toEpochMilli > now.toEpochMilli)) {
              self.run()
            }
            self.lastTick = Some(now)
          } else {
            if (self.schedule.upcoming(lt).exists(_.toEpochMilli > now.toEpochMilli)) {
              self.run()
            }
            self.lastTick = Some(now)
          }
        case _ =>
      }

    }
  }
}

trait Ticker[A] {
  def tick(self: A): Unit
}

object Ticker {

  implicit class TickerOps[A](private val self: A) extends AnyVal {

    def tick()(implicit ev: Ticker[A]): Unit = {
      ev.tick(self)
    }
  }
}
