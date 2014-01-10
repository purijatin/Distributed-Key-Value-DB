package kvstore

import akka.actor.{ OneForOneStrategy, Props, ActorRef, Actor }
import kvstore.Arbiter._
import scala.collection.immutable.Queue
import akka.actor.SupervisorStrategy.Restart
import scala.annotation.tailrec
import akka.pattern.{ ask, pipe }
import akka.actor.Terminated
import scala.concurrent.duration._
import akka.actor.PoisonPill
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.util.Timeout
import akka.actor.Terminated
import scala.util.Random
import scala.language.postfixOps

object Replica {
  sealed trait Operation {
    def key: String
    def id: Long
  }
  case class Insert(key: String, value: String, id: Long) extends Operation
  case class Remove(key: String, id: Long) extends Operation
  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply
  case class OperationAck(id: Long) extends OperationReply
  case class OperationFailed(id: Long) extends OperationReply
  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  private[Replica] case class Done(id: Long, sender: ActorRef)

  /**
   * To validate response
   */
  private[Replica] case class ValidateResponse(seq: Long, times: Int)

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {
  import Replica._
  import Replicator._
  import Persistence._
  import context.dispatcher

  /**
   * Key Value pair mapping
   */
  var kv = Map.empty[String, String]
  /**
   * A map from secondary replicas to replicators
   */
  var secondaries = Map.empty[ActorRef, ActorRef]
  /**
   * the current set of replicators
   */
  var replicators = Set.empty[ActorRef]
  def newStorage = context.actorOf(persistenceProps, "Storage_" + Math.abs(Random.nextInt))

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: PersistenceException => SupervisorStrategy.Restart
  }

  arbiter ! Join

  var storage: ActorRef = newStorage

  def receive = {
    case any @ _ =>
      context.become(receiver(List[(Any, ActorRef)]()))
      self.tell(any, sender)
  }

  /**
   * Primary Receive for the Replica. Based on the message received by Arbiter,
   * it decides if the replica should act as Primary or Secondary one.
   *
   * Meanwhile if any other message is received before, it is stored and no action is taken.
   * Only on receiving the status from [[kvstore.Arbiter]], all the messages received yet
   * are executed.
   */
  def receiver(toAdd: List[(Any, ActorRef)]): Receive = {
    case JoinedPrimary =>
      toAdd.reverseIterator.foreach { case (x, y) => self.tell(x, y) }
      context.become(leader)
    case JoinedSecondary =>
      toAdd.reverseIterator.foreach { case (x, y) => self.tell(x, y) }
      context.become(replica)
    case other @ _ => context.become(receiver((other, sender) :: toAdd))
  }

  /**
   * Map of Id to (senderRef, Persist).
   * This is used to validate if persistence was successful or not
   */
  var acks = Map.empty[Long, (ActorRef, Persist)]

  /**
   * Map of id to (senderRef, replicators to which message is sent to replicate)
   * This is used to validate if secondary-node operations were successful or not
   */
  var childAcks = Map.empty[Long, (ActorRef, Set[ActorRef])]

  /**
   * Receive for Primary Node
   */
  val leader: Receive = {
    case Replicas(replicas) => {
      val removed = secondaries.keySet -- replicas.filter(x => x != self)
      removed.map { x =>
        val rep = secondaries(x)
        val inProcess = childAcks.filter(x => x._2._2 contains rep)
        inProcess.foreach { f => self.tell(Replicated("", f._1), rep) }
        context.stop(rep)
        secondaries -= x
        replicators -= rep
      }

      var newOnes = Set[ActorRef]()
      replicas.filter(x => x != self).map { newR =>
        secondaries.get(newR) match {
          case Some(i) => //do nothing
          case None => {
            val replicator = context.actorOf(Replicator.props(newR), "Replicator_" + Math.abs(Random.nextInt))
            secondaries += (newR -> replicator)
            replicators += replicator
            newOnes += replicator
          }
        }
      }

      kv.foreach { x =>
        val random = nextId
        newOnes.foreach(_ ! Replicate(x._1, Some(x._2), random))
        childAcks += (random -> ((self, newOnes)))
      }

    }

    case Insert(key, value, id) =>
      persistPrimary(key, Some(value), id)
      val rep = Replicate(key, Some(value), id)
      replicators.map(x => x ! rep)
      childAcks += (id -> ((sender, replicators)))

    case Remove(key, id) =>
      persistPrimary(key, None, id)
      val rep = Replicate(key, None, id)
      replicators.map(x => x ! rep)
      childAcks += (id -> ((sender, replicators)))

    case Get(key, id) =>
      sender ! GetResult(key, kv.get(key), id)

    case Terminated(ref) =>
      context.unwatch(storage)
      storage = newStorage
      context.watch(storage)

    case ValidateResponse(seq, times) => validateResponse(seq, times)

    case p @ Persisted(key, id) =>
      acks.get(id) match {
        case Some((ref, x)) =>
          acks -= id
          isDone(Done(x.id, ref))
        case None => //throw new IllegalStateException("ActorRef for seq: " + id + " not found")
      }

    case r @ Replicated(key, id) =>
      childAcks.get(id) match {
        case Some(seq) =>
          childAcks -= id
          childAcks += (id -> (seq._1, seq._2 - sender))
          isDone(Done(id, seq._1))
        case None =>
        //throw new IllegalStateException("This cannot happen: " + r + " ")
      }
  }

