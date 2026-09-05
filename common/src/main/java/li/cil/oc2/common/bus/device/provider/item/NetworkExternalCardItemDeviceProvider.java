/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.bus.device.provider.item;

import li.cil.oc2.api.bus.device.ItemDevice;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import li.cil.oc2.common.Config;
import li.cil.oc2.common.bus.device.provider.util.AbstractItemDeviceProvider;
import li.cil.oc2.common.bus.device.vm.item.NetworkExternalCardDevice;
import li.cil.oc2.common.item.Items;

import java.util.Optional;

public final class NetworkExternalCardItemDeviceProvider extends AbstractItemDeviceProvider {
    public NetworkExternalCardItemDeviceProvider() {
        super(Items.NETWORK_EXTERNAL_CARD);
    }

    @Override
    protected Optional<ItemDevice> getItemDevice(final ItemDeviceQuery query) {
        return Optional.of(new NetworkExternalCardDevice(query.getItemStack()));
    }

    @Override
    protected int getItemDeviceEnergyConsumption(final ItemDeviceQuery query) {
        return Config.networkInterfaceEnergyPerTick;
    }
}
