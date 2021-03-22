import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.apache.commons.math3.util.Pair;
import java.io.*;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
/**
 * The standard chord node of the network,implements the {@link ChordNode} interface.
 */
public class ChordNodeImpl extends UnicastRemoteObject implements ChordNode {

    /**
     * Number of identifier bits.
     */
    private static int replication_Factor=2;
    private static int m = 11;

    /**
     * //Slices of each image (see demo part).
     */
    private static final int StabilizePeriod = 10000; // 10 sec
    private static final int FixFingerPeriod = 10000; // 10 sec
    private static final long serialVersionUID = 1L;

    /**
     * Maximum number of nodes allowed in this network.
     */
    private static int maxNodes = (int) Math.pow(2.0, (long) m);
    static BootStrapNode bootstrap;
    private static int num = 0;  // used during rmi registry binding
    private static volatile int fingerTableSize = 2 * m - 1; // finger table size
    private static volatile int fix_finger_count = 0; // store the id of next finger entry to update
    private static Timer timerStabilize = new Timer();
    private static Timer timerFixFinger = new Timer();
    private static Logger log = null;
    private static boolean chain_replication = true;
    private static String hostipaddress="127.0.0.1";
    /**
     * Data store for each Chord Node instance
     */
    //private HashMap<Integer, HashMap<String, String>> data = new HashMap<>();
    private HashMap<Integer, HashMap<String,Pair<Integer,String>>> data = new HashMap<>();

    //Key-> Integer 
    //Value -> String

    NodeInfo node;

    /**
     * Data Structure to store the finger table for the Chord Node
     */
    transient FingerTableEntry[] fingertable = null;
    NodeInfo predecessor;
    private ReentrantReadWriteLock data_rwlock = new ReentrantReadWriteLock();

    protected ChordNodeImpl(NodeInfo node) throws RemoteException {
        super();
        this.node = node;
        this.predecessor = null;
        this.fingertable = new FingerTableEntry[fingerTableSize];
    }

    public ChordNodeImpl() throws RemoteException {
        super();
        this.node = null;
        this.predecessor = null;
        this.fingertable = new FingerTableEntry[fingerTableSize];
    }

