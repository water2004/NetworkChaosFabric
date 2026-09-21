# Network Chaos Fabric

A small Fabric test mod for injecting deterministic network faults into local
Minecraft connections (integrated-server memory connections and loopback TCP).
It is intended for repeatable integration tests of mods such as printers,
inventory protocols, and server-authoritative interactions.

## Scope and safety

- Disabled by default.
- Only intercepts in-memory or loopback traffic, so it cannot affect a normal
  remote multiplayer connection.
- Only intercepts packets after the connection enters the PLAY protocol.
- Keep-alive, disconnect, login/configuration, ping/pong, cookie, and resource
  pack control packets are protected by default.
- Disabling or reconfiguring the injector invalidates all packets still in its
  delay queue, which makes test cleanup deterministic.
- A deliberately dropped packet completes an attached send listener as a
  successful local write, matching the sender-side view of loss after sending.

The loss option deliberately drops complete logical Minecraft packets. Real
Minecraft normally runs over TCP, where physical packet loss is usually seen
as retransmission delay rather than a missing application message. Logical
loss is intentionally harsher and is useful for reproducing ghost state and
missing-confirmation behavior.

## Supported faults

Each direction has an independent [`LinkProfile`](src/main/java/org/edtp/networkchaos/api/LinkProfile.java):

- packet loss probability;
- base latency;
- symmetric latency jitter;
- packet duplication probability;
- reordering probability and additional hold time.

A seed makes each direction's sequence independently reproducible, so adding
C2S traffic does not change the S2C fault sequence. A full-class-name regex can select
only packets relevant to a test, for example only carried-slot and block-update
packets.

Tests can also install ordered exact-drop rules. Each rule selects one traffic
direction and one full packet-class-name regex, then drops exactly the first
configured number of matches. This covers deterministic regression scenarios
without adding packet-specific mixins to every consuming project.

## In-game commands

Commands require an explicit seed:

```text
/networkchaos preset latency 1234
/networkchaos preset lossy 1234
/networkchaos preset bad 1234
/networkchaos status
/networkchaos off
/networkchaos reset
```

## Test API

```java
LinkProfile c2s = new LinkProfile(
        0.25, 100, 50, 0.01, 0.05, 250);
LinkProfile s2c = new LinkProfile(
        0.10, 150, 75, 0.01, 0.05, 250);

ChaosConfig config = new ChaosConfig(
        c2s,
        s2c,
        1234L,
        true,
        ".*(ServerboundSetCarriedItemPacket|ClientboundBlockUpdatePacket)",
        "(?!)");

config = config.withExactDropRules(
        ExactDropRule.forPacketClass(
                TrafficDirection.CLIENT_TO_SERVER,
                ServerboundSetCarriedItemPacket.class,
                3));

NetworkChaos.enable(config);
try {
    // Run the integration scenario and inspect NetworkChaos.stats().
} finally {
    NetworkChaos.reset();
}
```

The public API is under `org.edtp.networkchaos.api`; Minecraft interception,
scheduling, and random decisions remain internal.

## Build and verification

The project targets Minecraft 26.3 and Java 25.

```powershell
.\gradlew.bat build runGameTest
```

Unit tests verify configuration validation, filtering, and seeded decisions.
GameTests send real PLAY packets through Minecraft's `Connection` and the
production Mixin (using a package-private override because Mojang's mock player
uses an `EmbeddedChannel`, not the `LocalChannel` used by actual single-player)
and verify drop, delayed delivery, and queued-packet cancellation.
