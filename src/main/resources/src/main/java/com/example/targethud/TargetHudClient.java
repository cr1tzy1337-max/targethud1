package com.example.targethud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class TargetHudClient implements ClientModInitializer {
    // Размеры (пропорции взяты с твоего скриншота)
    private static final int HUD_WIDTH = 170;
    private static final int HUD_HEIGHT = 60;
    private static final int FACE_SIZE = 42;
    private static final int BAR_HEIGHT = 8;

    // Цвета
    private static final int BACKGROUND = 0xE6262626;
    private static final int NAME_COLOR = 0xFFEDEDED;
    private static final int HP_COLOR = 0xFFD0D0D0;
    private static final int BAR_COLOR = 0xFF429FF0;
    private static final int BAR_BACKGROUND = 0xFF3A3A3A;

    private static LivingEntity target;
    private static LivingEntity lastEntity;
    private static float shownHealth;

    private static boolean enabled = true;
    private static boolean hWasDown;
    private static boolean mouseWasDown;
    private static boolean dragging;
    private static int hudX = Integer.MIN_VALUE;
    private static int hudY = Integer.MIN_VALUE;
    private static int dragOffsetX;
    private static int dragOffsetY;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(TargetHudClient::handleInput);
        HudRenderCallback.EVENT.register(
            (drawContext, tickCounter) -> renderHud(drawContext)
        );
    }

    // ---------------------------------------------------------------
    // Ввод: H включает/выключает худ, в открытом чате его можно двигать
    // ---------------------------------------------------------------
    private static void handleInput(MinecraftClient client) {
        if (client.getWindow() == null || client.player == null) return;

        long window = client.getWindow().getHandle();

        // Любой экран открыт (чат, инвентарь...) - клавишу H не трогаем
        if (client.currentScreen != null) {
            hWasDown = true;
        } else {
            boolean hDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS;
            if (hDown && !hWasDown) enabled = !enabled;
            hWasDown = hDown;
        }

        // Перетаскивание только когда открыт чат (курсор свободен)
        if (!(client.currentScreen instanceof ChatScreen) || !enabled) {
            dragging = false;
            mouseWasDown = false;
            return;
        }

        boolean mouseDown = GLFW.glfwGetMouseButton(
            window, GLFW.GLFW_MOUSE_BUTTON_LEFT
        ) == GLFW.GLFW_PRESS;

        double scale = client.getWindow().getScaleFactor();
        int mx = (int) (client.mouse.getX() / scale);
        int my = (int) (client.mouse.getY() / scale);

        if (mouseDown && !mouseWasDown
                && hudX != Integer.MIN_VALUE
                && mx >= hudX && mx <= hudX + HUD_WIDTH
                && my >= hudY && my <= hudY + HUD_HEIGHT) {
            dragging = true;
            dragOffsetX = mx - hudX;
            dragOffsetY = my - hudY;
        }

        if (mouseDown && dragging) {
            hudX = MathHelper.clamp(mx - dragOffsetX, 0,
                Math.max(0, client.getWindow().getScaledWidth() - HUD_WIDTH));
            hudY = MathHelper.clamp(my - dragOffsetY, 0,
                Math.max(0, client.getWindow().getScaledHeight() - HUD_HEIGHT));
        }

        if (!mouseDown) dragging = false;
        mouseWasDown = mouseDown;
    }

    // ---------------------------------------------------------------
    // Выбор цели и отрисовка
    // ---------------------------------------------------------------
    private static void renderHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!enabled || client.player == null || client.world == null) return;

        LivingEntity entity = null;
        if (client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && living.isAlive()) {
            entity = living;
        }

        // В открытом чате показываем худ на себе, чтобы можно было его подвинуть
        if (entity == null && client.currentScreen instanceof ChatScreen) {
            entity = client.player;
        }

        target = entity;
        if (entity == null) return;

        if (hudX == Integer.MIN_VALUE) {
            hudX = (client.getWindow().getScaledWidth() - HUD_WIDTH) / 2;
            hudY = Math.min(
                client.getWindow().getScaledHeight() / 2 + 30,
                client.getWindow().getScaledHeight() - HUD_HEIGHT
            );
        }

        drawTargetHud(context, client, entity, hudX, hudY);
    }

    private static void drawTargetHud(DrawContext ctx, MinecraftClient client,
                                      LivingEntity entity, int x, int y) {
        // Фон со скруглёнными углами
        fillRounded(ctx, x, y, x + HUD_WIDTH, y + HUD_HEIGHT, 5, BACKGROUND);

        // Голова
        int faceX = x + 11;
        int faceY = y + 9;
        drawFace(ctx, entity, faceX, faceY);

        int textX = x + 64;
        int textWidth = HUD_WIDTH - 64 - 10;

        // Ник (крупный)
        float nameScale = 1.25F;
        String name = client.textRenderer.trimToWidth(
            entity.getName().getString(), (int) (textWidth / nameScale)
        );
        drawScaledText(ctx, client, name, textX, y + 9, nameScale, NAME_COLOR);

        // HP
        float health = Math.max(0.0F, entity.getHealth());
        float maxHealth = Math.max(1.0F, entity.getMaxHealth());
        String hpText = String.format(Locale.US, "HP: %.1f", health);
        drawScaledText(ctx, client, hpText, textX, y + 29, 1.1F, HP_COLOR);

        // Плавная полоска HP
        if (entity != lastEntity) {
            lastEntity = entity;
            shownHealth = health;
        } else {
            shownHealth += (health - shownHealth) * 0.15F;
        }

        int barX = textX;
        int barY = y + 44;
        fillRounded(ctx, barX, barY, barX + textWidth, barY + BAR_HEIGHT,
            BAR_HEIGHT / 2, BAR_BACKGROUND);

        float progress = MathHelper.clamp(shownHealth / maxHealth, 0.0F, 1.0F);
        int fillWidth = Math.round(textWidth * progress);
        if (fillWidth > 0) {
            fillRounded(ctx, barX, barY, barX + fillWidth, barY + BAR_HEIGHT,
                Math.min(BAR_HEIGHT / 2, fillWidth / 2), BAR_COLOR);
        }
    }

    private static void drawFace(DrawContext ctx, LivingEntity entity, int x, int y) {
        if (entity instanceof AbstractClientPlayerEntity player) {
            Identifier skin = player.getSkinTextures().texture();

            // Лицо
            ctx.drawTexture(RenderLayer::getGuiTextured, skin,
                x, y, 8F, 8F, FACE_SIZE, FACE_SIZE, 8, 8, 64, 64);
            // Внешний слой (волосы/шапка)
            ctx.drawTexture(RenderLayer::getGuiTextured, skin,
                x, y, 40F, 8F, FACE_SIZE, FACE_SIZE, 8, 8, 64, 64);
            return;
        }

        // Для мобов - яйцо призыва, если оно есть
        SpawnEggItem egg = SpawnEggItem.forEntity(entity.getType());
        if (egg != null) {
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(x, y, 0);
            float s = FACE_SIZE / 16.0F;
            m.scale(s, s, 1.0F);
            ctx.drawItem(new ItemStack(egg), 0, 0);
            m.pop();
        } else {
            ctx.fill(x, y, x + FACE_SIZE, y + FACE_SIZE, 0xFF3A3A3A);
        }
    }

    // ---------------------------------------------------------------
    // Помощники
    // ---------------------------------------------------------------
    private static void drawScaledText(DrawContext ctx, MinecraftClient client,
                                       String text, int x, int y,
                                       float scale, int color) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1.0F);
        ctx.drawText(client.textRenderer, text, 0, 0, color, false);
        m.pop();
    }

    /** Прямоугольник со скруглёнными углами (радиус r). */
    private static void fillRounded(DrawContext ctx, int x1, int y1,
                                    int x2, int y2, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));

        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            ctx.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
    }
}
