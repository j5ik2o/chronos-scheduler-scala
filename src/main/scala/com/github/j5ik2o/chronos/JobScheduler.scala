package com.github.j5ik2o.chronos

import java.util.UUID
import scala.concurrent.ExecutionContext

case class JobScheduler(id: UUID, jobs: Vector[Job] = Vector.empty) {

  def addJob(job: Job): JobScheduler =
    copy(jobs = jobs :+ job)

  def removeJob(jobId: UUID): JobScheduler =
    copy(jobs = jobs.filterNot(_.id == jobId))

  def tick()(implicit ec: ExecutionContext): Unit = {
    jobs.foreach(_.tick())
  }

}
