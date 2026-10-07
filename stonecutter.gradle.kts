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

    // Before 1.21.11: no gizmos (the mod brings the same calls, package render.legacy), no jspecify, and some
    // classes and getters had other names.
    oneWay(current.parsed < "1.21.11",
        "\\bnet\\.minecraft\\.gizmos\\." to "io.github.autyism.qolbundle.render.legacy.",
        "\\borg\\.jspecify\\.annotations\\.Nullable\\b" to "org.jetbrains.annotations.Nullable",
        "\\bimport net\\.minecraft\\.util\\.Util;" to "import net.minecraft.Util;",
        "\\.dimension\\(\\)\\.identifier\\(\\)" to ".dimension().location()",
        "(\\.|float )yRot\\(\\)" to "$1getYRot()",
        "(\\.|float )xRot\\(\\)" to "$1getXRot()",
        "\\bnet\\.minecraft\\.world\\.entity\\.npc\\.villager\\." to "net.minecraft.world.entity.npc.",
        "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.pig\\.Pig\\b" to "net.minecraft.world.entity.animal.Pig",
        "\\bnet\\.minecraft\\.world\\.level\\.gamerules\\.GameRules\\b" to "net.minecraft.world.level.GameRules",
        "\\bSheets\\.translucentBlockItemSheet\\(\\)" to "Sheets.translucentItemSheet()",
        "\\bextends Button\\.Plain\\b" to "extends Button",
    )
    // 1.21.9-1.21.10: GUI outline call renamed; and Fabric API for 1.21.9 has no world-drawing events, so on these
    // two versions the mod uses its own (render.legacy, same names and calls) and one jar runs on both.
    oneWay(current.parsed >= "1.21.9" && current.parsed < "1.21.11",
        "\\.renderOutline\\(" to ".submitOutline(",
        "\\bnet\\.fabricmc\\.fabric\\.api\\.client\\.rendering\\.v1\\.world\\.(WorldRenderContext|WorldRenderEvents)\\b" to "io.github.autyism.qolbundle.render.legacy.$1",
    )
    // Before 1.21.9: Fabric's older world-rendering package, no Avatar (players were just Player), and the
    // window handle had another getter. Self-test only: key presses were plain numbers, the chat screen took one
    // argument, and (1.21.6-1.21.8) leaving a world went through the pause screen.
    oneWay(current.parsed < "1.21.9",
        "\\brendering\\.v1\\.world\\.WorldRenderContext\\b" to "rendering.v1.WorldRenderContext",
        "\\brendering\\.v1\\.world\\.WorldRenderEvents\\b" to "rendering.v1.WorldRenderEvents",
        "\\bnet\\.minecraft\\.world\\.entity\\.Avatar\\b" to "net.minecraft.world.entity.player.Player",
        "\\bAvatar\\b" to "Player",
        "\\.getWindow\\(\\)\\.handle\\(\\)" to ".getWindow().getWindow()",
        "\\.keyPressed\\(new KeyEvent\\(([^()]*)\\)\\)" to ".keyPressed($1)",
        "\\bnew ChatScreen\\(\"\", false\\)" to "new ChatScreen(\"\")",
    )
    oneWay(current.parsed >= "1.21.6" && current.parsed < "1.21.9",
        "\\bclient\\.disconnectFromWorld\\(" to "net.minecraft.client.gui.screens.PauseScreen.disconnectFromWorld(client, ",
    )
    // Before 1.21.6: the GUI was moved with a 3D PoseStack, the camera's position had a getter, and
    // screenshots had no size option. Self-test only: no ready-made "leave the world" (Scenarios has its own).
    oneWay(current.parsed < "1.21.6",
        "\\bcontext\\.pose\\(\\)\\.pushMatrix\\(\\)" to "context.pose().pushPose()",
        "\\bcontext\\.pose\\(\\)\\.popMatrix\\(\\)" to "context.pose().popPose()",
        "\\bcontext\\.pose\\(\\)\\.translate\\(([^;]*)\\);" to "context.pose().translate($1, 0F);",
        "\\bcontext\\.pose\\(\\)\\.scale\\(([^;]*)\\);" to "context.pose().scale($1, 1F);",
        "\\bcontext\\.pose\\(\\)\\.rotate\\(([^;]*)\\);" to "context.pose().mulPose(com.mojang.math.Axis.ZP.rotation($1));",
        "\\.getMainCamera\\(\\)\\.position\\(\\)" to ".getMainCamera().getPosition()",
        "\\b(client\\.getMainRenderTarget\\(\\)|framebuffer), 1, message ->" to "$1, message ->",
        "\\bclient\\.disconnectFromWorld\\(" to "leaveWorld(client, ",
    )
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
    // 26.3: input goes through SDL. Keyboard keys are SDL scancodes of key type KEYBOARD, mouse buttons are
    // numbered from 1, and there is no GLFW. InputConstants names the same physical keys on every version.
    oneWay(current.parsed >= "26.3",
        "\\bInputConstants\\.Type\\.KEYSYM\\b" to "InputConstants.Type.KEYBOARD",
        "\\bGLFW\\.GLFW_KEY_LEFT_ALT\\b" to "com.mojang.blaze3d.platform.InputConstants.KEY_LALT",
        "\\bGLFW\\.GLFW_KEY_LEFT_BRACKET\\b" to "com.mojang.blaze3d.platform.InputConstants.KEY_LBRACKET",
        "\\bGLFW\\.GLFW_KEY_RIGHT_BRACKET\\b" to "com.mojang.blaze3d.platform.InputConstants.KEY_RBRACKET",
        "\\bGLFW\\.GLFW_KEY_(K|V|F6|F7|F8)\\b" to "com.mojang.blaze3d.platform.InputConstants.KEY_$1",
        "\\bclick\\.button\\(\\) == 0\\b" to "click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT",
        // self-test only: a key press carries the key's SDL code too (see Scenarios.keyEvent)
        "\\bnew KeyEvent\\(GLFW\\.GLFW_KEY_(UP|DOWN|TAB), 0, 0\\)" to "keyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_$1)",
        // blaze3d's GPU classes moved to renderpearl
        "\\bcom\\.mojang\\.blaze3d\\.textures\\." to "com.mojang.renderpearl.api.textures.",
        "\\bcom\\.mojang\\.blaze3d\\.GpuFormat\\b" to "com.mojang.renderpearl.api.GpuFormat",
        "\\bRedStoneWireBlock\\b" to "RedstoneWireBlock",
        // GameRendererMixin: renderLevel() takes no arguments any more
        "\\boriginal\\.call\\(renderer, tickCounter\\)" to "original.call(renderer)",
    )
}
