package com.xm6680.friendservermenu.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class MenuButton {
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int DESCRIPTION_COLOR = 0xFFC9D4DE;

    private final String title;
    private final String description;
    private final String actionId;
    private final String argument;
    private final boolean localOnly;
    private int x;
    private int y;
    private int width;
    private int height;

    public MenuButton(String title, String description, String actionId, String argument) {
        this(title, description, actionId, argument, false);
    }

    public MenuButton(String title, String description, String actionId, String argument, boolean localOnly) {
        this.title = title;
        this.description = description;
        this.actionId = actionId;
        this.argument = argument;
        this.localOnly = localOnly;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        boolean hovered = contains(mouseX, mouseY);
        int background = hovered ? 0xAA3E5570 : 0x88303A46;
        int border = hovered ? 0xFF74B6FF : 0xFF4C5A66;

        context.fill(x, y, x + width, y + height, background);
        context.fill(x, y, x + width, y + 1, border);
        context.fill(x, y + height - 1, x + width, y + height, border);
        context.fill(x, y, x + 1, y + height, border);
        context.fill(x + width - 1, y, x + width, y + height, border);

        context.drawText(textRenderer, Text.literal(trim(textRenderer, title, width - 16)), x + 8, y + 7, TITLE_COLOR, true);
        if (description != null && !description.isBlank()) {
            context.drawText(textRenderer, Text.literal(trim(textRenderer, description, width - 16)), x + 8, y + 24, DESCRIPTION_COLOR, false);
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

    private static String trim(TextRenderer textRenderer, String text, int maxWidth) {
        return textRenderer.trimToWidth(text, Math.max(10, maxWidth));
    }
}
