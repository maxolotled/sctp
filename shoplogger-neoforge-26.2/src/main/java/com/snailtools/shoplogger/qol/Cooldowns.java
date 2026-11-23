package com.snailtools.shoplogger.qol;



import com.snailtools.shoplogger.qol.utils.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;


public class Cooldowns {


    //adds the countdown to rotating quest scrolls
    public static void addQuestRotation(ItemStack stack, List<Component> lines) {
        if (!stack.isEmpty()) {
            if (Util.hasCustomItemModel(stack, "minecraft:paper")) {
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (tag.contains("PublicBukkitValues")) {
                    CompoundTag publicBukkitValues = tag.getCompound("PublicBukkitValues").get();
                    if (publicBukkitValues.contains("quests:randomtime")) {
                        long coolDown = publicBukkitValues.getLong("quests:randomtime").get() - System.currentTimeMillis();
                        if (coolDown > 0) {
                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
                            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                            MutableComponent component = Component.literal("Changes In: " + simpleDateFormat.format(new Date(coolDown)));
                            component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
                            lines.add(2, component);
                        }
                    }
                }
            }
        }
    }

    //adds cooldowns for rare items that contain the cooldown within the item not player based cooldowns
    public static void addAvailableRareCooldowns(ItemStack stack, List<Component> lines){
        if(!stack.isEmpty()){
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("PublicBukkitValues")) {
                CompoundTag publicBukkitValues = tag.getCompound("PublicBukkitValues").get();
                String cooldownKey = null;
                for (String key : List.of("epicitems:cooldown-end", "epicitems:default", "epicitems:trickortreater")) {
                    if (publicBukkitValues.contains(key)) {
                        cooldownKey = key;
                        break;
                    }
                }
                if (cooldownKey != null) {
                    long initial = publicBukkitValues.getLong(cooldownKey).get();
                    long coolDown = initial - System.currentTimeMillis();
                    if (coolDown > 0) {
                        // Calculate time components
                        long days = coolDown / (1000 * 60 * 60 * 24);
                        long hours = (coolDown % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
                        long minutes = (coolDown % (1000 * 60 * 60)) / (1000 * 60);
                        long seconds = (coolDown % (1000 * 60)) / 1000;

                        StringBuilder timeFormat = new StringBuilder();

                        if (days > 0) {
                            timeFormat.append(String.format("%dd ", days));
                        }
                        if (hours > 0 || days > 0) {
                            timeFormat.append(String.format("%02dh ", hours));
                        }
                        if (minutes > 0 || hours > 0 || days > 0) {
                            timeFormat.append(String.format("%02dm ", minutes));
                        }
                        if (!timeFormat.isEmpty() || seconds > 0) {
                            timeFormat.append(String.format("%02ds", seconds));
                        }


                        if (timeFormat.isEmpty()) {
                            timeFormat.append("0s");
                        }

                        MutableComponent component = Component.literal("Ready In: " + timeFormat.toString().trim());
                        component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
                        lines.add(1, component);
                    }
                }
            }
        }
    }


}
