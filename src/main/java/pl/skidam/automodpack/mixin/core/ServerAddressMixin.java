package pl.skidam.automodpack.mixin.core;

import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import pl.skidam.automodpack_core.auth.AddressPins;

/**
 * Lets players append the AutoModpack certificate fingerprint to the server address
 * ("play.example.com;<fingerprint>"). The suffix is stripped before vanilla parses or
 * validates the address, and the fingerprint is pinned for this session so the first
 * connection needs no manual verification screen.
 */
@Mixin(ServerAddress.class)
public abstract class ServerAddressMixin {

    @ModifyVariable(method = "parseString", at = @At("HEAD"), argsOnly = true)
    private static String automodpack$stripEmbeddedPin(String address) {
        return AddressPins.extractAndStore(address);
    }

    @ModifyVariable(method = "isValidAddress", at = @At("HEAD"), argsOnly = true)
    private static String automodpack$stripEmbeddedPinForValidation(String address) {
        return AddressPins.extractAndStore(address);
    }
}
