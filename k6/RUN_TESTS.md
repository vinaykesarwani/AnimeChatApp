# Run the load tests

These commands use PowerShell. Install Java 21, Node.js, k6 and MySQL first. Java, Node.js and k6 should be on PATH, and JAVA_HOME should point to your JDK.

Run the tests one at a time. The full set takes about 95 minutes.

## 1. Check MySQL and local settings

```powershell
Get-Service MySQL80
```

The status should be `Running`. Set up `backend/.env` using [backend/.env.example](../backend/.env.example), including your local database credentials. Keep the actual `.env` file out of Git.

## 2. Build and start the backend

Open PowerShell at the project root:

```powershell
cd backend
./mvnw.cmd clean package

$dbName = 'animechat_benchmark_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
$env:DB_URL = "jdbc:mysql://127.0.0.1:3306/${dbName}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:PORT = '18080'

java -Dspring.devtools.restart.enabled=false -jar ./target/backend-0.0.1-SNAPSHOT.jar --spring.jpa.show-sql=false --logging.level.org.springframework.security=WARN
```

This creates a fresh benchmark database. Your MySQL user needs permission to create it. Wait for `Started BackendApplication` and leave this terminal running.

## 3. Create test users and rooms

Open a second PowerShell terminal at the project root. Use this same terminal for all remaining commands.

```powershell
$env:BASE_URL = 'http://localhost:18080'
$env:USER_PREFIX = 'manual_'
$env:PASSWORD = 'LoadTestOnly123!'
$env:USERS = '100'
$env:ROOM_COUNT = '28'

node k6/seed.js
```

PASSWORD is for the test chat accounts. The seed command creates and verifies 100 accounts.

The commands below assume it prints room IDs `1` through `28`. If the IDs differ, use the printed IDs in the same order. Each test gets separate rooms so it starts with empty history.

## 4. Set the workload and report folder

```powershell
$env:SESSION_SECONDS = '30'
$env:MSG_INTERVAL_MS = '1000'
$env:ACK_TIMEOUT_MS = '5000'
$env:CONNECT_TIMEOUT_MS = '10000'

$reports = 'k6/reports/manual-' + (Get-Date -Format 'yyyyMMdd_HHmmss')
New-Item -ItemType Directory -Force -Path $reports | Out-Null
```

## 5. Run smoke tests

```powershell
k6 run -e PROFILE=smoke -e TOPOLOGY=single -e PEAK_VUS=2 -e HOLD_SECONDS=20 -e ROOM_IDS=1 -e "REPORT_PREFIX=$reports/smoke-single" k6/load-test.js
k6 run -e PROFILE=smoke -e TOPOLOGY=multi -e PEAK_VUS=3 -e HOLD_SECONDS=20 -e ROOM_IDS=2,3,4 -e "REPORT_PREFIX=$reports/smoke-multi" k6/load-test.js
```

## 6. Run steady-load tests

100 users, with a five-minute hold in each test:

```powershell
k6 run -e PROFILE=steady -e TOPOLOGY=single -e PEAK_VUS=100 -e HOLD_SECONDS=300 -e ROOM_IDS=5 -e "REPORT_PREFIX=$reports/steady-single" k6/load-test.js
k6 run -e PROFILE=steady -e TOPOLOGY=multi -e PEAK_VUS=100 -e HOLD_SECONDS=300 -e ROOM_IDS=6,7,8,9,10 -e "REPORT_PREFIX=$reports/steady-multi" k6/load-test.js
```

## 7. Run stress tests

The profile ramps through 25, 50 and 100 users, holding each level for one minute.

```powershell
k6 run -e PROFILE=stress -e TOPOLOGY=single -e PEAK_VUS=100 -e ROOM_IDS=11 -e "REPORT_PREFIX=$reports/stress-single" k6/load-test.js
k6 run -e PROFILE=stress -e TOPOLOGY=multi -e PEAK_VUS=100 -e ROOM_IDS=12,13,14,15,16 -e "REPORT_PREFIX=$reports/stress-multi" k6/load-test.js
```

## 8. Run spike tests

The profile starts at 10 users, ramps to 100 in five seconds, holds for 30 seconds, then returns to lower load.

```powershell
k6 run -e PROFILE=spike -e TOPOLOGY=single -e PEAK_VUS=100 -e ROOM_IDS=17 -e "REPORT_PREFIX=$reports/spike-single" k6/load-test.js
k6 run -e PROFILE=spike -e TOPOLOGY=multi -e PEAK_VUS=100 -e ROOM_IDS=18,19,20,21,22 -e "REPORT_PREFIX=$reports/spike-multi" k6/load-test.js
```

## 9. Run soak tests

20 users, with a full 30-minute hold in each test:

```powershell
k6 run -e PROFILE=soak -e TOPOLOGY=single -e PEAK_VUS=20 -e HOLD_SECONDS=1800 -e ROOM_IDS=23 -e "REPORT_PREFIX=$reports/soak-single" k6/load-test.js
k6 run -e PROFILE=soak -e TOPOLOGY=multi -e PEAK_VUS=20 -e HOLD_SECONDS=1800 -e ROOM_IDS=24,25,26,27,28 -e "REPORT_PREFIX=$reports/soak-multi" k6/load-test.js
```

## 10. Check reports and stop the backend

Each test writes `.json` and `.txt` summaries. Check for `Accounting: complete`, `Failed thresholds: none` and zero interrupted iterations. Let active sessions finish after each stage.

```powershell
explorer.exe $reports
```

For raw timestamped metrics, add `--out "json=$reports/<test-name>.samples.json"` to a k6 command. These files can be large.

Press Ctrl+C in the backend terminal when finished. The benchmark database stays available for checking saved messages.

Previous measured results are in [RESULTS.md](RESULTS.md).
