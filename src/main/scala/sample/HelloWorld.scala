package sample

import akka.actor.Actor
import kvstore.Arbiter
import akka.actor.ActorRef
import kvstore.Replica
import kvstore.Persistence
import kvstore.Replica._
import akka.actor.ActorSystem
import akka.actor.Props

/**
 * Hello World example:
 * It creates a arbiter with one Primary Node
 *
 */
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
  /**
   * On doing below, the replica will automatically send a call to arbiter to add itself in the cluster.
   * Because this is the first actor to Join arbiter, hence it becomes the Primary Node 
   */
  val replica = context.actorOf(Replica.props(arbiter, Persistence.props(flaky = false)))

  def receive = {
    case "send" =>
      replica ! Insert("a", "a", 0) //send an Insert message
      replica ! Get("a", 1) //Get the value of key "a"
    case GetResult(key, opt, id) =>
      println(s"$self GetResult: ($key, $opt)")
      context.system.shutdown()
    case x @ _ => println(x) //Any Acknowledgement for Insert in this case
  }

}