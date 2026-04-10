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
val version = "..."

libraryDependencies += Seq(
  "com.github.j5ik2o" %% "chronos-scheduler-scala-core" % version,
  "com.github.j5ik2o" %% "chronos-scheduler-scala-akka-actor" % version // Scala 2.13 のみ
)
```

## 使い方

### Core

core モジュールはシンプルな同期 API を提供します。

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

actor モジュールは Akka Typed を使った非同期・ノンブロッキング API を提供します。

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

## ライセンス

MIT ライセンス ([LICENSE](LICENSE) または https://opensource.org/licenses/MIT)
