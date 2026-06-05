package com.xm6680.friendservermenu.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class MenuButton {
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int DESCRIPTION_COLOR = 0xFFC9D4DE;

    public enum Variant {
        NORMAL,
        DANGER,
        TOGGLE_ON,
        TOGGLE_OFF,
        DISABLED
    }

    private final String title;
    private final String description;
    private final String actionId;
    private final String argument;
    private final boolean localOnly;
    private final Variant variant;
    private int x;
    private int y;
    private int width;
    private int height;

    public MenuButton(String title, String description, String actionId, String argument) {
        this(title, description, actionId, argument, false);
    }

    public MenuButton(String title, String description, String actionId, String argument, boolean localOnly) {
        this(title, description, actionId, argument, localOnly, Variant.NORMAL);
    }

    public MenuButton(String title, String description, String actionId, String argument, boolean localOnly, Variant variant) {
        this.title = title;
        this.description = description;
        this.actionId = actionId;
        this.argument = argument;
        this.localOnly = localOnly;
        this.variant = variant == null ? Variant.NORMAL : variant;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        boolean hovered = contains(mouseX, mouseY);
        Palette palette = palette(hovered);

        context.fill(x, y, x + width, y + height, palette.background());
        context.fill(x, y, x + width, y + 1, palette.border());
        context.fill(x, y + height - 1, x + width, y + height, palette.border());
        context.fill(x, y, x + 1, y + height, palette.border());
        context.fill(x + width - 1, y, x + width, y + height, palette.border());
        if (variant != Variant.NORMAL) {
            context.fill(x + 2, y + 2, x + 5, y + height - 2, palette.accent());
        }

        boolean showDescription = description != null && !description.isBlank() && height >= 36;
        if (showDescription) {
            int textX = variant == Variant.NORMAL ? x + 8 : x + 11;
            int textWidth = width - (variant == Variant.NORMAL ? 16 : 20);
            context.drawText(textRenderer, Text.literal(trim(textRenderer, title, textWidth)), textX, y + 7, TITLE_COLOR, true);
            context.drawText(textRenderer, Text.literal(trim(textRenderer, description, textWidth)), textX, y + 24, palette.descriptionColor(), false);
        } else {
            String visibleTitle = trim(textRenderer, title, width - 8);
            int titleX = x + Math.max(4, (width - textRenderer.getWidth(visibleTitle)) / 2);
            int titleY = y + Math.max(4, (height - textRenderer.fontHeight) / 2);
            context.drawText(textRenderer, Text.literal(visibleTitle), titleX, titleY, TITLE_COLOR, true);
        }
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public int top() {
        return y;
    }

    public int bottom() {
        return y + height;
    }

    public String actionId() {
        return actionId;
    }

    public String argument() {
        return argument;
    }

    public boolean localOnly() {
        return localOnly;
    }

    public boolean enabled() {
        return variant != Variant.DISABLED;
    }

    private Palette palette(boolean hovered) {
        return switch (variant) {
            case DANGER -> new Palette(hovered ? 0xAA6D3434 : 0x8845292F, hovered ? 0xFFFF8A8A : 0xFFD76474, 0xFFFF8A8A, 0xFFFFC2C8);
            case TOGGLE_ON -> new Palette(hovered ? 0xAA2E5C49 : 0x88324B3F, hovered ? 0xFF77E287 : 0xFF55B978, 0xFF77E287, 0xFFD7F8E1);
            case TOGGLE_OFF -> new Palette(hovered ? 0xAA4A5059 : 0x88343A42, hovered ? 0xFFB9C4CE : 0xFF6C7783, 0xFFB9C4CE, 0xFFC9D4DE);
            case DISABLED -> new Palette(0x66303A46, 0xFF59636C, 0xFF59636C, 0xFF9FAAB4);
            default -> new Palette(hovered ? 0xAA3E5570 : 0x88303A46, hovered ? 0xFF74B6FF : 0xFF4C5A66, 0xFF74B6FF, DESCRIPTION_COLOR);
        };
    }

    private static String trim(TextRenderer textRenderer, String text, int maxWidth) {
        return textRenderer.trimToWidth(text, Math.max(10, maxWidth));
    }

    private record Palette(int background, int border, int accent, int descriptionColor) {
    }
}
