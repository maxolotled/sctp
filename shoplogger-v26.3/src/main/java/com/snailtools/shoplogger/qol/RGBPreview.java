package com.snailtools.shoplogger.qol;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RGBPreview {

    private static final Pattern HEX_BLOCK = Pattern.compile("&#([0-9a-fA-F]{6})");

    public static int execute(CommandContext<FabricClientCommandSource> ctx) {

        String input = StringArgumentType.getString(ctx, "name");
        Minecraft minecraft = Minecraft.getInstance();
        MutableComponent name = parseName(input);

        if (name == null || name.getString().isEmpty()) {
            minecraft.player.sendSystemMessage(Component.literal("Please input a valid rename string format"));
            return 0;
        }

        if (minecraft.player == null) {
            return 0;
        }

        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            minecraft.player.sendSystemMessage(Component.literal("Please hold an item whilst using this command"));
            return 0;
        }

        held.set(DataComponents.CUSTOM_NAME, name);
        minecraft.player.sendOverlayMessage(name);
        minecraft.player.sendSystemMessage(Component.literal("Preview ' ")
                .append(name.copy())
                .append(Component.literal(" ' applied to held item")));
        minecraft.player.sendSystemMessage(Component.literal("Move or drop the item to remove preview."));
        return 1;
    }


    private static MutableComponent parseName(String command) {
        String input = unwrapQuoted(command == null ? "" : command.trim());
        Matcher matcher = HEX_BLOCK.matcher(input);
        if (!matcher.find()) {
            return null;
        }

        MutableComponent name = Component.empty();
        int color = Integer.parseInt(matcher.group(1), 16);
        int textStart = matcher.end();

        while (matcher.find()) {
            appendStyled(name, input.substring(textStart, matcher.start()), color);
            color = Integer.parseInt(matcher.group(1), 16);
            textStart = matcher.end();
        }

        appendStyled(name, input.substring(textStart), color);
        return name;
    }

    private static void appendStyled(MutableComponent name, String text, int color) {
        if (text.isEmpty()) {
            return;
        }

        LegacyFormat format = new LegacyFormat();
        int runStart = 0;

        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) != '&') {
                continue;
            }

            char code = Character.toLowerCase(text.charAt(i + 1));
            if (!isLegacyFormatCode(code)) {
                continue;
            }

            appendStyledRun(name, text.substring(runStart, i), color, format);
            format.apply(code);
            i++;
            runStart = i + 1;
        }

        appendStyledRun(name, text.substring(runStart), color, format);
    }

    private static void appendStyledRun(MutableComponent name, String text, int color, LegacyFormat format) {
        if (text.isEmpty()) {
            return;
        }

        boolean bold = format.bold;
        boolean italic = format.italic;
        boolean underlined = format.underlined;
        boolean strikethrough = format.strikethrough;
        boolean obfuscated = format.obfuscated;

        name.append(Component.literal(text).withStyle(style -> style
                .withColor(color)
                .withBold(bold)
                .withItalic(italic)
                .withUnderlined(underlined)
                .withStrikethrough(strikethrough)
                .withObfuscated(obfuscated)));
    }

    private static boolean isLegacyFormatCode(char code) {
        return code == 'k'
                || code == 'l'
                || code == 'm'
                || code == 'n'
                || code == 'o'
                || code == 'r';
    }

    private static final class LegacyFormat {
        private boolean bold;
        private boolean italic;
        private boolean underlined;
        private boolean strikethrough;
        private boolean obfuscated;

        private void apply(char code) {
            switch (code) {
                case 'k' -> obfuscated = true;
                case 'l' -> bold = true;
                case 'm' -> strikethrough = true;
                case 'n' -> underlined = true;
                case 'o' -> italic = true;
                case 'r' -> reset();
                default -> {
                }
            }
        }

        private void reset() {
            bold = false;
            italic = false;
            underlined = false;
            strikethrough = false;
            obfuscated = false;
        }
    }

    private static String unwrapQuoted(String input) {
        if (input.length() < 2) {
            return input;
        }

        char first = input.charAt(0);
        char last = input.charAt(input.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return input.substring(1, input.length() - 1);
        }

        return input;
    }



}
