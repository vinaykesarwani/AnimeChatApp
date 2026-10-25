# Load test results

Ran on 24-25 October 2026. All 10 tests passed their thresholds.

## Setup

- Windows 11 Pro, Intel Core Ultra 7 268V, 8 logical CPUs, about 32 GB RAM.
- Spring Boot 4.0.2, Java 21.0.10, MySQL 8.0.45 and k6 1.7.1.
- Backend, MySQL and k6 ran on the same machine. SQL logging was off.
- Used the app's JWT login and SockJS/STOMP connection. Users were registered before measurement.
- Each test started with empty rooms. Users sent 200-character messages once per second in 30-second sessions, then waited five seconds for echoes.

## Results

Rates and latency cover the listed hold. Smoke latency covers the whole run. Message totals include ramps and drain time.

| Test | Rooms | Peak users | Hold | Acked messages/sec | Echo p95 (ms) | Echo p99 (ms) | Total messages |
| --- | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| Smoke | 1 | 2 | 20-second stage | - | 118.50 | 133.88 | 58 |
| Smoke | 3 | 3 | 20-second stage | - | 93.70 | 112.14 | 87 |
| Steady | 1 | 100 | 5 minutes | 81.02 | 60 | 170 | 28,085 |
| Steady | 5 | 100 | 5 minutes | 82.07 | 24 | 54 | 28,429 |
| Stress | 1 | 100 | 1 minute at peak | 81.28 | 68 | 145 | 14,187 |
| Stress | 5 | 100 | 1 minute at peak | 81.87 | 25 | 46 | 14,245 |
| Spike | 1 | 100 | 30 seconds at peak | 89.53 | 36 | 61 | 4,670 |
| Spike | 5 | 100 | 30 seconds at peak | 89.57 | 21 | 35.14 | 4,701 |
| Soak | 1 | 20 | 30 minutes | 16.26 | 42 | 78 | 30,052 |
| Soak | 5 | 20 | 30 minutes | 16.40 | 51 | 75.85 | 30,337 |

Messages/sec = acknowledged original messages sent during the hold / hold seconds. Peak users are concurrent virtual users; running sessions can finish during ramp-down.

Totals:

- 154,851 messages sent and acknowledged, with 100% sender-echo success.
- MySQL message and distinct-content counts matched each run's send count.
- No HTTP failures, message errors, unexpected disconnects or unacknowledged sends.
- All sessions finished and every send had an outcome. All 13 backend tests passed before these runs.

Sender-echo latency covers sending a message to receiving its matching echo after save and broadcast. Delivery to every subscriber was not measured. These local results do not establish maximum capacity.

## Observations

History-fetch p95 rose during soak. Early = 5-10 minutes; late = 25-30 minutes:

| Layout | Early history p95 | Late history p95 | Peak k6 memory |
| --- | ---: | ---: | ---: |
| One room | 197.39 ms | 565.50 ms | 4.37 GiB |
| Five rooms | 68.60 ms | 140.91 ms | 0.92 GiB |

History returns all messages. MySQL used the room index and sorted by creation time and ID. The cause still needs profiling. Try pagination next.

Steady tests and single-room stress ran on 24 October; the rest on 25 October with raw metric export on. Both used the same application and load-test code. Export and other machine activity can affect results.

Commands: [RUN_TESTS.md](RUN_TESTS.md).
