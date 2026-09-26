package org.edtp.networkchaos.mixin;

import org.edtp.networkchaos.internal.NetworkChaosRuntime;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow
    private Channel channel;

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;"
                    + "Lnet/minecraft/network/PacketSendListener;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private void networkChaos$interceptSend(
            Packet<?> packet,
            PacketSendListener listener,
            boolean flush,
            CallbackInfo callback) {
        if (NetworkChaosRuntime.intercept(
                (Connection) (Object) this,
                packet,
                listener,
                channel,
                flush)) {
            callback.cancel();
        }
    }
}
