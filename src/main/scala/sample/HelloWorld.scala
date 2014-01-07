package sample

import akka.actor.Actor
import kvstore.Arbiter
import akka.actor.ActorRef
import kvstore.Replica
import kvstore.Persistence
import kvstore.Replica._
import akka.actor.ActorSystem
import akka.actor.Props

object HelloWorld {
  def main(args: Array[String]) {
    val system = ActorSystem("Main")
    val arbiter = system.actorOf(Props[Arbiter])
    val main = system.actorOf(Props(new Main2(arbiter)))
    main ! "send"
  }
}

class Main2(arbiter: ActorRef) extends Actor {
  import kvstore.Arbiter._

  val replica = context.actorOf(Replica.props(arbiter, Persistence.props(flaky = false)))

  def receive = {
    case "send" =>
      replica ! Insert("a", "a", 0)
      replica ! Get("a", 1)
    case GetResult(key, opt, id) => 
      println(s"$self GetResult: ($key, $opt)")
      context.system.shutdown()
    case x @ _ => println(x)
  }

}