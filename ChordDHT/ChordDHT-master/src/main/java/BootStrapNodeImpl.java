import java.math.BigInteger;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.rmi.registry.LocateRegistry;
import java.io.File;  
import java.io.FileNotFoundException; 
import java.util.Scanner; 
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;



/**
 * This class serves as the starting point for the BootStrap server and
 * has functions to assist in node joins and departure while also serves
 * the purpose of collecting metrics for the improvements made.
 */

public class BootStrapNodeImpl extends UnicastRemoteObject implements BootStrapNode {
    private static final long serialVersionUID = 10L;

    private static String hostipaddress="127.0.0.1";
    private static Logger log = null;
    private static int m = 11;

    /**
     * Maximum number of permitted nodes in the Chord Ring
     */
    private static int maxNodes = (int) Math.pow(2.0, (long) m);

    /**
     * Variables to identify the nodes in the Chord Ring
     */
    private static HashMap<Integer, NodeInfo> nodes = new HashMap<>();
    private static int noOfNodes = 0;
    private static ArrayList<NodeInfo> nodeList = new ArrayList<>();
    private static ArrayList<Integer> nodeIds = new ArrayList<>();

    /**
     * Dummy constructor
     *
     * @throws RemoteException Due to RMI.
     */
    public BootStrapNodeImpl() throws RemoteException {
        System.out.println("Bootstrap Node created");
    }

