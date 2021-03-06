import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Defines the standard operations as stated in the original Chord paper with methods such as
 * find_successor and fix_fingers.
 */
public interface ChordNode extends Remote {
    /**
     * This function is used to determine the successor node for a given node id.
     *
     * @param id     The node identifier whose successor is to be found
     * @return newNode NodeInfo object containing details of successor node
     * @throws RemoteException Due to RMI.
     */
    NodeInfo find_successor(int id) throws RemoteException;

    /**
     * This function is used to find the predecessor node for a given node id.
     * Loop until the id is not in the range (nn, successor(nn))
     * at each iteration, update nn = nn.closest_preceding_finger(id)
     * when loop ends, nn will be id's predecessor
     *
     * @param id     The node identifier whose predecessor is to be found
     * @return nn NodeInfo object containing details of predecessor node
     * @throws RemoteException Due to RMI.
     */
    NodeInfo find_predecessor(int id) throws RemoteException;

    /**
     * This function is used to determine the closest preceding finger(CPF) of the given node identifier
     *
     * @param id Node identifier whose CPF is to be found.
     * @return NodeInfo NodeInfo object containing details of the CPF node
     * @throws RemoteException Due to RMI.
     */
    NodeInfo closest_preceding_finger(int id) throws RemoteException;

    /**
     * This function is called immediately after the join to initialize data structures specifically the finger table
     * entries to assist in routing.Start by initializing the table. The (i+1)th finger table entry
     * should be same as ith entry, if not invoke find_successor.Check if FTE needs to be updated to s, or it
     * should continue pointing to me. Finally, set predecessor of successor as me.
     *
     * @param n      The successor node of the current Chord Node instance
     * @throws RemoteException Due to RMI.
     */
    void init_finger_table(NodeInfo n) throws RemoteException;

    /**
     * The stabilize function is used to periodically verify the current nodes immediate successor and tell the successor about itself
     * In case of exception a second chance is given to find the successor (Not a 3rd though).
     *
     * @throws RemoteException Due to RMI.
     */
    void stabilize() throws RemoteException;

    /**
     * This function is used to periodically refresh finger table entries
     *
     * @throws RemoteException Due to RMI.
     */
    void fix_fingers() throws RemoteException;

    /**
     * This function is used to update the finger table entries of Chord nodes when a node leaves the network/ring
     *
     * @param t      The NodeInfo object of the node which departs from the ring
     * @param i      The ith finger table entry to be updated
     * @param s      The NodeInfo object to be set as successor in the finger table entry
     * @throws RemoteException Due to RMI.
     */
    void update_finger_table_leave(NodeInfo t, int i, NodeInfo s) throws RemoteException;

    /**
     * This function is used to update other nodes when a Chord Node voluntarily leaves the ring/network
     *
     * @throws RemoteException Due to RMI.
     */
    void update_others_before_leave() throws RemoteException;

    /**
     * This function is used to move keys in range (predecessor, n) to successor node
     *
     * @param pred    Predecessor nodeinfo object
     * @param newNode The current instances nodeinfo object
     * @throws RemoteException Due to RMI.
     */
    void migrate_keys(NodeInfo pred, NodeInfo newNode,Integer replication_number) throws RemoteException;

    /**
     * This function notifes the other nodes that it might be their predecessor
     *
     * @param n NodeInfo object of the probable successor node to notify
     * @throws RemoteException Due to RMI.
     */
    void notify_successor(NodeInfo n) throws RemoteException;

    /**
     * Dummy Function to send a heart beat message to verify status
     *
     * @throws RemoteException Due to RMI.
     */
    void send_beat() throws RemoteException;

    NodeInfo get_successor() throws RemoteException;

    void set_successor(NodeInfo n) throws RemoteException;

    NodeInfo get_predecessor() throws RemoteException;

    void set_predecessor(NodeInfo p) throws RemoteException;

