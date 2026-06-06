package com.alex.skinreplacer;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.config.Configuration;

@Mod(modid = "skinreplacer", name = "AlexInd's Server Skins", version = "1.0.0", acceptableRemoteVersions = "*")
public class SkinReplacerMod {

    @SidedProxy(
        clientSide = "com.alex.skinreplacer.ClientProxy",
        serverSide = "com.alex.skinreplacer.ServerProxy"
    )
    public static CommonProxy proxy;

    private static String apiUrlTemplate = "https://node1.desert-chat.ru/api/minecraft/textures/%s";

    static String getApiUrlTemplate() {
        return apiUrlTemplate;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        apiUrlTemplate = config.getString("apiUrl", "general",
            "https://node1.desert-chat.ru/api/minecraft/textures/%s",
            "URL template for skin API. Use %s as the nickname placeholder.");
        config.save();
        System.out.println(">>>>> AlexInd's Server Skins initialized, apiUrl=" + apiUrlTemplate);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
