# chronos-scheduler-scala

A job scheduler for Scala.

[![CI](https://github.com/j5ik2o/chronos-scheduler-scala/workflows/CI/badge.svg)](https://github.com/j5ik2o/chronos-scheduler-scala/actions?query=workflow%3ACI)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/chronos-scheduler-scala_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/chronos-scheduler-scala_2.13)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://renovatebot.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Tokei](https://tokei.rs/b1/github/j5ik2o/chronos-scheduler-scala)](https://github.com/XAMPPRocky/tokei)

## Installation

Add the following to your sbt build (2.13.x):

```scala
val version = "..."

libraryDependencies += Seq(
  "com.github.j5ik2o" %% "chronos-scheduler-scala-core" % version,
  "com.github.j5ik2o" %% "chronos-scheduler-scala-akka-actor" % version
)
```

## Usage

### Core

The core module provides a simple synchronous API.

```scala
var counter = 0

val jobScheduler = JobScheduler(UUID.randomUUID()).addJob(
  Job(
    id = UUID.randomUUID(),
    schedule = CronSchedule("*/1 * * * *", ZoneId.systemDefault()),
    run = { () =>
      println(s"run job: $counter")
      counter += 1
    }
  )
)

while(true) {
  jobScheduler.tick()
  Thread.sleep(1000 * 60)
}
```

### Actor

The actor module provides an asynchronous non-blocking API.

```scala
object Main extends App {

  val system = ActorSystem(apply, "job-scheduler-actor-main")

  sealed trait Command
  case class WrappedAddJobReply(reply: JobSchedulerProtocol.AddJobReply) extends Command

  def apply: Behavior[Command] = Behaviors.setup[Command] { ctx =>

    var counter = 0
    val id      = UUID.randomUUID()

    val jobSchedulerActorRef = ctx.spawn(
      JobSchedulerActor(id, Some(1.seconds)), 
      "job-scheduler-actor"
    )

    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(
      id,
      Job(
        id = UUID.randomUUID(),
        schedule = CronSchedule("*/1 * * * *", ZoneId.systemDefault()),
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
```

## License

MIT license ([LICENSE](LICENSE) or https://opensource.org/licenses/MIT)
