# Network Traffic Visualizer

## Overview

Network Traffic Visualizer is a JavaFX desktop application for visualizing network topologies and live traffic flows. It connects to an NDT API endpoint, renders nodes and links, animates flow paths in real time, and supports playback of historical data files.

## System Requirements

- OS: Windows, macOS, or Linux
- Java: JDK 21
- Build tool: Maven (wrapper included via `./mvnw`)
- Network: Reachable NDT API endpoint for live mode

## Project Structure

```
Network-Traffic-Visualizer/
├── src/
│   ├── main/
│   │   ├── java/org/example/demo2/
│   │   └── resources/org/example/demo2/
├── images/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── network_traffic_visualizer.sh
├── node_positions.json
├── node_positions_playback.json
├── porttable.json
├── protocoltable.json
└── settings.json
```

## Build

From the project root:

```bash
./mvnw clean compile
./mvnw clean package
```

## Run

### macOS / Linux

```bash
chmod +x ./network_traffic_visualizer.sh
./network_traffic_visualizer.sh
```

You can also run directly with:

```bash
./mvnw javafx:run
```

### Windows

```bat
mvnw.cmd javafx:run
```

## Configuration

The app reads the API base URL from `NDT_API_URL` (default: `http://localhost:8000`).

```bash
export NDT_API_URL="http://your-server:8000"
```

Endpoints used:

- `/ndt/get_graph_data`
- `/ndt/get_detected_flow_data`
- `/ndt/get_cpu_utilization`
- `/ndt/get_memory_utilization`

## Playback Mode (Historical Data)

Use the Playback panel in the UI to load:

- Flow data file (JSON or NDJSON)
- Topology data file (JSON or NDJSON)

Notes:

- Large JSON files are automatically converted to NDJSON in a `preprocessed/` folder.
- Index files (`.idx`) are generated next to the selected data files for fast seeking.
- NDJSON entries should include a timestamp field named `t` or `timestamp`.

## Data Files

These files are read or written in the project root:

- `settings.json`: saved UI settings (flow speed).
- `node_positions.json`: saved node layout for live mode.
- `node_positions_playback.json`: saved node layout for playback mode.
- `porttable.json` / `protocoltable.json`: lookup tables used in the Info dialog.

## Troubleshooting

- **API connection errors**: verify `NDT_API_URL` and server availability.
- **JavaFX errors**: confirm JDK 21 is installed and Maven dependencies are downloaded.

## Ubuntu Desktop Shortcut (Optional)

```bash
mkdir -p ~/.local/share/applications
cat > ~/.local/share/applications/network-traffic-visualizer.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Network Traffic Visualizer
Exec=/bin/bash -lc "cd /path/to/Network-Traffic-Visualizer && ./network_traffic_visualizer.sh"
Icon=/path/to/Network-Traffic-Visualizer/images/NDTwin.jpg
Terminal=false
Categories=Network;Development;
EOF

chmod +x ~/.local/share/applications/network-traffic-visualizer.desktop
```

Replace `/path/to/Network-Traffic-Visualizer` with your actual project path.
