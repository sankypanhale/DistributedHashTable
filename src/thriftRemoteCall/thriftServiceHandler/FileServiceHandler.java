package thriftRemoteCall.thriftServiceHandler;

import java.beans.FeatureDescriptor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.w3c.dom.NodeList;

import ch.qos.logback.core.pattern.parser.Node;

import thriftRemoteCall.thriftUtil.FileStore;
import thriftRemoteCall.thriftUtil.NodeID;
import thriftRemoteCall.thriftUtil.RFile;
import thriftRemoteCall.thriftUtil.RFileMetadata;
import thriftRemoteCall.thriftUtil.SystemException;

import java.util.Date;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class FileServiceHandler implements FileStore.Iface{

	private NodeID meNode;
	private List<NodeID> fingertable;
	private NodeID sucessor;
	private NodeID predecessor;
	private HashMap<String, RFile> filemap;
	private String workingDir;
	private File directory;

	private static BigInteger tempBig;
	public FileServiceHandler(int port) {
		filemap = new HashMap<String, RFile>();
		//	fingertable = new ArrayList<NodeID>();
		meNode = new NodeID();
		meNode.port = port;

		try {
			meNode.ip = Inet4Address.getLocalHost().getHostAddress();

		} catch (UnknownHostException e) {
			System.out.println("Error while getting IP of machine..!");
			System.exit(0);
		}
		meNode.id = getSHAHash(meNode.ip+":"+port);

		System.out.println("Big integer valued to: "+tempBig);
		System.out.println("Ip is current machine is: "+meNode.ip+":"+port);
		System.out.println("Current Node Hash key is: "+meNode.id);
		//sucessor = new NodeID();

		//calling function to initialise fingertable
		init_fingertable();

		System.out.println("FIngertable after init:"+fingertable);
		System.out.println("Size of finger table is:"+fingertable.size());
		workingDir = "./files/";
		//code to clean the working directory
		directory = new File(workingDir);
		for(File f: directory.listFiles()) 
			f.delete(); 
	}



	@Override
	public void writeFile(RFile rFile) throws SystemException,
	TException {
		SystemException excep = null;

		String filename = rFile.getMeta().getFilename();
		String filecontent = rFile.getContent();
		FileWriter fw = null;
		BufferedWriter bw = null;
		File file = null;
		Date date = null;
		RFile updateFile = null;
		RFileMetadata rMeta = null;
		StringBuffer sbuff = null;
		Boolean isnew = null;

		rMeta = rFile.getMeta();

		String trimfilename = null;
		String[] splitvalues = null;
		splitvalues = filename.split("/");
		trimfilename = splitvalues[splitvalues.length-1];

		System.out.println("trimmed name is: "+ trimfilename);
		file = new File(workingDir+trimfilename);

		// code to generate SHA-256 hash code and save it to RFile object
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(filecontent.getBytes());
			byte[] digest = md.digest();
			sbuff = new StringBuffer();
			for (byte b : digest) {
				sbuff.append(String.format("%02x", b & 0xff));
			}
		} catch (NoSuchAlgorithmException e) {
			excep = new SystemException();
			excep.setMessage("Error while generating SHA-256 Hash Code.");
			throw excep;		
		}

		if((updateFile = filemap.get(filename)) != null)
		{
			isnew = false;
			//file exists
			if(updateFile.getMeta().getOwner().equals(rFile.getMeta().getOwner()))
			{
				//if file belongs to same owner allow the update of file
				date = new Date();

				updateFile.getMeta().setUpdated(date.getTime());
				updateFile.getMeta().setVersion(updateFile.getMeta().getVersion()+1);
				updateFile.setContent(filecontent);
				updateFile.getMeta().setContentLength(filecontent.length());
				updateFile.getMeta().setContentHash(sbuff.toString());

				filemap.put(filename, updateFile);
			}
			else
			{
				excep = new SystemException();
				excep.setMessage("File name already exists...!!");
				throw excep;
			}
		}
		else
		{
			isnew = true;
			//file does not exist
			date = new Date();	

			rMeta.setCreated(date.getTime());
			rMeta.setUpdated(date.getTime());
			rMeta.setDeleted(0);
			rMeta.setVersion(0);
			rMeta.setContentLength(filecontent.length());
			rMeta.setContentHash(sbuff.toString());

			rFile.setMeta(rMeta);

			filemap.put(filename, rFile);		// adding value to hashmap
		}
		// if file doesn't exists, then create it
		try {
			if(isnew == true)				
			{
				file.createNewFile();
			}
			fw = new FileWriter(file.getAbsoluteFile());
			bw = new BufferedWriter(fw);
			bw.write(filecontent);			
			bw.close();
		}catch (IOException e) {
			excep = new SystemException();
			excep.setMessage("Error in file writing ...!!");
			throw excep;
		}finally
		{
			try {
				bw.close();
			} catch (IOException e) {
				excep = new SystemException();
				excep.setMessage("Error in file writing ...!!");
				throw excep;
			}
		}
	}

	@Override
	public RFile readFile(String filename, String owner)
			throws SystemException, TException {
		RFile rFile = null;
		//boolean fileexist = false;
		SystemException excep = null;
		if(filemap.containsKey(filename))
		{
			// file metadata exist on server
			/*directory = new File(workingDir);
			for(File f: directory.listFiles()) 
			{
				if(f.getName().equals(filename))
				{
					fileexist = true;
					break;
				}
			}*/
			//if(fileexist)
			//{
			//file exist on the server


			rFile = filemap.get(filename);
			if(rFile.getMeta().getDeleted() != 0)
			{
				excep = new SystemException();
				excep.setMessage("File have been deleted..!!");
				throw excep;
			}
			if(owner.equals(rFile.getMeta().getOwner()))
			{
				//files belongs to same user
				return rFile;
			}
			else
			{
				//file does not belong to user
				excep = new SystemException();
				excep.setMessage("File does not belong to user "+owner);
				throw excep;
			}
			/*}
			else
			{
				excep = new SystemException();
				excep.setMessage("File have been deleted..!!");
				throw excep;
			}*/
		}
		else
		{
			excep = new SystemException();
			excep.message = "File does not exist";
			excep.setMessage("File does not exist");
			throw excep;
		}
	}

	@Override
	public void deleteFile(String filename, String owner)
			throws SystemException, TException {
		RFile rFile = null;
		RFile updateFile = null;
		File file = null;
		Date date = null;
		SystemException excep = null;
		if(filemap.containsKey(filename))
		{
			// file with filename exist on server
			rFile = filemap.get(filename);
			if(owner.equals(rFile.getMeta().getOwner()))
			{
				//files belongs to same user
				file = new File(workingDir+filename);
				if(file.delete())
				{
					updateFile = filemap.get(filename);
					date = new Date();
					updateFile.getMeta().setDeleted(date.getTime());
					//System.out.println("Deleted at: "+ updateFile.getMeta().getDeleted());
					filemap.put(filename, updateFile);
				}
				else
				{
					//System.out.println("Error in file deletion!");
					excep = new SystemException();
					excep.setMessage("Error in file deletion...!!");
					throw excep;
				}
			}
			else
			{
				//diff user
				//System.out.println("File does not belong to user "+owner);
				excep = new SystemException();
				excep.setMessage("File does not belong to user "+owner);
				throw excep;
			}
		}
		else
		{
			//System.out.println("File does not exist!!");
			excep = new SystemException();
			//excep.message = "File does not exist.";
			excep.setMessage("File does not exist.");
			throw excep;
		}
	}

	/*@Override
	public void setFingertable(List<NodeID> node_list) throws TException {

		//save finger table for local reference
		//this.setFingertable(node_list);
		fingertable = node_list;

		//save the successor for current node depending on first entry
		sucessor = fingertable.get(0);
		//this.setSucessor(fingertable.get(0));
	}*/


	@Override
	public NodeID findSucc(String key) throws SystemException, TException {

		TTransport transport = null;
		TProtocol protocol = null;
		//System.out.println("Hash key for owner:filename is: " + key);
		NodeID nodetoconnect = null;
		NodeID nodetoreturn = null;

		//BigInteger equivalent of key
		byte[] b = new BigInteger(key,16).toByteArray();
		BigInteger tempBig2 = new BigInteger(b);
		//		System.out.println("Biginterger for key is:"+ tempBig2);

		//	System.out.println("Findsucc called");

		SystemException excep = null;
		try{
			if(key.compareToIgnoreCase(meNode.id) == 0)
			{
				//client requests is satisfied at the requested node itself
				return meNode;
			}
			else if(key.compareToIgnoreCase(meNode.id) > 0)
			{
				//key is greater than meNode.id
				if(key.compareToIgnoreCase(fingertable.get(0).id) <= 0)
				{
					//key is less than first finger table entry
					//temp/go directly to successor function
					//no need to go further in the findSucc loop
					//nodetoconnect = meNode;

					// return sucessor of current node
					return this.getNodeSucc();
				}
			}
			// removed else: if no above condition then just go for it

			nodetoconnect = findPred(key);
			//System.out.println("I have found predesor : "+ nodetoconnect);

			if(nodetoconnect.getId().equalsIgnoreCase(meNode.getId()))
			{
				// if the sucessor is same node
				//no need to go for RPC
				//call local method
				return this.getNodeSucc();
			}

			//Sanket: new condition to handle equal to case in finger table
			if(nodetoconnect.getId().equalsIgnoreCase(key))
			{
				return nodetoconnect;
			}

			//call findSucc(recursive call) on nodetoconnect using RMI
			if(nodetoconnect != null)
			{
				transport = new TSocket(nodetoconnect.ip, nodetoconnect.port);
				transport.open();

				protocol = new TBinaryProtocol(transport);
				FileStore.Client client = new FileStore.Client(protocol);
				//System.out.println("Calling sucessor of : "+nodetoconnect);
				nodetoreturn = client.getNodeSucc();
				transport.close();
				return nodetoreturn;
			}
			else
			{
				excep = new SystemException();
				excep.setMessage("Node corresponding to key not found..!!");
				throw excep;
			}
		}
		catch (SystemException e) {
			throw e;
		}
		catch (IndexOutOfBoundsException e) {
			excep = new SystemException();
			excep.setMessage("Finger table not initialised properly..!!");
			throw excep;
		}
	}




	@Override
	public NodeID findPred(String key) throws SystemException, TException {
		TTransport transport = null;
		TProtocol protocol = null;
		NodeID pred = null;
		NodeID next = null;
		NodeID nodetoreturn = null;
		int i;
		SystemException excep;
		boolean recurse = true;
		//	System.out.println("findPred called on "+this.meNode + "for the key: "+key);
		if(key.compareToIgnoreCase(meNode.id) > 0)
			//if(key.compareToIgnoreCase(meNode.id) > 0 || key.compareToIgnoreCase(fingertable.get(0).getId()) <= 0)
		{
			//key is greater than meNode.id
			if(key.compareToIgnoreCase(fingertable.get(0).getId()) <= 0)
			{
				//key is less than or equal to first finger table entry
				//go directly to successor function
				//no need to go further in the findSucc loop
				//System.out.println("Special Case achived..!");
				// return current node
				return meNode;
			}
		}

		//Sanket: duplicate logic
		if(key.compareToIgnoreCase(fingertable.get(0).getId()) == 0)
		{
			return meNode;
		}


		if(meNode.getId().compareToIgnoreCase(fingertable.get(0).getId()) > 0)
		{
			//condition is satisfied for the last node in the chord
			//first finger table entry will less than current node key

			//System.out.println("I am in new base case 1 ");

			//BigInteger equivalent of key so that perform arithmetic 
			byte[] b = new BigInteger(key,16).toByteArray();
			BigInteger keyBig = new BigInteger(b);
			//System.out.println("Biginterger for key is:"+ keyBig);


			b = new BigInteger(fingertable.get(0).getId(),16).toByteArray();
			keyBig = new BigInteger(b);
			//System.out.println("Biginterger for first finger table entry at 92 is:"+ keyBig);

			if((key.compareToIgnoreCase(fingertable.get(0).getId()) < 0) || (key.compareToIgnoreCase(meNode.getId()) > 0))
			{
				//first finger table entry is greater than the key
				//means that first entry will be successor of the key
				//i.e current node must be predecesssor
				//System.out.println("I am in new base case 2");
				return meNode;
			}
		}
		try
		{
			for(i=0; i < fingertable.size()-1; i++)
			{
				pred = fingertable.get(i);
				next = fingertable.get(i+1);
				/*if(i==253)
					System.out.println("Test done..!!");
				if(pred.getPort()== 9092)
				{
					System.out.println("current entry port: "+pred.getPort());
					System.out.println("next entry port: "+next.getPort());
				}*/
				if(pred != null && next != null)
				{
					if(key.compareToIgnoreCase(pred.getId()) == 0)
					{
						System.out.println("Exact key found!!!");
						// if key matches to current node
						nodetoreturn = pred;
						//return pred;
						recurse = false;
						break;
					}
					if(pred.getId().compareToIgnoreCase(next.getId()) > 0	)
					{
						//additional case if i entry is greater than i+1
						//in this case just return the first entry blindly
						if(pred.getId().compareToIgnoreCase(key) <= 0 || next.getId().compareToIgnoreCase(key) >=0)
						{	
							nodetoreturn = pred;
							recurse = false;
							break;
						}

					}
					else if(key.compareToIgnoreCase(pred.getId()) > 0)
					{
						if(key.compareToIgnoreCase(next.getId()) <= 0)
						{
							// if key is in between finger table entry i and i+1
							//System.out.println("Entry between i and i+1 found");
							nodetoreturn = pred;
							//return pred;
							break;
						}
					}
				}
			}
			//if(recurse)
			// go in only if nodeto return is not set
			if(nodetoreturn == null)
			{
				// this is to avoid problem when reverse entry comes at last
				i++; //i++;
				if(i == fingertable.size())
				{
					// if last record is reached in finger table return last node
					//System.out.println("Last entry is returned..!!");
					nodetoreturn = next;
					//return next;
				}
			}
		}catch(IndexOutOfBoundsException e)
		{
			excep = new SystemException();
			excep.setMessage("Finger table is not intialised properely..!!");
			throw excep;
		}
		// in other condition return null

		//Sanket: to avoid the infinite loop
		if(nodetoreturn.getId().equalsIgnoreCase(this.getMeNode().getId()))
		{
			//to avoid the infinite loop the returned node is same as current node
			//System.out.println("I am avoiding the infinite loop..!!");
			recurse = false;
		}
		if(recurse){
			if(nodetoreturn != null)
			{
				try{
					transport = new TSocket(nodetoreturn.ip, nodetoreturn.port);
					transport.open();

					protocol = new TBinaryProtocol(transport);
					FileStore.Client client = new FileStore.Client(protocol);
					//System.out.println("Calling findPredecessor of : "+nodetoreturn);
					nodetoreturn = client.findPred(key);
					transport.close();
				}
				catch (TTransportException x)
				{
					//System.out.println("Error while looping in hops...!!");
					excep = new SystemException();
					excep.setMessage("Error while looping in hops...!!");
					throw excep;
					//System.exit(0);
				}
				catch (TException x) {
					x.printStackTrace();
				} 
				return nodetoreturn;
			}
			else
			{
				//System.out.println("Error condition..!!");
				excep = new SystemException();
				excep.setMessage("Error while looping in hops...!!");
				throw excep;
			}
		}
		else
			return nodetoreturn;
	}



	@Override
	public NodeID getNodeSucc() throws SystemException, TException {
		SystemException excep = null;
		if(sucessor != null)
		{
			//return this.sucessor;
			//return sucessor;
			return this.fingertable.get(0);
		}
		else
		{
			excep = new SystemException();
			excep.setMessage("Node not found corresponding to key...!!");
			throw excep;	
		}
	}	

	public static String getSHAHash(String content)
	{
		String tobehashed = null;
		tobehashed = content;
		StringBuffer sbuff = null;
		sbuff = new StringBuffer();
		SystemException excep = null;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(tobehashed.getBytes());
			byte[] digest = md.digest();

			tempBig = new BigInteger(1,digest);

			sbuff = new StringBuffer();
			for (byte b : digest) {
				sbuff.append(String.format("%02x", b & 0xff));
			}
		} catch (NoSuchAlgorithmException e) {
			excep = new SystemException();
			excep.setMessage("Error while generating MD5 Hash Code.");			
		}
		return sbuff.toString();
	}



	@Override
	public void setNodePred(NodeID nodeId) throws SystemException, TException {
		// TODO Auto-generated method stub
		this.predecessor = nodeId;
	}



	@Override
	public void updateFinger(int idx, NodeID nodeId) throws SystemException,
	TException {
		// TODO Auto-generated method stub
		//this.fingertable.set(idx, nodeId);
		this.getFingertable().set(idx, nodeId);
	}

	/*	@Override
	public void updateFinger(int idx, NodeID nodeId) throws SystemException,
	TException {
		TTransport transport = null;
		TProtocol protocol = null;
		// TODO Auto-generated method stub
		NodeID nodepred = null;
		if(this.meNode.getId().compareToIgnoreCase(nodeId.getId()) <= 0)
		{
			if(this.fingertable.get(idx).getId().compareToIgnoreCase(nodeId.getId()) > 0)
			{
				this.fingertable.set(idx, nodeId);
				nodepred = this.predecessor;

				//recursive call
				transport = new TSocket(nodepred.getIp(), nodepred.getPort());
				transport.open();
				protocol = new TBinaryProtocol(transport);
				FileStore.Client client3 = new FileStore.Client(protocol);
				client3.updateFinger(idx, nodeId);
				transport.close();

			}
		}
	}
	 */
	@Override
	public List<RFile> pullUnownedFiles() throws SystemException, TException {
		// TODO Auto-generated method stub
		List<RFile> pulledfiles = null;
		String currentkey = null;
		int i;
		RFile currentfile = null;
		String currentowner = null;
		String currentfilename =null;
		String[] splitvalues = null;
		String trimfilename = null;
		File file = null;

		

		NodeID newnode = this.predecessor;

		if(this.filemap.size() != 0)
		{
			pulledfiles = new ArrayList<RFile>();
			//special case
			if(this.meNode.getId().compareToIgnoreCase(newnode.getId()) < 0)
			{
				//code to check if predecessor of currentnode(i.e. = newnode) is greater than current
				for (Map.Entry<String, RFile> entry : this.filemap.entrySet()) {
					currentfile = entry.getValue();
					currentowner = currentfile.getMeta().getOwner();
					currentfilename = currentfile.getMeta().getFilename();
					splitvalues = currentfilename.split("/");
					trimfilename = splitvalues[splitvalues.length-1];

					//System.out.println("trimmed name is: "+ trimfilename);
					//tobehashed = owner+":"+filename;
					currentkey = getSHAHash(currentowner+":"+trimfilename);

					if(newnode.getId().compareToIgnoreCase(currentkey) >= 0)
					{
						//current key is less than newnode
						if(this.meNode.getId().compareToIgnoreCase(currentkey) < 0)
						{
							//current key is greater than meNode(this)
							pulledfiles.add(currentfile);    

							//delete the current file
							file = new File(workingDir+trimfilename);
							file.delete();

							//delete that entry from hashmap
							//	filemap.remove(currentfilename);
						}
					}
				}
			}
			else if(this.meNode.getId().compareToIgnoreCase(newnode.getId()) > 0)
			{
				//normal case
				//code to check if predecessor of currentnode(i.e. = newnode) is less than current
				for (Map.Entry<String, RFile> entry1 : this.filemap.entrySet()) {
					currentfile = entry1.getValue();
					currentowner = currentfile.getMeta().getOwner();
					currentfilename = currentfile.getMeta().getFilename();
					splitvalues = currentfilename.split("/");
					trimfilename = splitvalues[splitvalues.length-1];

					//System.out.println("trimmed name is: "+ trimfilename);
					//tobehashed = owner+":"+filename;
					currentkey = getSHAHash(currentowner+":"+trimfilename);

					if(this.meNode.getId().compareToIgnoreCase(currentkey) >= 0 &&
							newnode.getId().compareToIgnoreCase(currentkey) < 0)
					{
						//current key is less than meNode &&
						//current key is greater than newnode
						//that means key falls in the range of newnode and it's sucessor
						//hence not to be sent anywhere
					}
					else
					{
						//key out of range, send it
						pulledfiles.add(currentfile);    

						//delete the current file
						file = new File(workingDir+trimfilename);
						file.delete();

						//delete that entry from hashmap
						//filemap.remove(currentfilename);
					}

				}
			}
		}
		else
		{
			System.out.println("No file added to server");
			/*SystemException e = new SystemException();
			e.setMessage("No File added to server yet");
			throw e;*/
		}
		//delete that entry from hashmap
		for (int j = 0; j < pulledfiles.size(); j++) {
			this.filemap.remove(pulledfiles.get(j).getMeta().getFilename());
		}
		return pulledfiles;
	}



	@Override
	public void pushUnownedFiles(List<RFile> files) throws SystemException,
	TException {
		// TODO Auto-generated method stub
		for(int j=0; j < files.size();j++)
		{
			RFile file = files.get(j);
			this.filemap.put(file.getMeta().getFilename(), file);
		}
	}

	public String fileNodeKey(RFile filenode)
	{
		String filekey = null;
		String owner = filenode.getMeta().getOwner();
		String filename = filenode.getMeta().getFilename();
		String temp = owner+":"+filename;
		filekey = getSHAHash(temp);
		return filekey;
	}

	@Override
	public void join(NodeID nodeId) throws SystemException, TException {
		// TODO Auto-generated method stub
		TTransport transport = null;
		TProtocol protocol = null;

		int i;
		NodeID nodeentrytoadd = null;
		NodeID nodetosetaspredecessor = null; 
		String key = null;
		BigInteger bigtwo = new BigInteger("2");
		BigInteger twopowervalue = null;
		BigInteger bignewkey = null;


		//BigInteger equivalent of new node key
		byte[] b = new BigInteger(this.meNode.getId(),16).toByteArray();
		BigInteger tempBig2 = new BigInteger(b);
		System.out.println("Biginterger for newly joining node is:"+ tempBig2);

		if(this.getMeNode().getId().equals(nodeId.getId()))
		{
			//condition is satisfied for the first join call
			//no need to reintialise the finger table
			System.out.println("First Join call");
		}
		else
		{
			System.out.println("1+ Join call");
			//reinitialize the finger table
			//fingertable = new ArrayList<NodeID>();
			for(i=1; i<=256; i++)
			{
				twopowervalue = bigtwo.pow(i-1);
				bignewkey = twopowervalue.add(tempBig2);
				key = bignewkey.toString(16);

				//make RPC call to find successor of the above key
				transport = new TSocket(nodeId.getIp(), nodeId.getPort());
				transport.open();
				protocol = new TBinaryProtocol(transport);
				FileStore.Client client = new FileStore.Client(protocol);
				nodeentrytoadd = client.findSucc(key);
				transport.close();
				//add the successor entry to the fingertable of current node
				this.fingertable.set(i-1,nodeentrytoadd);


				/*if(i == 1)
				{
					//set the predecessor of the current node
					// the predecessor of the successor 
				}
				 */
			}

			//set the successor of the newnode
			this.sucessor = this.fingertable.get(0);

			//set the predecessor of the newnode
			//the predecessor of the successor will be the predecessor of the newnode
			// make RPC call to successor to get it's predecessor
			transport = new TSocket(this.getSucessor().getIp(), this.getSucessor().getPort());
			transport.open();
			protocol = new TBinaryProtocol(transport);
			FileStore.Client client = new FileStore.Client(protocol);
			//RPC call on the successor node to findPred method with 
			// key = key of successor which will give predecessor of successor
			nodetosetaspredecessor = client.findPred(this.getSucessor().getId());
			this.predecessor = nodetosetaspredecessor;
			transport.close();

			//update the predecessor of the successor
			// make RPC call to successor to set it's predecessor to newnode
			transport = new TSocket(this.getSucessor().getIp(), this.getSucessor().getPort());
			transport.open();
			protocol = new TBinaryProtocol(transport);
			FileStore.Client client2 = new FileStore.Client(protocol);
			client2.setNodePred(this.meNode);
			transport.close();


			//call update others method to update finger table
			//update_others(this.sucessor,this.meNode);
			update_others("join");

			System.out.println("Printing the new finger table for newnode:"+this.fingertable);
			System.out.println("New size of finger table is :"+this.fingertable.size());

			//call the pullUnownedFiles files to sucessor node to get files from it
			transport = new TSocket(this.getSucessor().getIp(), this.getSucessor().getPort());
			transport.open();
			protocol = new TBinaryProtocol(transport);
			FileStore.Client client3 = new FileStore.Client(protocol);
			List<RFile> pulledfiles = client3.pullUnownedFiles();
			transport.close();
			if(pulledfiles != null){
				//added files to the current node's filemap
				for(int j=0; j < pulledfiles.size();j++)
				{
					RFile file = pulledfiles.get(j);
					this.filemap.put(file.getMeta().getFilename(), file);
				}
				System.out.println("Files pulled sucessfully...!!");
			}
		}
		/*		transport = new TSocket("127.0.1.1",9091);
		transport.open();
		protocol = new TBinaryProtocol(transport);
		FileStore.Client client5 = new FileStore.Client(protocol);
		NodeID tempsucc = client5.findSucc("a6b873b993b8001a41955edf17f4e4b23758155e49d5ab4d1e8c7daf93e8ed0d");
		System.out.println("temp succ is "+tempsucc);
		transport.close();*/
	}



	@Override
	public void remove() throws SystemException, TException {
		// TODO Auto-generated method stub
		TTransport transport = null;
		TProtocol protocol = null;

		update_others("remove");
		System.out.println("remove done...!!");

		transport = new TSocket(this.getSucessor().getIp(),this.getSucessor().getPort());
		transport.open();
		protocol = new TBinaryProtocol(transport);
		FileStore.Client client4 = new FileStore.Client(protocol);
		client4.setNodePred(this.predecessor);
		transport.close();
		System.out.println("UPdated the predecessor..!!");

		//platform for push funtion
		transport = new TSocket(this.getSucessor().getIp(), this.getSucessor().getPort());
		transport.open();
		protocol = new TBinaryProtocol(transport);
		FileStore.Client client3 = new FileStore.Client(protocol);
		List<RFile> files = null;
		files = new ArrayList<RFile>();
		for (Map.Entry<String, RFile> entry : this.filemap.entrySet()) {
			files.add(entry.getValue());
		}
		client3.pushUnownedFiles(files);
		transport.close();
	}

	//////////////////////////////////////////////////////////////

	/*	public void update_others()
	{
		TTransport transport = null;
		TProtocol protocol = null;
		int i;
		NodeID nodep = null;
		BigInteger bigkey = null;
		BigInteger bigtwo = new BigInteger("2");
		BigInteger twopowervalue = null;
		BigInteger subvalue = null;
		BigInteger maxvalue = null;
		SystemException excep = null;
		NodeID nodeq = null;
		String key = null;

		//for(i=0; i<256; i++)
		for(i=1; i<=256; i++)
		{
			bigkey = getBigIntegerEquivalent(this.getMeNode().id);
			if(i==256)
			{
				System.out.println("I have reached the final entry in fingertable while updating");
			}
			//calculate (newnode - (2^i))
			twopowervalue = bigtwo.pow(i);
			subvalue = bigkey.subtract(twopowervalue);
			if(subvalue.signum() == -1)
			{
				System.out.println("Negative subtarction..!!");
				maxvalue = bigtwo.pow(256);
				subvalue = subvalue.add(maxvalue);
			}
			key = getHexStringEquuivalent(subvalue);
			try {
				//get node p
				nodep = findPred(key);

				//the below code will handle the special case when (newnode - (2^i)) is 
				//actual physical node
				transport = new TSocket(nodep.getIp(), nodep.getPort());
				transport.open();
				protocol = new TBinaryProtocol(transport);
				FileStore.Client client3 = new FileStore.Client(protocol);
				nodeq = client3.getNodeSucc();
				if(key.compareToIgnoreCase(nodeq.getId()) == 0)
				{
					//here special case is satisfied
					nodep = nodeq;
				}
				transport.close();

				transport = new TSocket(nodep.getIp(), nodep.getPort());
				transport.open();
				protocol = new TBinaryProtocol(transport);
				FileStore.Client client = new FileStore.Client(protocol);
				//client.updateFinger(i, this.meNode);
				client.updateFinger(i-1, this.meNode);
				transport.close();

			} catch (SystemException e) {
				//throw e;
			} catch (TException e) {
				//throw e;
			}
		}
	}
	 */	
	////// *************************** ?? /////////////////



	public void update_others(String operation)
	{
		TTransport transport = null;
		TProtocol protocol = null;
		int i;
		NodeID nodep = null;
		BigInteger bigkey = null;
		BigInteger bigtwo = new BigInteger("2");
		BigInteger twopowervalue = null;
		BigInteger subvalue = null;
		BigInteger maxvalue = null;
		SystemException excep = null;
		NodeID nodeq = null;
		String key = null;
		boolean change = false;
		for(i=0; i<256; i++)
			//for(i=1; i<=256; i++)
		{
			bigkey = getBigIntegerEquivalent(this.getMeNode().id);
			//calculate (newnode - (2^i))
			twopowervalue = bigtwo.pow(i);
			subvalue = bigkey.subtract(twopowervalue);
			if(subvalue.signum() == -1)
			{
				System.out.println("Negative subtarction..!!");
				maxvalue = bigtwo.pow(255);
				subvalue = subvalue.add(maxvalue);
			}
			key = getHexStringEquuivalent(subvalue);
			//get node p
			try {
				nodep = findPred(key);
				if(operation.equals("join"))
				{
					updateExtenstionJoin(nodep,key,i);
				}
				else if(operation.equals("remove"))
				{
					updateExtenstionRemove(nodep,key,i);
				}
				/*
				//the below code will handle the special case when (newnode - (2^i)) is 
				//actual physical node
				if(nodep.getId().compareToIgnoreCase(this.meNode.getId()) == 0)
				{
					//node need not to do the RPC
					//to avoid the calling RPC on the the same node

				}					
				else
				{
					transport = new TSocket(nodep.getIp(), nodep.getPort());
					transport.open();
					protocol = new TBinaryProtocol(transport);
					FileStore.Client client3 = new FileStore.Client(protocol);
					nodeq = client3.getNodeSucc();
					if(key.compareToIgnoreCase(nodeq.getId()) == 0)
					{
						//here special case is satisfied
						nodep = nodeq;
					}
					transport.close();
				}
				//calculate (p + 2^i) = addvalue
				BigInteger pkey = getBigIntegerEquivalent(nodep.getId());
				BigInteger addvalue = pkey.add(twopowervalue);

				//check if this addvalue is in between pred(newnode) and newnode
				//convert this add value to hexString to compare
				String addValString = getHexStringEquuivalent(addvalue);

				if(addValString.compareToIgnoreCase(this.predecessor.getId()) > 0)
				{
					//the add value should be greater than predecessor of newnode
					//check the condition why added equal to sign
					if(addValString.compareToIgnoreCase(this.meNode.getId()) <= 0)
					{
						//the add value should be less than the newnode

						//if both above conditions are satisfied then make RPC
						//call to updatefinger on node P
						if(nodep.getId().compareToIgnoreCase(this.meNode.getId()) == 0)
						{
							//node need to do the RPC
							//to avoid the calling RPC on the the same node
							updateFinger(i-1, this.meNode);
						}
						else{
							transport = new TSocket(nodep.getIp(), nodep.getPort());
							transport.open();
							protocol = new TBinaryProtocol(transport);
							FileStore.Client client = new FileStore.Client(protocol);
							//client.updateFinger(i, this.meNode);
							client.updateFinger(i-1, this.meNode);
							transport.close();
						}
					}
				}*/
			} catch (SystemException e) {
				//throw e;
			} catch (TException e) {
				//throw e;
			}
		}
	}
	public void updateExtenstionJoin(NodeID nodep,String key,int i)
	{
		TTransport transport = null;
		TProtocol protocol = null;
		NodeID nodeq = null;
		BigInteger twopowervalue = null;
		BigInteger bigtwo = new BigInteger("2");
		twopowervalue = bigtwo.pow(i);

		if(i==255)
			System.out.println("Checking");

		try{
			//the below code will handle the special case when (newnode - (2^i)) is 
			//actual physical node
			if(nodep.getId().compareToIgnoreCase(this.meNode.getId()) == 0)
			{
				//node need not to do the RPC
				//to avoid the calling RPC on the the same node

			}					
			else
			{
				transport = new TSocket(nodep.getIp(), nodep.getPort());
				transport.open();
				protocol = new TBinaryProtocol(transport);
				FileStore.Client client3 = new FileStore.Client(protocol);
				nodeq = client3.getNodeSucc();
				if(key.compareToIgnoreCase(nodeq.getId()) == 0)
				{
					//here special case is satisfied
					nodep = nodeq;
				}
				transport.close();
			}
			//calculate (p + 2^i) = addvalue
			BigInteger pkey = getBigIntegerEquivalent(nodep.getId());
			BigInteger addvalue = pkey.add(twopowervalue);

			//check if this addvalue is in between pred(newnode) and newnode
			//convert this add value to hexString to compare
			String addValString = getHexStringEquuivalent(addvalue);

			//Special Case to check when pred(newnode) is greater than newnode
			if(this.meNode.getId().compareToIgnoreCase(this.predecessor.getId()) < 0)
			{
				if(addValString.compareToIgnoreCase(this.meNode.getId()) <= 0 || addValString.compareToIgnoreCase(this.predecessor.getId()) >= 0)
				{
					//key is less than newnode
					if(nodep.getId().compareToIgnoreCase(this.meNode.getId()) == 0)
					{
						//no need to do the RPC
						//to avoid the calling RPC on the the same node
						//updateFinger(i-1, this.meNode);
						updateFinger(i, this.meNode);
					}
					else{
						transport = new TSocket(nodep.getIp(), nodep.getPort());
						transport.open();
						protocol = new TBinaryProtocol(transport);
						FileStore.Client client = new FileStore.Client(protocol);
						//client.updateFinger(i, this.meNode);
						//client.updateFinger(i-1, this.meNode);
						client.updateFinger(i, this.meNode);
						transport.close();
					}
					NodeID recursenode = findPred(nodep.getId());
					updateExtenstionJoin(recursenode, key, i);
				}
			}



			if(addValString.compareToIgnoreCase(this.predecessor.getId()) > 0)
			{
				//the add value should be greater than predecessor of newnode
				//check the condition why added equal to sign
				if(addValString.compareToIgnoreCase(this.meNode.getId()) <= 0)
				{
					//the add value should be less than the newnode

					//if both above conditions are satisfied then make RPC
					//call to updatefinger on node P
					if(nodep.getId().compareToIgnoreCase(this.meNode.getId()) == 0)
					{
						//no need to do the RPC
						//to avoid the calling RPC on the the same node
						//updateFinger(i-1, this.meNode);
						//System.out.println("Join Updating "+i+" th entry of "+nodep.getPort()+" to "+this.meNode.getPort());
						updateFinger(i, this.meNode);
					}
					else{
						transport = new TSocket(nodep.getIp(), nodep.getPort());
						transport.open();
						protocol = new TBinaryProtocol(transport);
						FileStore.Client client = new FileStore.Client(protocol);
						//client.updateFinger(i, this.meNode);
						//client.updateFinger(i-1, this.meNode);
						//	System.out.println("Join Updating "+i+" th entry of "+nodep.getPort()+" to "+this.meNode.getPort());
						client.updateFinger(i, this.meNode);
						transport.close();
					}
					NodeID recursenode = findPred(nodep.getId());
					//System.out.println("I am getting predecessor as: "+ recursenode.getPort());
					//	if(recursenode.getId().compareToIgnoreCase(nodep.getId()) == 0)
					//	{
					//condition occured when there s only one node
					//		return;
					//	}
					//	else{
					updateExtenstionJoin(recursenode, key, i);
					//	}
				}

			}

		}catch (Exception e) {
			// TODO: handle exception
		}
	}


	public void updateExtenstionRemove(NodeID nodep,String key,int i)
	{
		TTransport transport = null;
		TProtocol protocol = null;
		NodeID nodeq = null;
		BigInteger twopowervalue = null;
		BigInteger bigtwo = new BigInteger("2");
		twopowervalue = bigtwo.pow(i);

		if(i==255)
			System.out.println("Checking");

		try{
			//the below code will handle the special case when (newnode - (2^i)) is 
			//actual physical node
			if(nodep.getId().compareToIgnoreCase(this.meNode.getId()) == 0)
			{
				//node need not to do the RPC
				//to avoid the calling RPC on the the same node

			}					
			else
			{
				transport = new TSocket(nodep.getIp(), nodep.getPort());
				transport.open();
				protocol = new TBinaryProtocol(transport);
				FileStore.Client client3 = new FileStore.Client(protocol);
				nodeq = client3.getNodeSucc();
				if(key.compareToIgnoreCase(nodeq.getId()) == 0)
				{
					//here special case is satisfied
					nodep = nodeq;
				}
				transport.close();
			}
			//calculate (p + 2^i) = addvalue
			BigInteger pkey = getBigIntegerEquivalent(nodep.getId());
			BigInteger addvalue = pkey.add(twopowervalue);

			//check if this addvalue is in between pred(newnode) and newnode
			//convert this add value to hexString to compare
			String addValString = getHexStringEquuivalent(addvalue);


			//Special Case to check when pred(newnode) is greater than newnode
			if(this.meNode.getId().compareToIgnoreCase(this.predecessor.getId()) < 0)
			{
				if(addValString.compareToIgnoreCase(this.meNode.getId()) <= 0 || addValString.compareToIgnoreCase(this.predecessor.getId()) >= 0)
				{
					//key is less than newnode
					if(nodep.getId().compareToIgnoreCase(this.meNode.getId()) == 0)
					{
						//no need to do the RPC
						//to avoid the calling RPC on the the same node
						//updateFinger(i-1, this.meNode);
						System.out.println("Remove Local Updating "+i+" th entry of "+nodep.getPort()+" to "+this.fingertable.get(0));
						updateFinger(i, this.fingertable.get(0));
					}
					else{
						transport = new TSocket(nodep.getIp(), nodep.getPort());
						transport.open();
						protocol = new TBinaryProtocol(transport);
						FileStore.Client client = new FileStore.Client(protocol);
						//client.updateFinger(i, this.meNode);
						//client.updateFinger(i-1, this.meNode);
						System.out.println("Remove Remote Updating "+i+" th entry of "+nodep.getPort()+" to "+this.fingertable.get(0));
						client.updateFinger(i, this.fingertable.get(0));
						transport.close();
					}
					NodeID recursenode = findPred(nodep.getId());
					updateExtenstionRemove(recursenode, key, i);
				}
			}


			if(addValString.compareToIgnoreCase(this.predecessor.getId()) > 0)
			{
				//the add value should be greater than predecessor of newnode
				//check the condition why added equal to sign
				if(addValString.compareToIgnoreCase(this.meNode.getId()) <= 0)
				{
					//the add value should be less than the newnode

					//if both above conditions are satisfied then make RPC
					//call to updatefinger on node P
					if(nodep.getId().compareToIgnoreCase(this.meNode.getId()) == 0)
					{
						//no need to do the RPC
						//to avoid the calling RPC on the the same node
						//updateFinger(i-1, this.meNode);
						System.out.println("Remove Local Updating "+i+" th entry of "+nodep.getPort()+" to "+this.fingertable.get(0));
						updateFinger(i, this.fingertable.get(0));
					}
					else{
						transport = new TSocket(nodep.getIp(), nodep.getPort());
						transport.open();
						protocol = new TBinaryProtocol(transport);
						FileStore.Client client = new FileStore.Client(protocol);
						//client.updateFinger(i, this.meNode);
						//client.updateFinger(i-1, this.meNode);
						System.out.println("Remove Remote Updating "+i+" th entry of "+nodep.getPort()+" to "+this.fingertable.get(0));
						client.updateFinger(i, this.fingertable.get(0));
						transport.close();
					}
					NodeID recursenode = findPred(nodep.getId());
					//NodeID recursenode = this.getNodeSucc();
					System.out.println("I am getting predecessor as: "+ recursenode.getPort());
					//	if(recursenode.getId().compareToIgnoreCase(nodep.getId()) == 0)
					//	{
					//condition occured when there s only one node
					//		return;
					//	}
					//	else{
					updateExtenstionRemove(recursenode, key, i);
					//	}
				}

			}

		}catch (Exception e) {
			// TODO: handle exception
		}
	}


	/////////////////////////////////////////////////////////////

	/*
	public void update_others(NodeID nodetoexamine,NodeID newnode)
	{
		TTransport transport = null;
		TProtocol protocol = null;
		int i;
		NodeID nodep = null;
		BigInteger bigkey = null;
		BigInteger bignewkey = null;
		BigInteger bigtwo = new BigInteger("2");
		BigInteger twopowervalue = null;
		BigInteger subvalue = null;
		SystemException excep = null;
		String key = null;
		NodeID newsucessor = null;
		try{
			//find sucessor of nodetoexamine
			//if it is same as new node, we will terminate this recursive call as we visited all the nodes
			transport = new TSocket(nodetoexamine.getIp(), nodetoexamine.getPort());
			transport.open();
			protocol = new TBinaryProtocol(transport);
			FileStore.Client client2 = new FileStore.Client(protocol);
			newsucessor = client2.getNodeSucc();
			if(newsucessor.getId().equals(newnode.getId()))
			{
				//base case
				return;
			}
		}catch (Exception e) {
			// TODO: handle exception
		}
		BigInteger addvalue = null;

		List<NodeID> importedfingertable = null;
		/*try{

			transport = new TSocket(nodetoexamine.getIp(), nodetoexamine.getPort());
			transport.open();
			protocol = new TBinaryProtocol(transport);
			FileStore.Client client2 = new FileStore.Client(protocol);
			importedfingertable = client2.getFingertable();
			transport.close();
		}catch (Exception e) {
			// TODO: handle exception
		}
	 */
	/*		for(i=0; i<256; i++)
		{
			bigkey = getBigIntegerEquivalent(nodetoexamine.id);
			twopowervalue = bigtwo.pow(i);
			addvalue = bigkey.add(twopowervalue);
			key = getHexStringEquuivalent(addvalue);
			try {
				//check if this addvalue is in between pred(newnode) and newnode
				//convert this add value to hexString to compare
				String addValString = getHexStringEquuivalent(addvalue);

				if(addValString.compareToIgnoreCase(this.predecessor.getId()) > 0)
				{
					//the add value should be greater than predecessor of newnode
					//check the condition why added equal to sign
					if(addValString.compareToIgnoreCase(this.meNode.getId()) <= 0)
					{
						//the add value should be less than the newnode

						//if both above conditions are satisfied then make RPC
						//call to updatefinger on node P
						transport = new TSocket(nodetoexamine.getIp(), nodetoexamine.getPort());
						transport.open();
						protocol = new TBinaryProtocol(transport);
						FileStore.Client client = new FileStore.Client(protocol);
						client.updateFinger(i-1, newnode);
						transport.close();
					}
				}

			} catch (SystemException e) {
				//throw e;
			} catch (TException e) {
				//throw e;
			}
		}
		//make recursive call to successor of nodetoexamine
		update_others(newsucessor, newnode);
	}*/
	public void init_fingertable()
	{
		int i;
		// the first node
		// calculate the finger table and set to current node
		fingertable = new ArrayList<NodeID>();
		for(i=1; i<=256; i++)
		{
			//every entry will point to node itself
			this.fingertable.add(i-1,this.meNode);
		}
		//successor and predecessor of this node is the node itself
		this.sucessor = this.getMeNode();
		this.predecessor = this.getMeNode();
	}

	public BigInteger getBigIntegerEquivalent(String key)
	{
		byte[] b = new BigInteger(key,16).toByteArray();
		BigInteger tempBig2 = new BigInteger(b);
		//System.out.println("Biginterger for newly joining node is:"+ tempBig2);
		return tempBig2;
	}
	public String getHexStringEquuivalent(BigInteger b)
	{
		String key;
		key = b.toString(16);
		return key;
	}

	public static String getSHAHash(String ip,String port)
	{
		String tobehashed = null;
		tobehashed = ip+":"+port;
		StringBuffer sbuff = null;
		sbuff = new StringBuffer();
		SystemException excep = null;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(tobehashed.getBytes());
			byte[] digest = md.digest();
			sbuff = new StringBuffer();
			for (byte b : digest) {
				sbuff.append(String.format("%02x", b & 0xff));
			}
		} catch (NoSuchAlgorithmException e) {
			excep = new SystemException();
			excep.setMessage("Error while generating SHA-256 Hash Code for new node.");	
		}
		return sbuff.toString();
	}

	public NodeID getMeNode() {
		return meNode;
	}
	public void setMeNode(NodeID meNode) {
		this.meNode = meNode;
	}
	public NodeID getSucessor() {
		return sucessor;
	}
	public void setSucessor(NodeID sucessor) {
		this.sucessor = sucessor;
	}
	public HashMap<String, RFile> getFilemap() {
		return filemap;
	}
	public void setFilemap(HashMap<String, RFile> filemap) {
		this.filemap = filemap;
	}
	public String getWorkingDir() {
		return workingDir;
	}
	public void setWorkingDir(String workingDir) {
		this.workingDir = workingDir;
	}
	public File getDirectory() {
		return directory;
	}
	public void setDirectory(File directory) {
		this.directory = directory;
	}



	@Override
	public List<NodeID> getFingertable() throws SystemException, TException {
		// TODO Auto-generated method stub

		return this.fingertable;
	}




}
