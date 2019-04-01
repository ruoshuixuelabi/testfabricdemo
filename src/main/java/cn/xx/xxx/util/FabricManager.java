package cn.xx.xxx.util;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import cn.aberic.fabric.ChaincodeManager;
import cn.aberic.fabric.FabricConfig;
import cn.aberic.fabric.bean.Chaincode;
import cn.aberic.fabric.bean.Orderers;
import cn.aberic.fabric.bean.Peers;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class FabricManager {
	private ChaincodeManager manager;
	private static FabricManager instance = null;
	public static FabricManager obtain() throws CryptoException, InvalidArgumentException, NoSuchAlgorithmException,
			NoSuchProviderException, InvalidKeySpecException, TransactionException, IOException, IllegalAccessException,
			InstantiationException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
		if (null == instance) {
			synchronized (FabricManager.class) {
				if (null == instance) {
					instance = new FabricManager();
				}
			}
		}
		return instance;
	}

	private FabricManager() throws CryptoException, InvalidArgumentException, NoSuchAlgorithmException,
			NoSuchProviderException, InvalidKeySpecException, TransactionException, IOException, IllegalAccessException,
			InstantiationException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
		manager = new ChaincodeManager(getConfig());
	}

	/**
	 * 获取节点服务器管理器
	 * @return 节点服务器管理器
	 */
	public ChaincodeManager getManager() {
		return manager;
	}
	/**
	 * 根据节点作用类型获取节点服务器配置
	 * @param type 服务器作用类型（1、执行；2、查询）
	 * @return 节点服务器配置
	 */
	private FabricConfig getConfig() {
		//创建配置
		FabricConfig config = new FabricConfig();
		//设置排序服务器
		config.setOrderers(getOrderers());
		//设置节点服务器对象
		config.setPeers(getPeers());
		//设置智能合约对象
		config.setChaincode(getChaincode("foo", "example_cc_go", "github.com/example_cc", "1.0"));
		//设置channel-artifacts配置路径
		config.setChannelArtifactsPath(getChannleArtifactsPath());
		//设置crypto-config所在路径
		config.setCryptoConfigPath(getCryptoConfigPath());
		return config;
	}
	/**
	 * 获取Order节点的方法
	 * @return
	 */
	private Orderers getOrderers() {
		Orderers orderer = new Orderers();
		orderer.setOrdererDomainName("org2.example.com");
		orderer.addOrderer("orderer.example.com", "grpc://192.168.10.10:7050");
//		orderer.addOrderer("orderer0.example.com", "grpc://192.168.10.10:7050");
//		orderer.addOrderer("orderer2.example.com", "grpc://192.168.10.10:7050");
		return orderer;
	}
	/**
	 * 获取节点服务器集
	 * @return 节点服务器集
	 */
	private Peers getPeers() {
		Peers peers = new Peers();
		peers.setOrgName("Org2");
		peers.setOrgMSPID("Org2MSP");
		peers.setOrgDomainName("org2.example.com");
		peers.addPeer("peer0.org1.example.com", "peer1.xxx.example.com", "grpc://192.168.10.10:7051", "grpc://192.168.10.10:7053","http://192.168.10.10:7054");
		return peers;
	}
	/**
	 * 获取智能合约
	 * @param channelName 频道名称
	 * @param chaincodeName 智能合约名称
	 * @param chaincodePath 智能合约路径
	 * @param chaincodeVersion 智能合约版本
	 * @return智能合约
	 */
	private Chaincode getChaincode(String channelName, String chaincodeName, String chaincodePath,
			String chaincodeVersion) {
		Chaincode chaincode = new Chaincode();
		//设置当前将要访问的智能合约所属频道名称
		chaincode.setChannelName(channelName);
		//设置智能合约名称
		chaincode.setChaincodeName(chaincodeName);
		//设置智能合约安装路径
		chaincode.setChaincodePath(chaincodePath);
		//设置智能合约版本号
		chaincode.setChaincodeVersion(chaincodeVersion);
		chaincode.setInvokeWatiTime(100000);
		chaincode.setDeployWatiTime(120000);
		return chaincode;
	}
	/**
	 * 获取channel-artifacts配置路径
	 * @return /WEB-INF/classes/fabric/channel-artifacts/
	 */
	private String getChannleArtifactsPath() {
		String directorys = FabricManager.class.getClassLoader().getResource("fabric").getFile();
		log.debug("directorys = " + directorys);
		File directory = new File(directorys);
		log.debug("directory = " + directory.getPath());
		return directory.getPath() + "/channel-artifacts/";
	}
	/**
	 * 获取crypto-config配置路径
	 * @return /WEB-INF/classes/fabric/crypto-config/
	 */
	private String getCryptoConfigPath() {
		String directorys = FabricManager.class.getClassLoader().getResource("fabric").getFile();
		log.debug("directorys = " + directorys);
		File directory = new File(directorys);
		log.debug("directory = " + directory.getPath());
		return directory.getPath() + "/crypto-config/";
	}
}