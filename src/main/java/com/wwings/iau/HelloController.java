package com.wwings.iau;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.testutils.TestConfig;
import org.hyperledger.fabric.sdkintegration.SampleOrg;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static java.nio.charset.StandardCharsets.UTF_8;
@Controller
public class HelloController {
    static String testUser1 = "user1";
    TransactionRequest.Type CHAIN_CODE_LANG = TransactionRequest.Type.JAVA;
    String CHAIN_CODE_NAME = "example_cc_java";
    String CHAIN_CODE_VERSION = "1";
    //期待的事件数据此处是!
    private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(UTF_8);
    //期待的事件名字
    private static final String EXPECTED_EVENT_NAME = "event";
    Collection<ProposalResponse> successful = new LinkedList<>();
    @ResponseBody
    @RequestMapping("/hello")
    public String hello(String key,String value) throws Exception{
        Collection<ProposalResponse> successful = new LinkedList<>();
        ChaincodeID.Builder chaincodeIDBuilder = ChaincodeID.newBuilder()//
                                                                                        .setName(CHAIN_CODE_NAME)//
                                                                                        .setVersion(CHAIN_CODE_VERSION);
        ChaincodeID chaincodeID = chaincodeIDBuilder.build();
        TestConfig testConfig=CheckRunner.testConfig;
        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        SampleOrg sampleOrg = testConfig.getIntegrationTestsSampleOrg("peerOrg1");
        client.setUserContext(sampleOrg.getPeerAdmin());
        TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(chaincodeID);
        transactionProposalRequest.setChaincodeLanguage(CHAIN_CODE_LANG);
        transactionProposalRequest.setFcn("move");
        transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
        transactionProposalRequest.setArgs(key, value, "100");
        Map<String, byte[]> tm2 = new HashMap<>();
        tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8)); //Just some extra junk in transient map
        tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8)); // ditto
        tm2.put("result", ":)".getBytes(UTF_8));  // This should be returned in the payload see chaincode why.
        tm2.put(EXPECTED_EVENT_NAME, EXPECTED_EVENT_DATA);  //This should trigger an event see chaincode why.
        transactionProposalRequest.setTransientMap(tm2);
        client.setUserContext(sampleOrg.getPeerAdmin());
        Channel channel=CheckRunner.sampleStore.getChannel(client, "foo").initialize();
        System.out.println("获取到的通道是channel="+channel);
        Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
        for (ProposalResponse response : transactionPropResp) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                successful.add(response);
            } else {
                //failed.add(response);
            }
        }
        channel.sendTransaction(successful).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);
        return "Hello World!";
    }
}
