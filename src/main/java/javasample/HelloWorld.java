package javasample;

import kvstore.Arbiter;
import kvstore.Persistence;
import kvstore.Replica;
import kvstore.Replica.Get;
import kvstore.Replica.GetResult;
import kvstore.Replica.Insert;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.*;

/**
 * Hello World example: It creates an arbiter with one Primary Node and later
 * adds and element to it
 * 
 */
public class HelloWorld {
	public static void main(String[] args) {
		ActorSystem system = ActorSystem.create("Main");
		final ActorRef arbiter = system.actorOf(Props.create(Arbiter.class));

		ActorRef main = system.actorOf(Props.create(Main.class, arbiter));
		main.tell("send", null);
	}
}

class Main extends UntypedActor {

	final ActorRef arbiter;

	final ActorRef replica;

	public Main(ActorRef arbiter) {
		this.arbiter = arbiter;
		/**
		 * On doing below, the replica will automatically send a call to
		 * arbiter to add itself in the cluster. Because this is the first
		 * actor to Join arbiter, hence it becomes the Primary Node
		 */
		this.replica = getContext().actorOf(
				Replica.props(arbiter, Persistence.props(false)));
	}

	public void onReceive(Object message) {
		if (message.equals("send")) {
			// send an Insert message
			replica.tell(new Insert("a", "a", 0), getSelf());
			// Get the value of key "a"
			replica.tell(new Get("a", 1), getSelf());

		} else if (message instanceof GetResult) {
			GetResult get = (GetResult) message;
			System.out.println(getSelf() + " GetResult: (" + get.key()
					+ ", " + get.valueOption() + ")");
			getContext().system().shutdown();
		} else {
			System.out.println(message);
		}
	}

}

