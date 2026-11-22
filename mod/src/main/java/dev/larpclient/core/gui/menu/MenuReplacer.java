package dev.larpclient.core.gui.menu;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Swaps the vanilla title screen for {@link LarpMainMenu}. */
public final class MenuReplacer {
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui instanceof GuiMainMenu) {
            event.gui = new LarpMainMenu();
        }
    }
}
