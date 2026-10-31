package com.hbm.port.infrastructure;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import java.util.List;

public final class BarrelItem extends BlockItem {
    public BarrelItem(Block block){super(block,new Item.Properties());}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);var state=stack.getOrDefault(InfrastructureContent.BARREL_STATE.get(),BarrelState.EMPTY);
        lines.add(Component.translatable("tooltip.hbm.barrel_contents",state.amount(),16_000,state.fluid().label()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.hbm.barrel_fluids").withStyle(ChatFormatting.GRAY));
    }
}
