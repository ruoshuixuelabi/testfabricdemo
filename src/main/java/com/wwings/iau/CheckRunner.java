package com.wwings.iau;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.testutils.TestConfig;
import org.hyperledger.fabric.sdkintegration.SampleOrg;
import org.hyperledger.fabric.sdkintegration.SampleStore;
import org.hyperledger.fabric.sdkintegration.SampleUser;
import org.hyperledger.fabric.sdkintegration.Util;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.sdk.Channel.NOfEvents.createNofEvents;
import static org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions;
import static org.hyperledger.fabric.sdk.Channel.TransactionOptions.createTransactionOptions;
public class CheckRunner implements ApplicationRunner {
    //首先定义一个存储测试用户的文件
    File sampleStoreFile = new File("e://HFCSampletest.properties");
    static SampleStore sampleStore = null;
    private Collection<SampleOrg> testSampleOrgs;
    static final TestConfig testConfig = TestConfig.getConfig();
    static String testUser1 = "user1";
    static final String TEST_ADMIN_NAME = "admin";
    private static final String FOO_CHANNEL_NAME = "foo";
    private static final String TEST_FIXTURES_PATH = "src/test/fixture";
    //链码的版本
    String CHAIN_CODE_VERSION = "1";
    String CHAIN_CODE_FILEPATH = "sdkintegration/javacc/sample1"; //override path to Node code
    //这个参数只对GO语言的链码有用
    String CHAIN_CODE_PATH = null;
    //链码的名字
    String CHAIN_CODE_NAME = "example_cc_java";
    //链码的版是Java
    TransactionRequest.Type CHAIN_CODE_LANG = TransactionRequest.Type.JAVA;
    //部署的等待时间
    private static final int DEPLOYWAITTIME = testConfig.getDeployWaitTime();
    @Override
    public void run(ApplicationArguments args) throws Exception {
        checkConfig();
        sampleStore = new SampleStore(sampleStoreFile);
        //这将使用Fabric CA注册用户,并设置示例存储,以便稍后获取用户
        enrollUsersSetup(sampleStore);
        //执行Fabric的测试
        runFabricTest(sampleStore);
    }
    /**
     * 首先每次运行都会检查一下配置情况
     */
    public void checkConfig() throws Exception {
        //获得整合的测试的简单组织Orgs
        testSampleOrgs = testConfig.getIntegrationTestsSampleOrgs();
        System.out.println("获取到的testSampleOrgs=" + testSampleOrgs);
        //设置每个组织的CA客户端
        for (SampleOrg sampleOrg : testSampleOrgs) {
            //这个时候的SampleOrg还没有设置CAClient的字段
            //获取每一个组织的caName
            String caName = sampleOrg.getCAName(); //Try one of each name and no name.
            //如果caName不是空的时候
            if (caName != null && !caName.isEmpty()) {
                //设置CAClient,参数为caName,以及caName的url地址和Properties配置文件
                sampleOrg.setCAClient(HFCAClient.createNewInstance(caName, sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            } else {
                //由于caName是空因此和上面比较起来少了caName参数,剩余的参数是一样的
                sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            }
        }
    }
    //注册登记用户
    public void enrollUsersSetup(SampleStore sampleStore) throws Exception {
        for (SampleOrg sampleOrg : testSampleOrgs) {
            //获取集合里面每个成员的HFCAClient
            HFCAClient ca = sampleOrg.getCAClient();
            //获取组织的名字
            final String orgName = sampleOrg.getName();
            //获取组织的mspid
            final String mspid = sampleOrg.getMSPID();
            //设置HFCAClient的密码适配
            ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
            //获取HFCAClient的信息HFCAInfo
            HFCAInfo info = ca.info(); //just check if we connect at all.
            System.out.println("获取到的HFCAInfo=" + info);
            //此处得到的infoName是ca0
            String infoName = info.getCAName();
            //根据实际的打印效果这个值可能是空的,目前一个值是ca0一个是空
            System.out.println("info.getCAName()获取到的infoName值是=" + infoName);
            //从sampleStore里面获取简单样例成员SampleUser,根据组织名字orgName,成员的名字是admin
            //在这个测试用例里面由于sampleStore是空的,因此其实这里是创建了一个新的SampleUser
            SampleUser admin = sampleStore.getMember(TEST_ADMIN_NAME, orgName);
            System.out.println("SampleUser成员是admin=" + admin);
            System.out.println("admin.isEnrolled()背书情况是=" + admin.isEnrolled());
            //如果没有注册背书,那就设置注册背书的属性
            //判断admin是否登记,如果没登记就设置
            if (!admin.isEnrolled()) {  //Preregistered admin only needs to be enrolled with Fabric caClient.
                admin.setEnrollment(ca.enroll(admin.getName(), "adminpw"));
                System.out.println("设置的mspid=" + mspid);
                admin.setMspId(mspid);
            }
            //从sampleStore里面获取简单样例成员SampleUser,根据组织名字orgName,user
            SampleUser user = sampleStore.getMember(testUser1, sampleOrg.getName());
            System.out.println("测试用户的成员是user=" + user);
            System.out.println("目前的这个用户是否已经注册=" + user.isRegistered());
            //判断用户的注册,没注册就注册一下
            if (!user.isRegistered()) {  // users need to be registered AND enrolled
                System.out.println("目前没有注册,因此下面开始执行注册的步骤");
                //TODO 目前不知道org1.department1是做什么的
                RegistrationRequest rr = new RegistrationRequest(user.getName(), "org1.department1");
                System.out.println("RegistrationRequest的值是=" + rr);
                user.setEnrollmentSecret(ca.register(rr, admin));
            }
            System.out.println("看看user用户是否背书user.isEnrolled()=" + user.isEnrolled());
            if (!user.isEnrolled()) {
                user.setEnrollment(ca.enroll(user.getName(), user.getEnrollmentSecret()));
                user.setMspId(mspid);
            }
            //获取组织的名字
            final String sampleOrgName = sampleOrg.getName();
            System.out.println("获取到的final组织的名字是sampleOrgName=" + sampleOrgName);
            //获取住址的DomainName领域名字
            final String sampleOrgDomainName = sampleOrg.getDomainName();
            SampleUser peerOrgAdmin = sampleStore.getMember(sampleOrgName + "Admin", sampleOrgName, sampleOrg.getMSPID(),
                    Util.findFileSk(Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/",
                            sampleOrgDomainName, format("/users/Admin@%s/msp/keystore", sampleOrgDomainName)).toFile()),
                    Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/", sampleOrgDomainName,
                            format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", sampleOrgDomainName, sampleOrgDomainName)).toFile());
            System.out.println("获取到的peerOrgAdmin=" + peerOrgAdmin);
            //这里设置一个特殊的Peer节点,类似于admin,这个节点可以创建通道以及加入peers和安装链码
            sampleOrg.setPeerAdmin(peerOrgAdmin); //A special user that can create channels, join peers and install chaincode
            sampleOrg.addUser(user);
            sampleOrg.setAdmin(admin); // The admin of this org --
        }
    }
    /**
     * 真正运行的方法
     */
    public void runFabricTest(final SampleStore sampleStore) throws Exception {
        HFClient client = HFClient.createNewInstance();
        //设置客户的成员适配
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        //获取到peerOrg1组织
        SampleOrg sampleOrg = testConfig.getIntegrationTestsSampleOrg("peerOrg1");
        System.out.println("运行的时候我是可以获取到peerOrg1组织的" + sampleOrg);
        Channel fooChannel = null;
        client.setUserContext(sampleOrg.getPeerAdmin());
        try {
            fooChannel = sampleStore.getChannel(client, FOO_CHANNEL_NAME).initialize();
        }catch (Exception e){

        }
        if (fooChannel != null) {

        } else {
            //根据条件初始化Channel
            fooChannel = constructChannel(FOO_CHANNEL_NAME, client, sampleOrg);
            System.out.println("这个时候创建的Channel信息为Channel=fooChannel=" + fooChannel);
            //把初始化的Channel保存到sampleStore,下次就可以拿了
            sampleStore.saveChannel(fooChannel);
        }
        //运行初始化的Channel
        runChannel(client, fooChannel, true, sampleOrg, 0);
        //这里是创建bar这个Channel的
    }

    /**
     * 创建通道的方法
     */
    Channel constructChannel(String name, HFClient client, SampleOrg sampleOrg) throws Exception {
        //获取到peerAdmin节点 用户成员
        SampleUser peerAdmin = sampleOrg.getPeerAdmin();
        //把目前的HFClient客户端上下文设置为peerAdmin
        client.setUserContext(peerAdmin);
        //定义一个集合存储排序节点
        Collection<Orderer> orderers = new LinkedList<>();
        for (String orderName : sampleOrg.getOrdererNames()) {
            Properties ordererProperties = testConfig.getOrdererProperties(orderName);
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveWithoutCalls", new Object[]{true});
            //创建排序节点并把排序节点添加到集合
            orderers.add(client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName), ordererProperties));
        }
        //Just pick the first orderer in the list to create the channel.
        //获取到第一个排序节点Orderer
        Orderer anOrderer = orderers.iterator().next();
        //从集合里面踢出刚才获取的排序节点Orderer
        orderers.remove(anOrderer);
        //创建通道所需要的tx文件
        String path = TEST_FIXTURES_PATH + "/sdkintegration/e2e-2Orgs/" + testConfig.getFabricConfigGenVers() + "/" + name + ".tx";
        //根据channel的配置文件创建ChannelConfiguration
        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(path));
        Channel newChannel = client.newChannel(name, anOrderer, channelConfiguration,
                client.getChannelConfigurationSignature(channelConfiguration, peerAdmin));
        boolean everyother = true; //test with both cases when doing peer eventing.
        for (String peerName : sampleOrg.getPeerNames()) {
            String peerLocation = sampleOrg.getPeerLocation(peerName);
            Properties peerProperties = testConfig.getPeerProperties(peerName); //test properties for peer.. if any.
            if (peerProperties == null) {
                peerProperties = new Properties();
            }
            //Example of setting specific options on grpc's NettyChannelBuilder
            peerProperties.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);
            //创建新的Peer节点
            Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
            if (testConfig.isFabricVersionAtOrAfter("1.3")) {
                newChannel.joinPeer(peer, createPeerOptions().setPeerRoles(EnumSet.of(Peer.PeerRole.ENDORSING_PEER,
                        Peer.PeerRole.LEDGER_QUERY, Peer.PeerRole.CHAINCODE_QUERY, Peer.PeerRole.EVENT_SOURCE))); //Default is all roles.
            } else {
                newChannel.joinPeer(peer, createPeerOptions().setPeerRoles(EnumSet.of(Peer.PeerRole.ENDORSING_PEER,
                        Peer.PeerRole.LEDGER_QUERY, Peer.PeerRole.CHAINCODE_QUERY)));
            }
            everyother = !everyother;
        }
        for (Orderer orderer : orderers) { //add remaining orderers if any.
            //把遍历到的每一个排序节点加入newChannel
            newChannel.addOrderer(orderer);
        }
        //目前不知道EventHub是什么意思,貌似是事件回调
        for (String eventHubName : sampleOrg.getEventHubNames()) {
            final Properties eventHubProperties = testConfig.getEventHubProperties(eventHubName);
            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});
            EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
                    eventHubProperties);
            newChannel.addEventHub(eventHub);
        }
        newChannel.initialize();
        byte[] serializedChannelBytes = newChannel.serializeChannel();
        newChannel.shutdown(true);
        return client.deSerializeChannel(serializedChannelBytes).initialize();
    }

    void runChannel(HFClient client, Channel channel, boolean installChaincode, SampleOrg sampleOrg, int delta) {
        try {
            //定义一个变量ChaincodeID
            final ChaincodeID chaincodeID;
            //定义教义的响应,这是一个集合
            Collection<ProposalResponse> responses;
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();
            //这里判断不是foo这个channel,不是的话不注册事件以及不回调
            ChaincodeID.Builder chaincodeIDBuilder = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setVersion(CHAIN_CODE_VERSION);
            if (null != CHAIN_CODE_PATH) {
                chaincodeIDBuilder.setPath(CHAIN_CODE_PATH);
            }
            //获取到chaincodeID,这个时候是创建的
            chaincodeID = chaincodeIDBuilder.build();
            //判断是否安装了链码
            if (!checkInstalledChaincode(client, channel.getPeers().iterator().next(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {
                //如果需要安装链码
                if (installChaincode) {
                    client.setUserContext(sampleOrg.getPeerAdmin());
                    InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
                    //把上面的chaincodeID设置到InstallProposalRequest里面
                    installProposalRequest.setChaincodeID(chaincodeID);
                    //如果是foo这个名字的channel
                    //设置链码的源码路径 这里的链码是
                    installProposalRequest.setChaincodeSourceLocation(Paths.get(TEST_FIXTURES_PATH, CHAIN_CODE_FILEPATH).toFile());
                    if (testConfig.isFabricVersionAtOrAfter("1.1")) { // Fabric 1.1 added support for  META-INF in the chaincode image.
                        System.out.println("这里是1.1之后的版本这个时候支持 META-INF in the chaincode image");
                        installProposalRequest.setChaincodeMetaInfLocation(new File("src/test/fixture/meta-infs/end2endit"));
                    }
                    installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);
                    installProposalRequest.setChaincodeLanguage(CHAIN_CODE_LANG);
                    System.out.println("把安装链码的协议发送出去了");
                    int numInstallProposal = 0;
                    Collection<Peer> peers = channel.getPeers();
                    numInstallProposal = numInstallProposal + peers.size();
                    //这个时候才是真正的发送安装链码的请求到节点上
                    responses = client.sendInstallProposal(installProposalRequest, peers);
                    for (ProposalResponse response : responses) {
                        if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                            System.out.println("安装链码返回的响应是成功的Txid=" + response.getTransactionID() + "peer=" + response.getPeer().getName());
                            successful.add(response);
                        } else {
                            failed.add(response);
                        }
                    }
                    System.out.println("目前收到的安装链码的响应是成功的有=" + numInstallProposal + "个,应该成功的有=" + successful.size() + "失败的数量是=" + failed.size());
                }
            }
            //检查是否实例化了链码
            if (!checkInstantiatedChaincode(channel, channel.getPeers().iterator().next(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {
                System.out.println("开始实例化代码");
                //安装完链码之后需要实例化链码
                InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
                instantiateProposalRequest.setProposalWaitTime(DEPLOYWAITTIME);
                instantiateProposalRequest.setChaincodeID(chaincodeID);
                instantiateProposalRequest.setChaincodeLanguage(CHAIN_CODE_LANG);
                //调用链码的实例化方法,初始化
                instantiateProposalRequest.setFcn("init");
                //初始化的传入参数,初始化链码的时候传入节点的公钥证书
                instantiateProposalRequest.setArgs(new String[]{"a", "500", "b", "" + (200 + delta)});
                Map<String, byte[]> tm = new HashMap<>();
                tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
                tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
                instantiateProposalRequest.setTransientMap(tm);
            /*
              policy OR(Org1MSP.member, Org2MSP.member) meaning 1 signature from someone in either Org1 or Org2
              See README.md Chaincode endorsement policies section for more details.
            */
                //这里是设置背书策略
                ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
                chaincodeEndorsementPolicy.fromYamlFile(new File(TEST_FIXTURES_PATH + "/sdkintegration/chaincodeendorsementpolicy.yaml"));
                //设置背书策略
                instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);
                successful.clear();
                failed.clear();
                responses = channel.sendInstantiationProposal(instantiateProposalRequest, channel.getPeers());
                for (ProposalResponse response : responses) {
                    if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                        successful.add(response);
                        System.out.println("实例化链码成功了");
                    } else {
                        failed.add(response);
                    }
                }
                Channel.NOfEvents nOfEvents = createNofEvents();
                if (!channel.getPeers(EnumSet.of(Peer.PeerRole.EVENT_SOURCE)).isEmpty()) {
                    nOfEvents.addPeers(channel.getPeers(EnumSet.of(Peer.PeerRole.EVENT_SOURCE)));
                }
                if (!channel.getEventHubs().isEmpty()) {
                    nOfEvents.addEventHubs(channel.getEventHubs());
                }
                channel.sendTransaction(successful, createTransactionOptions() //Basically the default options but shows it's usage.
                        .userContext(client.getUserContext()) //could be a different user context. this is the default.
                        .shuffleOrders(false) // don't shuffle any orderers the default is true.
                        .orderers(channel.getOrderers()) // specify the orderers we want to try this transaction. Fails once all Orderers are tried.
                        .nOfEvents(nOfEvents) // The events to signal the completion of the interest in the transaction
                ).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);
            } else {

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * 检查链码是否实例化的方法
     * @param channel
     * @param peer
     * @param ccName
     * @param ccPath
     * @param ccVersion
     */
    private static boolean checkInstantiatedChaincode(Channel channel, Peer peer, String ccName, String ccPath, String ccVersion) throws InvalidArgumentException, ProposalException {
        //查询相应的peer节点上所有实例化的链码的列表集合
        List<Query.ChaincodeInfo> ccinfoList = channel.queryInstantiatedChaincodes(peer);
        return checkChaincode(ccName, ccPath, ccVersion, ccinfoList);
    }
    private static boolean checkChaincode(String ccName, String ccPath, String ccVersion, List<Query.ChaincodeInfo> ccinfoList) {
        boolean found = false;
        for (Query.ChaincodeInfo ccifo : ccinfoList) {
            if (ccPath != null) {
                found = ccName.equals(ccifo.getName()) && ccPath.equals(ccifo.getPath()) && ccVersion.equals(ccifo.getVersion());
                if (found) {
                    break;
                }
            }
            found = ccName.equals(ccifo.getName()) && ccVersion.equals(ccifo.getVersion());
            if (found) {
                break;
            }
        }
        return found;
    }
    private static boolean checkInstalledChaincode(HFClient client, Peer peer, String ccName, String ccPath, String ccVersion)
            throws InvalidArgumentException, ProposalException {
        //查询相应的peer节点的链码列表
        List<Query.ChaincodeInfo> ccinfoList = client.queryInstalledChaincodes(peer);
        return checkChaincode(ccName, ccPath, ccVersion, ccinfoList);
    }
}
