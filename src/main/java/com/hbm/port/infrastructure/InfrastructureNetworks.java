package com.hbm.port.infrastructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Event-invalidated, dimension-local loaded topology. No chunk requests and no persistent network IDs. */
@EventBusSubscriber(modid="hbm")
public final class InfrastructureNetworks {
    private static final Map<ServerLevel,State> LEVELS=new IdentityHashMap<>();
    private static final Comparator<BlockPos> POS=Comparator.comparingLong(BlockPos::asLong);
    public static void register(BlockEntity be){
        if(be.getLevel() instanceof ServerLevel level){var state=LEVELS.computeIfAbsent(level,key->new State());if(state.nodes.put(be.getBlockPos(),be)!=be)state.dirty=true;}
    }
    public static void unregister(BlockEntity be){
        if(be.getLevel() instanceof ServerLevel level){var state=LEVELS.get(level);if(state!=null&&state.nodes.remove(be.getBlockPos(),be))state.dirty=true;}
    }
    public static void dirty(Level level){if(level instanceof ServerLevel server){var state=LEVELS.get(server);if(state!=null)state.dirty=true;}}
    private static boolean live(ServerLevel level,BlockEntity be){return !be.isRemoved()&&be.getLevel()==level&&level.hasChunkAt(be.getBlockPos())&&level.getBlockEntity(be.getBlockPos())==be;}
    public static List<SteelBarrelBlockEntity> pipeTanks(NetworkLinkBlockEntity pipe){
        if(!(pipe.getLevel() instanceof ServerLevel level)||!live(level,pipe)||!pipe.isFluid()||pipe.fluid()==FluidKind.NONE)return List.of();
        var state=LEVELS.get(level);if(state==null)return List.of();state.rebuildIfNeeded(level);
        return state.pipeTanks.getOrDefault(pipe,List.of()).stream().filter(tank->live(level,tank)&&tank.fluid()==pipe.fluid()).toList();
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){for(var level:event.getServer().getAllLevels())tick(level);}
    public static void tick(ServerLevel level){
        var state=LEVELS.get(level);if(state==null)return;state.rebuildIfNeeded(level);
        for(var network:state.energy){
            var accounts=new ArrayList<TransferPlanner.Account<BlockEntity>>();
            for(var be:network)if(live(level,be)){
                var node=(HeNetworkNode)be;accounts.add(new TransferPlanner.Account<>(be,node.available(),node.demand(),node.networkPriority()));
            }else state.dirty=true;
            var plan=TransferPlanner.plan(accounts,level.getGameTime());var changed=new ArrayList<BlockEntity>();
            for(var account:accounts){
                var socket=account.owner();long in=plan.incoming().getOrDefault(socket,0L),out=plan.outgoing().getOrDefault(socket,0L);
                if(in!=0||out!=0){((HeNetworkNode)socket).commit(in,out);changed.add(socket);}
            }
            for(var socket:changed)socket.setChanged();
        }
        for(var network:state.fluids){
            var accounts=new ArrayList<TransferPlanner.Account<SteelBarrelBlockEntity>>();
            var initial=new IdentityHashMap<SteelBarrelBlockEntity,Integer>();
            for(var tank:network.tanks())if(live(level,tank)&&tank.fluid()==network.type()){
                initial.put(tank,tank.amount());
                accounts.add(new TransferPlanner.Account<>(tank,tank.provides()?tank.amount():0,tank.receives()?16_000-tank.amount():0,tank.mode()==1?1:2));
            }else state.dirty=true;
            var plan=TransferPlanner.plan(accounts,level.getGameTime());var changed=new ArrayList<SteelBarrelBlockEntity>();
            for(var account:accounts){
                var tank=account.owner();int next=Math.toIntExact(initial.get(tank)+plan.incoming().getOrDefault(tank,0L)-plan.outgoing().getOrDefault(tank,0L));
                if(next!=tank.amount()){tank.commitAmount(next);changed.add(tank);}
            }
            for(var tank:changed)tank.setChanged();
        }
        for(var be:List.copyOf(state.nodes.values()))if(be instanceof EnergySocketBlockEntity socket&&live(level,socket))socket.observeTick();
    }
    @SubscribeEvent public static void levelUnload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)LEVELS.remove(level);}
    @SubscribeEvent public static void serverStop(ServerStoppedEvent event){LEVELS.keySet().removeIf(level->level.getServer()==event.getServer());}
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event){
        if(event.getLevel() instanceof ServerLevel level){var state=LEVELS.get(level);if(state==null)return;var chunk=event.getChunk().getPos();
            state.nodes.entrySet().removeIf(entry->(entry.getKey().getX()>>4)==chunk.x&&(entry.getKey().getZ()>>4)==chunk.z);state.dirty=true;}
    }
    private static final class State {
        final Map<BlockPos,BlockEntity> nodes=new TreeMap<>(POS);
        boolean dirty=true;
        List<List<BlockEntity>> energy=List.of();
        List<FluidNetwork> fluids=List.of();
        Map<NetworkLinkBlockEntity,List<SteelBarrelBlockEntity>> pipeTanks=new IdentityHashMap<>();
        void rebuildIfNeeded(ServerLevel level){
            if(!dirty)return;dirty=false;nodes.values().removeIf(be->!live(level,be));buildEnergy(level);buildFluids(level);
        }
        private void buildEnergy(ServerLevel level){
            var cells=new TreeMap<BlockPos,BlockEntity>(POS);var positions=new IdentityHashMap<BlockEntity,List<BlockPos>>();
            for(var be:nodes.values()){
                List<BlockPos> ports=be instanceof EnergySocketBlockEntity socket?socket.cells():be instanceof NetworkLinkBlockEntity link&&!link.isFluid()?List.of(be.getBlockPos()):List.of();
                var loaded=ports.stream().filter(level::hasChunkAt).filter(pos->{
                    if(!(be instanceof EnergySocketBlockEntity))return true;var block=level.getBlockState(pos);
                    return block.getBlock() instanceof EnergySocketBlock&&EnergySocketBlock.corePos(pos,block).equals(be.getBlockPos());
                }).toList();
                if(!loaded.isEmpty()){positions.put(be,loaded);for(var cell:loaded)cells.put(cell,be);}
            }
            var visited=new HashSet<BlockPos>();var result=new ArrayList<List<BlockEntity>>();
            for(var start:cells.keySet()){
                if(!visited.add(start))continue;var queue=new ArrayDeque<BlockPos>();queue.add(start);var sockets=new LinkedHashSet<BlockEntity>();
                while(!queue.isEmpty()){
                    var pos=queue.removeFirst();var owner=cells.get(pos);
                    if(owner instanceof EnergySocketBlockEntity socket)sockets.add(socket);
                    for(var ownCell:positions.get(owner))if(visited.add(ownCell))queue.add(ownCell);
                    for(var side:Direction.values()){
                        if(owner instanceof EnergySocketBlockEntity&&side.getAxis().isVertical())continue;
                        var target=pos.relative(side);var neighbor=cells.get(target);
                        if(neighbor==null){
                            if(nodes.get(target) instanceof HeNetworkNode&&!(nodes.get(target) instanceof EnergySocketBlockEntity))sockets.add(nodes.get(target));
                            continue;
                        }
                        if(neighbor instanceof EnergySocketBlockEntity&&side.getAxis().isVertical())continue;
                        if(visited.add(target))queue.add(target);
                    }
                }
                if(!sockets.isEmpty())result.add(sockets.stream().sorted(Comparator.comparing(BlockEntity::getBlockPos,POS)).toList());
            }
            energy=List.copyOf(result);
        }
        private void buildFluids(ServerLevel level){
            var result=new ArrayList<FluidNetwork>();pipeTanks=new IdentityHashMap<>();
            for(var type:FluidKind.values()){
                if(type==FluidKind.NONE)continue;var cells=new TreeMap<BlockPos,BlockEntity>(POS);
                for(var be:nodes.values()){
                    if(be instanceof NetworkLinkBlockEntity pipe&&pipe.isFluid()&&pipe.fluid()==type)cells.put(be.getBlockPos(),be);
                    if(be instanceof SteelBarrelBlockEntity barrel&&barrel.fluid()==type&&barrel.mode()==1)cells.put(be.getBlockPos(),be);
                }
                var visited=new HashSet<BlockPos>();
                for(var start:cells.keySet()){
                    if(!visited.add(start))continue;var queue=new ArrayDeque<BlockPos>();queue.add(start);
                    var pipes=new ArrayList<NetworkLinkBlockEntity>();var tanks=new LinkedHashSet<SteelBarrelBlockEntity>();
                    while(!queue.isEmpty()){
                        var pos=queue.removeFirst();var owner=cells.get(pos);
                        if(owner instanceof NetworkLinkBlockEntity pipe)pipes.add(pipe);else tanks.add((SteelBarrelBlockEntity)owner);
                        for(var side:Direction.values()){
                            var target=pos.relative(side);if(!level.hasChunkAt(target))continue;
                            if(cells.containsKey(target)){if(visited.add(target))queue.add(target);}
                            else if(nodes.get(target) instanceof SteelBarrelBlockEntity barrel&&barrel.fluid()==type)tanks.add(barrel);
                        }
                    }
                    var sorted=tanks.stream().sorted(Comparator.comparing(BlockEntity::getBlockPos,POS)).toList();
                    for(var pipe:pipes)pipeTanks.put(pipe,sorted);
                    if(!sorted.isEmpty())result.add(new FluidNetwork(type,sorted));
                }
            }
            // Non-buffer barrels do not join networks, but an output may feed an adjacent input directly.
            for(var be:nodes.values())if(be instanceof SteelBarrelBlockEntity source&&source.mode()==2&&source.fluid()!=FluidKind.NONE)
                for(var side:Direction.values())if(nodes.get(source.getBlockPos().relative(side)) instanceof SteelBarrelBlockEntity target
                        &&target.mode()==0&&target.fluid()==source.fluid())result.add(new FluidNetwork(source.fluid(),List.of(source,target)));
            fluids=List.copyOf(result);
        }
    }
    private record FluidNetwork(FluidKind type,List<SteelBarrelBlockEntity> tanks){}
    private InfrastructureNetworks(){}
}
