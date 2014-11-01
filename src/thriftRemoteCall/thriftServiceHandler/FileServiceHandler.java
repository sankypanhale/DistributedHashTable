package thriftRemoteCall.thriftServiceHandler;

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
	private HashMap<String, RFile> filemap;
	private String workingDir;
	private File directory;

	private static BigInteger tempBig;
	public FileServiceHandler(int port) {
		filemap = new HashMap<String, RFile>();
		fingertable = new ArrayList<NodeID>();
		meNode = new NodeID();
		meNode.port = port;
		
		try {
			meNode.ip = Inet4Address.getLocalHost().getHostAddress();

		} catch (UnknownHostException e) {
			System.out.println("Error while getting IP of machine..!");
			System.exit(0);
		}
		meNode.id = getSHAHash(meNode.ip+":"+port);

/*		System.out.println("Big integer valued to: "+tempBig);
		System.out.println("Ip is current machine is: "+meNode.ip+":"+port);
		System.out.println("Current Node Hash key is: "+meNode.id);*/
		sucessor = new NodeID();
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

		file = new File(workingDir+filename);

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
			
			if(nodetoconnect.getId() == meNode.getId())
			{
				// if the sucessor is same node
				//no need to go for RPC
				//call local method
				return this.getNodeSucc();
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
		if(key.compareToIgnoreCase(meNode.id) > 0)
		{
			//key is greater than meNode.id
			if(key.compareToIgnoreCase(fingertable.get(0).getId()) <= 0)
			{
				//key is less than or equal to first finger table entry
				//go directly to successor function
				//no need to go further in the findSucc loop

				// return current node
				return meNode;
			}
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
						//System.out.println("Exact key found!!!");
						// if key matches to current node
						nodetoreturn = pred;
						//return pred;
						break;
					}
					if(pred.getId().compareToIgnoreCase(next.getId()) > 0	)
					{
						//additional case if i entry is greater than i+1
						//in this case just return the first entry blindly
						nodetoreturn = pred;
						//break;
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
				i++; i++;
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
			return sucessor;
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
	public List<NodeID> getFingertable() {
		return fingertable;
	}



	@Override
	public void setNodePred(NodeID nodeId) throws SystemException, TException {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateFinger(int idx, NodeID nodeId) throws SystemException,
			TException {
		// TODO Auto-generated method stub
		
	}



	@Override
	public List<RFile> pullUnownedFiles() throws SystemException, TException {
		// TODO Auto-generated method stub
		return null;
	}



	@Override
	public void pushUnownedFiles(List<RFile> files) throws SystemException,
			TException {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void join(NodeID nodeId) throws SystemException, TException {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void remove() throws SystemException, TException {
		// TODO Auto-generated method stub
		
	}


}
