# chronos-scheduler-scala

[English](README.md)

Scala 用のジョブスケジューラライブラリです。

[![CI](https://github.com/j5ik2o/chronos-scheduler-scala/workflows/CI/badge.svg)](https://github.com/j5ik2o/chronos-scheduler-scala/actions?query=workflow%3ACI)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.j5ik2o/chronos-scheduler-scala-core_2.13)](https://central.sonatype.com/artifact/com.github.j5ik2o/chronos-scheduler-scala-core_2.13)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://renovatebot.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

## 動作環境

- JDK 17 以上
- Scala 2.13.18 / Scala 3.3.7

## インストール

sbt ビルドに以下を追加してください:

```scala
val version = "<version>"

libraryDependencies ++= Seq(
  "com.github.j5ik2o" %% "chronos-scheduler-scala-core" % version,
  // 以下のいずれかを選択:
  "com.github.j5ik2o" %% "chronos-scheduler-scala-pekko-actor" % version,
  "com.github.j5ik2o" %% "chronos-scheduler-scala-akka-actor" % version
)
```

## 使い方

### Core

core モジュールはシンプルな同期 API を提供します。

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

pekko-actor モジュールは Apache Pekko Typed を使った非同期・ノンブロッキング API を提供します。

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

akka-actor モジュールは Akka Typed を使った同等の API を提供します。

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

## ライセンス

MIT ライセンス ([LICENSE](LICENSE) または https://opensource.org/licenses/MIT)
