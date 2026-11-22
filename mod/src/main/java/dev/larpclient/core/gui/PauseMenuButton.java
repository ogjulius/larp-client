package dev.larpclient.core.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Adds a "LARP Mods" button to the in-game pause menu so the menu is discoverable without a keybind. */
public final class PauseMenuButton {
    private static final int ID = 0x1A2B;

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiIngameMenu)) {
            return;
        }
        for (GuiButton button : event.buttonList) {
            if (button.id == 7) { // "Open to LAN" (Forge widens it to the full row)
                button.width = 98;
                event.buttonList.add(new GuiButton(ID, button.xPosition + 102, button.yPosition, 98, 20, "LARP Mods"));
                return;
            }
        }
        // Fallback: put it under everything else.
        int x = event.gui.width / 2 - 100;
        int y = event.gui.height / 4 + 144;
        event.buttonList.add(new GuiButton(ID, x, y, 200, 20, "LARP Mods"));
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.gui instanceof GuiIngameMenu && event.button.id == ID) {
            Minecraft.getMinecraft().displayGuiScreen(new ModMenuScreen());
            event.setCanceled(true);
        }
    }
}
