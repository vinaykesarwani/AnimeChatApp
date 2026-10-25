# Chat load tests

This suite covers roadmap point 4: smoke, steady load, stress, spike, and soak tests against the chat application's REST login, JWT authentication, and SockJS/STOMP transport. Run each profile against one busy room and against several rooms. Keep the workload and environment the same when comparing a change.

## Prepare the application and users

Run the backend, MySQL, and any services required by the chat application. Use a dedicated test environment: these tests create real users and persist chat messages. Record the application revision, Java and database versions, machine resources, relevant configuration, and the starting message count.

Install Node.js and k6, then run these commands from the repository root in PowerShell. Set `PASSWORD` in the shell without including it in command arguments or reports.

```powershell
$credential = Get-Credential -UserName 'loaduser_' -Message 'Enter the shared load-test password'
$env:PASSWORD = $credential.GetNetworkCredential().Password
$env:BASE_URL = 'http://localhost:8080'
$env:USERS = '100'
$env:USER_PREFIX = 'loaduser_'
$env:ROOM_COUNT = '3'
node k6/seed.js
```

Use the room IDs reported by the seed script below. `BASE_URL` defaults to `http://localhost:8080`; `USERS`, `USER_PREFIX`, and `ROOM_COUNT` default to the values above. Seed enough accounts for the largest `PEAK_VUS` you intend to run. The application must accept the configured origin; `ORIGIN` defaults to `http://localhost:3000`.

## Run the scenarios

Start with smoke to check the wiring. Then run each remaining profile with both topologies. `single` puts every virtual user in the first room; `multi` distributes users across `ROOM_IDS`.

```powershell
k6 run -e PROFILE=smoke -e TOPOLOGY=single -e ROOM_IDS=1,2,3 k6/load-test.js
k6 run -e PROFILE=smoke -e TOPOLOGY=multi -e PEAK_VUS=3 -e ROOM_IDS=1,2,3 k6/load-test.js

k6 run -e PROFILE=steady -e TOPOLOGY=single -e ROOM_IDS=1,2,3 k6/load-test.js
k6 run -e PROFILE=stress -e TOPOLOGY=multi -e ROOM_IDS=1,2,3 k6/load-test.js
k6 run -e PROFILE=spike -e TOPOLOGY=single -e ROOM_IDS=1,2,3 k6/load-test.js
k6 run -e PROFILE=soak -e TOPOLOGY=multi -e ROOM_IDS=1,2,3 k6/load-test.js
```

| Profile | Default workload | Purpose |
| --- | --- | --- |
| `smoke` | 2 users for 20 seconds | Verify login, history, connection, subscription, and message echoes. |
| `steady` | Ramp to 20 users in 30 seconds, hold 5 minutes, ramp down in 30 seconds | Measure a sustained, repeatable workload. |
| `stress` | Peak 100 users: 25% ramp for 30 seconds and hold 1 minute; 50% ramp for 30 seconds and hold 1 minute; 100% ramp for 30 seconds and hold 1 minute; ramp down in 30 seconds | Locate the first workload level where latency or errors deteriorate. |
| `spike` | About 10% of peak for 30 seconds; ramp to peak in 5 seconds; hold 30 seconds; return to low load in 5 seconds; hold 30 seconds; ramp down in 10 seconds | Observe the effect of a sudden arrival burst. Default peak: 100 users. |
| `soak` | Ramp to 20 users in 30 seconds, hold 30 minutes, ramp down in 30 seconds | Look for gradual degradation as messages and resource use accumulate. |

`PEAK_VUS` overrides the profile's peak concurrency. `HOLD_SECONDS` overrides the smoke duration or the steady/soak hold period. Each user logs in once, then opens a series of chat sessions: fetch history, connect, subscribe, send messages, and disconnect. A session sends for 30 seconds by default, then waits up to 5 seconds for echoes, so this workload includes history reads and reconnections as well as ongoing chat traffic. Message sends follow a timer without waiting for the previous echo. The default content is 200 ASCII characters; replies, edits, deletes, similarity requests and browser rendering are outside this workload.

| Setting | Default | Meaning |
| --- | --- | --- |
| `SESSION_SECONDS` | `30` | Sending period of each chat session, before the echo drain. |
| `MSG_INTERVAL_MS` | `1000` | Target interval between messages from each user. |
| `ACK_TIMEOUT_MS` | `5000` | Maximum wait for the sender's matching message echo. |
| `CONNECT_TIMEOUT_MS` | `10000` | STOMP negotiation timeout after the WebSocket upgrade. |
| `ORIGIN` | `http://localhost:3000` | Origin sent during connection setup. |

