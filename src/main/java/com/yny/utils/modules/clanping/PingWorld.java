package com.yny.utils.modules.clanping;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Server;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.lwjgl.opengl.GL11;

public final class PingWorld {
    private static Class<?> mcClass;
    private static Method getMinecraftMethod;
    private static Field theWorldField;
    private static Field providerField;
    private static Method getDimensionIdMethod;
    private static Method getDimensionNameMethod;
    private static Method getScoreboardMethod;
    private static Field fontRendererField;
    private static Method fontWidthMethod;
    private static Method fontDrawMethod;
    private static Method renderManagerMethod;
    private static Field playerViewYField;
    private static Field playerViewXField;
    private static boolean initialized;

    private PingWorld() {}

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            try {
                mcClass = Class.forName("net.minecraft.client.Minecraft");
            } catch (ClassNotFoundException e) {
                mcClass = Class.forName("ave");
            }

            try {
                getMinecraftMethod = mcClass.getMethod("getMinecraft");
            } catch (NoSuchMethodException e) {
                getMinecraftMethod = mcClass.getMethod("A");
            }

            try {
                theWorldField = mcClass.getField("theWorld");
            } catch (NoSuchFieldException e) {
                theWorldField = mcClass.getField("f");
            }

            Class<?> worldClass = theWorldField.getType();
            try {
                providerField = worldClass.getField("provider");
            } catch (NoSuchFieldException e) {
                providerField = worldClass.getField("t");
            }

            Class<?> providerClass = providerField.getType();
            try {
                getDimensionIdMethod = providerClass.getMethod("getDimensionId");
            } catch (NoSuchMethodException e) {
                getDimensionIdMethod = providerClass.getMethod("q");
            }

            try {
                getDimensionNameMethod = providerClass.getMethod("getDimensionName");
            } catch (NoSuchMethodException e) {
                getDimensionNameMethod = providerClass.getMethod("k");
            }

            try {
                getScoreboardMethod = worldClass.getMethod("getScoreboard");
            } catch (NoSuchMethodException e) {
                getScoreboardMethod = worldClass.getMethod("Z");
            }

            try {
                fontRendererField = mcClass.getField("fontRendererObj");
            } catch (NoSuchFieldException e) {
                try {
                    fontRendererField = mcClass.getField("k");
                } catch (NoSuchFieldException ignored) {}
            }
            if (fontRendererField != null) {
                Class<?> fontClass = fontRendererField.getType();
                try {
                    fontWidthMethod = fontClass.getMethod("getStringWidth", String.class);
                } catch (NoSuchMethodException e) {
                    try {
                        fontWidthMethod = fontClass.getMethod("a", String.class);
                    } catch (NoSuchMethodException ignored) {}
                }
                try {
                    fontDrawMethod = fontClass.getMethod("drawStringWithShadow", String.class, float.class, float.class, int.class);
                } catch (NoSuchMethodException e) {
                    try {
                        fontDrawMethod = fontClass.getMethod("a", String.class, float.class, float.class, int.class);
                    } catch (NoSuchMethodException e2) {
                        try {
                            fontDrawMethod = fontClass.getMethod("drawString", String.class, float.class, float.class, int.class, boolean.class);
                        } catch (NoSuchMethodException e3) {
                            try {
                                fontDrawMethod = fontClass.getMethod("a", String.class, float.class, float.class, int.class, boolean.class);
                            } catch (NoSuchMethodException ignored) {}
                        }
                    }
                }
            }

