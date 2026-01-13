package wsmc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;

@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {
	@Invoker("updateStatus")
	void wsmc$updateStatus(Component component);
}
