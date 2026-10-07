plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"

// src/ is always kept in the 1.21.11 state; never switch the active version.
// The renames below go one way: they are applied to the versions their condition is true for, and
// their reverse pattern "(?!)" matches nothing, so the 1.21.11 source is never touched by them.
// Only whole names that mean one thing in this code are renamed this way; everything else uses //? conditions.
val never = "(?!)"

stonecutter parameters {
    fun oneWay(condition: Boolean, vararg renames: Pair<String, String>) {
        for ((from, to) in renames) {
            replacements.regex(condition) { replace(from, to, never, to) }
        }
    }

    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }

    // 26.1: GUI drawing renamed (same arguments). These calls only ever go to GuiGraphics in this code.
    oneWay(current.parsed >= "26.1",
        "\\bGuiGraphics\\b" to "GuiGraphicsExtractor",
        "\\.drawString\\(" to ".text(",
        "\\.drawCenteredString\\(" to ".centeredText(",
        "\\.renderItemDecorations\\(" to ".itemDecorations(",
        "\\.renderItem\\(" to ".item(",
        "\\.renderOutline\\(" to ".outline(",
    )
    // 26.1: Fabric API renames
    oneWay(current.parsed >= "26.1",
        "\\bkeybinding\\.v1\\.KeyBindingHelper\\b" to "keymapping.v1.KeyMappingHelper",
        "\\bKeyBindingHelper\\.registerKeyBinding\\(" to "KeyMappingHelper.registerKeyMapping(",
        "\\bKeyBindingHelper\\.getBoundKeyOf\\(" to "KeyMappingHelper.getBoundKeyOf(",
        "\\brendering\\.v1\\.world\\.WorldRenderContext\\b" to "rendering.v1.level.LevelRenderContext",
        "\\bWorldRenderContext\\b" to "LevelRenderContext",
        "\\bScreens\\.getButtons\\(" to "Screens.getWidgets(",
        "\\bScreenEvents\\.afterRender\\(" to "ScreenEvents.afterExtract(",
    )
    // 26.1: classes that moved or were renamed
    oneWay(current.parsed >= "26.1",
        "\\bnet\\.minecraft\\.client\\.GuiMessage\\b" to "net.minecraft.client.multiplayer.chat.GuiMessage",
        "\\bnet\\.minecraft\\.client\\.GuiMessageTag\\b" to "net.minecraft.client.multiplayer.chat.GuiMessageTag",
        "\\bnet\\.minecraft\\.world\\.inventory\\.ClickType\\b" to "net.minecraft.world.inventory.ContainerInput",
        "\\bClickType\\b" to "ContainerInput",
        "\\.handleInventoryMouseClick\\(" to ".handleContainerInput(",
        // a container's stacks are now handed out as templates; copies read the same
        "\\.nonEmptyItems\\(\\)" to ".nonEmptyItemCopyStream().toList()",
    )
    // 26.2: the open screen moved from Minecraft to Gui, chat and on-screen messages to Gui's Hud.
    // "client" is always a Minecraft in this code, "minecraft" always a screen's Minecraft field.
    oneWay(current.parsed >= "26.2",
        "(?<![.\\w])client\\.screen\\b" to "client.gui.screen()",
        "\\b(client|minecraft)\\.setScreen\\(" to "$1.gui.setScreen(",
        "\\bclient\\.getOverlay\\(\\)" to "client.gui.overlay()",
        "\\bclient\\.getToastManager\\(\\)" to "client.gui.toastManager()",
        "\\bclient\\.getMainRenderTarget\\(\\)" to "client.gameRenderer.mainRenderTarget()",
        "\\bclient\\.options\\.hideGui\\b" to "client.gui.hud.isHidden()",
        "\\.gui\\.getChat\\(\\)" to ".gui.hud.getChat()",
        "\\.gui\\.getGuiTicks\\(\\)" to ".gui.hud.getGuiTicks()",
        "\\.gui\\.setOverlayMessage\\(" to ".gui.hud.setOverlayMessage(",
        "\\.getMainCamera\\(\\)" to ".mainCamera()",
        "\\bI18n\\.exists\\(" to "net.minecraft.locale.Language.getInstance().has(",
        "\\bclient\\.levelRenderer\\.countRenderedSections\\(\\)" to "client.levelExtractor.countRenderedSections()",
        "\\bDripstoneThickness\\b" to "SpeleothemThickness",
        // self-test only: the flat preset helper is gone (Scenarios has its own)
        "\\bWorldPresets::createFlatWorldDimensions\\b" to "Scenarios::flatDimensions",
    )
}
