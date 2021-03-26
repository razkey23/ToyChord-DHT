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
 
 * *ChordDHT/ChordDHT-master* : See Readme.md in this directory for code explanation
 * *ChordDHTPeer/ChordDHT-master*: See Readme.md in this directory for code explanation
 
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
After deploying ToyChord we wanted to test how throughput is affected from the two replication policies.Therefore, we used the options 6,7,8 from a Peer's CLI to execute inserts,queries and a combination of the two respectively from random Peer Nodes and measure the average time it took per one such request.
### Experiment 1
We used insert.txt from transactions/ and measure the throughput for the following ToyChord Deployments.
|Replication Policy| Replication Factor|
|-|-|
|Chain Replication (linearizability)|1,3,5|
|Eventual Consistency|1,3,5| 

In total we used insert.txt for 6 different setups and the results are the following :

![inserts-txt](https://github.com/razkey23/ToyChord-DHT/blob/main/resources/inserts.png?raw=true)

Obviously throughput with replication factor 1 is practically the same for both chain and eventual replication (practically there is no replication). When it comes to replication factor 3 chain replication has a decreased throughput (it takes more time per insertion) compared to eventual which is expected since in chain replication we wait until all the replicas are put in the proper Node while on eventual consistency insert process returns after the <key,pair> is inserted without caring too much about the replicas. Same applies to replication factor 5. Even though in eventual consistency the insert process returns immediately after finding the Replica Manager for the <key,value> pair , a rise in latency (decrease in throughput) was expected when increasing the replication factor due to the average traffic surge in the network.

### Experiment 2
We used query.txt from transactions/ and measure the throughput for the following ToyChord Deployments.
|Replication Policy| Replication Factor|
|-|-|
|Chain Replication (linearizability)|1,3,5|
|Eventual Consistency|1,3,5|

In total we used insert.txt for 6 different setups and the results are the following :
![inserts-txt](https://github.com/razkey23/ToyChord-DHT/blob/main/resources/query.png?raw=true)

The results shown above were expected.Results are pretty straight-forward and easy to interpret. Read throughput decreases using chain replication policy because ensuring always-fresh reads demands extra latency.On the contrary read throughput increases using the eventual consistency policy since there are more nodes carrying a replica and we do not demand reading non-stale values.

### Experiment 3
Last experiment was to check how many dirty reads will occur and the average throughput while inserting and querying <key,value> Pairs. We used requests.txt from transactions/ and measure the dirty-reads and average time (per action) for the following ToyChord Deployments.
|Replication Policy| Replication Factor|
|-|-|
|Chain Replication (linearizability)|3|
|Eventual Consistency|3| 

In total we used request.txt for 2 different setups and the results are the following :
![requests-png](https://github.com/razkey23/ToyChord-DHT/blob/main/resources/requests.png?raw=true)

![requests-stale](https://github.com/razkey23/ToyChord-DHT/blob/main/resources/dirty-reads.png?raw=true)

Obviously inserts seem slower that queries which is expected. We have 0 dirty-reads when using chain replication policy on the expense of higher latencies , and 4 dirty-reads when using eventual consistency which sacrifices absolute consistency to achieve a high query throughput. 
