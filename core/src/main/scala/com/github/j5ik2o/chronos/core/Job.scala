package com.github.j5ik2o.chronos.core

import com.github.j5ik2o.cron.CronSchedule

import java.time.Instant
import java.util.UUID

case class Job(id: UUID, schedule: CronSchedule, run: () => Unit, limitMissedRuns: Int = 5) {
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
      if (self.lastTick.isEmpty) {
        self.lastTick = Some(now)
      } else if (self.limitMissedRuns > 0) {
        if (
          self.schedule
            .upcoming(self.lastTick.get).take(self.limitMissedRuns).exists(event =>
              event.toEpochMilli > now.toEpochMilli
            )
        ) {
          self.run()
        }
        self.lastTick = Some(now)
      } else {
        if (self.schedule.upcoming(self.lastTick.get).exists(event => event.toEpochMilli > now.toEpochMilli)) {
          self.run()
        }
        self.lastTick = Some(now)
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