    /**
     * Starting point for the Chord Node instances
     *
     * @param args variable length command line argument list
     * @throws RemoteException Due to RMI.
     */
    public static void main(String[] args) throws RemoteException {
        ChordNode c;
        ChordNodeImpl cni;
        boolean running = true;

        if (args.length < 2) {
            System.out.println("Usage : java ChordNodeImpl <ip address of current node> <ipaddress of bootstrap>");
            System.exit(-1);
        }

        // Logging Module initialize
        PatternLayout layout = new PatternLayout();
        String conversionPattern = "%-7p %d [%t] %c %x - %m%n";
        layout.setConversionPattern(conversionPattern);

        // creates file appender
        FileAppender fileAppender = new FileAppender();

        fileAppender.setFile("logs/chord.log");
        fileAppender.setLayout(layout);
        fileAppender.activateOptions();

        //logger assign
        log = Logger.getLogger(ChordNodeImpl.class);
        log.addAppender(fileAppender);
        log.setLevel(Level.DEBUG);

        log.info("\n## Creating chord node instance ##\n");

        String nodeIPAddress = args[0];
	    String hosturl="rmi://"+hostipaddress+"/";
	
        try {
	        String rmiUrl="rmi://"+hostipaddress+"/ChordRing";
            bootstrap = (BootStrapNode) Naming.lookup(rmiUrl);
        } catch (MalformedURLException | RemoteException | NotBoundException e) {
            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
        }

        try {
            while (true) {
                try {
                    c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+nodeIPAddress+"_"+num);
                } catch (Exception e) {
                    c = null;
                }
                if (c == null) {
		    Registry theRegistry = LocateRegistry.getRegistry("rmi://"+hostipaddress+"",1099);
                    cni = new ChordNodeImpl();
                    bootstrap.insertToRing(num, cni,nodeIPAddress);
                    break;
                } else {
                    num++;
                }
            }
        } catch (Exception e) {
            log.error("Error in binding ChordNode " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return;
        }

        ArrayList<NodeInfo> nodes = bootstrap.addNodeToRing(nodeIPAddress, num + "");
        if (nodes != null) {
            cni.node = nodes.get(0);
            FingerTableEntry fte = new FingerTableEntry((cni.node.nodeID + 1) % maxNodes, nodes.get(1));
            cni.fingertable[0] = fte;
            cni.predecessor = nodes.get(2);
        } else {
            log.error("Join unsuccessful");
            return;
        }

        fileAppender.close();
        fileAppender = new FileAppender();
        fileAppender.setFile("logs/chord_" + cni.node.nodeID + ".log");
        fileAppender.setLayout(layout);
        fileAppender.setAppend(false);
        fileAppender.activateOptions();
        log.removeAllAppenders();
        log.addAppender(fileAppender);

        cni.run();

        Scanner sc = new Scanner(System.in, "UTF-8");
        String key, value;
        boolean res;
        int choice;

        while (running) {
            System.out.println("\nMenu: \n1. Print Finger Table"
                    + "\n2. Get Key \n3. Put Key \n4. Delete Key \n5. Display data stored \n"
                    +"6. Insert Elements\n7. Search Elements\n8. Execute Requests\n9. Leave Chord Ring\n10. Overlay");
            System.out.println("Enter your choice: ");
            try {
                choice = sc.nextInt();
            } catch (Exception e) {
                System.out.println("Give valid input please.");
                continue;
            } finally {
                if (sc.hasNextLine()) {
                    sc.nextLine();
                }
                System.out.println("\n");
            }

            switch (choice) {
                case 1:
                    cni.print_finger_table();
                    break;
                case 2:
                    System.out.print("Enter key: ");
                    key = sc.nextLine();
                    if (key.equals("*")) {
                        String output=new String();
                        String empty="";
                        output=cni.getAllkeys(cni.node.nodeID,empty);
                    }
                    else {
                        value = cni.get_value(key);
                        System.out.println(value != null ? "Value is: " + value : "Key not found.");
                    }
                    break;
                case 3:
                    System.out.print("Enter key: ");
                    key = sc.nextLine();
                    System.out.print("Enter value: ");
                    value = sc.nextLine();
                    res = cni.insert_key(key, value);
                    System.out.println(res ? key + ": " + value + " successfully inserted." : "Insertion unsuccessful.");
                    break;
                case 4:
                    System.out.print("Enter key: ");
                    key = sc.nextLine();
                    res = cni.delete_key(key);
                    System.out.println(res ? key + " successfully deleted." : "Key not found. Deletion unsuccessful.");
                    break;
                case 5:
                    System.out.println("Printing all data stored in the node");
                    cni.display_data_stored();
                    break;
                case 6:
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                bootstrap.executeInsert();
                            } catch (Exception e) {
                                e.printStackTrace();
                                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                            }
                        }
                    }).start();
                    
                    break;
                case 7:
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                bootstrap.executeQuery();
                            } catch (Exception e) {
                                e.printStackTrace();
                                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                            }
                        }
                    }).start();
                    
                    break;
                case 8:
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                bootstrap.executeCombo();
                            } catch (Exception e) {
                                e.printStackTrace();
                                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                            }
                        }
                    }).start();
                    
                    break;                
                case 9:
                    if (cni.leave_ring()) {
                        timerStabilize.cancel();
                        timerFixFinger.cancel();
                        System.out.println("Node left...No more operations allowed");

                        try {
                            bootstrap.removeFromRing(cni.node.port,nodeIPAddress);
                            System.out.println("Node removed from RMI registry!");
                        } catch (Exception e) {
                            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                        }
                        running = false;
                        sc.close();
                    } else {
                        System.out.println("Error: Cannot leave ring right now");
                    }
                    break;
                case 10:
                    ArrayList<Integer> topology = new ArrayList<>();
                    topology=bootstrap.getNodesTopology();
                    System.out.println(topology);
                    break;
                default:
                    break;
            }
        }

    }

    @Override
    public NodeInfo find_successor(int id) throws RemoteException {
        NodeInfo newNode = find_predecessor(id);
        try {
            ChordNode c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+newNode.ipaddress+"_"+newNode.port);
            
            newNode = c.get_successor();
        } catch (Exception e) {
            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return null;
        }
        return newNode;
    }

    @Override
    public NodeInfo find_predecessor(int id) throws RemoteException {
        NodeInfo nn = this.node;
        int myID = this.node.nodeID;
        int succID = this.get_successor().nodeID;
        ChordNode c = null;


        while ((myID >= succID && (myID >= id && succID < id)) || (myID < succID && (myID >= id || succID < id))) {
            try {
                if (nn == this.node) {
                    nn = closest_preceding_finger(id);
                } else {
                    assert c != null;
                    nn = c.closest_preceding_finger(id);
                }

                myID = nn.nodeID;
                c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+nn.ipaddress+"_"+nn.port);
                succID = c.get_successor().nodeID;
            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                return null;
            }
        }

        return nn;
    }

    @Override
    public NodeInfo closest_preceding_finger(int id) {
        int myID = node.nodeID;
        for (int i = fingerTableSize - 1; i >= 0; i--) {
            int succID = fingertable[i].successor.nodeID;
            if ((myID < id && (succID > myID && succID < id)) || (myID >= id && (succID > myID || succID < id))) {
                return fingertable[i].successor;
            }
        }
        return this.node;
    }

	/*
    (1) Check if successor is dead (try pinging/reaching it twice).
	(2) Find the next finger table entry that does not point either to me or to the dead successor.
	(3) Once such a finger table entry is found, query that node for its predecessor.
	(3a) In a loop, follow the predecessor chain till you find the node whose predecessor is our dead successor.
	(3b) Set my successor as that node and set the predecessor of that node as me.
	(3c) Inform bootstrap to update its list of active chord nodes.
	(4) If no such finger table entry is found, contact bootstrap to return a different successor.
	*/

    public void init_finger_table(NodeInfo n) throws RemoteException {
        ChordNode c;
        try {
             c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+n.ipaddress+"_"+n.port);

            int myID = this.node.nodeID;
            for (int i = 0; i < fingerTableSize - 1; i++) {
                int nextID = fingertable[i].successor.nodeID;

                if ((myID >= nextID && (fingertable[i + 1].start >= myID || fingertable[i + 1].start <= nextID)) ||
                        (myID < nextID && (fingertable[i + 1].start >= myID && fingertable[i + 1].start <= nextID))) {

                    fingertable[i + 1].successor = fingertable[i].successor;
                } else {
                    NodeInfo s = c.find_successor(fingertable[i + 1].start);

                    int myStart = fingertable[i + 1].start;
                    int succ = s.nodeID;
                    int mySucc = fingertable[i + 1].successor.nodeID;

                    if (myStart > succ) {
                        succ += maxNodes;
                    }
                    if (myStart > mySucc) {
                        mySucc += maxNodes;
                    }
                    if (myStart <= succ && succ <= mySucc) {
                        fingertable[i + 1].successor = s;
                    }
                }
            }
            c.set_predecessor(this.node);
        } catch (MalformedURLException | NotBoundException e) {
            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
        }
    }

    public void update_others_before_leave() throws RemoteException {
        for (int i = 1; i <= fingerTableSize; i++) {
            int id = this.node.nodeID - (int) Math.pow(2, i - 1) + 1;
            if (id < 0) {
                id += maxNodes;
            }
            NodeInfo p = find_predecessor(id);
            try {
                ChordNode c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+p.ipaddress+"_"+p.port);
                c.update_finger_table_leave(this.node, i - 1, this.get_successor());
            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }
        }
    }

    public void update_finger_table_leave(NodeInfo t, int i, NodeInfo s) throws RemoteException {
        if (fingertable[i].successor.nodeID == t.nodeID && t.nodeID != s.nodeID) {
            fingertable[i].successor = s;
            NodeInfo p = predecessor;

            try {
                ChordNode c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+p.ipaddress+"_"+p.port);
                c.update_finger_table_leave(t, i, s);
            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }
        }
    }

    @Override
    public void send_beat() throws RemoteException {
        log.debug("Acknowledged heart beat message.");
    }

    @Override
    public void stabilize() {
        NodeInfo successorNodeInfo = null, tempNodeInfo = null;
        ChordNode successor, temp;

        try {
            successorNodeInfo = get_successor();

            if (successorNodeInfo.nodeID == this.node.nodeID) {
                // single node so no stabilization
                successor = this;
            } else {
                successor = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+successorNodeInfo.ipaddress+"_"+successorNodeInfo.port);
                successor.send_beat();
            }
        } catch (Exception e) {
            log.error("Failed Heart beat message. Error in stabilize: " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            try {
                assert successorNodeInfo != null;
                successor = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+successorNodeInfo.ipaddress+"_"+successorNodeInfo.port);
                successor.send_beat();
            } catch (Exception e1) {
                successor = null;
                log.error("Failed Heart beat message. Error in stabilize: " + e1.getClass() + ": " + e1.getMessage() + ": " + e1.getCause() + "\n" + Arrays.toString(e1.getStackTrace()), e1);
            }
        }
        // Current successor is dead. Get a new one.
        if (successor == null) {
            log.error("Failed to contact successor. Declare successor dead");
            // iterate over fingertable entries till you find a node that is not me and not the dead successor
            int i;
            for (i = 1; i < fingerTableSize; i++) {
                tempNodeInfo = fingertable[i].successor;
                if (tempNodeInfo.nodeID != successorNodeInfo.nodeID && tempNodeInfo.nodeID != node.nodeID)
                    break;
            }
            if (i != fingerTableSize) {
                assert tempNodeInfo != null;
                while (true) {// follow the predecessor chain from tempNodeInfo
                    try {
                        temp = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+tempNodeInfo.ipaddress+"_"+tempNodeInfo.port);
                        if (temp.get_predecessor().nodeID == successorNodeInfo.nodeID) {
                            temp.set_predecessor(this.node);
                            this.set_successor(tempNodeInfo);
                            break;
                        }
                        tempNodeInfo = temp.get_predecessor();
                    } catch (Exception e) {
                        log.error("Error in stabilize while following predecessor chain: " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                        break;
                    }
                }
                try {//notify the bootstrap of node exit
                    bootstrap.removeNodeFromRing(successorNodeInfo);

                } catch (RemoteException e) {
                    log.error("Error in notifying bootstrap about dead node: " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                }
            } else {
                try {// My finger table does not include a different node. Request from bootstrap for a new successor.
                    NodeInfo new_suc = bootstrap.findNewSuccessor(this.node, successorNodeInfo);
                    this.set_successor(new_suc);
                    temp = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+new_suc.ipaddress+"_"+new_suc.port);
                    //temp = (ChordNode) Naming.lookup("rmi://" + new_suc.ipaddress + "/ChordNode_" + new_suc.port);
                    temp.set_predecessor(this.node);
                } catch (Exception e) {
                    log.error("Error in requesting new successor from bootstrap: " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                }
            }
        } else {// Current successor is alive. Ensure that you are your successor's predecessor.
            NodeInfo x = null;
            try {
                x = successor.get_predecessor();
            } catch (RemoteException e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }
            if ((x != null) && (inCircularInterval(x.nodeID, this.node.nodeID, this.fingertable[0].successor.nodeID)))
                this.fingertable[0].successor = x;

            try {// Ensure that you are your successor's predecessor is set correctly.
                if (successorNodeInfo.nodeID == this.node.nodeID) {
                    successor.notify_successor(this.node);
                }
            } catch (RemoteException e) {
                log.error("Error in calling the successor's notifyall" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }
        }

    }

    @Override
    public void notify_successor(NodeInfo n) {
        if (this.predecessor == null)
            this.predecessor = n;
        if (inCircularInterval(n.nodeID, this.predecessor.nodeID, this.node.nodeID))
            this.predecessor = n;

    }

    private boolean inCircularInterval(int x, int a, int b) {
        boolean val = false;
        if (a == b)
            val = true;
        else if (a < b) {// normal range
            if ((x > a) && (x < b))
                val = true;
        } else { // when on one current node is after 0 but predecessor is before 0
            if ((x > a) && (x < (b + maxNodes))) {// in ring before 0
                val = true;
            } else if ((x < b) && ((x + maxNodes) > a)) {// in ring after 0
                val = true;
            }
        }
        return val;
    }

    private boolean inCircularIntervalEndInclude(int x, int a, int b) {
        return (x == b) || inCircularInterval(x, a, b);
    }

    @Override
    public void fix_fingers() throws RemoteException {
        //periodically fix all fingers
        ChordNodeImpl.fix_finger_count++;
        if (ChordNodeImpl.fix_finger_count == ChordNodeImpl.fingerTableSize) {
            ChordNodeImpl.fix_finger_count = 1;
        }
        fingertable[fix_finger_count].successor = find_successor(fingertable[fix_finger_count].start);
    }

    /**
     * This function is called after contacting the BootStrap server and obtaining the successor and predecessor nodes to initialize finger table and update other nodes after joining.
     */
    void run() {
        ChordNode c;
        NodeInfo suc = fingertable[0].successor;

        int ringsize = -1;
        long endTime;
        try {
            ringsize = bootstrap.getNodesInRing();
        } catch (RemoteException e) {
            log.error(e);
        }

        // Allocate storage for finger table
        int i, j;
        for (i = 1; i < m; i++) {
            int start = (this.node.nodeID + (int) Math.pow(2, i)) % maxNodes;
            NodeInfo succ = this.node;
            FingerTableEntry fte = new FingerTableEntry(start, succ);
            fingertable[i] = fte;
        }
        for (j = m - 2; i < fingerTableSize; i++, j--) {
            int start = (this.node.nodeID + maxNodes - (int) Math.pow(2, j)) % maxNodes;
            NodeInfo succ = this.node;
            FingerTableEntry fte = new FingerTableEntry(start, succ);
            fingertable[i] = fte;
        }

		/* Order of operations for initializing a new node:
        1) Initialize Finger table.
		2) Inform successor node.
		3) Migrate keys from successor.
		4) Inform predecessor node.
		*/

        if (ringsize > 1) { //More than one node in the Chord Ring
            try {
                this.init_finger_table(suc);
            } catch (RemoteException e) {
                log.error(e);
            }
            try {
                c= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+suc.ipaddress+"_"+suc.port);
                c.migrate_keys(this.predecessor, this.node,0);
            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }

            try {// set successor of predecessor as me
                c= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+predecessor.ipaddress+"_"+predecessor.port);
                c.set_successor(this.node);

            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }
        }

        try {
            bootstrap.acknowledgeNodeJoin(this.node.nodeID);
            endTime = System.currentTimeMillis();
            long timetaken = endTime;
        } catch (Exception e) {
            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
        }


        //Set the timer to run notify every StabilizePeriod
        timerStabilize.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                stabilize();
            }
        }, new Date(System.currentTimeMillis()), ChordNodeImpl.StabilizePeriod);

        //Set the timer to run notify every FixFingerPeriod
        timerFixFinger.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                try {
                    fix_fingers();
                } catch (Exception e) {
                    log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                }
            }
        }, new Date(System.currentTimeMillis()), ChordNodeImpl.FixFingerPeriod);
    }

    public NodeInfo get_predecessor() throws RemoteException {
        return this.predecessor;
    }

    @Override
    public void set_predecessor(NodeInfo p) throws RemoteException {
        this.predecessor = p;
    }

    @Override
    public NodeInfo get_successor() throws RemoteException {
        return this.fingertable[0].successor;
    }

    @Override
    public void set_successor(NodeInfo n) throws RemoteException {
        this.fingertable[0].successor = n;
    }

    @Override
    public void print_finger_table() throws RemoteException {
        System.out.println("My ID: " + node.nodeID + " Predecessor ID: " + (predecessor == null ? "NULL" : predecessor.nodeID));
        System.out.println("Index\tStart\tSuccessor ID\tIP Address\tRMI Identifier");
        for (int i = 0; i < fingerTableSize; i++) {
            System.out.println((i + 1) + "\t" + fingertable[i].start + "\t" + fingertable[i].successor.nodeID + "\t\t" + fingertable[i].successor.ipaddress + "\t" + fingertable[i].successor.port);
        }
    }

    @Override
    public boolean insert_key(String key, String value) {
        try {
            int keyID = generate_ID(key, maxNodes);
            NodeInfo n = find_successor(keyID);
            if (n != this.node) {
                ChordNode c= c= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+n.ipaddress+"_"+n.port);
                boolean flag = c.insert_key_local(keyID, key, value,false,0);
                return flag;
            } else {
                boolean flag=insert_key_local(keyID,key,value,true,0);
                ChordNode crep= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+this.get_successor().ipaddress+"_"+this.get_successor().port);
                boolean flag1=crep.insert_key_local(keyID,key,value,true,1);
                return flag;
            }
        } catch (Exception e) {
            log.error("Error in inserting keys" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return false;
        }
    }





    @Override
    public boolean delete_key(String key) {
        try {
            int keyID = generate_ID(key, maxNodes);
            NodeInfo n = find_successor(keyID);
            if (n != this.node) {
                ChordNode c= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+n.ipaddress+"_"+n.port);
                return c.delete_key_local(keyID, key,false,0);
            } else {
                return delete_key_local(keyID, key,false,0);
            }
        } catch (Exception e) {
            log.error("Error in deleting key" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return false;
        }
    }

    @Override
    public String get_value(String key) {
        try {
            int keyID = generate_ID(key, maxNodes);
            String val = null;
            if(!chain_replication){
                data_rwlock.readLock().lock();
                HashMap <String,Pair<Integer,String>> entry= data.get(keyID);
                if (entry != null) {
                    Pair<Integer,String> pair = entry.get(key);
                    val = pair.getValue();
                }
                data_rwlock.readLock().unlock();
                if (entry != null) {
                    return val;
                }
            }
            NodeInfo n = find_successor(keyID);
            if (n != this.node) {
                ChordNode c= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+n.ipaddress+"_"+n.port);
                val = c.get_key_local(keyID, key);
                return val;
            } else {
                val = get_key_local(keyID, key);
                return val;
            }
        } catch (Exception e) {
            log.error("Error in get value of key" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return null;
        }
    }

    @Override
    public boolean insert_key_local(int keyID, String key, String value,boolean insertHere,Integer replica) throws RemoteException {
       if(insertHere=false) {  
            boolean res = true;
            data_rwlock.writeLock().lock();
            if (!inCircularIntervalEndInclude(keyID, get_predecessor().nodeID, node.nodeID)) {
                data_rwlock.writeLock().unlock();
                res = insert_key(key, value);
            } else {
                HashMap<String,Pair<Integer,String>> entry= data.computeIfAbsent(keyID, k -> new HashMap<>());
                Pair<Integer,String> pair = new Pair<>(0, value);
                entry.put(key,pair);
                data_rwlock.writeLock().unlock();
            }
            return res;
            }
      else {
            boolean res = true;
            data_rwlock.writeLock().lock();
            HashMap<String,Pair<Integer,String>> entry= data.computeIfAbsent(keyID, k -> new HashMap<>());
            Pair<Integer,String> pair = new Pair<>(replica, value);
            entry.put(key,pair);
            data_rwlock.writeLock().unlock();
            if (replica!=replication_Factor) {
                if (chain_replication) {
                    try {
                        ChordNode crep= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+this.get_successor().ipaddress+"_"+this.get_successor().port);
                        boolean flag1=crep.insert_key_local(keyID,key,value,true,replica+1);
                    } catch (Exception e) {
                        log.error("Error in inserting keys" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                        return false;
                    }
                }
                else {
                    NodeInfo succ_node = this.get_successor();
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                ChordNode crep = (ChordNode) Naming.lookup("rmi://" + hostipaddress + "/ChordNode_" + succ_node.ipaddress + "_" + succ_node.port);
                                boolean flag1=crep.insert_key_local(keyID,key,value,true,replica+1);
                            } catch (Exception e) {
                                log.error("Error in inserting keys" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                            }
                        }
                    }).start();
                }
            }
            return res;            
        }
    }

    @Override
    public boolean delete_key_local(int keyID, String key,boolean deleteHere,Integer replica) throws RemoteException {
        if(deleteHere=false) {  
            boolean res = true;
            data_rwlock.writeLock().lock();
            if (!inCircularIntervalEndInclude(keyID, get_predecessor().nodeID, node.nodeID)) {
            data_rwlock.writeLock().unlock();
                res = delete_key(key);
            } else 
            {
            HashMap<String,Pair<Integer,String>> entry= data.get(keyID);
            if (entry != null)
                if (entry.get(key) != null) {
                    entry.remove(key);
                } else {
                    res = false;
                }
            data_rwlock.writeLock().unlock();
            }
            return res;
        }
        else {
            if (replica!=replication_Factor) {
                if (chain_replication) {
                    try {
                        ChordNode crep= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+this.get_successor().ipaddress+"_"+this.get_successor().port);
                        crep.delete_key_local(keyID, key,true,replica+1);
                    } catch (Exception e) {
                        log.error("Error in inserting keys" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                    }
                }
                else {
                    NodeInfo succ_node = this.get_successor();
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                ChordNode crep = (ChordNode) Naming.lookup("rmi://" + hostipaddress + "/ChordNode_" + succ_node.ipaddress + "_" + succ_node.port);
                                crep.delete_key_local(keyID, key,true,replica+1);
                            } catch (Exception e) {
                                log.error("Error in inserting keys" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                            }
                        }
                    }).start();
                }
            }
            boolean res = true;
            data_rwlock.writeLock().lock();
            HashMap<String,Pair<Integer,String>> entry= data.get(keyID);
            if (entry != null)
                if (entry.get(key) != null) {
                    entry.remove(key);
                } else {
                    res = false;
                }
                 data_rwlock.writeLock().unlock();
                 return res;
        }
    }

    @Override
    public String get_key_local(int keyID, String key) throws RemoteException {
        String val = null;
        data_rwlock.readLock().lock();
        
        HashMap <String,Pair<Integer,String>> entry= data.get(keyID);
        if (entry != null) {
            Pair<Integer,String> pair = entry.get(key);
            if(chain_replication) {
                if(pair.getKey() != replication_Factor) {
                    try {
                        ChordNode crep= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+this.get_successor().ipaddress+"_"+this.get_successor().port);
                        val = crep.get_key_local(keyID, key);
                    } catch (Exception e) {
                        log.error("Error in searching keys" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                    }
                }
                else {
                    val = pair.getValue();
                }
            }
            else {
                val = pair.getValue();
            }
        }

        data_rwlock.readLock().unlock();
        if (entry == null && !inCircularIntervalEndInclude(keyID, get_predecessor().nodeID, node.nodeID)) {
            val = get_value(key);
        }
        return val;
    }

    @Override
    public boolean leave_ring() throws RemoteException {
        ChordNode c;
        data_rwlock.writeLock().lock();
        try {
            c= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+this.get_successor().ipaddress+"_"+this.get_successor().port);
            for (Map.Entry<Integer,HashMap<String,Pair<Integer,String>>> hashkeys : data.entrySet()) {
                int key = hashkeys.getKey();
                for (Map.Entry<String,Pair<Integer,String>> e: hashkeys.getValue().entrySet()) {
            
                    Pair <Integer,String> pair = e.getValue();
                   data_rwlock.writeLock().unlock();
                   c.insert_key_local(key,e.getKey(),pair.getValue(),true,pair.getKey());
                   data_rwlock.writeLock().lock();
                }
            }
            data.clear();

        } catch (Exception e) {
            log.error(e);
            return false;
        } finally {
            data_rwlock.writeLock().unlock();
        }
        try {
            //Set successor's predecessor to my predecessor
            c.set_predecessor(this.get_predecessor());

            //Set predecessor's successor to my successor
            c= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+this.predecessor.ipaddress+"_"+this.predecessor.port);
            c.set_successor(this.get_successor());
            //Inform bootstrap and other chord nodes of departure
            bootstrap.removeNodeFromRing(this.node);
            update_others_before_leave();
        } catch (Exception e) {
            log.error(e);
        }
        return true;
    }


    @Override
    public int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.reset();
        byte[] hashBytes;
        hashBytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
        BigInteger hashValue = new BigInteger(1, hashBytes);
        return Math.abs(hashValue.intValue()) % maxNodes;
    }
