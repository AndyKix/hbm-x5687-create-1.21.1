package com.hbm.port.test;

import com.hbm.port.machine.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.util.Properties;

/** Opt-in one-machine native menu and separate-process save check; excluded from player JAR. */
@EventBusSubscriber(modid="hbm",value=Dist.CLIENT)
public final class ElectricFurnaceClientSmoke {
    static int stage,ticks;static long started;static String world;static BlockPos pos;
    static volatile Throwable failure;static volatile boolean finished;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        boolean reload=Boolean.getBoolean("hbm.electricReloadSmoke");if(!reload&&!Boolean.getBoolean("hbm.electricClientSmoke"))return;
        var mc=Minecraft.getInstance();if(started==0)started=System.nanoTime();
        if(failure!=null)throw new IllegalStateException("Electric furnace client smoke failed",failure);
        if(System.nanoTime()-started>180_000_000_000L)throw new IllegalStateException("Electric furnace client timeout stage "+stage);
        var manifest=mc.gameDirectory.toPath().resolve("hbm-stage7-smoke.properties");
        if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            for(var state:ElectricFurnaceContent.BLOCK.get().getStateDefinition().getPossibleStates())for(var side:Direction.values())
                if(mc.getBlockRenderer().getBlockModel(state).getQuads(state,side,RandomSource.create(1)).size()!=1)throw new IllegalStateException("Missing electric furnace model face");
            LogUtils.getLogger().info("HBM ELECTRIC MODELS PASSED: all 8 facing/lit states and six original faces");stage=1;
            if(reload){var props=new Properties();try(var in=Files.newInputStream(manifest)){props.load(in);}world=props.getProperty("world");pos=new BlockPos(Integer.parseInt(props.getProperty("x")),Integer.parseInt(props.getProperty("y")),Integer.parseInt(props.getProperty("z")));mc.createWorldOpenFlows().openWorld(world,()->{throw new IllegalStateException("Cannot reopen electric furnace world");});}
            else{world="hbm-stage7-smoke-"+System.currentTimeMillis();mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings("HBM base electric furnace",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(5687,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);}
        }else if(stage==1&&mc.player!=null&&mc.getSingleplayerServer()!=null){
            stage=2;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();server.execute(()->{try{
                var level=server.overworld();var player=server.getPlayerList().getPlayer(uuid);
                if(!reload){pos=player.blockPosition().above(12);level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,ElectricFurnaceContent.BLOCK.get().defaultBlockState());var furnace=(ElectricFurnaceBlockEntity)level.getBlockEntity(pos);furnace.receive(80000,false);furnace.setItem(1,new ItemStack(Items.IRON_ORE));var props=new Properties();props.setProperty("world",world);props.setProperty("x",""+pos.getX());props.setProperty("y",""+pos.getY());props.setProperty("z",""+pos.getZ());try(var out=Files.newOutputStream(manifest)){props.store(out,"Stage7 fixture");}}
                else{var f=(ElectricFurnaceBlockEntity)level.getBlockEntity(pos);if(f.stored()!=75000||f.getItem(2).getCount()!=1||!f.getItem(2).is(Items.IRON_INGOT)||!f.getItem(1).isEmpty())throw new IllegalStateException("Electric furnace lost saved HE or result");LogUtils.getLogger().info("HBM ELECTRIC RELOAD PASSED: 75000HE and one iron ingot survived process restart");}
                player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(pos.getX()+.5,pos.getY()+1,pos.getZ()+3,180,15);level.setDayTime(6000);player.openMenu((ElectricFurnaceBlockEntity)level.getBlockEntity(pos));
            }catch(Throwable e){failure=e;}});
        }else if(stage==2&&mc.player.containerMenu instanceof ElectricFurnaceMenu menu&&++ticks==30){
            if(menu.power()<75000||menu.power()>80000)throw new IllegalStateException("Native menu truncated power above 65535");
            Screenshot.grab(mc.gameDirectory,reload?"hbm-stage7-reload.png":"hbm-stage7-electric.png",mc.getMainRenderTarget(),c->{});
            LogUtils.getLogger().info("HBM ELECTRIC CLIENT MENU PASSED: native split-int HE={}, progress={}",menu.power(),menu.data.get(2));mc.player.closeContainer();stage=3;ticks=0;
        }else if(stage==3&&++ticks%20==0){
            var server=mc.getSingleplayerServer();server.execute(()->{try{var f=(ElectricFurnaceBlockEntity)server.overworld().getBlockEntity(pos);if(f.getItem(2).is(Items.IRON_INGOT)){if(f.stored()!=75000||f.getItem(2).getCount()!=1)throw new IllegalStateException("Wrong real-tick HE balance");finished=true;}}catch(Throwable e){failure=e;}});
            if(finished){LogUtils.getLogger().info("HBM ELECTRIC REAL TICKS PASSED: one ore -> one ingot, exactly 5000HE consumed");mc.stop();stage=4;}
        }
    }
}