    /**
     * This function is used to generate a unique identifier for a key using SHA-1 algorithm
     *
     * @param key      Key for which identifier is to be generated
     * @param maxNodes Maximum no of nodes in the Chord ring
     * @return int unique identifier for the key
     * @throws NoSuchAlgorithmException Due to SHA-1 usage.
     * @throws RemoteException          Due to RMI.
     */
    int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException;

    /**
     * This function is used to print the finger table entries to verify if they are being set properly
     *
     * @throws RemoteException Due to RMI.
     */
    void print_finger_table() throws RemoteException;

    /**
     * Wrapper function to insert a new key-value pair
     *
     * @param key    The key for the data
     * @param value  The value associated to the key
     * @return boolean Indicator to check if operation was successful or not
     * @throws RemoteException Due to RMI.
     */
    boolean insert_key(String key, String value) throws RemoteException;

    /**
     * Wrapper Function to delete data tagged to a key
     *
     * @param key    Key to be deleted
     * @return boolean Indicator to check if operation was successful or not
     * @throws RemoteException Due to RMI.
     */
    boolean delete_key(String key) throws RemoteException;

    /**
     * Wrapper function to get value associated to a key
     *
     * @param key    Key for which data is to be retrieved
     * @return val The data associated to the key
     * @throws RemoteException Due to RMI.
     */
    String get_value(String key) throws RemoteException;

    /**
     * This function is called when a Chord Node leaves the ring
     * Order of operations when a node leaves the ring:
     * 1) Migrate keys to successor.
     * 2) Inform successor.
     * 3) Inform predecessor.
     * 4) Inform bootstrap.
     * 5) Inform other nodes.
     * Note: Any key-related requests that are routed to this node
     * between steps (1) and (3) will fail.
     *
     * @return boolean Status of leave operation
     * @throws RemoteException Due to RMI.
     */
    boolean leave_ring() throws RemoteException;

    /**
     * Function to display the data stored in the current Chord Node instance
     *
     * @throws RemoteException Due to RMI.
     */
    void display_data_stored() throws RemoteException;

    /**
     * Function to insert key value pair in current Chord Node instance.
     * Reasons to move to query the ring again for this key:
     * -keyID does not lie in my range.
     * -Maybe ring topology has changed while this request was routed to me.
     *
     * @param keyID  Hashed valued for the key
     * @param key    Key to be inserted
     * @param value  Value associated to the key
     * @return boolean Indicator to check if operation was successful or not
     * @throws RemoteException Due to RMI.
     */
    boolean insert_key_local(int keyID, String key, String value,boolean insertHere,Integer replica) throws RemoteException;

    /**
     * Function to delete key value pair in current Chord Node instance
     * Reasons to move to query the ring again for this key:
     * -keyID does not lie in my range.
     * -Maybe ring topology has changed while this request was routed to me.
     *
     * @param keyID  Hashed valued for the key
     * @param key    Key to be deleted
     * @return boolean Indicator to check if operation was successful or not
     * @throws RemoteException Due to RMI.
     */
    boolean delete_key_local(int keyID, String key,boolean deleteHere,Integer replica) throws RemoteException;

    /**
     * Function to relieve key value pair in current Chord Node instance
     * Reasons to move to query the ring again for this key:
     * Key does not lie in my range.
     * Maybe the key migrated due to a recent node join, and this is an old query that has reached me late.
     *
     * @param keyID  Hashed valued for the key
     * @param key    Key to be retrieved
     * @return boolean Indicator to check if operation was successful or not
     * @throws RemoteException Due to RMI.
     */
    String get_key_local(int keyID, String key) throws RemoteException;

    /**
     * Dummy function to assist in latency calculation during node joins
     *
     * @param n NodeInfo object of node which is to be called
     * @throws RemoteException Due to RMI.
     */
    void makeCall(NodeInfo n) throws RemoteException;

    String getAllkeys(Integer term,String currentResult) throws RemoteException;
    String display_data_stored_string(Integer id,String currentRes) throws RemoteException;
}