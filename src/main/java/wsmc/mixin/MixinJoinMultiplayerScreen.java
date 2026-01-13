package wsmc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

import wsmc.client.WSMCScreen;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {
	protected MixinJoinMultiplayerScreen(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void wsmc$addButton(CallbackInfo ci) {
		int x = this.width - 80;
		int y = 6;
		this.addRenderableWidget(Button.builder(Component.literal("WSMC"), b -> {
			Minecraft.getInstance().setScreen(new WSMCScreen());
		}).pos(x, y).size(70, 20).build());
	}
}
