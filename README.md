# chronos-scheduler-scala

[Japanese](README.ja.md)

A job scheduler library for Scala.

[![CI](https://github.com/j5ik2o/chronos-scheduler-scala/workflows/CI/badge.svg)](https://github.com/j5ik2o/chronos-scheduler-scala/actions?query=workflow%3ACI)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.j5ik2o/chronos-scheduler-scala-core_2.13)](https://central.sonatype.com/artifact/com.github.j5ik2o/chronos-scheduler-scala-core_2.13)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://renovatebot.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

## Requirements

- JDK 17 or later
- Scala 2.13.18 / Scala 3.3.7

## Installation

Add the following to your sbt build:

```scala
val version = "<version>"

libraryDependencies ++= Seq(
  "com.github.j5ik2o" %% "chronos-scheduler-scala-core" % version,
  // Choose one of the following:
  "com.github.j5ik2o" %% "chronos-scheduler-scala-pekko-actor" % version,
  "com.github.j5ik2o" %% "chronos-scheduler-scala-akka-actor" % version
)
```

## Usage

### Core

The core module provides a simple synchronous API.

```scala
val zoneId: ZoneId = ZoneId.systemDefault()
var counter = 0

val jobScheduler = JobScheduler(UUID.randomUUID()).addJob(
  Job(
    id = UUID.randomUUID(),
    cronExpression = "*/1 * * * *",
    zoneId,
    run = { () =>
      println(s"run job: $counter")
      counter += 1
    }
  )
)

while (true) {
  jobScheduler.tick()
  Thread.sleep(1000)
}
```

### Pekko Actor

The pekko-actor module provides an asynchronous non-blocking API using Apache Pekko Typed.

```scala
object Main extends App {

  val system = ActorSystem(apply, "job-scheduler-actor-main")

  sealed trait Command
  case class WrappedAddJobReply(reply: JobSchedulerProtocol.AddJobReply) extends Command

  def apply: Behavior[Command] = Behaviors.setup[Command] { ctx =>
    val zoneId  = ZoneId.systemDefault()
    var counter = 0
    val id      = UUID.randomUUID()

    val jobSchedulerActorRef = ctx.spawn(
      JobSchedulerActor(id, Some(500.millis)),
      "job-scheduler-actor"
    )

    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(
      id,
      Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
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

### Akka Actor

The akka-actor module provides the same API using Akka Typed.

```scala
object Main extends App {

  val system = ActorSystem(apply, "job-scheduler-actor-main")

  sealed trait Command
  case class WrappedAddJobReply(reply: JobSchedulerProtocol.AddJobReply) extends Command

  def apply: Behavior[Command] = Behaviors.setup[Command] { ctx =>
    val zoneId  = ZoneId.systemDefault()
    var counter = 0
    val id      = UUID.randomUUID()

    val jobSchedulerActorRef = ctx.spawn(
      JobSchedulerActor(id, Some(500.millis)),
      "job-scheduler-actor"
    )

    jobSchedulerActorRef ! JobSchedulerProtocol.AddJob(
      id,
      Job(
        id = UUID.randomUUID(),
        cronExpression = "*/1 * * * *",
        zoneId,
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
