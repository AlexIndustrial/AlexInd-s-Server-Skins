package com.alex.alexinds_server_skins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientProxy extends CommonProxy {

    static Field locationSkinField;
    static final ConcurrentHashMap<String, BufferedImage> pendingTextures = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, ResourceLocation> readySkins = new ConcurrentHashMap<>();
    static final Set<String> downloading = new HashSet<>();
    static final Gson gson = new Gson();

    @Override
    public void init() {
        findField();
        System.out.println(">>>>> Registering tick handler");
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new SkinTicker());
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

    static void fetchSkin(String playerName, String apiUrlTemplate) {
        synchronized (downloading) {
            if (downloading.contains(playerName)) return;
            downloading.add(playerName);
        }

        new Thread(() -> {
            try {
                String apiUrl = String.format(apiUrlTemplate, playerName);
                System.out.println(">>>>> Fetching skin info for: " + playerName + " from " + apiUrl);

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "AlexIndServerSkins/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.connect();

                System.out.println(">>>>> API response: " + conn.getResponseCode());
                if (conn.getResponseCode() != 200) {
                    System.out.println(">>>>> API error for " + playerName + ": " + conn.getResponseCode());
                    return;
                }

                StringBuilder jsonStr = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) jsonStr.append(line);
                } finally {
                    conn.disconnect();
                }

                JsonObject json = gson.fromJson(jsonStr.toString(), JsonObject.class);
                String skinUrl = json.getAsJsonObject("SKIN").get("url").getAsString();
                System.out.println(">>>>> Got skin URL for " + playerName + ": " + skinUrl);

                System.out.println(">>>>> Downloading skin PNG for: " + playerName);
                URL skinPngUrl = new URL(skinUrl);
                HttpURLConnection pngConn = (HttpURLConnection) skinPngUrl.openConnection();
                pngConn.setRequestProperty("User-Agent", "AlexIndServerSkins/1.0");
                pngConn.setConnectTimeout(10000);
                pngConn.setReadTimeout(10000);
                pngConn.connect();

                if (pngConn.getResponseCode() != 200) {
                    System.out.println(">>>>> PNG error for " + playerName + ": " + pngConn.getResponseCode());
                    return;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream in = pngConn.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) baos.write(buf, 0, read);
                } finally {
                    pngConn.disconnect();
                }

                byte[] pngBytes = baos.toByteArray();
                System.out.println(">>>>> Downloaded skin PNG for " + playerName + ": " + pngBytes.length + " bytes");

                BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
                if (img == null) {
                    System.out.println(">>>>> Failed to read skin image for " + playerName);
                    return;
                }

                System.out.println(">>>>> Skin image for " + playerName + ": " + img.getWidth() + "x" + img.getHeight());
                img = new net.minecraft.client.renderer.ImageBufferDownload().parseUserSkin(img);

                pendingTextures.put(playerName, img);
                System.out.println(">>>>> Skin ready for main thread: " + playerName);
            } catch (Exception e) {
                System.out.println(">>>>> Failed to fetch skin for " + playerName);
                e.printStackTrace();
            } finally {
                synchronized (downloading) {
                    downloading.remove(playerName);
                }
            }
        }, "Skin-" + playerName).start();
    }

    public static class SkinTicker {

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null) return;

            for (String name : pendingTextures.keySet()) {
                BufferedImage img = pendingTextures.remove(name);
                if (img != null) {
                    try {
                        DynamicTexture dynTex = new DynamicTexture(img.getWidth(), img.getHeight());
                        int[] pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
                        System.arraycopy(pixels, 0, dynTex.getTextureData(), 0,
                            Math.min(pixels.length, dynTex.getTextureData().length));
                        dynTex.updateDynamicTexture();

                        ResourceLocation loc = new ResourceLocation("alexinds_server_skins", "skins/" + name);
                        mc.getTextureManager().loadTexture(loc, dynTex);
                        readySkins.put(name, loc);
                        System.out.println(">>>>> Skin texture ready: " + name + " -> " + loc);
                    } catch (Exception e) {
                        System.out.println(">>>>> Texture upload failed for " + name);
                        e.printStackTrace();
                    }
                }
            }

            if (locationSkinField == null) return;

            for (Object obj : mc.theWorld.playerEntities) {
                if (!(obj instanceof AbstractClientPlayer)) continue;
                AbstractClientPlayer p = (AbstractClientPlayer) obj;
                String name = p.getCommandSenderName();

                ResourceLocation skin = readySkins.get(name);
                if (skin != null) {
                    try {
                        locationSkinField.set(p, skin);
                    } catch (Exception e) {
                        System.out.println(">>>>> Error setting skin for " + name);
                    }
                } else if (!pendingTextures.containsKey(name)) {
                    fetchSkin(name, SkinReplacerMod.getApiUrlTemplate());
                }
            }
        }
    }
}
