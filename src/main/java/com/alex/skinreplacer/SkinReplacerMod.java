package com.alex.skinreplacer;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

@Mod(modid = "skinreplacer", name = "Skin Replacer", version = "1.0.0", acceptableRemoteVersions = "*")
public class SkinReplacerMod {

    private static Field locationSkinField;
    private static ResourceLocation customSkinLocation;
    private static boolean skinReady = false;

    // Downloaded image stored here, loaded on main thread
    private static final AtomicReference<BufferedImage> pendingImage = new AtomicReference<>(null);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        findField();
        downloadSkin("https://node1.desert-chat.ru/api/static/skins/1.png");
    }

    private static void findField() {
        try {
            locationSkinField = AbstractClientPlayer.class.getDeclaredField("locationSkin");
            locationSkinField.setAccessible(true);
            System.out.println(">>>>> Found field: locationSkin");
        } catch (NoSuchFieldException e) {
            try {
                locationSkinField = AbstractClientPlayer.class.getDeclaredField("field_110312_d");
                locationSkinField.setAccessible(true);
                System.out.println(">>>>> Found field: field_110312_d");
            } catch (NoSuchFieldException e2) {
                System.out.println(">>>>> FAILED to find locationSkin field!");
            }
        }
    }

    private static void downloadSkin(String skinUrl) {
        new Thread(() -> {
            try {
                System.out.println(">>>>> Downloading skin from: " + skinUrl);
                URL url = new URL(skinUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "SkinReplacer/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.connect();

                System.out.println(">>>>> HTTP response: " + conn.getResponseCode());
                if (conn.getResponseCode() != 200) {
                    System.out.println(">>>>> HTTP error: " + conn.getResponseCode());
                    return;
                }

                File cacheDir = new File(Minecraft.getMinecraft().mcDataDir, "config/skinreplacer");
                cacheDir.mkdirs();
                File cacheFile = new File(cacheDir, "skin.png");

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(cacheFile)) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                } finally {
                    conn.disconnect();
                }

                System.out.println(">>>>> Downloaded to: " + cacheFile.getAbsolutePath());

                BufferedImage img = ImageIO.read(cacheFile);
                if (img == null) {
                    System.out.println(">>>>> Failed to read image");
                    return;
                }

                System.out.println(">>>>> Image loaded: " + img.getWidth() + "x" + img.getHeight());

                // Convert to 1.7.10 format using vanilla skin parser
                System.out.println(">>>>> Converting to 64x32...");
                img = new net.minecraft.client.renderer.ImageBufferDownload().parseUserSkin(img);

                pendingImage.set(img);
            } catch (Exception e) {
                System.out.println(">>>>> Download FAILED!");
                e.printStackTrace();
            }
        }, "SkinDownloader").start();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println(">>>>> Registering tick handler");
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new SkinTicker());
    }

    public static class SkinTicker {
        private boolean textureUploaded = false;

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getMinecraft();

            // Step 1: Upload texture to OpenGL on main thread
            if (!textureUploaded) {
                BufferedImage img = pendingImage.getAndSet(null);
                if (img != null) {
                    try {
                        System.out.println(">>>>> Creating DynamicTexture on main thread...");
                        DynamicTexture dynTex = new DynamicTexture(img.getWidth(), img.getHeight());
                        int[] pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
                        System.arraycopy(pixels, 0, dynTex.getTextureData(), 0,
                            Math.min(pixels.length, dynTex.getTextureData().length));
                        dynTex.updateDynamicTexture();

                        customSkinLocation = new ResourceLocation("skinreplacer", "skin");
                        mc.getTextureManager().loadTexture(customSkinLocation, dynTex);
                        skinReady = true;
                        textureUploaded = true;
                        System.out.println(">>>>> Skin READY! location=" + customSkinLocation);
                    } catch (Exception e) {
                        System.out.println(">>>>> Texture upload FAILED!");
                        e.printStackTrace();
                    }
                }
                return; // Continue next tick
            }

            // Step 2: Apply skin on main thread
            if (!skinReady || locationSkinField == null || mc.theWorld == null) return;

            try {
                for (Object obj : mc.theWorld.playerEntities) {
                    if (obj instanceof AbstractClientPlayer) {
                        AbstractClientPlayer p = (AbstractClientPlayer) obj;
                        locationSkinField.set(p, customSkinLocation);
                    }
                }
            } catch (Exception e) {
                System.out.println(">>>>> Apply skin error!");
                e.printStackTrace();
            }
        }
    }
}
