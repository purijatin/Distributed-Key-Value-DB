package sample

import akka.actor.Actor
import kvstore.Arbiter
import akka.actor.ActorRef
import kvstore.Replica
import kvstore.Persistence
import kvstore.Replica._
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.LoggingReceive

object Sample1 {
  def main(args: Array[String]) {
    val system = ActorSystem("Main")
    val arbiter = system.actorOf(Props[Arbiter])

    val diff = 50000 * 3

    var set = Set[ActorRef]()

    val main = system.actorOf(Props(new Main(arbiter, 0, diff)))
    main ! "insert"
    
    Thread.sleep(5000)
    
    set += main
    for (i <- 1 until 10) {
      set += system.actorOf(Props(new Main(arbiter, i, 0)))
    }

    Thread.sleep(10000)
    println("sleep over")

    set.map(x => x ! Get(diff / 20 + "", Int.MaxValue))
    //main ! Get(diff * 2 + "", 34231)
    Thread.sleep(5000)
    main ! Insert("a","a",0)
    //main ! Get("a",2)
    set.map(_ ! Get("a",1))
    Thread.sleep(5000)
    system.shutdown
  }
}

class Main(arbiter: ActorRef, start: Long = 0, end: Long = 0) extends Actor {
  import kvstore.Arbiter._

  val replica = context.actorOf(Replica.props(arbiter, Persistence.props(flaky = false)), "start"+start)

  def receive = LoggingReceive{
    case g @ Get(key, id) => replica ! g
    case GetResult(key, opt, id) => println(s"$self GetResult: ($key, $opt)")
    case i:Insert => 
      println(i)
      replica ! i
    case "insert" =>
      for (i <- start until end) {
        replica ! Insert(i + "", i + "", i)
      }
      println("Sending done: "+(end-start))

    case x @ _ => //println(x)
  }
}