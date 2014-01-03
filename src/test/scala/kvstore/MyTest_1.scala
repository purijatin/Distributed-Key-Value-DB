package kvstore

import akka.actor.ActorSystem
import akka.testkit.{ TestProbe, ImplicitSender, TestKit }
import kvstore.Arbiter.{ Replicas, JoinedSecondary, JoinedPrimary, Join }
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{ BeforeAndAfterAll, FunSuite }
import kvstore.Persistence.Persist
import scala.concurrent.duration._

class MyTest_1 extends TestKit(ActorSystem("MyTest1"))
  with FunSuite
  with BeforeAndAfterAll
  with ShouldMatchers
  with ImplicitSender
  with Tools {

  override def afterAll() { system.shutdown() }
  test("case1: Key should be removed from secondary if persistence fails in primary") {
    val arbiter = TestProbe()
    val primaryPer = TestProbe()

    val primary = system.actorOf(
      Replica.props(arbiter.ref, probeProps(primaryPer)), "case1-primary")
    arbiter.expectMsg(Join)
    arbiter.send(primary, JoinedPrimary)

    val secondary = system.actorOf(
      Replica.props(arbiter.ref, Persistence.props(flaky = false)), "case1-secondary")
    arbiter.expectMsg(Join)
    arbiter.send(secondary, JoinedSecondary)

    arbiter.send(primary, Replicas(Set(primary, secondary)))

    val client = session(primary)
    val child =  session(secondary)
    client.set("1","1")
    primaryPer.expectMsg(Persist("1", Some("1"),0))
    Thread.sleep(800)
    
    client.get("1") should be === Some("1")
    child.get("1") should be === Some("1")
    Thread.sleep(2000)
    child.get("1") should be === None

  }
}