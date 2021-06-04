package com.github.j5ik2o.chronos

import com.github.j5ik2o.cron.CronSchedule
import org.slf4j.{ Logger, LoggerFactory }

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }

case class Job(id: UUID, schedule: CronSchedule, run: () => Unit, limitMissedRuns: Int = 5) {
  private val logger: Logger             = LoggerFactory.getLogger(getClass)
  private var _lastTick: Option[Instant] = None

  @volatile
  private var runing = false

  def lastTick: Option[Instant] = _lastTick

  def tick()(implicit ec: ExecutionContext): Unit = {
    if (runing) {
      println(s"${id}, running...")
      logger.info(s"${id}, running...")
    } else {
      val now = Instant.now()
      if (_lastTick.isEmpty) {
        _lastTick = Some(now)
      } else if (limitMissedRuns > 0) {
        if (
          !runing &&
          schedule
            .upcoming(_lastTick.get).take(limitMissedRuns)
            .exists(event => event.toEpochMilli > now.toEpochMilli)
        ) {
          Future {
            runing = true
            run()
            runing = false
          }
        }
        _lastTick = Some(now)
      } else {
        if (
          !runing &&
          schedule
            .upcoming(_lastTick.get)
            .exists(event => event.toEpochMilli > now.toEpochMilli)
        ) {
          Future {
            runing = true
            run()
            runing = false
          }
        }
        _lastTick = Some(now)
      }
    }
  }
}
