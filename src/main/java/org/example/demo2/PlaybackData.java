package org.example.demo2;

import java.util.List;

public class PlaybackData {
    public List<PlaybackFrame> playback;
    
    public static class PlaybackFrame {
        public long time;
        public List<EdgeData> edges;
        public List<NodeData> nodes;
        public List<FlowData> flow;
    }
    
    public static class EdgeData {
        public int number;
    }
    
    public static class NodeData {
        public String brand_name;
        public int number;
    }
    
    public static class FlowData {
        public int number;
    }
}
