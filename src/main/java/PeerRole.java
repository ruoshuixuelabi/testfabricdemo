import java.util.EnumSet;

public enum PeerRole {
        /**
         * Endorsing peer installs and runs chaincode.
         */
        ENDORSING_PEER("endorsingPeer"),
        /**
         * Chaincode query peer will be used to invoke chaincode on chaincode query requests.
         */
        CHAINCODE_QUERY("chaincodeQuery"),
        /**
         * Ledger Query will be used when query ledger operations are requested.
         */
        LEDGER_QUERY("ledgerQuery"),
        /**
         * Peer will monitor block events for the channel it belongs to.
         */
        EVENT_SOURCE("eventSource"),

        /**
         * Peer will monitor block events for the channel it belongs to.
         */
        SERVICE_DISCOVERY("serviceDiscovery");

        /**
         * All roles.
         */
        public static final EnumSet<PeerRole> ALL = EnumSet.allOf(PeerRole.class);
        /**
         * All roles except event source.
         */
        public static final EnumSet<PeerRole> NO_EVENT_SOURCE = EnumSet.complementOf(EnumSet.of(PeerRole.EVENT_SOURCE));
        private final String propertyName;

        PeerRole(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getPropertyName() {
            return propertyName;
        }
    }