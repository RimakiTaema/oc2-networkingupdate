/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.bus.device.vm.item;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import li.cil.oc2.common.Config;
import li.cil.oc2.common.network.external.LibslirpNative;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class NetworkExternalCardDevice extends AbstractNetworkInterfaceDevice {
    private static final Set<NetworkExternalCardDevice> DEVICES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final int MAX_FRAME_SIZE = 65_536;

    private LibslirpNative slirp;

    public NetworkExternalCardDevice(final ItemStack identity) {
        super(identity);
    }

    public static void initialize() {
        TickEvent.SERVER_PRE.register(server -> DEVICES.forEach(NetworkExternalCardDevice::tick));
        LifecycleEvent.SERVER_STOPPED.register(server -> DEVICES.forEach(NetworkExternalCardDevice::close));
        LifecycleEvent.SERVER_STOPPED.register(server -> DEVICES.clear());
    }

    @Override
    public <T> T getCapability(final li.cil.oc2.common.capabilities.CapabilityType<T> capability, final net.minecraft.core.Direction side) {
        // This card is deliberately isolated from Minecraft's virtual Ethernet bus.
        return null;
    }

    @Override
    public li.cil.oc2.api.bus.device.vm.VMDeviceLoadResult mount(final li.cil.oc2.api.bus.device.vm.context.VMContext context) {
        if (!Config.externalNetworkEnabled) {
            return li.cil.oc2.api.bus.device.vm.VMDeviceLoadResult.fail()
                .withErrorMessage(Component.literal("External networking is disabled by the server"));
        }
        if (!LibslirpNative.isAvailable()) {
            return li.cil.oc2.api.bus.device.vm.VMDeviceLoadResult.fail()
                .withErrorMessage(Component.literal("The oc2slirp native library is not installed"));
        }

        final var result = super.mount(context);
        if (!result.wasSuccessful()) {
            return result;
        }

        slirp = LibslirpNative.create();
        if (slirp == null) {
            super.unmount();
            return li.cil.oc2.api.bus.device.vm.VMDeviceLoadResult.fail()
                .withErrorMessage(Component.literal("Failed to initialize external networking"));
        }
        DEVICES.add(this);
        return result;
    }

    @Override
    public void unmount() {
        DEVICES.remove(this);
        close();
        super.unmount();
    }

    private void tick() {
        if (slirp == null) return;
        final var network = getNetworkInterface();
        int budget = Math.max(Config.externalNetworkFramesPerTick, 0);
        byte[] frame;
        while (budget-- > 0 && (frame = network.readEthernetFrame()) != null) {
            slirp.input(frame);
        }
        slirp.poll(0);
        final byte[] output = new byte[MAX_FRAME_SIZE];
        budget = Math.max(Config.externalNetworkFramesPerTick, 0);
        while (budget-- > 0) {
            final int length = slirp.nextFrame(output);
            if (length <= 0) break;
            final byte[] frameCopy = java.util.Arrays.copyOf(output, length);
            network.writeEthernetFrame(network, frameCopy, 1);
        }
    }

    private void close() {
        if (slirp != null) {
            slirp.close();
            slirp = null;
        }
    }
}
