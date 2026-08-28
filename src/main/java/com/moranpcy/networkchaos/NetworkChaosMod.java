package com.moranpcy.networkchaos;

import com.moranpcy.networkchaos.api.ChaosConfig;
import com.moranpcy.networkchaos.api.ChaosPresets;
import com.moranpcy.networkchaos.api.ChaosStats;
import com.moranpcy.networkchaos.api.DirectionStats;
import com.moranpcy.networkchaos.api.NetworkChaos;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class NetworkChaosMod implements ModInitializer {
    public static final String MOD_ID = "network_chaos_fabric";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> dispatcher.register(
                        literal("networkchaos")
                                .requires(source -> source.permissions()
                                        .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(literal("off").executes(context -> {
                                    NetworkChaos.disable();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "Network Chaos disabled; queued packets discarded"),
                                            false);
                                    return 1;
                                }))
                                .then(literal("reset").executes(context -> {
                                    NetworkChaos.reset();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "Network Chaos reset"),
                                            false);
                                    return 1;
                                }))
                                .then(literal("status").executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(status()), false);
                                    return 1;
                                }))
                                .then(literal("preset")
                                        .then(literal("latency")
                                                .then(seedArgument(ChaosPreset.LATENCY)))
                                        .then(literal("lossy")
                                                .then(seedArgument(ChaosPreset.LOSSY)))
                                        .then(literal("bad")
                                                .then(seedArgument(ChaosPreset.BAD))))));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> NetworkChaos.reset());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<
            net.minecraft.commands.CommandSourceStack, Long> seedArgument(
            ChaosPreset preset) {
        return argument("seed", LongArgumentType.longArg())
                .executes(context -> {
                    long seed = LongArgumentType.getLong(context, "seed");
                    ChaosConfig config = switch (preset) {
                        case LATENCY -> ChaosPresets.latency(seed);
                        case LOSSY -> ChaosPresets.lossy(seed);
                        case BAD -> ChaosPresets.bad(seed);
                    };
                    NetworkChaos.enable(config);
                    context.getSource().sendSuccess(
                            () -> Component.literal(
                                    "Network Chaos enabled: "
                                            + preset.name().toLowerCase(Locale.ROOT)
                                            + ", seed=" + seed),
                            false);
                    return 1;
                });
    }

    private static String status() {
        ChaosStats stats = NetworkChaos.stats();
        return "Network Chaos "
                + (NetworkChaos.isEnabled() ? "enabled" : "disabled")
                + " | C2S " + compact(stats.clientToServer())
                + " | S2C " + compact(stats.serverToClient())
                + " | ignored(filter/control/non-play/non-local)="
                + stats.ignoredByFilter() + "/"
                + stats.protectedControlPackets() + "/"
                + stats.ignoredNonPlay() + "/"
                + stats.ignoredNonLocal();
    }

    private static String compact(DirectionStats stats) {
        return "seen=" + stats.seen()
                + ",pass=" + stats.passedImmediately()
                + ",drop=" + stats.dropped()
                + ",delay=" + stats.delayed()
                + ",dup=" + stats.duplicated()
                + ",deliver=" + stats.deliveredFromQueue()
                + ",cancel=" + stats.cancelledFromQueue()
                + ",fail=" + stats.failedFromQueue();
    }

    private enum ChaosPreset {
        LATENCY,
        LOSSY,
        BAD
    }
}
