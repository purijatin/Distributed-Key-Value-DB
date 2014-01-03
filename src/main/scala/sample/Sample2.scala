package sample

import akka.actor.Actor
import kvstore.Arbiter
import akka.actor.ActorRef
import kvstore.Replica
import kvstore.Persistence
import kvstore.Replica._
import akka.actor.ActorSystem
import akka.actor.Props

object Sample2 {
  def main(args: Array[String]) {
    val system = ActorSystem("Main")
    val arbiter = system.actorOf(Props[Arbiter])
    val main = system.actorOf(Props(new Main2(arbiter)))
    main ! Insert("a", "a", 0)
    main ! Get("a", 1)

  }
}

class Main2(arbiter: ActorRef) extends Actor {
  import kvstore.Arbiter._

  val replica = context.actorOf(Replica.props(arbiter, Persistence.props(flaky = false)))

  def receive = {
    case g @ Get(key, id) => replica ! g
    case GetResult(key, opt, id) => println(s"$self GetResult: ($key, $opt)")
    case i: Insert =>
      println(i)
      replica ! i
    case x @ _ => println(x)
  }


}