An active session can finish during the graceful stop period. Its budget includes two 10-second HTTP timeouts, k6/ws's 60-second upgrade timeout, STOMP negotiation, sending, echo drain and a close allowance. Healthy sessions finish sooner. Ramp-down stages allow an already running session to finish, so actual traffic can linger for a session after a spike. Use time-series samples to see that tail. The profile controls client concurrency rather than a guaranteed arrival rate, and generator saturation can reduce the actual sending rate. See [k6 graceful stop](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/graceful-stop/).

## Read the measurements honestly

The key chat measurement is the time from sending a message to receiving its matching `tempId` echo. This covers the application's save-and-broadcast path as observed by the sender. A timeout is reported as **unacknowledged**: it does not prove the message was lost. Investigate application logs and persisted messages before making a loss claim.

The receiver count includes this run's observed `CREATE` message events. It is useful for comparing traffic, but does not prove that every subscribed recipient received every message. Login and history success, STOMP connection success, application errors, protocol errors and early socket closes are tracked separately. Subscription rejection is visible as a STOMP error or missing echoes; there is no separate subscription-success metric because the simple broker has no receipt support. The suite does not report an active-connections gauge. Empty echo latency is printed as `n/a`, and sends without a successful echo fail the required-sample check.

The initial performance budgets are proposals, not measured capacity: echo p95 below 500 ms and p99 below 1,000 ms; connection p95 below 2,000 ms; history p95 below 1,000 ms. Override them with `ECHO_P95_MS`, `ECHO_P99_MS`, `CONNECT_P95_MS`, and `HISTORY_P95_MS` when you have an agreed target. Login, history and HTTP requests require no failures; connection and echo success must exceed 99%, with zero protocol/application errors or early closes. Held stages also require echoes and meet the echo budgets separately, so low-load samples cannot hide a failing stress level. k6 exits nonzero on a failed threshold. See [k6 metrics](https://grafana.com/docs/k6/latest/using-k6/metrics/) and [thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/).

Set a unique `RUN_ID` when comparing runs; otherwise setup generates one. To retain readable and JSON summaries, provide `REPORT_PREFIX`. Create its parent directory first. Detailed samples help relate a stress stage or a soak slowdown to server metrics.

```powershell
New-Item -ItemType Directory -Force -Path reports | Out-Null
k6 run -e PROFILE=stress -e TOPOLOGY=single -e ROOM_IDS=1,2,3 -e RUN_ID=baseline-stress-single -e REPORT_PREFIX=reports/baseline-stress-single --out json=reports/baseline-stress-single.samples.json.gz k6/load-test.js
```

The prefix produces `.json` and `.txt` summaries. The compressed samples file can be large, especially for soak tests. k6 documents [summary export](https://grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/) and [JSON output](https://grafana.com/docs/k6/latest/results-output/real-time/json/).

Use results only when k6 exits successfully, the summary says `Accounting: complete`, and k6 reports zero interrupted iterations. The JSON report also contains `complete`. Cancellation, a forced termination, a blocked socket write or a shortened CLI grace setting can prevent final accounting; even a zero exit code does not make such a run valid. The session counters and send/outcome balance flag an unfinished run when a summary is available.

## Investigate a bottleneck

Capture CPU, memory, JVM heap and GC, database connections and query timings alongside the run. Compare the same profile with the same total users and send interval in each topology. One room increases fan-out to a shared audience; distributing users reduces each room's audience. Higher receiver traffic and worse latency in the busy room can point to broadcast work, but server evidence is needed to establish the cause.

| Observation | Evidence to check |
| --- | --- |
| One busy room degrades before multiple rooms | Broadcast volume, outbound queues, network throughput, and the [Spring simple broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html). |
| History gets slower as the run continues | Returned history size, unpaginated queries, database query plans, and response size. |
| Echo latency rises with database wait time | Connection-pool wait, pool saturation, slow queries, locks, and write latency. |
| Periodic latency spikes or rising memory | JVM allocation, heap use, GC pauses, and thread activity. |
| k6 itself reaches CPU or network limits | Generator CPU, memory, socket use, and network throughput; repeat with sufficient generator resources. |

Record the first affected stage, error types, latency distribution, actual throughput, and matching resource evidence. A useful conclusion names the observed limit and the supporting measurement; it does not infer production capacity from a single run. An isolated H2 smoke test verifies suite wiring only. Performance claims require a measured run against the intended MySQL environment.

Failure and recovery experiments belong to point 5. CI automation and retained benchmark evidence belong to point 6; neither is established by adding this suite.