            try {
                renderManagerMethod = mcClass.getMethod("getRenderManager");
            } catch (NoSuchMethodException e) {
                try {
                    renderManagerMethod = mcClass.getMethod("af");
                } catch (NoSuchMethodException ignored) {}
            }
            if (renderManagerMethod != null) {
                Class<?> rmClass = renderManagerMethod.getReturnType();
                try {
                    playerViewYField = rmClass.getField("playerViewY");
                } catch (NoSuchFieldException e) {
                    try {
                        playerViewYField = rmClass.getField("e");
                    } catch (NoSuchFieldException ignored) {}
                }
                try {
                    playerViewXField = rmClass.getField("playerViewX");
                } catch (NoSuchFieldException e) {
                    try {
                        playerViewXField = rmClass.getField("f");
                    } catch (NoSuchFieldException ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    public static Object getWorldInstance() {
        init();
        try {
            if (getMinecraftMethod != null && theWorldField != null) {
                Object mc = getMinecraftMethod.invoke(null);
                if (mc != null) {
                    return theWorldField.get(mc);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static int getCurrentDimension() {
        init();
        try {
            Object world = getWorldInstance();
            if (world != null && providerField != null && getDimensionIdMethod != null) {
                Object provider = providerField.get(world);
                if (provider != null) {
                    return ((Number) getDimensionIdMethod.invoke(provider)).intValue();
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public static String getDimensionName(int dim) {
        if (dim == -1) return "Nether";
        if (dim == 1) return "The End";
        if (dim == 0) return "Mundo Normal";
        return "Dimensão " + dim;
    }

    public static String getCurrentWorldName() {
        int dim = getCurrentDimension();
        if (dim == -1) return "Nether";
        if (dim == 1) return "The End";

        String sub = getScoreboardWorldContext();
        if (sub != null && !sub.isEmpty()) {
            return sub;
        }
        return "Mundo Normal";
    }

    private static String getScoreboardWorldContext() {
        init();
        try {
            Object world = getWorldInstance();
            if (world != null && getScoreboardMethod != null) {
                Object sb = getScoreboardMethod.invoke(world);
                if (sb != null) {
                    Method getObjMethod = null;
                    try {
                        getObjMethod = sb.getClass().getMethod("getObjectiveInDisplaySlot", int.class);
                    } catch (NoSuchMethodException e) {
                        getObjMethod = sb.getClass().getMethod("a", int.class);
                    }
                    Object obj = getObjMethod.invoke(sb, 1);
                    if (obj != null) {
                        Method getDisplayMethod = null;
                        try {
                            getDisplayMethod = obj.getClass().getMethod("getDisplayName");
                        } catch (NoSuchMethodException e) {
                            getDisplayMethod = obj.getClass().getMethod("d");
                        }
                        String title = (String) getDisplayMethod.invoke(obj);
                        if (title != null) {
                            String clean = cleanColors(title).trim();
                            if (!clean.isEmpty() && clean.length() <= 20) {
                                return clean;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isSameWorld(ClanPing.Active ping) {
        if (ping == null) return true;
        int currentDim = getCurrentDimension();
        int pingDim = getPingDimension(ping);
        return currentDim == pingDim;
    }

    public static int getPingDimension(ClanPing.Active ping) {
        if (ping == null) return 0;
        if (ping.id != null && ping.id.contains("__dim")) {
            try {
                int idx = ping.id.indexOf("__dim") + 5;
                int end = ping.id.indexOf("__w", idx);
                String sub = end >= 0 ? ping.id.substring(idx, end) : ping.id.substring(idx);
                return Integer.parseInt(sub);
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    public static String getPingWorldName(ClanPing.Active ping) {
        if (ping == null) return "Mundo Normal";
        if (ping.id != null && ping.id.contains("__w")) {
            try {
                int idx = ping.id.indexOf("__w") + 3;
                return ping.id.substring(idx);
            } catch (Throwable ignored) {}
        }
        return getDimensionName(getPingDimension(ping));
    }

    public static String buildPingId(int dim, String world) {
        return java.util.UUID.randomUUID().toString() + "__dim" + dim + "__w" + sanitizeWorld(world);
    }

    public static String sanitizeWorld(String world) {
        if (world == null) return "MundoNormal";
        String clean = cleanColors(world).replaceAll("[^a-zA-Z0-9]", "");
        if (clean.trim().isEmpty()) return "MundoNormal";
        return clean;
    }

    public static String cleanColors(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    public static String worldMeta(ClanPing.Active active, int meters) {
        if (active == null) return "";
        if (!isSameWorld(active)) {
            return " §c[Mundo: " + getPingWorldName(active) + "] §7• §bFIXO";
        }
        return " §f" + meters + "m §7• §bFIXO";
    }

    public static String hudDetail(ClanPing.Active active, String dirArrow, int meters) {
        if (active == null) return "";
        if (!isSameWorld(active)) {
            String pWorld = getPingWorldName(active);
            return "§c[OUTRO MUNDO: " + pWorld + "] §7• " + active.label() + " §7• §bFIXO";
        }
        String arrow = dirArrow != null ? dirArrow : "";
        return arrow + " " + active.label() + "  §7• §f" + meters + "m  §7• §bFIXO";
    }

    private static int lastDim = Integer.MIN_VALUE;
    private static String lastWorld = "";
    private static String lastPingId = null;
    private static int alertTicks = 0;

    public static void checkWorldTick(ClanPing ping) {
        if (ping == null) return;
        ClanPing.Active active = ping.active;
        if (active == null) {
            lastPingId = null;
            lastDim = Integer.MIN_VALUE;
            lastWorld = "";
            alertTicks = 0;
            return;
        }
        int curDim = getCurrentDimension();
        int pDim = getPingDimension(active);
        boolean dimChanged = (curDim != lastDim);
        boolean isNewPing = (lastPingId == null || !active.id.equals(lastPingId));

        if (dimChanged || isNewPing) {
            lastDim = curDim;
            lastPingId = active.id;
            alertTicks = 0;
        }

        boolean same = isSameWorld(active);
        String pWorld = getDimensionName(pDim);
        String curWorld = getDimensionName(curDim);

        if (!same) {
            alertTicks++;
            if (alertTicks % 80 == 1 || isNewPing || dimChanged) {
                Chat.actionBar("§c§lClan Ping: Alvo em outra dimensão (" + pWorld + ")! Você está em: " + curWorld);
            }
            if (isNewPing || dimChanged) {
                Chat.print("§c§l[AVISO] §eClan Ping: O alvo está em outra dimensão (§c" + pWorld + "§e)! Você está em: §c" + curWorld);
            }
        } else {
            if (dimChanged && !isNewPing) {
                Chat.actionBar("§a§lClan Ping: Você entrou na mesma dimensão do ping (" + curWorld + ")!");
                Chat.print("§a§l[AVISO] §aClan Ping: Você entrou na mesma dimensão do ping (" + curWorld + ")!");
            }
            alertTicks = 0;
        }
    }

    public static void onIncomingPing(ClanPing.Active ping) {
        if (ping == null) return;
        try {
            int x = (int) Math.round(ping.x);
            int y = (int) Math.round(ping.y);
            int z = (int) Math.round(ping.z);
            String coords = "§eX: §f" + x + " §eY: §f" + y + " §eZ: §f" + z;

            Object self = Entities.self();
            String distDir = "";
            String dirOnly = "Frente";
            int dist = 0;
            if (self != null) {
                try {
                    double dx = ping.x - Entities.x(self, 1.0F);
                    double dz = ping.z - Entities.z(self, 1.0F);
                    dist = PingHudModel.meters(dx, dz);
                    int sector = PingHudModel.sector(dx, dz, Entities.yaw(self, 1.0F));
                    dirOnly = PingHudModel.direction(sector);
                    distDir = " §7(" + dist + "m · §f" + dirOnly + "§7)";
                } catch (Throwable ignored) {}
            }

            boolean isSelf = false;
            try {
                String selfNick = Players.self();
                if (selfNick != null && selfNick.equalsIgnoreCase(ping.author)) {
                    isSelf = true;
                }
            } catch (Throwable ignored) {}

            if (ping.enemy) {
                String name = ping.targetName != null && !ping.targetName.isEmpty() ? ping.targetName : "Inimigo";
                if (isSelf) {
                    Chat.print("§c§l[Clan Ping] §aVocê marcou inimigo §f" + name + " §7em " + coords);
                    Chat.actionBar("§c§lClan Ping: §aInimigo marcado em " + coords);
                } else {
                    Chat.print("§c§l[Clan Ping] §f" + ping.author + " §cfocou inimigo §f" + name + " §7em " + coords + distDir);
                    Chat.actionBar("§c§lClan Ping: §f" + ping.author + " §cfocou §f" + name + distDir);
                }
            } else {
                if (isSelf) {
                    Chat.print("§b§l[Clan Ping] §aVocê marcou local: " + coords);
                    Chat.actionBar("§b§lClan Ping: §aLocal marcado em " + coords);
                } else {
                    Chat.print("§b§l[Clan Ping] §f" + ping.author + " §bmarcou local: " + coords + distDir);
                    Chat.actionBar("§b§lClan Ping: §f" + ping.author + " §7marcou " + coords + distDir);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return cleanColors(s).trim().toLowerCase();
    }

    public static Object getFontRenderer() {
        init();
        try {
            if (getMinecraftMethod != null && fontRendererField != null) {
                Object mc = getMinecraftMethod.invoke(null);
                if (mc != null) {
                    return fontRendererField.get(mc);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static int getStringWidth(String text) {
        if (text == null) return 0;
        try {
            Object fr = getFontRenderer();
            if (fr != null && fontWidthMethod != null) {
                return ((Number) fontWidthMethod.invoke(fr, text)).intValue();
            }
        } catch (Throwable ignored) {}
        return cleanColors(text).length() * 6;
    }

    public static void drawString(String text, float x, float y, int color) {
        if (text == null) return;
        try {
            Object fr = getFontRenderer();
            if (fr != null && fontDrawMethod != null) {
                if (fontDrawMethod.getParameterCount() == 4) {
                    fontDrawMethod.invoke(fr, text, x, y, color);
                } else if (fontDrawMethod.getParameterCount() == 5) {
                    fontDrawMethod.invoke(fr, text, x, y, color, true);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void drawScaledString(String text, float x, float y, float scale, int color) {
        if (text == null || text.isEmpty()) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        drawString(text, 0.0F, 0.0F, color);
        GL11.glPopMatrix();
    }

    public static float[] getViewerAngles() {
        init();
        try {
            if (getMinecraftMethod != null && renderManagerMethod != null) {
                Object mc = getMinecraftMethod.invoke(null);
                if (mc != null) {
                    Object rm = renderManagerMethod.invoke(mc);
                    if (rm != null && playerViewYField != null && playerViewXField != null) {
                        return new float[]{playerViewYField.getFloat(rm), playerViewXField.getFloat(rm)};
                    }
                }
            }
        } catch (Throwable ignored) {}
        return new float[]{0.0F, 0.0F};
    }

    public static void renderWorldLabel(String labelText, double x, double y, double z,
                                        double viewerX, double viewerY, double viewerZ,
                                        int defaultColor) {
        if (labelText == null || labelText.trim().isEmpty()) return;
        init();

        double dx = x - viewerX;
        double dy = y - viewerY;
        double dz = z - viewerZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Distance adaptive scale: keeps nametag prominent and readable even from far away
        float scale = (float) Math.max(0.032F, 0.024F + dist * 0.0026F);

        float[] angles = getViewerAngles();
        float viewerYaw = angles[0];
        float viewerPitch = angles[1];

        String badge = null;
        String heroName = null;
        String meta = null;
        int accentColor = 0xFFFF4444; // Default red for enemy

        if (labelText.contains("§7• ")) {
            String[] parts = labelText.split("§7• ");
            if (parts.length >= 2) {
                badge = parts[0].trim();
                heroName = parts[1].trim();
                if (parts.length >= 3) {
                    meta = parts[2].trim();
                }
            }
        }

        // Check if heroName still contains meta attached at the end (e.g. "Nick §f14m")
        if (heroName != null) {
            int mIdx = heroName.lastIndexOf(" §f");
            if (mIdx < 0) mIdx = heroName.lastIndexOf(" §c[");
            if (mIdx < 0) mIdx = heroName.lastIndexOf(" §e");
            if (mIdx >= 0) {
                if (meta == null) {
                    meta = heroName.substring(mIdx).trim();
                }
                heroName = heroName.substring(0, mIdx).trim();
            }
        }

        boolean isWaypoint = labelText.contains("IR AQUI") || labelText.contains("§b");
        if (isWaypoint) {
            accentColor = 0xFF33CCEE; // Cyan for location waypoints
        }

        // Format badge
        if (badge != null) {
            if (badge.contains("ATACAR ESTE INIMIGO")) {
                badge = "§c⚔ FOCAR INIMIGO ⚔";
            } else if (badge.contains("INIMIGO FORA DO RENDER") || badge.contains("ULTIMA")) {
                badge = "§c⚠ ÚLTIMA POSIÇÃO";
            } else if (badge.contains("IR AQUI") || badge.contains("PONTO")) {
                badge = "§b📍 PONTO MARCADO";
            }
        }

        // Format hero name (the prominent nametag line)
        if (heroName != null && !heroName.isEmpty()) {
            heroName = GfxWorldMarkers.formatHeroNick(heroName);
        } else {
            heroName = labelText;
        }

        if (meta != null && !meta.isEmpty()) {
            if (!meta.contains("§e") && !meta.contains("§a")) {
                meta = meta.replace("§f", "§e").replace("§b", "§a");
            }
        }

        GL11.glPushMatrix();
        GL11.glTranslated(dx, dy, dz);
        GL11.glRotatef(-viewerYaw, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(viewerPitch, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float badgeScale = 0.85F;
        float heroScale = 1.35F;
        float metaScale = 0.80F;

        int badgeW = badge != null ? (int) Math.ceil(getStringWidth(badge) * badgeScale) : 0;
        int heroW = (int) Math.ceil(getStringWidth(heroName) * heroScale);
        int metaW = meta != null ? (int) Math.ceil(getStringWidth(meta) * metaScale) : 0;

        int maxW = Math.max(heroW, Math.max(badgeW, metaW));
        float padX = 7.0F;
        float halfW = (maxW / 2.0F) + padX;

        float boxTop = badge != null ? -14.0F : -7.0F;
        float boxBottom = meta != null ? 16.0F : (badge != null ? 8.0F : 8.0F);

        // Draw dark translucent nametag plate
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.04F, 0.06F, 0.10F, 0.78F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(-halfW, boxTop);
        GL11.glVertex2f(-halfW, boxBottom);
        GL11.glVertex2f(halfW, boxBottom);
        GL11.glVertex2f(halfW, boxTop);
        GL11.glEnd();

        // Top accent line
        float aR = ((accentColor >> 16) & 0xFF) / 255.0F;
        float aG = ((accentColor >> 8) & 0xFF) / 255.0F;
        float aB = (accentColor & 0xFF) / 255.0F;
        GL11.glColor4f(aR, aG, aB, 0.90F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(-halfW, boxTop - 1.5F);
        GL11.glVertex2f(-halfW, boxTop);
        GL11.glVertex2f(halfW, boxTop);
        GL11.glVertex2f(halfW, boxTop - 1.5F);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // Draw Lines
        if (badge != null) {
            drawScaledString(badge, -badgeW / 2.0F, boxTop + 2.5F, badgeScale, 0xFFFFFFFF);
        }

        float heroY = badge != null ? (boxTop + 10.5F) : (boxTop + 2.0F);
        drawScaledString(heroName, -heroW / 2.0F, heroY, heroScale, 0xFFFFFFFF);

        if (meta != null) {
            float metaY = heroY + (10.0F * heroScale) - 1.5F;
            drawScaledString(meta, -metaW / 2.0F, metaY, metaScale, 0xFFFFFFFF);
        }

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }
}

