package com.hbm.port.test;

import com.hbm.port.infrastructure.InfrastructureContent;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in actual Survival crafting packet; excluded from player JAR. */
@EventBusSubscriber(modid="hbm",value=Dist.CLIENT)
public final class InsulatorClientSmoke {
    static int stage,ticks;static long started;static volatile Throwable failure;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("hbm.insulatorClientSmoke"))return;
        var mc=Minecraft.getInstance();if(started==0)started=System.nanoTime();
        if(failure!=null)throw new IllegalStateException("Insulator client test failed",failure);
        if(System.nanoTime()-started>180_000_000_000L)throw new IllegalStateException("Insulator test timeout at "+stage);
        if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            var stack=new ItemStack(InfrastructureContent.INSULATOR.get());
            if(mc.getItemRenderer().getModel(stack,null,null,0).getParticleIcon().contents().name().getPath().equals("missingno")||!net.minecraft.locale.Language.getInstance().has(stack.getDescriptionId()))throw new IllegalStateException("Insulator model/translation missing");
            stage=1;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.createWorldOpenFlows().createFreshLevel("hbm-stage8-smoke-"+System.currentTimeMillis(),new LevelSettings("HBM original insulator crafts",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(5687,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
        }else if(stage==1&&mc.player!=null&&mc.getSingleplayerServer()!=null){
            stage=2;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();server.execute(()->{try{
                var level=server.overworld();var player=server.getPlayerList().getPlayer(uuid);var pos=player.blockPosition().above(12);
                for(int x=-2;x<=2;x++)for(int z=-1;z<=3;z++)level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pos,Blocks.CRAFTING_TABLE.defaultBlockState());player.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()+2.5,180,15);
                player.openMenu(new SimpleMenuProvider((id,inventory,p)->new CraftingMenu(id,inventory,ContainerLevelAccess.create(level,pos)),Component.translatable("container.crafting")));
                var menu=player.containerMenu;menu.getSlot(1).set(new ItemStack(Items.BRICK));menu.getSlot(2).set(new ItemStack(Items.BRICK));menu.broadcastChanges();
            }catch(Throwable e){failure=e;}});
        }else if(stage==2&&mc.player.containerMenu instanceof CraftingMenu menu&&++ticks==30){
            if(!menu.getSlot(0).getItem().is(InfrastructureContent.INSULATOR.get())||menu.getSlot(0).getItem().getCount()!=4)throw new IllegalStateException("Native crafting result missing");
            Screenshot.grab(mc.gameDirectory,"hbm-stage8-insulator.png",mc.getMainRenderTarget(),c->{});
            mc.gameMode.handleInventoryMouseClick(menu.containerId,0,0,ClickType.PICKUP,mc.player);stage=3;ticks=0;
        }else if(stage==3&&++ticks==30){
            var menu=mc.player.containerMenu;
            if(!menu.getCarried().is(InfrastructureContent.INSULATOR.get())||menu.getCarried().getCount()!=4||menu.getSlot(1).hasItem()||menu.getSlot(2).hasItem())throw new IllegalStateException("Native Survival click did not consume exactly two bricks");
            LogUtils.getLogger().info("HBM INSULATOR CLIENT PASSED: original texture/translation, native Survival crafting packet consumed two bricks and produced four insulators");mc.player.closeContainer();stage=4;ticks=0;
        }else if(stage==4&&++ticks==30)mc.stop();
    }
}
