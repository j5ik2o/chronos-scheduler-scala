package com.github.j5ik2o.chronos.example

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import com.github.j5ik2o.chronos.akka.JobSchedulerProtocol.AddJobSucceeded
import com.github.j5ik2o.chronos.akka.{ JobSchedulerActor, JobSchedulerProtocol }
import com.github.j5ik2o.chronos.core.Job
import com.github.j5ik2o.cron.CronSchedule

import java.time.ZoneId
import java.util.UUID
import scala.concurrent.duration._

object AkkaActorMain extends App {

  val system = ActorSystem(apply, "job-scheduler-actor-main")

  sealed trait Command
  case class WrappedAddJobReply(reply: JobSchedulerProtocol.AddJobReply) extends Command

  def apply: Behavior[Command] = Behaviors.setup[Command] { ctx =>
    val zoneId  = ZoneId.systemDefault()
    var counter = 0
    val id      = UUID.randomUUID()

    val jobSchedulerActorRef = ctx.spawn(JobSchedulerActor(id, Some(500.millis)), "job-scheduler-actor")

    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(
      id,
      Job(
        id = UUID.randomUUID(),
        schedule = CronSchedule("*/1 * * * *", zoneId),
        run = { () =>
          println(s"run job: $counter")
          counter += 1
        }
      ),
      ctx.messageAdapter[JobSchedulerProtocol.AddJobReply](ref => WrappedAddJobReply(ref))
    )
    Behaviors.receiveMessagePartial[Command] { case WrappedAddJobReply(AddJobSucceeded) =>
      Behaviors.same
    }
  }

}