public void migrate_keys(NodeInfo pred, NodeInfo newNode, Integer replication_number) throws RemoteException {
    ArrayList<Integer> removelist = new ArrayList<>();
    data_rwlock.writeLock().lock();

    if(replication_number == 0) {
        for (Map.Entry<Integer,HashMap<String,Pair<Integer,String>>> hashkeys : data.entrySet()) {
            int key = hashkeys.getKey();
            for (Map.Entry<String,Pair<Integer,String>> e : hashkeys.getValue().entrySet()) {
                Pair<Integer,String> pair = e.getValue();
                HashMap<String,Pair<Integer,String>> entry= data.computeIfAbsent(key, k -> new HashMap<>());
                Integer current_replica = pair.getKey();
                if(current_replica == replication_Factor){
                    data_rwlock.writeLock().unlock();
                    try {
                        ChordNode c= (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+newNode.ipaddress+"_"+newNode.port);
                        
                        c.insert_key_local(key, e.getKey(), pair.getValue(), true, pair.getKey());
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                    data_rwlock.writeLock().lock();
                    data.remove(key);
                }
                else if (current_replica > replication_number) {
                    Pair<Integer,String> newValues = new Pair<>(current_replica+1, pair.getValue());
                    data_rwlock.writeLock().unlock();
                    try {
                        ChordNode c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+newNode.ipaddress+"_"+newNode.port);
                        c.insert_key_local(key, e.getKey(), pair.getValue(), true, pair.getKey());
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                    data_rwlock.writeLock().lock();
                    entry.put(e.getKey(), newValues);

                }
                else if (current_replica == 0 && this.inCircularIntervalEndInclude(key, pred.nodeID, newNode.nodeID)) {
                    Pair<Integer,String> newValues = new Pair<>(current_replica+1, pair.getValue());
                    data_rwlock.writeLock().unlock();
                    try {
                        ChordNode c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+newNode.ipaddress+"_"+newNode.port);
                        c.insert_key_local(key, e.getKey(), pair.getValue(), true, pair.getKey());
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                    data_rwlock.writeLock().lock();
                    entry.put(e.getKey(), newValues);

                }
            }
        }
    }

    else {
        for (Map.Entry<Integer,HashMap<String,Pair<Integer,String>>> hashkeys : data.entrySet()) {
            int key = hashkeys.getKey();
            HashMap<String,Pair<Integer,String>> entry= data.computeIfAbsent(key, k -> new HashMap<>());
            for (Map.Entry<String,Pair<Integer,String>> e : hashkeys.getValue().entrySet()) {
                Pair<Integer,String> pair = e.getValue();
                Integer current_replica = pair.getKey();
                if(current_replica == replication_Factor){
                    data.remove(key);
                }
                else if (current_replica > replication_number) {
                    Pair<Integer,String> newValues = new Pair<>(current_replica+1, pair.getValue());
                    entry.put(e.getKey(), newValues);
                }
            }
        }
    }
    
    data_rwlock.writeLock().unlock();
    try {
        ChordNode nextNode = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+this.get_successor().ipaddress+"_"+this.get_successor().port);
        if(replication_number != replication_Factor) nextNode.migrate_keys(newNode, this.node, replication_number+1);
    } catch (Exception e1) {
        e1.printStackTrace();
    }
}

    @Override
    public void display_data_stored() throws RemoteException {
        for (Map.Entry<Integer,HashMap<String,Pair<Integer,String>>> hashkeys : data.entrySet()) {
            int key = hashkeys.getKey();
            for (Map.Entry<String,Pair<Integer,String>> e : hashkeys.getValue().entrySet()) {
                System.out.print("Hash Key: " + key);
                System.out.print("\tActual Key: " + e.getKey());
                Pair pair = e.getValue();
                System.out.print("\tReplica: " + pair.getKey());
                System.out.println("\tActual Value: " + pair.getValue());
            }
        }
    }

    

    public String getAllkeys(Integer term,String currentResult) throws RemoteException {
        data_rwlock.writeLock().lock();
      
        if (term!=this.node.nodeID || currentResult.isEmpty()==true){
            String x= display_data_stored_string(this.node.nodeID, currentResult);
            String newCurrentResult=currentResult+x;
            String useless=new String();
            data_rwlock.writeLock().unlock();
            try{
                ChordNode crep = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+this.get_successor().ipaddress+"_"+this.get_successor().port);
                useless = crep.getAllkeys(term, newCurrentResult);
            }
            catch (Exception e1) {
                e1.printStackTrace();
            }
            String empty="";
            return empty;
        }
        else {
            data_rwlock.writeLock().unlock();
            System.out.println(currentResult);
            return currentResult;
        }
    }

    @Override
    public  String display_data_stored_string(Integer id,String currentRes) throws RemoteException {
        String start=new String("Node is "+this.node.nodeID+"\n");
        String accum=new String();
        String temp = new String();
        String temp1= new String();
        String temp2 = new String();
        String temp3= new String();
        int i = 0;
        for (Map.Entry<Integer,HashMap<String,Pair<Integer,String>>> hashkeys : data.entrySet()) {
       // for (Map.Entry<Integer, HashMap<String, String>> hashkeys : data.entrySet()) {
            int key = hashkeys.getKey();
            for (Map.Entry<String,Pair<Integer,String>> e : hashkeys.getValue().entrySet()) {
            //for (Map.Entry<String, String> e : hashkeys.getValue().entrySet()) {
                i ++;
                temp= new String("Hash Key: " + key);
                temp1=new String ("\tActual Key: " + e.getKey());
                Pair pair = e.getValue();
                temp2 = new String("\tReplica: " + pair.getKey());
                temp3 = new String("\tActual Value: " + pair.getValue());
            }
            accum+=temp+temp1+temp2+temp3+"\n";
        }
        return start+accum+String.valueOf(i)+"***************************\n" ;
    } 
    


    @Override
    public void makeCall(NodeInfo n) throws RemoteException {
        if (n != null) {
            ChordNode c;
            try {
                c = (ChordNode) Naming.lookup("rmi://"+hostipaddress+"/ChordNode_"+n.ipaddress+"_"+n.port);
                //c = (ChordNode) Naming.lookup("rmi://" + n.ipaddress + "/ChordNode_" + n.port);
                c.send_beat();
            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }
}
