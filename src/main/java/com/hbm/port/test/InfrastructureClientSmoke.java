package com.hbm.port.test;

import com.hbm.port.HbmPort;
import com.hbm.port.infrastructure.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import java.nio.file.Files;
import java.util.Properties;

/** Opt-in actual native menus and cross-process persistence. Excluded from player JAR. */
@EventBusSubscriber(modid=HbmPort.MOD_ID,value=Dist.CLIENT)
public final class InfrastructureClientSmoke {
    static int stage,ticks;static long started;static String world;static BlockPos core;
    static volatile boolean ready;static volatile Throwable failure;
    static final long SAVED=123456789012345678L;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        boolean reload=Boolean.getBoolean("hbm.infrastructureReloadSmoke");if(!reload&&!Boolean.getBoolean("hbm.infrastructureClientSmoke"))return;
        var mc=Minecraft.getInstance();if(started==0)started=System.nanoTime();
        if(failure!=null)throw new IllegalStateException("Infrastructure client smoke failed",failure);
        if(System.nanoTime()-started>240_000_000_000L)throw new IllegalStateException("Infrastructure client smoke timeout stage "+stage);
        var manifest=mc.gameDirectory.toPath().resolve("hbm-stage6-smoke.properties");
        if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            int states=0;
            for(var block:java.util.List.of(InfrastructureContent.SOCKET.get(),InfrastructureContent.BARREL.get(),InfrastructureContent.CABLE.get(),InfrastructureContent.PIPE.get())){
                for(var state:block.getStateDefinition().getPossibleStates()){
                    if(state.hasProperty(EnergySocketBlock.PART)&&state.getValue(EnergySocketBlock.PART)!=0)continue;
                    var model=mc.getBlockRenderer().getBlockModel(state);
                    if(model.getParticleIcon().contents().name().getPath().equals("missingno")||model.getQuads(state,null,RandomSource.create(1)).isEmpty())throw new IllegalStateException("Missing infrastructure model "+state);
                    states++;
                }
            }
            for(var item:InfrastructureContent.BATTERIES.values())if(mc.getItemRenderer().getModel(new ItemStack(item.get()),null,null,0).getQuads(null,null,RandomSource.create()).isEmpty())throw new IllegalStateException("Missing battery item mesh");
            LogUtils.getLogger().info("HBM INFRASTRUCTURE MODELS PASSED: {} core/connection states, all battery items",states);
            stage=1;
            if(reload){var props=new Properties();try(var in=Files.newInputStream(manifest)){props.load(in);}world=props.getProperty("world");core=new BlockPos(Integer.parseInt(props.getProperty("x")),Integer.parseInt(props.getProperty("y")),Integer.parseInt(props.getProperty("z")));mc.createWorldOpenFlows().openWorld(world,()->{throw new IllegalStateException("Cannot reopen infrastructure world");});}
            else{world="hbm-stage6-smoke-"+System.currentTimeMillis();mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings("HBM HE and fluid foundation",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(5687,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);}
        }else if(stage==1&&mc.player!=null&&mc.getSingleplayerServer()!=null){
            stage=2;ticks=0;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();server.execute(()->{try{
                var level=server.overworld();var player=server.getPlayerList().getPlayer(uuid);
                if(!reload){
                    core=player.blockPosition().above(12);
                    for(int x=-3;x<12;x++)for(int z=-2;z<8;z++){level.setBlockAndUpdate(core.offset(x,-1,z),Blocks.STONE.defaultBlockState());for(int y=0;y<4;y++)level.setBlockAndUpdate(core.offset(x,y,z),Blocks.AIR.defaultBlockState());}
                    socket(level,core,BatteryKind.CUBE,SAVED,3);socket(level,core.offset(4,0,0),BatteryKind.REDSTONE,12345,3);
                    level.setBlockAndUpdate(core.offset(1,0,0),InfrastructureContent.CABLE.get().defaultBlockState());level.setBlockAndUpdate(core.offset(2,0,0),InfrastructureContent.CABLE.get().defaultBlockState());
                    barrel(level,core.offset(7,0,0),FluidKind.WATER,12345,3);barrel(level,core.offset(9,0,0),FluidKind.LAVA,4321,3);
                    level.setBlockAndUpdate(core.offset(8,0,0),InfrastructureContent.PIPE.get().defaultBlockState().setValue(NetworkLinkBlock.FLUID,FluidKind.WATER));
                    var id=new ItemStack(InfrastructureContent.IDENTIFIER.get());id.set(InfrastructureContent.PRIMARY.get(),FluidKind.WATER);id.set(InfrastructureContent.SECONDARY.get(),FluidKind.LAVA);player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,id);
                    var props=new Properties();props.setProperty("world",world);props.setProperty("x",""+core.getX());props.setProperty("y",""+core.getY());props.setProperty("z",""+core.getZ());try(var out=Files.newOutputStream(manifest)){props.store(out,"HBM stage6 persistence fixture");}
                }else{
                    var first=(EnergySocketBlockEntity)level.getBlockEntity(core);var second=(EnergySocketBlockEntity)level.getBlockEntity(core.offset(4,0,0));
                    if(first.stored()!=SAVED||second.stored()!=12345||first.lowMode!=3||first.highMode!=1||first.priority!=3)throw new IllegalStateException("Saved HE/modes/priority changed");
                    if(!((SteelBarrelBlockEntity)level.getBlockEntity(core.offset(7,0,0))).portable().equals(new BarrelState(FluidKind.WATER,12345,3))||!((SteelBarrelBlockEntity)level.getBlockEntity(core.offset(9,0,0))).portable().equals(new BarrelState(FluidKind.LAVA,4321,3)))throw new IllegalStateException("Barrel fluid/mode changed after restart");
                    if(FluidIdentifierItem.primary(player.getMainHandItem())!=FluidKind.LAVA||FluidIdentifierItem.secondary(player.getMainHandItem())!=FluidKind.WATER)throw new IllegalStateException("Identifier menu selection did not persist");
                    first.lowMode=2;second.lowMode=0;first.setChanged();second.setChanged();
                    LogUtils.getLogger().info("HBM INFRASTRUCTURE RELOAD DATA PASSED: long HE, battery items, fluids, modes, priority and held identifier survived process restart");
                }
                player.getAbilities().flying=true;player.onUpdateAbilities();level.setDayTime(6000);player.connection.teleport(core.getX()+4,core.getY()+5,core.getZ()+10,180,25);ready=true;
            }catch(Throwable e){failure=e;}});
        }else if(stage==2&&ready&&++ticks==80){
            Screenshot.grab(mc.gameDirectory,reload?"hbm-stage6-reload.png":"hbm-stage6-foundation.png",mc.getMainRenderTarget(),c->{});
            if(reload){mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();var a=(EnergySocketBlockEntity)level.getBlockEntity(core);var b=(EnergySocketBlockEntity)level.getBlockEntity(core.offset(4,0,0));if(b.stored()<=12345||a.stored()+b.stored()!=SAVED+12345)throw new IllegalStateException("Reloaded cable graph did not rebuild conservatively");a.lowMode=b.lowMode=3;a.setChanged();b.setChanged();LogUtils.getLogger().info("HBM INFRASTRUCTURE RELOAD NETWORK PASSED: cable topology rebuilt and conserved HE after full restart");}catch(Throwable e){failure=e;}});stage=9;ticks=0;}
            else{serverOpen(mc,0);stage=3;ticks=0;}
        }else if(stage==3&&mc.player.containerMenu instanceof EnergySocketMenu menu&&++ticks==30){
            if(menu.value(0)!=SAVED||menu.value(1)!=BatteryKind.CUBE.capacity)throw new IllegalStateException("Native menu packets truncated 64-bit HE");
            Screenshot.grab(mc.gameDirectory,"hbm-stage6-socket.png",mc.getMainRenderTarget(),c->{});mc.gameMode.handleInventoryButtonClick(menu.containerId,1);mc.gameMode.handleInventoryButtonClick(menu.containerId,1);mc.gameMode.handleInventoryButtonClick(menu.containerId,1);mc.gameMode.handleInventoryButtonClick(menu.containerId,2);mc.gameMode.handleInventoryButtonClick(menu.containerId,2);stage=4;ticks=0;
        }else if(stage==4&&++ticks==30){
            var menu=(EnergySocketMenu)mc.player.containerMenu;if(menu.data.get(21)!=1||menu.data.get(22)!=3)throw new IllegalStateException("Socket mode/priority packets failed");serverOpen(mc,1);stage=5;ticks=0;
        }else if(stage==5&&mc.player.containerMenu instanceof SteelBarrelMenu menu&&++ticks==30){
            if(menu.data.get(0)!=12345||menu.fluid()!=FluidKind.WATER||menu.data.get(2)!=3)throw new IllegalStateException("Native barrel menu data failed");Screenshot.grab(mc.gameDirectory,"hbm-stage6-barrel.png",mc.getMainRenderTarget(),c->{});serverOpen(mc,2);stage=6;ticks=0;
        }else if(stage==6&&mc.player.containerMenu instanceof FluidIdentifierMenu menu&&++ticks==30){
            if(menu.primary()!=FluidKind.WATER||menu.secondary()!=FluidKind.LAVA)throw new IllegalStateException("Identifier initial data failed");mc.gameMode.handleInventoryButtonClick(menu.containerId,2);mc.gameMode.handleInventoryButtonClick(menu.containerId,4);stage=7;ticks=0;
        }else if(stage==7&&++ticks==30){
            var menu=(FluidIdentifierMenu)mc.player.containerMenu;if(menu.primary()!=FluidKind.LAVA||menu.secondary()!=FluidKind.WATER)throw new IllegalStateException("Identifier tracked item did not synchronize: "+menu.primary()+" / "+menu.secondary());Screenshot.grab(mc.gameDirectory,"hbm-stage6-identifier.png",mc.getMainRenderTarget(),c->{});mc.player.closeContainer();LogUtils.getLogger().info("HBM INFRASTRUCTURE CLIENT MENUS PASSED: native long packets, mode/priority edits, barrel contents and identifier held-item sync");stage=9;ticks=0;
        }else if(stage==9&&++ticks==40)mc.stop();
    }
    static void serverOpen(Minecraft mc,int type){var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();server.execute(()->{try{var player=server.getPlayerList().getPlayer(uuid);player.connection.teleport(core.getX()+3,core.getY()+1,core.getZ()+3,180,15);if(type==0)player.openMenu((EnergySocketBlockEntity)server.overworld().getBlockEntity(core));else if(type==1)player.openMenu((SteelBarrelBlockEntity)server.overworld().getBlockEntity(core.offset(7,0,0)));else player.openMenu(new SimpleMenuProvider((id,inv,p)->new FluidIdentifierMenu(id,inv),net.minecraft.network.chat.Component.translatable("container.hbm.fluid_identifier")));}catch(Throwable e){failure=e;}});}
    static void socket(ServerLevel level,BlockPos pos,BatteryKind kind,long charge,int mode){var block=InfrastructureContent.SOCKET.get();var state=block.defaultBlockState();level.setBlockAndUpdate(pos,state);block.setPlacedBy(level,pos,state,null,new ItemStack(block));var be=(EnergySocketBlockEntity)level.getBlockEntity(pos);var item=InfrastructureContent.BATTERIES.get(kind).get();var stack=new ItemStack(item);item.setCharge(stack,charge);be.lowMode=mode;be.setItem(0,stack);}
    static void barrel(ServerLevel level,BlockPos pos,FluidKind kind,int amount,int mode){level.setBlockAndUpdate(pos,InfrastructureContent.BARREL.get().defaultBlockState());var be=(SteelBarrelBlockEntity)level.getBlockEntity(pos);be.configure(kind);be.rawHandler().fill(kind.stack(amount),FluidAction.EXECUTE);be.setMode(mode);}
}