  /**
   * Checks if it is done and the client can get notification. Else does nothing
   */
  private def isDone(done: Done) {
    val Done(id, ref) = done
    if (!acks.contains(id) && childAcks(id)._2.isEmpty) {
      ref ! OperationAck(id)
    }

  }

  var retryRemove = Set[Long]()

  /**
   * Action when validateResponse message is received.
   * It checks if it has been primary node has been persisted. If successively failure then sends Remove message to all children. Else waits for one more time
   * If primary node has been
   */
  private def validateResponse(seq: Long, times: Int) {
    acks.get(seq) match {

      case None => if (childAcks(seq)._2.isEmpty) {
        //do nothing
      } else {
        if (times < 10)
          context.system.scheduler.scheduleOnce(100 milliseconds, self, ValidateResponse(seq, times + 1))
        else childAcks(seq)._1 ! OperationFailed(seq)
      }
      case Some((ref, rep)) =>
        if (times < 10) {
          storage ! rep
          context.system.scheduler.scheduleOnce(100 milliseconds, self, ValidateResponse(seq, times + 1))
        } else {
          if (retryRemove.contains(rep.id)) {
            ref ! OperationFailed(rep.id)
            retryRemove -= rep.id
          } else {
            self.tell(Remove(rep.key, rep.id), ref)
            retryRemove += rep.id
          }
        }
    }
  }

  /**
   * Try persisting. It schedules a message to itself after 100 msec to check if operation was succesful or not
   */
  def persistPrimary(key: String, opt: Option[String], id: Long) {
    opt match {
      case Some(x) => kv += ((key, x))
      case None => kv -= key
    }
    storage ! Persist(key, opt, id)
    context.system.scheduler.scheduleOnce(100 milliseconds, self, ValidateResponse(id, 1))
    acks += ((id, (sender, Persist(key, opt, id))))

  }

  /**
   * Receive for replica role
   */
  val replica: Receive = {
    case Get(key, id) =>
      sender ! GetResult(key, kv.get(key), id)
    case s @ Snapshot(key, value, seq) => snapshot(s)
    case Persisted(key, seq) => acks.get(seq) match {
      case Some((ref, x)) =>
        ref ! SnapshotAck(key, seq)
        acks -= seq
      case None => // throw new IllegalStateException("ActorRef for seq: " + seq + " not found " + acks)
    }

    case ValidateResponse(seq, _) =>
      acks.get(seq) match {
        case None => //do nothing
        case Some((ref, rep)) =>
          storage ! rep
          context.system.scheduler.scheduleOnce(100 milliseconds, self, ValidateResponse(seq, 1))
      }
    case x @ _ => println("Unknown message " + x)
  }

  var expSeq = 0L;

  def snapshot(s: Snapshot) {
    val Snapshot(key, value, seq) = s
    if (seq > expSeq) {
      //do nothing
    } else if (seq < expSeq) {
      sender ! SnapshotAck(key, seq)
    } else {
      value match {
        case Some(x) => kv += ((key, x))
        case None => kv -= key
      }
      expSeq = Math.max(expSeq, seq + 1)
      storage ! Persist(key, value, seq)
      context.system.scheduler.scheduleOnce(100 milliseconds, self, ValidateResponse(seq, 1))
      acks += ((seq, (sender, Persist(key, value, seq))))
    }

  }

  /**
   * Returns next long id which can be safely used for internal requests
   */
  private def nextId: Long = {
    var random = Random.nextLong
    while (childAcks.contains(random)) {
      random = Random.nextLong
    }
    -1 * Math.abs(random)
  }

}
