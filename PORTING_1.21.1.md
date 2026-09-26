# Minecraft 1.21.1 compatibility

This branch targets Fabric Minecraft 1.21.1 with Java 21, Gradle 8.12.1 and Loom 1.10.5. Other 1.21.x versions are not supported by this artifact.

## Changes

- Use Mojang mappings and remap the release JAR to intermediary names.
- Adapt Connection interception and completion callbacks to PacketSendListener.
- Use the 1.21.1 command permission API.
- Identify protected control packets by packet type ID, which remains stable in production.
- Preserve packet loss, latency, jitter, duplication, reordering, seeds and exact-drop rules.

## Build and verification

Run with Java 21:

```shell
./gradlew build runGameTest
```

The build produces `build/libs/network-chaos-fabric-1.0.0-alpha.3+1.21.1.jar`.

Validation on 2026-09-26 passed 10 unit tests and one sequential server GameTest covering packet loss, exact counts, delay, queue cancellation, keep-alive/ping protection, disabled protection and send completion callbacks.

The remapped JAR also loaded alongside Printer, ChainVein and Quick Shulker in a production Fabric client. Printing, replenishment and chain mining passed. This combined client scenario did not inject network faults; fault behavior was verified by the unit and server tests. Public-network and long-running multiplayer stress tests were not performed.

Gradle writes generated test reports under `build/reports`; generated reports and JARs are not tracked in Git.
