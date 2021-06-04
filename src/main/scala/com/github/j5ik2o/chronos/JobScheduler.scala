package com.github.j5ik2o.chronos

import java.util.UUID
import scala.concurrent.ExecutionContext

case class JobScheduler(id: UUID, jobs: Vector[Job] = Vector.empty)(implicit ec: ExecutionContext) {

  def addJob(job: Job): JobScheduler =
    copy(jobs = jobs :+ job)

  def removeJob(jobId: UUID): JobScheduler =
    copy(jobs = jobs.filterNot(_.id == jobId))

  def tick(): Unit = {
    jobs.foreach(_.tick())
  }

}
