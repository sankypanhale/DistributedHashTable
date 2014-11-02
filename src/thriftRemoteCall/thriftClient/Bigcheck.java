package thriftRemoteCall.thriftClient;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import thriftRemoteCall.thriftUtil.NodeID;
import thriftRemoteCall.thriftUtil.SystemException;

public class Bigcheck {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		int i;
		NodeID nodeentrytoadd = null;
		String key = null;
		BigInteger bigtwo = new BigInteger("2");
		BigInteger twopowervalue = null;
		BigInteger bignewkey = null;
		BigInteger keygenertor = null;
		//get key for the current node
		//String id = getSHAHash(nodeId.getIp(),Integer.toString(nodeId.getPort()));
		//nodeId.setId(id);
		String oldkey = "9cf8f07dd310e61bba8688a0d4b8cc7a948783482ba21bc5b5eb5fd309d9329e";
		//BigInteger equivalent of key
		byte[] b = new BigInteger(oldkey,16).toByteArray();
		BigInteger tempBig2 = new BigInteger(b);
		System.out.println("Biginterger for newly joining is:"+ tempBig2);
		
//		for(i=1; i<256; i++)
		{
			twopowervalue = bigtwo.pow(0);
			bignewkey = twopowervalue.add(tempBig2);
			key = bignewkey.toString(16);
			System.out.println("Old key is: "+ oldkey);
			System.out.println("New key is: "+key);
			//nodeentrytoadd = findSucc(key);
			
		}
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


}
