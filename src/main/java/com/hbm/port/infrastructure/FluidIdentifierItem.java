package com.hbm.port.infrastructure;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import java.util.List;

/** X5687 primary/secondary selection and right-click swap; only mapped fluids are offered. */
public final class FluidIdentifierItem extends Item {
    public FluidIdentifierItem(){super(new Properties().stacksTo(1));}
    public static FluidKind primary(ItemStack stack){return stack.getOrDefault(InfrastructureContent.PRIMARY.get(),FluidKind.NONE);}
    public static FluidKind secondary(ItemStack stack){return stack.getOrDefault(InfrastructureContent.SECONDARY.get(),FluidKind.NONE);}
    @Override public boolean doesSneakBypassUse(ItemStack stack,LevelReader level,BlockPos pos,Player player){return true;}
    @Override public boolean hasCraftingRemainingItem(ItemStack stack){return true;}
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack){return stack.copyWithCount(1);}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null||!player.isShiftKeyDown())return InteractionResult.PASS;
        var be=context.getLevel().getBlockEntity(context.getClickedPos());var kind=primary(context.getItemInHand());
        if(be instanceof SteelBarrelBlockEntity barrel){if(!context.getLevel().isClientSide)barrel.configure(kind);return InteractionResult.sidedSuccess(context.getLevel().isClientSide);}
        if(be instanceof NetworkLinkBlockEntity pipe&&pipe.isFluid()){if(!context.getLevel().isClientSide)pipe.configure(kind);return InteractionResult.sidedSuccess(context.getLevel().isClientSide);}
        return InteractionResult.PASS;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(!level.isClientSide){
            if(player.isShiftKeyDown()){
                if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.pass(stack);
                player.openMenu(new SimpleMenuProvider((id,inventory,p)->new FluidIdentifierMenu(id,inventory),Component.translatable("container.hbm.fluid_identifier")));
            }else{
                var first=primary(stack);stack.set(InfrastructureContent.PRIMARY.get(),secondary(stack));stack.set(InfrastructureContent.SECONDARY.get(),first);
                player.displayClientMessage(primary(stack).label(),true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.hbm.identifier_primary",primary(stack).label()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.hbm.identifier_secondary",secondary(stack).label()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.hbm.identifier_controls").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.hbm.identifier_flush").withStyle(ChatFormatting.YELLOW));
    }
}
