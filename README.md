# ToyChord-DHT
## Project Summary
This project is the implementation of ToyChord , it's a simplified / lightweight version of Chord. The main purpose of Toychord is to create a Chord-like ring of nodes. Every Chord-node  should be able to do the following actions :
* Have a distinct id after applying a hash function to his IP and Open Socket
* Join the ring 
* Depart from the ring 
* Keep a Finger Table for routing requests 
* Insert a <key,value> Pair (<String,String>) after applying a hash function
* Query a <key,value> Pair 
* Update a <key,value> Pair 
* Delete a <key,value> Pair 
* Keep a Finger Table for routing 

Note: Obviously a Chord-node Joining or Departing from the ring shouldn't result in invalid Chord state. Every Chord-node should be responsible for the proper set of keys at all times.

On top of the functionalities listed above ,we also implemented two replication policies  for the inserted <key,value> Pairs to choose from.
* Chain Replication Policy: During the deployment of the ToyChord a Chain Replication Policy can be applied. After a <key,value> Pair is inserted into the ring the pair is also copied into the next k successors (where k is also defined during deployment) .The write(i.e. insertion) process is returned from the first node of the chain after the replicas have been  created. Querying a <key,value> Pair returns from the last node of the chain, meaning that always the most "fresh/recent" insertion is returned.
* Eventual Consistency Policy: During the deployment of the ToyChord an Eventual Consistency Replication Policy can be applied. After a <key,value> Pair is inserted into the ring the pair is also copied into the next k successors (where k is also defined during deployment) . The main difference between Eventual Consistency Policy and the Chain Replication is that the write process returns right after the <key,value> Pair is inserted into the first ChordNode. (It doesn't wait until all the replicas are created). Eventual consistency is different from Chain Replication in regards to the Querying process as well.Querying a <key,value> Pair can return from any ChordNode holding a replica resulting in reading potential stale Values (Dirty-Reads).

## ToyChord Code Explanation
For the implementation of ToyChord we used Java RMI. A bootstrap node is used whose ipaddress is available to every ChordNode (server-like behavior).The bootstrap node binds every Peer-Node to the RMI Registry during the admission of the Peer-node to the ring, so that every Peer node can lookup other Peers.

Two maven projects are included in this repo ,one for BootstrapNode and the other for the Peer nodes. The source code of BootstrapNode is similar to ChordNodePeer. The main difference is in pom.xml . Bootstrap node's code has  BootstrapnodeImpl as main class , while ChordNodePeer code has ChordNodeImpl as main class.
 
 *ChordDHT/ChordDHT-master* : See Readme.md in this directory for code explanation
 *ChordDHTPeer/ChordDHT-master*: See Readme.md in this directory for code explanation
 
## ToyChord Deployment
 
### Prerequisites
* Apache Maven
* Java 8+

### Instructions
#### Deploy Locally:
1. Boostrap Node : 
*  Execute the following Command in *ChordDHT/ChordDHT-master* 
```mvn clean compile assembly:single```
* Execute the following Command in /target/classes of *ChordDHT/ChordDHT-master*
```java -jar ../ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar```

2. ChordPeer Node:
*  Execute the following Command in  *ChordDHTPeer/ChordDHT-master* 
```mvn clean compile assembly:single```
* Execute the following Command in /target/classes of *ChordDHTPeer/ChordDHT-master*
```java -jar ../ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar [myIP] [hostIP] [replication_factor] [type of replication]```
*[type of replication]*: leave null for chai replication
 Example for local deployment:
```java -jar ../ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 127.0.0.1 1``` 

#### Deploy on Local Network (used in 5 VMs in private network):
1. Boostrap Node : 
* Go to BootstrapNodeImpl and line32 ,change hostip to BootstrapNode's local IP
*  Execute the following Command in *ChordDHT/ChordDHT-master* 
```mvn clean compile assembly:single```
* Execute the following Command in /target/classes of *ChordDHT/ChordDHT-master*
```java -jar ../ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar```

2. ChordPeer Node:
*  Execute the following Command in  *ChordDHTPeer/ChordDHT-master* 
```mvn clean compile assembly:single```
* Execute the following Command in /target/classes of *ChordDHTPeer/ChordDHT-master*
```java -jar ../ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar [myIP] [hostIP] [replication_factor] [type of replication]```
*[type of replication]*: leave null for chain replication
 Example for local deployment in okeanos:
```java -jar ../ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar 192.168.0.5 192.168.0.1 3 eventual``` 

### Use ToyChord
Now on every Peer-node we are equipped with a command line tool where we have the following options:

1. Print Finger Table
2. Get Key (Use special character * to get all the <key,value> Pairs)
3. Insert Key
4. Delete Key
5. Display Data Stored in current node
6. Insert Elements (Insert.txt from transcations/)
7. Search Elements (Query.txt from transactions/)
8. Execute Requests (Requests.txt from transactions/)
9. Leave Ring
10. Overlay (See the Ring Topology)

## ToyChord Testing
