package org.example.demo2;

import java.util.List;

public class Flow {
    public List<String> pathNodes;
    public List<Integer> pathPorts;
    public String srcIp;
    public String dstIp;
    public int srcPort;
    public int dstPort;
    public int protocolId;
    public int startTimeMs;
    public int endTimeMs;
    public double estimatedFlowSendingRateBpsInTheLastSec;
    public double estimatedFlowSendingRateBpsInTheProceeding1secTimeslot;
    public int estimatedPacketRateInTheLastSec;
    public int estimatedPacketRateInTheProceeding1secTimeslot;

    public Flow(List<String> pathNodes, List<Integer> pathPorts, String srcIp, String dstIp,
                int srcPort, int dstPort, int protocolId, int startTimeMs, int endTimeMs,
                double estimatedFlowSendingRateBpsInTheLastSec,
                double estimatedFlowSendingRateBpsInTheProceeding1secTimeslot,
                int estimatedPacketRateInTheLastSec,
                int estimatedPacketRateInTheProceeding1secTimeslot) {
        this.pathNodes = pathNodes;
        this.pathPorts = pathPorts;
        this.srcIp = srcIp;
        this.dstIp = dstIp;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.protocolId = protocolId;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.estimatedFlowSendingRateBpsInTheLastSec = estimatedFlowSendingRateBpsInTheLastSec;
        this.estimatedFlowSendingRateBpsInTheProceeding1secTimeslot = estimatedFlowSendingRateBpsInTheProceeding1secTimeslot;
        this.estimatedPacketRateInTheLastSec = estimatedPacketRateInTheLastSec;
        this.estimatedPacketRateInTheProceeding1secTimeslot = estimatedPacketRateInTheProceeding1secTimeslot;
    }

    /**
     * Sending rate for UI, sorting, and topology weighting.
     * Uses {@code estimated_flow_sending_rate_bps_in_the_proceeding_1sec_timeslot} so the client does not
     * rely on the heavier last-second estimate when many flows are present.
     */
    public double getSendingRateBps() {
        return estimatedFlowSendingRateBpsInTheProceeding1secTimeslot;
    }
} 