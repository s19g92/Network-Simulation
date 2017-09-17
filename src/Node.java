/*
 * @author Shubham Gupta
 */

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.Map.Entry;
import java.lang.*;
import java.lang.reflect.Array;

@SuppressWarnings({"unused"})
public class Node {

	static char nodeId;
	static char destId;
	static char resend_dest;
	static char[] list;
	static int timer = 00;
	static int lsr_number = 00;
	static int tcp_seq_number = -1;
	static boolean packetlost = false;
	static boolean msgsent = false;
	static int seq_to_resend = 00;
	static destinations to_reach = new destinations();
	static Map<String,String> tcp_msg_list = new HashMap<String, String>();
	static Map<String, String> nack_to_send = new HashMap<String,String>();
	static Map<String,String> lsp_timer = new HashMap<String, String>();
	static Map<String,String> lsp_nodes = new HashMap<String, String>();
	static Map<String,destinations> reach = new HashMap<String, destinations>();
	static Map<String, List<String>> all_msg_rcvd = new HashMap<String, List<String>>();
	
	

	/****************************** MAIN FUNCTION *********************************************/
	
	public static void main(String[] args) throws Exception {

		// Read all the neighbours.
		nodeId = args[0].charAt(0);
		destId = args[2].charAt(0);
		if (nodeId == destId) {
			String neighbour = "";
			for (int i = 3; i < args.length; i++) {
				neighbour = neighbour + args[i];
			}
			list = neighbour.toCharArray();
		} else {
			String neighbour = "";
			for (int i = 5; i < args.length; i++) {
				neighbour = neighbour + args[i];
			}
			list = neighbour.toCharArray();
		}
		System.out.print("Neighbour List At Node " + nodeId + " : ");
		System.out.print(list);
		System.out.println();
		// End reading from input channels
		
		// Initialize lsp timer
		for(int i=0; i<10;i++) {
			lsp_timer.put(""+i, "00");
			lsp_nodes.put(""+i, "");
			String s = ""+i;
			to_reach.next_hop = s.charAt(0);
			to_reach.cost = 99;
			reach.put(""+i, to_reach);
		}
		
		//Open all files from neighbours to this node for reading.
		Reader rdr[] = new Reader[list.length];
		for (int i = 0; i < list.length; i++){
			String file = "from" + list[i] + "to" + nodeId + ".txt";
			rdr[i] = new Reader(file, list[i]);
			to_reach.next_hop = list[i];
			to_reach.cost = 0;
			reach.put(""+list[i], to_reach);
		}
		
		// Time to live and the main working the node.
		int ttl = Integer.parseInt(args[1]);
		
		for (timer = 0; timer < ttl; timer++) {
			// Receive
			datalink_receive_from_channel(rdr);
			// Network Routing
			network_route(timer);
			// Send
			transport_send(args, timer);

			Thread.sleep(1 * 1000);

		}
		transport_output();
		System.out.println("Node :" + nodeId + " Terminated");
	}

	/***************************** DATA LINK LAYER **********************************************/
	// Receieve message from a channel and forward to network layer.
	public static void datalink_receive_from_channel(Reader[] rdr)
			throws Exception, IOException {

		// For every neighbour do
		for (int i = 0; i < list.length; i++) {

			// Read From Channel and pass to network layer
			char[] msg;
			msg = rdr[i].readFile();
			
			if(msg != null) {
				System.out.print("Message Received at Node " + nodeId + " From Node " + list[i] + " : " );
				System.out.print(msg);
				System.out.println("");
				// Check checksum
				int checksum = 0;
				for (int k = 0; k < 15; k++) {
					checksum = (checksum + (int) msg[k]) % 100;
				}
				DecimalFormat formatter = new DecimalFormat("00");
				String csum = formatter.format(checksum);
				char[] check = csum.toCharArray();
				
				//System.out.println(" " + msg[15] + msg[16] + "   " + check[0] + check[1]);
				
				// If checksum is correct, give the message to network layer.
				if (msg[15] == check[0] && msg[16] == check[1]) {
					char[] acutal_msg = Arrays.copyOfRange(msg, 0, 15);					
					network_receive_from_datalink(acutal_msg, list[i]);
				}
				else {
					System.out.println("Checksum Failed.");
				}
			}
		}
	}
	

	/***************************** DATA LINK LAYER **********************************************/
	// Receive message from network layers and give to next hop
	public static void datalink_receive_from_network(char[] msg, char next_hop)
			throws Exception {

		// Calculating check sum for the given message
		String s = "XX";
		int checksum = 0;
		for (int i = 0; i < 15; i++) {
			checksum = (checksum + (int) msg[i]) % 100;
		}
		DecimalFormat formatter = new DecimalFormat("00");
		String csum = formatter.format(checksum);

		// Final message to be written to File.
		String final_msg = new StringBuilder().append(s).append(msg)
				.append(csum).toString();

		// Write in Source to Next hop file
		System.out.print("Message sent from node " + nodeId + " to node " + next_hop + " : ");
		System.out.print(final_msg);
		System.out.println();
		String filePath = "from" + nodeId + "to" + next_hop + ".txt";
		BufferedWriter WriteFile = new BufferedWriter(new FileWriter(filePath,
				true));
		WriteFile.write(final_msg);
		WriteFile.write('\n');
		WriteFile.close();
	}

