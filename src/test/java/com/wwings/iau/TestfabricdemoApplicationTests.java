package com.wwings.iau;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletionException;
import org.hyperledger.fabric.protos.common.Configtx;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.testutils.TestConfig;
import org.hyperledger.fabric.sdkintegration.SampleOrg;
import org.hyperledger.fabric.sdkintegration.SampleStore;
import org.hyperledger.fabric.sdkintegration.SampleUser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions;
import static org.junit.Assert.*;
@RunWith(SpringRunner.class)
@SpringBootTest
public class TestfabricdemoApplicationTests {
	private static final TestConfig testConfig = TestConfig.getConfig();
	private static final int DEPLOYWAITTIME = testConfig.getDeployWaitTime();
	private static final boolean IS_FABRIC_V10 = testConfig.isRunningAgainstFabric10();
	private static final String TEST_ADMIN_NAME = "admin";
	private static final String TESTUSER_1_NAME = "user1";
	private static final String TEST_FIXTURES_PATH = "src/test/fixture";
	private static final String FOO_CHANNEL_NAME = "foo";
	private static final String BAR_CHANNEL_NAME = "bar";
	//private final TestConfigHelper configHelper = new TestConfigHelper();
	String testTxID = null;  // save the CC invoke TxID and use in queries
	SampleStore sampleStore;
	private Collection<SampleOrg> testSampleOrgs;
	String testName = "End2endAndBackAgainIT";
	String CHAIN_CODE_FILEPATH = "sdkintegration/gocc/sample_11";
	String CHAIN_CODE_NAME = "example_cc_go";
	String CHAIN_CODE_PATH = "github.com/example_cc";
	String CHAIN_CODE_VERSION_11 = "11";
	String CHAIN_CODE_VERSION = "1";
	TransactionRequest.Type CHAIN_CODE_LANG = TransactionRequest.Type.GO_LANG;
	ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
			.setVersion(CHAIN_CODE_VERSION)
			.setPath(CHAIN_CODE_PATH).build();
	ChaincodeID chaincodeID_11 = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
			.setVersion(CHAIN_CODE_VERSION_11)
			.setPath(CHAIN_CODE_PATH).build();
	File sampleStoreFile = new File(System.getProperty("java.io.tmpdir") + "/HFCSampletest.properties");
	@Test
	public void contextLoads() throws Exception {
		testSampleOrgs = testConfig.getIntegrationTestsSampleOrgs();
		sampleStore = new SampleStore(sampleStoreFile);
		for (SampleOrg sampleOrg : testSampleOrgs) {
			final String orgName = sampleOrg.getName();
			SampleUser admin = sampleStore.getMember(TEST_ADMIN_NAME, orgName);
			sampleOrg.setAdmin(admin); // The admin of this org.
			SampleUser user = sampleStore.getMember(TESTUSER_1_NAME, orgName);
			sampleOrg.addUser(user);  //Remember user belongs to this Org
			sampleOrg.setPeerAdmin(sampleStore.getMember(orgName + "Admin", orgName));
		}
		HFClient client = HFClient.createNewInstance();
		client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
		////////////////////////////
		//Reconstruct and run the channels
		SampleOrg sampleOrg = testConfig.getIntegrationTestsSampleOrg("peerOrg1");
		Channel fooChannel = reconstructChannel(FOO_CHANNEL_NAME, client, sampleOrg);
	//	runChannel(client, fooChannel, sampleOrg, 0);
		client.setUserContext(sampleOrg.getUser(TESTUSER_1_NAME));
		queryChaincodeForExpectedValue(client, fooChannel, "" + (300 + 0), chaincodeID);
	}
	private void queryChaincodeForExpectedValue(HFClient client, Channel channel, final String expect, ChaincodeID chaincodeID) {
		//out("Now query chaincode %s on channel %s for the value of b expecting to see: %s", chaincodeID, channel.getName(), expect);
		QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
		queryByChaincodeRequest.setArgs("b".getBytes(UTF_8)); // test using bytes as args. End2end uses Strings.
		queryByChaincodeRequest.setFcn("query");
		queryByChaincodeRequest.setChaincodeID(chaincodeID);
		Collection<ProposalResponse> queryProposals;
		try {
			queryProposals = channel.queryByChaincode(queryByChaincodeRequest);
		} catch (Exception e) {
			throw new CompletionException(e);
		}
		for (ProposalResponse proposalResponse : queryProposals) {
			if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ChaincodeResponse.Status.SUCCESS) {
				fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
						". Messages: " + proposalResponse.getMessage()
						+ ". Was verified : " + proposalResponse.isVerified());
			} else {
				String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
				System.out.println("返回的价钱是"+payload);
				//out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
				assertEquals(format("Failed compare on channel %s chaincode id %s expected value:'%s', but got:'%s'",
						channel.getName(), chaincodeID, expect, payload), expect, payload);
			}
		}
	}
	private Channel reconstructChannel(String name, HFClient client, SampleOrg sampleOrg) throws Exception {
		//out("Reconstructing %s channel", name);
		client.setUserContext(sampleOrg.getUser(TESTUSER_1_NAME));
		Channel newChannel;
		if (BAR_CHANNEL_NAME.equals(name)) { // bar channel was stored in samplestore in End2endIT testcase.
			/**
			 *  sampleStore.getChannel uses {@link HFClient#deSerializeChannel(byte[])}
			 */
			newChannel = sampleStore.getChannel(client, name);
			if (!IS_FABRIC_V10) {
			}
			assertEquals(testConfig.isFabricVersionAtOrAfter("1.3") ? 0 : 2, newChannel.getEventHubs().size());
			//out("Retrieved channel %s from sample store.", name);
		} else {
			newChannel = client.newChannel(name);
			for (String ordererName : sampleOrg.getOrdererNames()) {
				newChannel.addOrderer(client.newOrderer(ordererName, sampleOrg.getOrdererLocation(ordererName),
						testConfig.getOrdererProperties(ordererName)));
			}
			boolean everyOther = false;
			for (String peerName : sampleOrg.getPeerNames()) {
				String peerLocation = sampleOrg.getPeerLocation(peerName);
				Properties peerProperties = testConfig.getPeerProperties(peerName);
				Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
				final Channel.PeerOptions peerEventingOptions = // we have two peers on one use block on other use filtered
						everyOther ?
								createPeerOptions().registerEventsForBlocks().setPeerRoles(EnumSet.of(Peer.PeerRole.ENDORSING_PEER, Peer.PeerRole.LEDGER_QUERY,
										Peer.PeerRole.CHAINCODE_QUERY, Peer.PeerRole.EVENT_SOURCE)) :
								createPeerOptions().registerEventsForFilteredBlocks().setPeerRoles(EnumSet.of(Peer.PeerRole.ENDORSING_PEER,
										Peer.PeerRole.LEDGER_QUERY, Peer.PeerRole.CHAINCODE_QUERY, Peer.PeerRole.EVENT_SOURCE));
				newChannel.addPeer(peer, IS_FABRIC_V10 ?
						createPeerOptions().setPeerRoles(EnumSet.of(Peer.PeerRole.ENDORSING_PEER, Peer.PeerRole.LEDGER_QUERY,
								Peer.PeerRole.CHAINCODE_QUERY)) : peerEventingOptions);
				everyOther = !everyOther;
			}
			//For testing mix it up. For v1.1 use just peer eventing service for foo channel.
			if (IS_FABRIC_V10) {
				for (String eventHubName : sampleOrg.getEventHubNames()) {
					EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
							testConfig.getEventHubProperties(eventHubName));
					newChannel.addEventHub(eventHub);
				}
			} else {
			}
			assertEquals(IS_FABRIC_V10 ? sampleOrg.getEventHubNames().size() : 0, newChannel.getEventHubs().size());
		}
		byte[] serializedChannelBytes = newChannel.serializeChannel();
		//Just checks if channel can be serialized and deserialized .. otherwise this is just a waste :)
		// Get channel back.
		newChannel.shutdown(true);
		newChannel = client.deSerializeChannel(serializedChannelBytes);
		newChannel.initialize();
		//Begin tests with de-serialized channel.
		//Query the actual peer for which channels it belongs to and check it belongs to this channel
		for (Peer peer : newChannel.getPeers()) {
			Set<String> channels = client.queryChannels(peer);
			if (!channels.contains(name)) {
				throw new AssertionError(format("Peer %s does not appear to belong to channel %s", peer.getName(), name));
			}
		}
		//Just see if we can get channelConfiguration. Not required for the rest of scenario but should work.
		final byte[] channelConfigurationBytes = newChannel.getChannelConfigurationBytes();
		Configtx.Config channelConfig = Configtx.Config.parseFrom(channelConfigurationBytes);
		Configtx.ConfigGroup channelGroup = channelConfig.getChannelGroup();
		Map<String, Configtx.ConfigGroup> groupsMap = channelGroup.getGroupsMap();
		//Before return lets see if we have the chaincode on the peers that we expect from End2endIT
		//And if they were instantiated too. this requires peer admin user
		client.setUserContext(sampleOrg.getPeerAdmin());
		client.setUserContext(sampleOrg.getUser(TESTUSER_1_NAME));
		return newChannel;
	}
}
