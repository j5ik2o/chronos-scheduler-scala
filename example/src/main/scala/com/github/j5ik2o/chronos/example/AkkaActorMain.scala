package com.github.j5ik2o.chronos.example

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import com.github.j5ik2o.chronos.akka.JobSchedulerProtocol.AddJobSucceeded
import com.github.j5ik2o.chronos.akka.{ CborSerializable, Job, JobSchedulerActor, JobSchedulerProtocol }

import java.time.ZoneId
import java.util.UUID
import scala.concurrent.duration._

object AkkaActorMain extends App {

  sealed trait AppMessage extends CborSerializable
  case class PrintMessage(text: String) extends AppMessage

  sealed trait Command
  case class WrappedAddJobReply(reply: JobSchedulerProtocol.AddJobReply) extends Command
  case class WorkerMessage(msg: AppMessage)                               extends Command

  val system: ActorSystem[Command] = ActorSystem(apply, "job-scheduler-actor-main")

  def apply: Behavior[Command] = Behaviors.setup[Command] { ctx =>
    val zoneId  = ZoneId.systemDefault()
    var counter = 0
    val id      = UUID.randomUUID()

    val workerRef = ctx.spawn(
      Behaviors.receiveMessage[AppMessage] {
        case PrintMessage(text) =>
          println(text)
          Behaviors.same
      },
      "worker"
    )

    val jobSchedulerActorRef = ctx.spawn(JobSchedulerActor(id, Some(500.millis)), "job-scheduler-actor")

    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(
      id,
      Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
        sendTo = workerRef,
        message = PrintMessage(s"run job: ${counter}")
      ),
      ctx.messageAdapter[JobSchedulerProtocol.AddJobReply](ref => WrappedAddJobReply(ref))
    )

    Behaviors.receiveMessagePartial[Command] { case WrappedAddJobReply(AddJobSucceeded) =>
      counter += 1
      Behaviors.same
    }
  }

}