	/***************************** NETWORK LAYER **********************************************/
	// Get a new message from transport layer.
	@SuppressWarnings({ "rawtypes" })
	public static void network_receive_from_transport(char[] msg, int len,
			char dest) throws Exception {

		// Construct data message to be sent.
		char[] msg_to_send = new char[15];
		msg_to_send[0] = 'D';
		msg_to_send[1] = dest;
		DecimalFormat formatter = new DecimalFormat("00");
		String length = formatter.format(len);
		char[] l = length.toCharArray();
		msg_to_send[2] = l[0];
		msg_to_send[3] = l[1];

		for (int i = 0; i < len; i++) {
			msg_to_send[4 + i] = msg[i];
		}
		for (int i = 4 + len; i < 15; i++) {
			msg_to_send[i] = ' ';
		}
		// No need to check routing tables, if destination is a neighbour
		boolean sent = false;
		for (int i1 = 0; i1 < list.length; i1++) {
			if (dest == list[i1]) {
				datalink_receive_from_network(msg_to_send, dest);
				sent = true;
			}
		}
		
		// Else, check routing tables for next hop
		if(!sent) {
			Iterator<Entry<String, destinations>> it = reach.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry pair = (Map.Entry)it.next();
				String key = (String) pair.getKey();
				if(key.equals(""+dest)) {
					if(reach.get(key).cost != 99)
						datalink_receive_from_network(msg_to_send, reach.get(key).next_hop);
					else
						System.out.println("Destination Unreachable");
				}
			}
			
		}
	}

	/***************************** NETWORK LAYER **********************************************/
	// Receive a new message from the data link layer.
	@SuppressWarnings({ "rawtypes" })
	public static void network_receive_from_datalink(char[] msg_rcvd, char from)
			throws Exception {

		// Check to whom the message is addressed to
		if (msg_rcvd[0] == 'D') { // Data Message

			if ((int) msg_rcvd[1] == nodeId) { // Message for this node
				String length = new StringBuilder().append(msg_rcvd[2])
						.append(msg_rcvd[3]).toString();
				int len = Integer.parseInt(length);

				// Give the message to transport layer after removing the
				// padding
				char[] msg = new char[len];
				for (int i = 0; i < len; i++) {
					msg[i] = msg_rcvd[4 + i];
				}
				transport_receive_from_network(msg, len, from);
			} else {

				// Message not for this node, forward to next hop
				// Need to Check routing tables to find out next hop for
				// destination.
				// Directly send if the destination is a neighbour.
				
				boolean sent = false;
				for (int i1 = 0; i1 < list.length; i1++) {
					if (msg_rcvd[1] == list[i1]) {
						System.out.println("Data Packet NOT for the current Node. Forward to next hop : " + msg_rcvd[1]);
						datalink_receive_from_network(msg_rcvd, msg_rcvd[1]);
						sent = true;
					}
				}
				
				// Else, check routing tables for next hop
				
				if(!sent) {
					Iterator<Entry<String, destinations>> it = reach.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry pair = (Map.Entry)it.next();
						String key = (String) pair.getKey();
						if(key.equals(""+msg_rcvd[1])) {
							System.out.println("Data Packet NOT for the current Node. Forward to next hop : " + reach.get(key).next_hop);
							datalink_receive_from_network(msg_rcvd, reach.get(key).next_hop);
						}
					}
				}
			}
		}

		if (msg_rcvd[0] == 'L') {
			
			String key = "" + msg_rcvd[1];
			String seq = "" + msg_rcvd[2] + msg_rcvd[3];
			int s = Integer.parseInt(seq);
			boolean go = false;
		
			if(s > Integer.parseInt(lsp_timer.get(key))) {
				// LSP packet sent to all the neighbours except the original source if the msg is new.
				for (int i1 = 0; i1 < list.length; i1++) {
					if (list[i1] != from) {
						datalink_receive_from_network(msg_rcvd, list[i1]);
					}
				}
						
				//Update entries.
			
				String n = "";
				for(int i= 4; i<msg_rcvd.length; i++) {
					n = n + msg_rcvd[i];
				}
				lsp_timer.put(key, seq);
				lsp_nodes.put(key, n);
			
				// Update reachability depending on this new information.
				char[] toadd = n.toCharArray();			
				int cost = reach.get(key).cost + 1;
				for(int l=0; l<toadd.length;l++){
					boolean isold = true;
					Iterator<Entry<String, destinations>> it = reach.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry pair = (Map.Entry)it.next();
						String key1 = (String) pair.getKey();
						if(key1.equals(""+toadd[l])) {
							if(reach.get(key1).cost > cost) {
								to_reach.cost = cost;
								to_reach.next_hop = reach.get(key).next_hop;
								reach.put(""+toadd[l], to_reach);
							}
							isold = true;
							break;
						}
					}
					if(!isold) {
						to_reach.cost = cost;
						to_reach.next_hop = reach.get(key).next_hop;
						reach.put(""+toadd[l], to_reach);
					}
				}
				
			}
		}
	}

	/***************************** NETWORK LAYER ***********************************************/
	// Make LSR packet and send to neighbours
	@SuppressWarnings("rawtypes")
	public static void network_route(int i) throws Exception {

		// Make LSR packet every 10 sec.
		if (i % 10 == 0) {
			char[] lsr = new char[15];
			lsr[0] = 'L';
			lsr[1] = (char) nodeId;
			lsr_number = (lsr_number + 1) % 100;
			DecimalFormat formatter = new DecimalFormat("00");
			String length = formatter.format(lsr_number);
			char[] l = length.toCharArray();
			lsr[2] = l[0];
			lsr[3] = l[1];

			for (int i1 = 0; i1 < list.length; i1++) {
				lsr[4 + i1] = list[i1];
			}
			for (int i1 = 4 + list.length; i1 < 15; i1++) {
				lsr[i1] = ' ';
			}
			// END of LSP packet.

			// For every neighbour send the LSP packet.
			for (int i1 = 0; i1 < list.length; i1++) {
				datalink_receive_from_network(lsr, list[i1]);
			}
		}
		
		// Check if a node is dead
		String dead = "";
		boolean hasdead = false;
		Iterator<Entry<String, String>> it = lsp_timer.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();	
			String node = (String) pair.getKey();
			String last_time = (String)pair.getValue();
			int lt = Integer.parseInt(last_time)*10;
			if((i-lt) >= 30 ) {
				// Node has died.
				hasdead = true;
				dead = dead+node;
				
				// Check if the dead node is a neighbour.
				boolean n = false;
				int l =0;
				for(int z=0; z<list.length; z++) {
					if(node.equals(""+list[z]))
						n = true;
				}
				
				//Remove from neighbour list if the node is a neighbour
				if(n) {
					char[] nlist = new char[list.length-1];
					for(int z=0; z<list.length; z++) {
						if(node.equals(""+list[z])){
							// Do Nothing
							System.out.println("Node : " + node + "removed from neighbour list of Node : " + nodeId);
						} else {
							nlist[l] = list[z];
							l++;
						}
					}
					list = nlist;
				}
				n = false;
			}
		}
		if(hasdead) {
			char[] node = dead.toCharArray();
			for(int z=0; z<node.length; z++) {
				lsp_nodes.remove(""+node[z]);
				lsp_timer.remove(""+node[z]);
			}
			hasdead = false;
		}
	
	}

	/***************************** TRANSPORT LAYER ***********************************************/
	// Send a new message to the network layer.
	@SuppressWarnings("rawtypes")
	public static void transport_send(String[] args, int ttl) throws Exception {

		// Check if TCP can send a message.
	if(nodeId != destId)
		if (ttl == Integer.parseInt(args[4]) && !msgsent) {			
			msgsent = true;
			if (nodeId == destId) {
				// DO Nothing, since destination is the same name.
			} else {

				// Divide the message into chunks of 5.
				String msg = args[3];
				List<String> ret = new ArrayList<String>(
						(msg.length() + 5 - 1) / 5);
				for (int start = 0; start < msg.length(); start += 5) {
					ret.add(msg.substring(start,
							Math.min(msg.length(), start + 5)));
				}

				// For every substring, make a packet and send to network layer.
				for (String temp : ret) {

					// Add sequence number.
					tcp_seq_number = (tcp_seq_number + 1) % 100;
					DecimalFormat formatter = new DecimalFormat("00");
					String seq = formatter.format(tcp_seq_number);
					
					String msg_to_network = "D" + nodeId + destId + seq + temp;
					int len = msg_to_network.length();
					
					// Store the msg in case it needs to be rexmitted.
					tcp_msg_list.put(""+tcp_seq_number, msg_to_network);			

					// Send the char array with length to network layer.
					char[] network_msg = msg_to_network.toCharArray();
					network_receive_from_transport(network_msg, len,destId);					
				}
			}

		} else {
			// Just Wait.
		}
		
		//Resend a packet if received a nack.
		if(packetlost) {
			
			if(seq_to_resend > tcp_seq_number) {
				// Done if sequence number is greater than last sequence number sent.
				System.out.println("Confirmed Complete Message Transmitted Succesfully");
			}
			else if (seq_to_resend <= tcp_seq_number) {
			// Resend the packet if number is less than last sequence number sent.
				
				Iterator<Entry<String, String>> it = tcp_msg_list.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry pair = (Map.Entry)it.next();		        
					if( pair.getKey().equals(""+ seq_to_resend)){
						String msg_to_resend = (String) pair.getValue();
						int len = msg_to_resend.length();
						char[] network_msg = msg_to_resend.toCharArray();
						System.out.println("Sending a message : " + msg_to_resend + " to "+resend_dest);
						network_receive_from_transport(network_msg, len,resend_dest);
					}
				}
			}
			packetlost = false;
		}
		
		// Check to see if a nack has to be sent.
		boolean nacksent = false;
		String key = "";
		Iterator<Entry<String, String>> it = nack_to_send.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();	
			key = (String) pair.getKey();
			String value = (String)pair.getValue();
			char[] val = value.toCharArray();
			String t = "";
			for(int i=2; i<val.length;i++) {
				t = t + val[i]; 
			}
			int time = Integer.parseInt(t);
				
				
			// If time has is t+5 for more then send the Nack.
			if(timer > time+4 ) {
				char dest = pair.getKey().toString().charAt(0);
				String seq = "" + val[0] + val[1];
				int number = Integer.parseInt(seq);
				number = number + 1;
				DecimalFormat formatter = new DecimalFormat("00");
				String nseq = formatter.format(number);
				String nack = "N" + nodeId + dest + nseq;
				
				char[] nack_to_network = nack.toCharArray();
				System.out.println("Sending a message : " + nack + " to Node " + dest);
				network_receive_from_transport(nack_to_network, nack.length(),dest);
				nacksent = true;
			}				
	
		}
		if(nacksent)
			nack_to_send.remove(key);
	}

	/***************************** TRANSPORT LAYER **********************************************/
	// Receive a new message from network layer.
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void transport_receive_from_network(char[] msg, int len,
			char from) {
		
		// If its a data message.
		if(msg[0] == 'D') {
			
			String seqn = "";
			seqn = seqn + msg[3] + msg[4];
			int seq = Integer.parseInt(seqn);
			String seq_msg = "";
			for (int i = 5; i<len ; i++ ) {
				seq_msg = seq_msg + msg[i];
			}
			String source = "";
			source = source + msg[1];
			List<String> rcvd = new ArrayList<String>();
			
			// Store the current timer, source and the sequence number of the packet.
			nack_to_send.put(source,seqn+timer);
			
			// Search over current received messages to see if its a part of already received message.
			boolean found = false;
			Iterator it = all_msg_rcvd.entrySet().iterator();
		    while (it.hasNext()) {
		        Map.Entry pair = (Map.Entry)it.next();		        
		        if( pair.getKey().equals(source)){
		        	found = true;
		        	rcvd = (List<String>) pair.getValue();
		        ;
		        	int length = rcvd.size();
		        	if(length < seq) {
		        		for(int i = length; i< seq; i++) {
		        			rcvd.add("");
		        		}
		        		rcvd.add(seq_msg);
		        	} else if ( length > seq) {
		        		rcvd.set(seq, seq_msg);
		        	} else {
		        		rcvd.add(seq_msg);
		        	}
		        	all_msg_rcvd.put(source, rcvd);
		        }
		    }
		    
		    // Message is from a new source.
		    if (!found) {
		    	if(seq == 0) {
		    		rcvd.add(seq_msg);
		    	} else {
		    		for(int i = 0; i< seq; i++) {
	        			rcvd.add("");
	        		}
	        		rcvd.add(seq_msg);
		    	}
		    	all_msg_rcvd.put(source, rcvd);
		    }
		}
		
		// If its a Neg Ack
		if(msg[0] == 'N') {
			resend_dest = msg[1];
			String seqn = "";
			seqn = seqn + msg[3] + msg[4];
			seq_to_resend = Integer.parseInt(seqn);
			packetlost = true;
		}
	}
	
	/***************************** TRANSPORT LAYER ***********************************************/
	// Write all the messages received.
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void transport_output() throws IOException {
		String filePath = "node" + nodeId + "received.txt";
		BufferedWriter WriteFile = new BufferedWriter(new FileWriter(filePath,
				true));
		Iterator it = all_msg_rcvd.entrySet().iterator();
		 while (it.hasNext()) {
				Map.Entry pair = (Map.Entry)it.next();	
				List<String> rcvd = new ArrayList<String>();
		        rcvd = (List<String>) pair.getValue();
		        String message = "";
		        for (String temp : rcvd) {
		            message = message + temp;
		        }
		        WriteFile.write(message);
				WriteFile.write('\n');
		    }		
		WriteFile.close();
	}

}

