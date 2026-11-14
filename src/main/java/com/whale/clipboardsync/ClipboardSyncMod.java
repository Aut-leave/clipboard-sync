package com.whale.clipboardsync;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ClipboardSyncMod.MODID)
public class ClipboardSyncMod {
    public static final String MODID = "clipboardsync";

    public ClipboardSyncMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        ModNetwork.register();
    }

    private void clientSetup(FMLClientSetupEvent e) {
        // 只在客户端物理端执行：启动本地 HTTP 桥，供网页编辑器连接
        ClientSync.start();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        ServerSync.onServerTick(e);
    }
}
