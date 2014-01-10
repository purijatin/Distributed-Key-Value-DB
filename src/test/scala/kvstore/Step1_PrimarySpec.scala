package kvstore

import akka.testkit.TestKit
import akka.actor.ActorSystem
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.ImplicitSender
import akka.testkit.TestProbe
import scala.concurrent.duration._
import kvstore.Persistence.{ Persisted, Persist }
import kvstore.Replica.OperationFailed
import kvstore.Replicator.{ Snapshot }
import scala.util.Random
import scala.util.control.NonFatal
import akka.actor.ActorRef
import kvstore.Replica._
import scala.concurrent.duration._

class Step1_PrimarySpec extends TestKit(ActorSystem("Step1PrimarySpec"))
  with FunSuite
  with BeforeAndAfterAll
  with ShouldMatchers
  with ImplicitSender
  with Tools {

  override def afterAll(): Unit = {
    system.shutdown()
  }

  import Arbiter._

  test("case1: Primary (in isolation) should properly register itself to the provided Arbiter") {
    val arbiter = TestProbe()
    system.actorOf(Replica.props(arbiter.ref, Persistence.props(flaky = false)), "case1-primary")

    arbiter.expectMsg(Join)
  }

  test("case2: Primary (in isolation) should react properly to Insert, Remove, Get") {
    val arbiter = TestProbe()
    val primary = system.actorOf(Replica.props(arbiter.ref, Persistence.props(flaky = false)), "case2-primary")
    val client = session(primary)

    arbiter.expectMsg(Join)
    arbiter.send(primary, JoinedPrimary)

    client.getAndVerify("k1")
    client.setAcked("k1", "v1")
    client.getAndVerify("k1")
    client.getAndVerify("k2")
    client.setAcked("k2", "v2")
    client.getAndVerify("k2")
    client.removeAcked("k1")
    client.getAndVerify("k1")
  }

  test("case3: Primary replica till receiving a message from Arbiter, should save and react later to messages") {
    val arbiter = TestProbe()
    val primary = system.actorOf(Replica.props(arbiter.ref, Persistence.props(flaky = false)), "case3-primary")
    val client = TestProbe()

    arbiter.expectMsg(Join)
    val input = for (i <- 1 to 10000) yield randomQuery(client, primary)

    arbiter.send(primary, JoinedPrimary)
    var ans = Set[Any]()
    client.receiveWhile(max = 5 seconds) {
      case a @ OperationAck(i) => ans += a
      case GetResult(key, value, i) => ans += GetResult(key, None, i)
    }

    val set = input.toSet

    set.map(_._2) should be === ans
  }

  test("case4: Primary replica till receiving a message from Arbiter, should maintain consistency") {
    val arbiter = TestProbe()
    val primary = system.actorOf(Replica.props(arbiter.ref, Persistence.props(flaky = false)), "case4-primary")
    val client = TestProbe()

    arbiter.expectMsg(Join)

    client.send(primary, Insert(1, 1, 1))
    client.send(primary, Insert(1, 2, 2))
    client.send(primary, Insert(1, 3, 3))
    client.send(primary, Insert(1, 4, 4))
    client.send(primary, Insert(1, 5, 5))

    arbiter.send(primary, JoinedPrimary)

    client.receiveN(5)

    client.send(primary, Get(1, 6))
    client.expectMsg(GetResult(1, Some(5), 6))

  }

  private def randomInt(implicit n: Int = 100000) = (Math.random * n).toInt
  implicit private def toStr(n: Long): String = n + ""

  private def randomQuery(client: TestProbe, replica: ActorRef): (Long, OperationReply) = {
    val rnd = Math.random
    if (rnd < 0.3) {
      val key = randomInt
      val i = Insert(key + "", key + "", key)
      client.send(replica, i)
      (key, OperationAck(key))
    } else if (rnd < 0.6) {
      val key = randomInt
      val r = Remove(key + "", key)
      client.send(replica, r)
      (key, OperationAck(key))
    } else {
      val key = randomInt
      client.send(replica, Get(key + "", key))
      (key, GetResult(key + "", None, key))
    }
  }

}