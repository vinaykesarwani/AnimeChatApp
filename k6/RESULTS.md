# Load test results

Tested on 24-25 October 2026. All 10 scenario and room-layout combinations passed their configured thresholds.

## Setup

- Windows 11 Pro, Intel Core Ultra 7 268V, 8 logical CPUs, about 32 GB RAM.
- Spring Boot 4.0.2, Java 21.0.10, MySQL 8.0.45 and k6 1.7.1.
- Backend, MySQL and k6 ran on the same machine. SQL logging was disabled.
- Actual JWT login and SockJS/STOMP transport were used. Registration happened before measurement.
- Each test started with empty rooms. Users sent 200-character messages once per second during 30-second sessions, followed by a five-second echo drain.

## Results

Messages/sec and latency below are for the listed hold. Smoke latency covers the whole run. Total messages include ramps and session drain.

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

Throughput is the number of acknowledged messages sent during the hold divided by the hold duration. Users are concurrent virtual users, and active sessions can finish during ramp-down.

Across all runs:

- 154,851 messages sent and acknowledged, with 100% sender-echo success.
- MySQL persisted-message and distinct-content counts matched the send counts in each run.
- Zero HTTP failures, message errors, unexpected disconnects or unacknowledged sends.
- Complete session/message accounting in every run. All 13 backend tests also passed before execution.

Echo latency measures the sender receiving its own matching message after the backend saves and broadcasts it. Delivery time to every subscriber was not measured. These are local results, not a maximum-capacity measurement.

## What we noticed

During soak, history-fetch p95 increased between the 5-10 minute and 25-30 minute windows:

| Layout | Early history p95 | Late history p95 | Peak k6 memory |
| --- | ---: | ---: | ---: |
| One room | 197.39 ms | 565.50 ms | 4.37 GiB |
| Five rooms | 68.60 ms | 140.91 ms | 0.92 GiB |

The history endpoint returns all messages. MySQL used the room index and then sorted by creation time and ID. Pagination is worth testing next; these runs do not isolate the full cause of the slowdown.

Steady tests and single-room stress ran on 24 October. The remaining tests ran on 25 October with raw metric export enabled. Application and load-test sources matched across both sessions. Raw export and other activity on the machine can affect results.

Run the same workloads using [RUN_TESTS.md](RUN_TESTS.md).
