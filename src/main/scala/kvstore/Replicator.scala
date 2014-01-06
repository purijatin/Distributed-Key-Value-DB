package kvstore

import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import scala.concurrent.duration._
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import scala.language.postfixOps

object Replicator {
  case class Replicate(key: String, valueOption: Option[String], id: Long)
  case class Replicated(key: String, id: Long)

  case class Snapshot(key: String, valueOption: Option[String], seq: Long)
  case class SnapshotAck(key: String, seq: Long)

  /**
   * To validate response
   */
  private[Replicator] case class ValidateRes(seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {
  import Replicator._
  import Replica._
  import context.dispatcher

  /**
   *  Map from sequence number to pair of sender and request
   */
  var acks = Map.empty[Long, (ActorRef, Replicate)]

  var _seqCounter = 0L
  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

  def receive: Receive = {
    case r @ Replicate(key, opt, id) =>
      val seq = nextSeq
      acks += ((seq, (sender, r)))
      replica ! Snapshot(key, opt, seq)
      context.system.scheduler.scheduleOnce(100 milliseconds, self, ValidateRes(seq))

    case SnapshotAck(key, seq) => acks.get(seq) match {
      case Some((_sender, rep)) =>
        acks -= seq
        _sender ! Replicated(key, rep.id)
      case None => //throw new IllegalStateException("No (sender, replicate) found for seq:" + seq+" key:"+key+" acks:"+acks)
    }

    case ValidateRes(seq) => acks.get(seq) match {
      case None => //do nothing. All izz well
      case Some((_sender, rep)) =>
        //try again
        replica ! Snapshot(rep.key, rep.valueOption, seq)
        context.system.scheduler.scheduleOnce(100 milliseconds, self, ValidateRes(seq))
    }
  }

}
