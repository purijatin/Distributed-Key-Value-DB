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

/**
 * In this example: 
 * 1) We create a Primary Node
 * 2) Add 5000 key value pairs as (i,i) with i from 0 to 4999
 * 3) Add 4 Secondary Replicas
 * 4) Send a call to all 5 nodes to get pair of key "500"
 * 5) Send a call to all 5 nodes to get pair of key "4999"
 * 
 * Above after step-3, automatically all the existing key-value pairs are added in the Secondary Replica's.
 * Later doing step-3, eventually all the replica's should return the same value.  
 *  
 */
object Sample1 {
  def main(args: Array[String]) {
    val system = ActorSystem("Main")
    val arbiter = system.actorOf(Props[Arbiter])

    val diff = 5000

    var set = Set[ActorRef]()

    val main = system.actorOf(Props(new Main(arbiter, 0, diff)))
    
    Thread.sleep(500) //wait for main to become PrimaryNode before adding other replicas
    
    main ! "insert" //send a call to Primary Node to insert some key-value pairs
    
    set += main //add 4 Secondary Replicas
    for (i <- 1 to 4) {
      set += system.actorOf(Props(new Main(arbiter, i, 0)))
    }

    //Eventually all the replicas will hold the same value, lets wait for some-time till it gets consistent.
    //Though the below sleep is not required, but some replica might remain in in-consistent state
    Thread.sleep(5000)
    println("sleep over")

    set.map(x => x ! Get(400 + "", Int.MaxValue))//check if all the replica contain key "400"
    set.map(x => x ! Get(4999 + "", Int.MaxValue))//check if all the replica contain key "4999"

    Thread.sleep(5000)
    system.shutdown
  }
}

class Main(arbiter: ActorRef, start: Long = 0, end: Long = 0) extends Actor {
  import kvstore.Arbiter._

  val replica = context.actorOf(Replica.props(arbiter, Persistence.props(flaky = false)), "start" + start)

  def receive = LoggingReceive {
    case g @ Get(key, id) => replica ! g
    case GetResult(key, opt, id) => println(s"$self GetResult: ($key, $opt)")
    case i: Insert =>
      replica ! i

    case "insert" =>
      for (i <- start until end) {
        replica ! Insert(i + "", i + "", i)
      }
      println("Sending done: " + (end - start))

    case x @ _ => println(x)
  }
}