package kvstore

import akka.actor.{ Props, Actor }
import scala.util.Random
import java.util.concurrent.atomic.AtomicInteger

object Persistence {
  /**
   * Object to be sent to persist the Key-Value pair in the persistent storage.
   * The client can then store it in any desired database or file for that matter.
   * The actor on success should returns [[kvstore.Persistence.Persisted]] with the same key
   * @param key Key of the pair
   * @param valueOption Value of the key
   * @param id id of the transaction
   */
  case class Persist(key: String, valueOption: Option[String], id: Long)

  /**
   * The object to be returned by the Actor when persistence is Successful.
   * @param key The key to be persisted
   * @param id id of the [[kvstore.Persistence.Persist]] object which was sent to persist
   */
  case class Persisted(key: String, id: Long)

  class PersistenceException extends Exception("Persistence failure")

  def props(flaky: Boolean): Props = Props(classOf[Persistence], flaky)
}

/**
 * A sample implementation of Persistence which does nothing. It might fail randomly.
 */
class Persistence(flaky: Boolean) extends Actor {
  import Persistence._

  def receive = {
    case Persist(key, _, id) =>
      if (!flaky || Random.nextBoolean()) sender ! Persisted(key, id)
      else throw new PersistenceException
  }

  override def postStop() {

  }
}
