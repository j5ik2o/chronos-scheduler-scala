package com.github.j5ik2o.chronos.core

import com.github.j5ik2o.chronos.core.Ticker.TickerOps

import java.util.UUID

case class JobScheduler(id: UUID, jobs: Vector[Job] = Vector.empty) {

  def addJob(job: Job): JobScheduler =
    copy(jobs = jobs :+ job)

  def removeJob(jobId: UUID): JobScheduler =
    copy(jobs = jobs.filterNot(_.id == jobId))

  def tick(): Unit = {
    jobs.foreach(_.tick())
  }

}
