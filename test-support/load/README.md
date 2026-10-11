# Local moving-client comparison

This fixture compares a fresh Paper server with and without the built NordGuard
JAR whose version matches `plugin.yml`. It does not modify that JAR. LoadProbe is test-only and must not be
installed on a public server. Its tick measurement uses Paper's
`ServerTickEndEvent`; this is **not a Folia benchmark**.

The runner binds only `127.0.0.1:25659`. It requires a new directory whose name
starts with `nordguard-test-`, an available port, an existing accepted EULA in an
isolated seed, and at least 5 GiB free physical RAM. Only the seed's server JAR,
libraries, cache, versions and EULA are copied. No worlds, player data, production
configuration or accounts are copied.

Both arms use a synthetic flat world, view/simulation distance 3, a 2 GiB maximum
Java heap, peaceful difficulty and LoadProbe. The guard arm uses the source
default configuration: OBSERVE, packet observer enabled, experimental replica
and prediction disabled. Stages have 10, 25 and 50 offline Mineflayer clients.
Each client walks in a separate lane and reverses every five seconds. Clients
remain connected as the population grows. After joining and a three-second
settle, each stage warms for 15 seconds and measures for 30 seconds.

The runner requires compatible Mineflayer modules that support protocol 26.2.
The installed local NordLoadTest dependency tree was used for the initial run;
the runner neither changes those modules nor publishes that directory.

```powershell
.\test-support\load\build.ps1
node .\test-support\load\run.cjs <fresh-test-root> <java.exe> <isolated-paper-seed> <node_modules>
```

Set `NORD_LOAD_REVERSE=1` to run the guard arm before the baseline. Use a fresh
root for every run. Both child servers stop after the comparison. Test worlds,
logs and `load-results.json` remain in that root for diagnosis.

## Measurements and limits

The JSON records median/p95/maximum server tick duration, observed tick and
position-change counts, server-process CPU time, GC count/time, maximum sampled
used heap and free host RAM. CPU is expressed as occupied cores, not a percentage
of the whole machine. Heap is sampled once per server tick, not a precise
allocation total, retained-size measurement or leak test. GC time comes from
the JVM management counters, not a breakdown of individual pause phases.

The generator's own CPU and event-loop p95 are recorded separately. The latter
uses a 20 ms histogram resolution; its values include that resolution and are
not a pure excess-delay figure. NordGuard's cumulative status is captured after
each guard stage, including completed correction and deferred-sample counts.

The runner checks free RAM every ten seconds and stops if it drops below 3 GiB.
It does not advance to a larger stage if a 30-second window contains fewer than
500 server ticks, p95 tick duration reaches 45 ms, a client disconnects, or the
position-change count is below 200 per client. These are experiment stop limits,
not production alert thresholds or guarantees against every resource spike.

The runner also marks a stage as workload-qualified only when it receives at
least 18 position-change events per player per second and generator event-loop
p95 stays below 100 ms. If this fails, the measurement is retained as
generator-limited and no larger stage is attempted in that arm. These checks
were added after the first two runs exposed the 50-client generator bottleneck;
their qualification can also be calculated from those saved raw measurements.

Server and generator share a Windows laptop, and Windows/client timers affect
packet cadence. This is a short walking-only synthetic test. It excludes combat,
building, mobs, exploration, real player networking, production plugin
interactions, long-term memory growth, Folia region scheduling and experimental
prediction. It cannot establish capacity for 600 players or performance relative
to another anti-cheat.

The first exploratory run used synchronous PowerShell memory polling, which
periodically paused the generator. It remains diagnostic evidence only. The
subsequent runner uses the operating system's free-memory value directly and
records generator timing to make this limitation visible.
