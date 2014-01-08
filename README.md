This is a micro but stable implementation of a distributed Key-Value pair database. Current implementation is based on Akka Actor's and hence can it only be used using message communication with Actors. This constraint might be removed in future.

Please refer [wiki](https://github.com/purijatin/Distributed-Key-Value-DB/wiki/Overview) for more information.

Binaries
======

For `sbt` add the below in your `build.sbt` :
	
	resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

	libraryDependencies += "com.jatinpuri" %% "kvstore" % "1.0.0-SNAPSHOT"

For `Maven` (link will be updated in a day)

External `Jar` can be downloaded from [repo](https://oss.sonatype.org/content/repositories/snapshots/com/jatinpuri/kvstore_2.10/1.0.0-SNAPSHOT/). Though you will manually have to download the dependencies. (Will update the part with link to all necessory links in future)



Summary
=======


The current version system includes a primary node, which is responsible for replicating all changes to a set of secondary nodes where secondary nodes might join and leave at arbitrary times. Internally all the changes are persisted locally both by primary or secondary node. (Persistence is loosely coupled and can be done using any SQL or NOSQL based database or file for that matter).

Clients contacting the primary node directly can use all operations on the key-value store, while clients contacting the secondaries can only use lookups.

The two set of operations are:

Update Commands
---------------
Insert(key, value, id) - This message instructs the primary to insert the (key, value) pair into the storage and replicate it to the secondaries.

Remove(key, id) - This message instructs the primary to remove the key (and its corresponding value) from the storage and then remove it from the secondaries.

A successful Insert or Remove results in a reply to the client in the form of an OperationAck(id) message where the id field matches the corresponding id field of the operation that has been acknowledged.
A failed Insert or Remove command results in an OperationFailed(id) reply. A failure is defined as the inability to confirm the operation within "1 second". See the wiki for more details

Lookup
-------
Get(key, id) - Instructs the replica to look up the "current" (what current means is described in detail in the next section) value assigned with the key in the storage and reply with the stored value.

A Get operation results in a GetResult(key, valueOption, id) message where the id field matches the value in the id field of the corresponding Get message. The valueOption field contains None if the key is not present in the replica or Some(value) if a value is currently assigned to the given key in that replica.

System Behavior - Consistency Guarantees
----------------------------------------
Ordering is maintained.

If the following command is sent to the primary replica, waiting for successful acknowledgement of each operation before proceeding with the next:

	Insert("key1", "a")
	Insert("key2", "1")
	Insert("key1", "b")
	Insert("key2", "2")

1) Ordering is guaranteed for clients contacting the primary replica:

A second client reading directly from the primary will not see:

	key1 containing b and then containing a (since a was written before b for key1)
	key2 containing 2 and then containing 1 (since 1 was written before 2 for key2)

2) Ordering is guaranteed for clients contacting the secondary replica:

For a second client reading from one of the secondary replicas, the exact same requirements apply as if that client was reading from the primary, with the following addition:

It is guaranteed that a client reading from a secondary replica will eventually see the following (at some point in the future):

key1 containing b
key2 containing 2

3) Ordering guarantees for clients contacting different replicas

If a second client asks different replicas for the same key, it may observe different values during the time window when an update is disseminated. The client asking for key1 might see:

	Answer b from one replica
	and subsequently answer a from a different replica

Eventually all reads will result in the value b if no other updates are done on key1. Eventual consistency means that given enough time, all replicas settle on the same view. 


Durability & Persistence
------------------------

Whenever the primary replica receives an update operation (either Insert or Remove) it replies with an OperationAck(id) or OperationFailed(id) message, which is sent at most 1 second after the update command was processed. 

A positive OperationAck reply is sent as soon as the following is successful:

	1) The Key-Value pair is persisted (for backup)
	2) Change has been replicated to all the secondary replicas and secondary replicas have acknowledged the replication of the update and persisted locally

Persistence trait has been implemented using which the data can be persisted using any SQL or NOSQL based database or file for that matter.

See Wiki for more information.
