package info.jkjensen.castex

/**
 * Created by jk on 10/31/17.
 */
class Preferences{
    companion object {
        /** Enables multicast, allowing a one-to-many transmission.
         * NOTE: This complicates the host-device operation if the host device is acting as AP.
         * Also, most public and private AP's don't allow multicast without additional network
         * configuration.
         */
        public val KEY_MULTICAST = "multicast"
        public val MULTICAST = false

        /**
         * Enables the use of TCP sockets instead of UDP. This will likely improve stream quality
         * but severely increase network latency.
         * NOTE: This setting holds higher priority than the multicast setting, meaning that if both
         * are set the transmission will be made via TCP.
         */
        public val KEY_TCP = "tcp"
        public val TCP = false

        public val KEY_DEBUG = "debug"
        public val DEBUG = false
    }
}