    /**
     * This function is the starting point for the BootStrap server
     *
     * @param args Variable length command line arguments
     * @throws RemoteException Due to RMI.
     */
    public static void main(String[] args) throws Exception {
        PatternLayout layout = new PatternLayout();
        String conversionPattern = "%-7p %d [%t] %c %x - %m%n";
        layout.setConversionPattern(conversionPattern);

        // creates file appender
        FileAppender fileAppender = new FileAppender();

        fileAppender.setFile("logs/bootstrap.log");
        fileAppender.setLayout(layout);
        fileAppender.activateOptions();

        //logger assign
        log = Logger.getLogger(BootStrapNodeImpl.class);
        log.addAppender(fileAppender);
        log.setLevel(Level.DEBUG);

        log.info("\n## Creating bootstrap node instance ##\n");

		System.setProperty("java.rmi.server.hostname",hostipaddress);
		LocateRegistry.createRegistry(1099);
        try {
            BootStrapNodeImpl bnode = new BootStrapNodeImpl();
	        Naming.rebind("rmi://"+hostipaddress+"/ChordRing",bnode);
          
            noOfNodes = 0;
            System.out.println("Waiting for nodes to join or leave the Chord Ring");
            System.out.println("Number of nodes in Chord Ring: " + noOfNodes + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<NodeInfo> addNodeToRing(String ipaddress, String port) throws RemoteException {
        synchronized (this) {
            if (nodeList.size() == maxNodes) {
                System.out.println("No more node joins allowed as Chord network has reached it capacity");
                return null;
            } else {
                ArrayList<NodeInfo> result = new ArrayList<>();
                ArrayList<Integer> copy = nodeIds;
                noOfNodes++;
                int nodeID = -1;
                String timeStamp;
                ArrayList<Integer> randomIds;//Stores the set of random Ids generated for the network proximity method
                ArrayList<Integer> succIds;
                randomIds = new ArrayList<>();
                succIds = new ArrayList<>();

                int i;
                int freeZoneCnt = 0;
                for (i = 0; i < m; i++) {//For each zone in the ring
                    boolean isFilled = isZoneFilled(i);
                    int c=0;
                    if (!isFilled) {//If zone is not completely filled, then generate a random ID in the corresponding zone range
                        freeZoneCnt++;
                        boolean repeat = true;
                        while (repeat) {
                            timeStamp = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS").format(new Date());
                            String temp = timeStamp.substring(16, timeStamp.length()-1);
                            //System.out.println(temp);
                            int k=i+1;
                         
                            try {
                                nodeID = generate_ID(ipaddress + port + temp, maxNodes);
                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }
                           int pivot=(int) Math.pow(2.0, (long) m)/10;
                           int lowerbound=i*pivot;
                           int upperbound=(i+1)*pivot;                           
                           if (nodeID >= lowerbound && nodeID < upperbound && nodeIds.indexOf(nodeID) == -1 && randomIds.indexOf(nodeID) == -1) {
                            //if (nodeID >= i * m && nodeID < (i + 1) * m && nodeIds.indexOf(nodeID) == -1 && randomIds.indexOf(nodeID) == -1) {
                                System.out.println("Got Here" + nodeID);
                                repeat = false;
                            }
                            if(c==20) repeat=false;
                            //System.out.println(nodeID);
                
                        }
                        if(c==20) continue;
                        else {
                            System.out.println("----");
                            randomIds.add(nodeID);
                            copy.add(nodeID);
                            Collections.sort(copy);
                            succIds.add(nodeIds.get((nodeIds.indexOf(nodeID) + 1) % noOfNodes));
                            copy.remove(Integer.valueOf(nodeID));
                        }
                    }
                }
        
                //If only one zone was found to be free directly add to the nodeIds list
                if (freeZoneCnt == 1) {
                    NodeInfo ni = new NodeInfo(ipaddress, port, nodeID);
                    nodes.put(nodeID, ni);
                    nodeIds.add(nodeID);
                    nodeList.add(ni);
                    System.out.println("New node added to ring with ID: " + nodeID);
                } else {//Calculate the latency for each probable ID and choose the best
                    int k;
                    long minLatency = Long.MAX_VALUE;
                    for (k = 0; k < randomIds.size(); k++) {
                        randomIds.get(k);
                        int succ_id = succIds.get(k);
                        long startTime = System.currentTimeMillis();
                        ChordNode c = null;
                        try {
                            c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+ipaddress+"_"+port);
                        } catch (NotBoundException | MalformedURLException e) {
                            e.printStackTrace();
                        }
                        assert c != null;
                        c.makeCall(nodes.get(succ_id));
                        long endTime = System.currentTimeMillis();
                        long timetaken = endTime - startTime;
                        if (timetaken < minLatency) {
                            minLatency = timetaken;
                        }
                    }
                    int rnd = new Random().nextInt(randomIds.size());
                    nodeID = randomIds.get(rnd);
                    NodeInfo ni = new NodeInfo(ipaddress, port, nodeID);
                    nodes.put(nodeID, ni);
                    nodeIds.add(nodeID);
                    nodeList.add(ni);
                    System.out.println("New node added to ring with ID: " + nodeID);
                }


                Collections.sort(nodeIds);
                int successor = nodeIds.get((nodeIds.indexOf(nodeID) + 1) % noOfNodes);//Get the successor node
                System.out.println("Successor for new node: " + successor);
                int predecessor = nodeIds.get((nodeIds.indexOf(nodeID) - 1 + noOfNodes) % noOfNodes);//Get the predecessor node
                System.out.println("Predecessor for new node: " + predecessor);

                result.add(nodes.get(nodeID));
                result.add(nodes.get(successor));
                result.add(nodes.get(predecessor));

                return result;
            }
        }
    }

    public void removeNodeFromRing(NodeInfo n) throws RemoteException {
        synchronized (this) {
            if (n == null || nodes.get(n.nodeID) == null)
                return;
            nodeList.remove(nodes.get(n.nodeID));
            System.out.println("Updated node list");
            nodeIds.remove(Integer.valueOf(n.nodeID));
            System.out.println("Updated node ID list");
            nodes.remove(n.nodeID);
            noOfNodes--;
            System.out.println("Node " + n.nodeID + " left Chord Ring");
            System.out.println("Number of nodes in Chord Ring: " + noOfNodes);
            displayNodesInRing();
        }
    }

    public NodeInfo findNewSuccessor(NodeInfo n, NodeInfo dead_node) throws RemoteException {
        NodeInfo succ;
        System.out.println("Received update from node " + n.nodeID + " that node " + dead_node.nodeID + " is dead.");
        try {
            removeNodeFromRing(dead_node);
        } catch (Exception e) {
            System.out.println("There is some problem with Removing dead node " + dead_node.nodeID + ": " + e.getMessage());
        }

        int successor = nodeIds.get((nodeIds.indexOf(n.nodeID) + 1) % noOfNodes);
        System.out.println("Assigning new successor " + successor + " to node " + n.nodeID);
        succ = nodes.get(successor);
        return succ;
    }

    public void acknowledgeNodeJoin(int nodeID) throws RemoteException {
        synchronized (this) {
            System.out.println("Join acknowledge: New node joined Chord Ring with identifier " + nodeID);
            System.out.println("Number of nodes in Chord Ring: " + noOfNodes);
            displayNodesInRing();
        }
    }

    public void displayNodesInRing() throws RemoteException {
        Iterator<NodeInfo> i = nodeList.iterator();
        System.out.println("*********************List of nodes in the ring********************");
        while (i.hasNext()) {
            NodeInfo ninfo = i.next();
            System.out.println("Node ID: " + ninfo.nodeID);
            System.out.println("Node IP: " + ninfo.ipaddress);
            System.out.println("Node Port: " + ninfo.port);
            System.out.println("******************\n");
        }
    }

    public int getNodesInRing() throws RemoteException {
        return nodeList.size();
    }


    @Override
    public int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.reset();
        md.update((key).getBytes(StandardCharsets.UTF_8));
        byte[] hashBytes = md.digest();
        BigInteger hashValue = new BigInteger(1, hashBytes);
        //return Math.abs(hashValue.intValue());
        return Math.abs(hashValue.intValue()) % maxNodes;
    }

    public boolean isZoneFilled(int zoneID) throws RemoteException {
        int i;
        boolean is_filled = true;
        for (i = 0; i < m; i++) {
            if (nodeIds.indexOf(zoneID * m + i) < 0) {
                is_filled = false;
                break;
            }
        }
        return is_filled;
    }
    public ArrayList<Integer> getNodesTopology() throws RemoteException {
        ArrayList<Integer> res = new ArrayList<Integer>();
        res=nodeIds;
        Collections.sort(res);
        return res;
    }

    public void insertToRing(int num, ChordNode node,String ipaddress) throws RuntimeException {
        try {
            Naming.rebind("rmi://"+hostipaddress+"/ChordNode_"+ipaddress+"_"+num,node);
        }
        catch (Exception e1) {
            System.out.println("Error In Binding ChordNode");
        }
    }
    public void removeFromRing(String port,String ipaddress) throws RuntimeException {
        try{
            Naming.unbind("rmi://"+hostipaddress+"/ChordNode_"+ipaddress+"_"+ port);
        }
        catch(Exception e1) {
            System.out.println("Error In UnBinding ChordNode");
        }
    }  

    public void executeInsert () throws RemoteException{
        log.info("Starting executing inserts from insert.txt file");
        Random r = new Random();
        try {
            File myObj = new File("../../../../transactions/insert.txt");
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                int number = r.nextInt(noOfNodes);
                NodeInfo cn = nodeList.get(number);
                //System.out.println("rmi://"+hostipaddress+"/ChordNode_"+cn.ipaddress+"_"+cn.port);
                String[] data = myReader.nextLine().split(", ");
                ChordNode c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+cn.ipaddress+"_"+cn.port);
                long startTime = System.nanoTime();
                c.insert_key(data[0], data[1]);
                long endTime = System.nanoTime();
                long timetaken = endTime - startTime;
                log.info("Time taken to insert: " + data[0] + " with value: " + data[1] + " starting from: ChordNode_"+cn.ipaddress+"_"+cn.port + " with Chord id " + nodeIds.get(number) + " is " + timetaken + " ns" );
            }
            System.out.println("Finished inserting keys-values");
            myReader.close();
        } catch (Exception e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
    public void executeQuery () throws RemoteException{
        log.info("Starting executing queries from query.txt file");
        Random r = new Random();
        try {
            File myObj = new File("../../../../transactions/query.txt");
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                int number = r.nextInt(noOfNodes);
                NodeInfo cn = nodeList.get(number);
                String data = myReader.nextLine();
                ChordNode c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+cn.ipaddress+"_"+cn.port);
                long startTime = System.nanoTime();
                String value = c.get_value(data);
                long endTime = System.nanoTime();
                long timetaken = endTime - startTime;
                log.info("Time taken for query of: " + data + " starting from: ChordNode_"+cn.ipaddress+"_"+cn.port+ " with Chord id " + nodeIds.get(number) + " is " + timetaken + " ns and resulted in value: " + value);
            }
            myReader.close();
            System.out.println("Finished searching keys");
        } catch (Exception e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void executeCombo() throws RemoteException{
        log.info("Starting executing requests from request.txt file");
        Random r = new Random();
        try {
            File myObj = new File("../../../../transactions/requests.txt");
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                int number = r.nextInt(noOfNodes);
                NodeInfo cn = nodeList.get(number);
                //System.out.println("rmi://"+hostipaddress+"/ChordNode_"+cn.ipaddress+"_"+cn.port);
                String[] data = myReader.nextLine().split(", ");
                ChordNode c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+cn.ipaddress+"_"+cn.port);
                long startTime = System.nanoTime(), endTime, timetaken;
                if (data[0].equals("insert")) {
                    c.insert_key(data[1], data[2]);
                    endTime = System.nanoTime();
                    timetaken = endTime - startTime;
                    log.info("Time taken to insert: `" + data[1] + "` with value: " + data[2] + " starting from: ChordNode_"+cn.ipaddress+"_"+cn.port + " with Chord id " + nodeIds.get(number) + " is " + timetaken + " ns" );
                }
                else if (data[0].equals("query")) {
                    String value = c.get_value(data[1]);
                    endTime = System.nanoTime();
                    timetaken = endTime - startTime;
                    log.info("Time taken for query of: `" + data[1] + "` starting from: ChordNode_"+cn.ipaddress+"_"+cn.port + " with Chord id " + nodeIds.get(number) + " is " + timetaken + " ns and resulted in value: " + value);
                }
                else log.info("Error In request");
            }
            myReader.close();
            System.out.println("Finished all queries");
        } catch (Exception e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
