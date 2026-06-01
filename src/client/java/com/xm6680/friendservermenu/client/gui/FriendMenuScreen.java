package com.xm6680.friendservermenu.client.gui;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import com.xm6680.friendservermenu.client.ClientCoordinateHud;
import com.xm6680.friendservermenu.client.ClientTaskHud;
import com.xm6680.friendservermenu.config.LocationEntry;
import com.xm6680.friendservermenu.network.ActivityTeleportPayload;
import com.xm6680.friendservermenu.network.ActivityTemplatePayload;
import com.xm6680.friendservermenu.network.AddLocationPayload;
import com.xm6680.friendservermenu.network.DeleteLocationPayload;
import com.xm6680.friendservermenu.network.EditLocationPayload;
import com.xm6680.friendservermenu.network.LocationMutationResultPayload;
import com.xm6680.friendservermenu.network.MenuActionPayload;
import com.xm6680.friendservermenu.network.MenuDataPayload;
import com.xm6680.friendservermenu.network.RequestMenuDataPayload;
import com.xm6680.friendservermenu.network.ServerStatusPayload;
import com.xm6680.friendservermenu.network.TaskActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FriendMenuScreen extends Screen {
    private static final String DEFAULT_TITLE = "小铭的服务器菜单";
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int RECENT_COORDINATE_LIMIT = 5;
    private static final DateTimeFormatter END_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static String lastClosedPageId = Page.TELEPORT.id;
    private static final List<String> recentCopiedCoordinates = new ArrayList<>();

    private final List<MenuButton> buttons = new ArrayList<>();
    private final List<TaskClickArea> taskClickAreas = new ArrayList<>();
    private final List<TextInput> visibleInputs = new ArrayList<>();
    private final LocationDraft locationDraft = new LocationDraft();
    private final ActivityDraft activityDraft = new ActivityDraft();
    private final TaskDraft taskDraft = new TaskDraft();
    private final TextInput setupTitleInput = new TextInput(DEFAULT_TITLE, 40);

    private String titleText;
    private boolean canUseAdmin;
    private Page selectedPage;
    private List<LocationEntry> locations = List.of();
    private ClientStatus serverStatus = ClientStatus.empty();
    private ActiveActivity activeActivity;
    private List<ClientTask> tasks = List.of();
    private long activeActivityReceivedAtMillis;

    private int navScroll;
    private int contentScroll;
    private int navContentHeight;
    private int pageContentHeight;
    private int refreshTicks;
    private TextInput focusedInput;
    private DrawContext activeRenderContext;
    private Layout activeRenderLayout;
    private int activeMouseX;
    private int activeMouseY;
    private String locationFormMessage = "";
    private boolean locationFormMessageSuccess;
    private String taskFormMessage = "";
    private String setupMessage = "";
    private boolean taskHudDragging;
    private boolean coordinateHudDragging;
    private String selectedTaskId = "";
    private boolean selectedTaskDetailFromHistory;
    private boolean taskInviteListOpen;
    private int currentPositionNoticeTicks;
    private String selectedAdminPlayer = "";

    public FriendMenuScreen(String titleText, boolean canUseAdmin, String initialPage) {
        super(Text.literal(normalizeTitle(titleText)));
        this.titleText = normalizeTitle(titleText);
        this.canUseAdmin = canUseAdmin;
        this.selectedPage = safe(initialPage).isBlank() ? Page.fromId(lastClosedPageId) : Page.fromId(initialPage);
        if (this.selectedPage.isAdminPage() && !canUseAdmin) {
            this.selectedPage = Page.TELEPORT;
        }
        if (this.selectedPage == Page.SETUP && !canUseAdmin) {
            this.selectedPage = Page.TELEPORT;
        }
        this.activityDraft.reset();
    }

    public void applyMenuData(MenuDataPayload payload) {
        this.titleText = normalizeTitle(payload.menuTitle());
        this.canUseAdmin = payload.canUseAdmin();
        if (selectedPage.isAdminPage() && !canUseAdmin) {
            selectedPage = Page.TELEPORT;
        }

        try {
            LocationEntry[] parsed = FriendServerMenuMod.GSON.fromJson(safe(payload.locationsJson()), LocationEntry[].class);
            this.locations = parsed == null ? List.of() : Arrays.asList(parsed);
        } catch (Exception ignored) {
            this.locations = List.of();
        }

        try {
            this.activeActivity = payload.activeActivityJson() == null || payload.activeActivityJson().isBlank()
                    ? null
                    : FriendServerMenuMod.GSON.fromJson(payload.activeActivityJson(), ActiveActivity.class);
            this.activeActivityReceivedAtMillis = this.activeActivity == null ? 0L : System.currentTimeMillis();
        } catch (Exception ignored) {
            this.activeActivity = null;
            this.activeActivityReceivedAtMillis = 0L;
        }

        try {
            ClientTask[] parsedTasks = FriendServerMenuMod.GSON.fromJson(safe(payload.tasksJson()), ClientTask[].class);
            this.tasks = parsedTasks == null ? List.of() : Arrays.asList(parsedTasks);
        } catch (Exception ignored) {
            this.tasks = List.of();
        }
    }

    public void applyStatus(ServerStatusPayload payload) {
        try {
            ClientStatus parsed = FriendServerMenuMod.GSON.fromJson(safe(payload.statusJson()), ClientStatus.class);
            this.serverStatus = parsed == null ? this.serverStatus : parsed;
        } catch (Exception ignored) {
            // Keep the last good status if a refresh arrives malformed.
        }
    }

    public void applyLocationMutationResult(LocationMutationResultPayload payload) {
        locationFormMessage = safe(payload.message());
        locationFormMessageSuccess = payload.success();
        if (payload.success()) {
            setFocusedInput(null);
            selectPage(Page.TELEPORT);
        }
    }

    @Override
    public void tick() {
        refreshTicks++;
        if (currentPositionNoticeTicks > 0) {
            currentPositionNoticeTicks--;
        }
        if (refreshTicks >= REFRESH_INTERVAL_TICKS) {
            refreshTicks = 0;
            ClientPlayNetworking.send(new RequestMenuDataPayload((int) (System.currentTimeMillis() & 0x7FFFFFFF)));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        buttons.clear();
        taskClickAreas.clear();
        visibleInputs.clear();

        if (selectedPage == Page.TASK_HUD_EDIT) {
            renderFullTaskHudEditPage(context, mouseX, mouseY);
            return;
        }
        if (selectedPage == Page.COORDINATE_HUD_EDIT) {
            renderFullCoordinateHudEditPage(context, mouseX, mouseY);
            return;
        }

        Layout layout = layout();
        navScroll = clamp(navScroll, 0, maxScroll(navContentHeight, layout.navHeight()));
        contentScroll = clamp(contentScroll, 0, maxScroll(pageContentHeight, layout.contentHeight()));

        context.fill(layout.panelX(), layout.panelY(), layout.panelX() + layout.panelWidth(), layout.panelY() + layout.panelHeight(), 0xD0161B22);
        context.fill(layout.panelX(), layout.panelY(), layout.panelX() + layout.navWidth(), layout.panelY() + layout.panelHeight(), 0xE01E252D);
        context.fill(layout.panelX() + layout.navWidth(), layout.panelY(), layout.panelX() + layout.navWidth() + 1, layout.panelY() + layout.panelHeight(), 0xFF35404B);

        context.drawText(textRenderer, Text.literal(titleText), layout.panelX() + 12, layout.panelY() + 12, 0xFFFFFFFF, true);
        context.drawText(textRenderer, Text.literal(selectedPage.label), layout.contentX(), layout.panelY() + 13, 0xFFFFFFFF, true);
        renderCloseButton(context, mouseX, mouseY, layout);

        renderTabs(context, mouseX, mouseY, layout);

        activeRenderContext = context;
        activeRenderLayout = layout;
        activeMouseX = mouseX;
        activeMouseY = mouseY;
        context.enableScissor(layout.contentX(), layout.contentY(), layout.contentX() + layout.contentWidth(), layout.contentBottom());
        int pageBottom = switch (selectedPage) {
            case TELEPORT -> renderTeleportPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case SETUP -> renderSetupPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case ADD_LOCATION -> renderLocationFormPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth(), false);
            case EDIT_LOCATION -> renderLocationFormPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth(), true);
            case COORDINATES -> renderCoordinatePage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case COORDINATE_HUD_EDIT -> renderCoordinateHudEditPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case TASKS -> renderTasksPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case TASK_HISTORY -> renderTaskHistoryPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case TASK_DETAIL -> renderTaskDetailPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case CREATE_TASK -> renderTaskFormPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth(), false);
            case EDIT_TASK -> renderTaskFormPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth(), true);
            case TASK_HUD_EDIT -> renderTaskHudEditPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case ACTIVITY -> renderActivityPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case STATUS -> renderStatusPage(context, layout.contentX(), layout.contentY() - contentScroll);
            case ADMIN -> renderAdminPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case ADMIN_TIME -> renderAdminTimePage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case ADMIN_WEATHER -> renderAdminWeatherPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case ADMIN_PLAYERS -> renderAdminPlayersPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case ADMIN_PLAYER_DETAIL -> renderAdminPlayerDetailPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case ADMIN_CLEANUP -> renderAdminCleanupPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
        };
        context.disableScissor();
        activeRenderContext = null;
        activeRenderLayout = null;
        pageContentHeight = Math.max(0, pageBottom - (layout.contentY() - contentScroll));

        drawScrollbar(context, layout.panelX() + layout.navWidth() - 4, layout.contentY(), 2, layout.navHeight(), navContentHeight, navScroll);
        drawScrollbar(context, layout.contentX() + layout.contentWidth() - 3, layout.contentY(), 2, layout.contentHeight(), pageContentHeight, contentScroll);
        renderButtonTooltip(context, mouseX, mouseY, layout);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        Layout layout = layout();

        if (selectedPage == Page.TASK_HUD_EDIT) {
            if (click.button() == 0) {
                for (MenuButton menuButton : buttons) {
                    if (menuButton.contains(mouseX, mouseY)) {
                        playClickSound();
                        runButton(menuButton);
                        return true;
                    }
                }
                if (ClientTaskHud.contains(mouseX, mouseY)) {
                    taskHudDragging = true;
                    return true;
                }
                setFocusedInput(null);
                return true;
            }
            return super.mouseClicked(click, doubleClick);
        }
        if (selectedPage == Page.COORDINATE_HUD_EDIT) {
            if (click.button() == 0) {
                for (MenuButton menuButton : buttons) {
                    if (menuButton.contains(mouseX, mouseY)) {
                        playClickSound();
                        runButton(menuButton);
                        return true;
                    }
                }
                if (ClientCoordinateHud.contains(mouseX, mouseY)) {
                    coordinateHudDragging = true;
                    return true;
                }
                setFocusedInput(null);
                return true;
            }
            return super.mouseClicked(click, doubleClick);
        }

        if (click.button() == 0) {
            if (closeButtonContains(mouseX, mouseY, layout)) {
                playClickSound();
                close();
                return true;
            }

            if (ClientTaskHud.isEditMode() && ClientTaskHud.contains(mouseX, mouseY)) {
                taskHudDragging = true;
                return true;
            }
            if (ClientCoordinateHud.isEditMode() && ClientCoordinateHud.contains(mouseX, mouseY)) {
                coordinateHudDragging = true;
                return true;
            }

            if (insideNav(mouseX, mouseY, layout)) {
                Page clickedPage = tabAt(mouseX, mouseY, layout);
                if (clickedPage != null) {
                    playClickSound();
                    selectPage(clickedPage);
                    return true;
                }
            }

            if (insideContent(mouseX, mouseY, layout)) {
                for (TextInput input : visibleInputs) {
                    if (input.contains(mouseX, mouseY)) {
                        setFocusedInput(input);
                        return true;
                    }
                }
                setFocusedInput(null);

                for (MenuButton menuButton : buttons) {
                    if (buttonVisible(menuButton, layout) && menuButton.contains(mouseX, mouseY)) {
                        playClickSound();
                        runButton(menuButton);
                        return true;
                    }
                }

                for (TaskClickArea area : taskClickAreas) {
                    if (taskClickAreaVisible(area, layout) && area.contains(mouseX, mouseY)) {
                        playClickSound();
                        openTaskDetail(area.taskId());
                        return true;
                    }
                }
            } else {
                setFocusedInput(null);
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = layout();
        if (selectedPage == Page.TASK_HUD_EDIT) {
            if (ClientTaskHud.contains(mouseX, mouseY)) {
                ClientTaskHud.resizeBy(verticalAmount > 0 ? 1 : -1, width, height);
            }
            return true;
        }
        if (selectedPage == Page.COORDINATE_HUD_EDIT) {
            if (ClientCoordinateHud.contains(mouseX, mouseY)) {
                ClientCoordinateHud.resizeBy(verticalAmount > 0 ? 1 : -1, width, height);
            }
            return true;
        }
        if (ClientTaskHud.isEditMode() && ClientTaskHud.contains(mouseX, mouseY)) {
            ClientTaskHud.resizeBy(verticalAmount > 0 ? 1 : -1, width, height);
            return true;
        }
        if (ClientCoordinateHud.isEditMode() && ClientCoordinateHud.contains(mouseX, mouseY)) {
            ClientCoordinateHud.resizeBy(verticalAmount > 0 ? 1 : -1, width, height);
            return true;
        }
        int delta = (int) Math.round(verticalAmount * 18.0D);
        if (insideNav(mouseX, mouseY, layout)) {
            navScroll = clamp(navScroll - delta, 0, maxScroll(navContentHeight, layout.navHeight()));
            return true;
        }
        if (insideContent(mouseX, mouseY, layout)) {
            contentScroll = clamp(contentScroll - delta, 0, maxScroll(pageContentHeight, layout.contentHeight()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(Click click, double horizontalAmount, double verticalAmount) {
        if (taskHudDragging && ClientTaskHud.isEditMode()) {
            ClientTaskHud.moveBy(horizontalAmount, verticalAmount, width, height);
            return true;
        }
        if (coordinateHudDragging && ClientCoordinateHud.isEditMode()) {
            ClientCoordinateHud.moveBy(horizontalAmount, verticalAmount, width, height);
            return true;
        }
        return super.mouseDragged(click, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(Click click) {
        taskHudDragging = false;
        coordinateHudDragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        rememberLastPage();
        ClientTaskHud.setEditMode(false);
        ClientCoordinateHud.setEditMode(false);
        super.close();
    }

    @Override
    public void removed() {
        rememberLastPage();
        ClientTaskHud.setEditMode(false);
        ClientCoordinateHud.setEditMode(false);
        super.removed();
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (focusedInput != null && focusedInput.charTyped(input)) {
            onFocusedInputChanged();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (focusedInput != null && focusedInput.keyPressed(input)) {
            onFocusedInputChanged();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void renderTabs(DrawContext context, int mouseX, int mouseY, Layout layout) {
        List<Page> pages = visiblePages();
        navContentHeight = pages.size() * 30;
        context.enableScissor(layout.panelX(), layout.contentY(), layout.panelX() + layout.navWidth(), layout.contentBottom());
        int y = layout.contentY() - navScroll;
        for (Page page : pages) {
            boolean selected = selectedNavPage() == page;
            boolean hovered = insideNav(mouseX, mouseY, layout) && tabContains(page, mouseX, mouseY, layout);
            int color = selected ? 0xFF315B82 : hovered ? 0xFF283847 : 0x00000000;
            context.fill(layout.panelX() + 7, y, layout.panelX() + layout.navWidth() - 8, y + 25, color);
            context.drawText(textRenderer, Text.literal(page.label), layout.panelX() + 16, y + 8, selected ? 0xFFFFFFFF : 0xFFD1DAE2, false);
            y += 30;
        }
        context.disableScissor();
    }

    private void renderCloseButton(DrawContext context, int mouseX, int mouseY, Layout layout) {
        int x = closeButtonX(layout);
        int y = closeButtonY(layout);
        boolean hovered = closeButtonContains(mouseX, mouseY, layout);
        int background = hovered ? 0xAA6D3434 : 0x88303A46;
        int border = hovered ? 0xFFFF8A8A : 0xFF4C5A66;
        context.fill(x, y, x + 20, y + 20, background);
        context.fill(x, y, x + 20, y + 1, border);
        context.fill(x, y + 19, x + 20, y + 20, border);
        context.fill(x, y, x + 1, y + 20, border);
        context.fill(x + 19, y, x + 20, y + 20, border);
        context.drawText(textRenderer, Text.literal("×"), x + 7, y + 6, 0xFFFFFFFF, true);
    }

    private int renderTeleportPage(DrawContext context, int x, int y, int contentWidth) {
        addButton("新增传送点", "", "open_add_location", "", true, x, y, Math.min(110, contentWidth), 26);
        y += 40;

        if (locations.isEmpty()) {
            context.drawText(textRenderer, Text.literal("暂无公共传送点，请点击“新增传送点”创建。"), x, y, 0xFFC9D4DE, false);
            return y + 22;
        }

        int cardHeight = 44;
        for (LocationEntry location : locations) {
            boolean canDeleteLocation = canUseAdmin || isLocationCreator(location);
            String description = safe(location.description) + "  " + dimensionName(location.world) + "  X:" + format(location.x) + " Y:" + format(location.y) + " Z:" + format(location.z);
            if (contentWidth >= 248) {
                int actionWidth = canDeleteLocation ? 98 : 50;
                int cardWidth = contentWidth - actionWidth;
                addButton(safe(location.name), description, "teleport_location", safe(location.id), false, x, y, cardWidth, cardHeight);
                addButton("编辑", "", "open_edit_location", safe(location.id), true, x + cardWidth + 6, y, 42, 20);
                if (canDeleteLocation) {
                    addButton("删除", "", "delete_location", safe(location.id), true, x + cardWidth + 6, y + 24, 42, 20);
                }
                y += cardHeight + 7;
            } else {
                addButton(safe(location.name), description, "teleport_location", safe(location.id), false, x, y, contentWidth - 6, cardHeight);
                y += cardHeight + 6;
                addButton("编辑", "", "open_edit_location", safe(location.id), true, x, y, 44, 22);
                if (canDeleteLocation) {
                    addButton("删除", "", "delete_location", safe(location.id), true, x + 50, y, 44, 22);
                }
                y += 29;
            }
        }
        return y;
    }

    private int renderLocationFormPage(DrawContext context, int x, int y, int contentWidth, boolean editing) {
        locationDraft.ensureDefaults();
        addButton("返回", "", "back_to_teleport", "", true, x, y, 58, 24);
        addButton("使用当前位置", "", "location_use_current", "", true, x + 66, y, 96, 24);
        if (currentPositionNoticeTicks > 0) {
            int noticeX = x + 170;
            drawInlineStatus(context, "已经将当前坐标填入！", noticeX, y + 8, x + contentWidth - noticeX, true);
        }
        y += 36;

        context.drawText(textRenderer, Text.literal("基础信息"), x, y, 0xFFFFFFFF, true);
        y += 16;
        y = addInput(context, locationDraft.name, "传送点名称", x, y, contentWidth);
        if (canUseAdmin) {
            y = addInput(context, locationDraft.id, "传送点 ID", x, y, contentWidth);
        }
        y = addInput(context, locationDraft.description, "描述", x, y, contentWidth);
        y = addDimensionDropdown(context, x, y, contentWidth);

        context.drawText(textRenderer, Text.literal("位置坐标"), x, y, 0xFFFFFFFF, true);
        y += 16;
        y = addCoordinateInput(context, locationDraft.x, "X 坐标", "x", x, y, contentWidth);
        y = addCoordinateInput(context, locationDraft.y, "Y 坐标", "y", x, y, contentWidth);
        y = addCoordinateInput(context, locationDraft.z, "Z 坐标", "z", x, y, contentWidth);

        if (canUseAdmin) {
            context.drawText(textRenderer, Text.literal("朝向"), x, y, 0xFFFFFFFF, true);
            y += 16;
            int half = Math.max(80, (contentWidth - 8) / 2);
            y = addInputRow(context, locationDraft.yaw, "yaw", x, y, half, locationDraft.pitch, "pitch", x + half + 8, half);
        }

        int submitWidth = editing ? 96 : 120;
        addButton(editing ? "保存修改" : "提交传送点", "", editing ? "submit_location_edit" : "submit_location", "", true, x, y, Math.min(submitWidth, contentWidth), 28);
        if (!locationFormMessage.isBlank()) {
            int messageX = x + Math.min(submitWidth, contentWidth) + 8;
            drawInlineStatus(context, locationFormMessage, messageX, y + 9, x + contentWidth - messageX, locationFormMessageSuccess);
        }
        return y + 36;
    }

    private int addDimensionDropdown(DrawContext context, int x, int y, int contentWidth) {
        context.drawText(textRenderer, Text.literal("所在维度"), x, y, 0xFF9FB0BF, false);
        addButton("维度：" + dimensionName(locationDraft.world), "", "location_dimension_dropdown", "", true, x, y + 12, Math.min(150, contentWidth - 6), 22);
        y += 40;
        if (locationDraft.dimensionDropdownOpen) {
            String[][] dimensions = {
                    {"主世界", "minecraft:overworld"},
                    {"下界", "minecraft:the_nether"},
                    {"末地", "minecraft:the_end"}
            };
            for (String[] dimension : dimensions) {
                addButton(dimension[0], "", "location_dimension_select", dimension[1], true, x + 8, y, Math.min(132, contentWidth - 14), 22);
                y += 25;
            }
        }
        return y;
    }

    private int renderCoordinatePage(DrawContext context, int x, int y, int contentWidth) {
        if (serverStatus.deathPoint != null) {
            y = renderDeathPointSection(context, x, y, contentWidth);
            y += 10;
        }

        context.drawText(textRenderer, Text.literal("当前位置"), x, y, 0xFFFFFFFF, true);
        drawTextLine(context, clientCoordinates(), x, y + 18, contentWidth);
        context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth("当前群系：" + clientBiomeName(), Math.max(20, contentWidth - 8))), x, y + 34, 0xFFC9D4DE, false);
        y += 56;

        int buttonWidth = Math.max(68, Math.min(84, (contentWidth - 12) / 3));
        int buttonHeight = 26;
        int currentX = x;
        int currentY = y;
        String[][] actions = {
                {"复制坐标", "copy_coords"},
                {"公开坐标", "send_coords_public"},
                {"私发坐标", "send_coords_private"}
        };
        for (String[] action : actions) {
            if (currentX + buttonWidth > x + contentWidth) {
                currentX = x;
                currentY += buttonHeight + 6;
            }
            addButton(action[0], "", action[1], "", "copy_coords".equals(action[1]), currentX, currentY, buttonWidth, buttonHeight);
            currentX += buttonWidth + 6;
        }
        y = currentY + buttonHeight + 18;

        context.drawText(textRenderer, Text.literal("坐标 HUD"), x, y, 0xFFFFFFFF, true);
        y += 18;
        int hudButtonWidth = Math.max(84, Math.min(112, (contentWidth - 8) / 2));
        addButton(ClientCoordinateHud.isEnabled() ? "HUD显示中" : "HUD已隐藏", "", "coordinate_hud_toggle", "", true, x, y, hudButtonWidth, 24);
        addButton("编辑HUD位置", "", "coordinate_hud_edit", "", true, x + hudButtonWidth + 6, y, hudButtonWidth, 24);
        y += 38;

        if (!recentCopiedCoordinates.isEmpty()) {
            context.drawText(textRenderer, Text.literal("最近复制的坐标"), x, y, 0xFFFFFFFF, true);
            y += 18;
            for (String coordinate : recentCopiedCoordinates) {
                int deleteWidth = 44;
                int copyWidth = Math.max(92, contentWidth - deleteWidth - 12);
                addButton("复制", coordinate, "copy_recent_coordinate", coordinate, true, x, y, copyWidth, 36);
                addButton("删除", "", "delete_recent_coordinate", coordinate, true, x + copyWidth + 6, y, deleteWidth, 36);
                y += 40;
            }
        }
        return y + 4;
    }

    private int renderDeathPointSection(DrawContext context, int x, int y, int contentWidth) {
        String coordinates = deathPointCoordinates();
        boolean wide = contentWidth >= 230;
        int sectionHeight = wide ? 72 : 100;
        int cardX = x - 4;
        int cardY = y - 4;
        int cardRight = x + contentWidth - 2;
        int cardBottom = y + sectionHeight;
        int innerX = cardX + 18;
        int innerY = cardY + 12;
        int innerRight = cardRight - 12;
        context.fill(cardX, cardY, cardRight, cardBottom, 0x88313A42);
        context.fill(cardX, cardY, cardRight, cardY + 1, 0xFF64717D);
        context.fill(cardX, cardBottom - 1, cardRight, cardBottom, 0xFF64717D);
        context.fill(cardX, cardY, cardX + 1, cardBottom, 0xFF64717D);
        context.fill(cardRight - 1, cardY, cardRight, cardBottom, 0xFF64717D);
        context.fill(cardX, cardY, cardX + 3, cardBottom, 0xFFD86A7B);
        context.drawText(textRenderer, Text.literal("最近死亡点"), innerX, innerY, 0xFFFFD7DD, true);

        if (wide) {
            int buttonWidth = 82;
            drawTextLine(context, coordinates, innerX, innerY + 19, innerRight - innerX - buttonWidth - 14);
            context.drawText(textRenderer, Text.literal("5分钟内有效"), innerX, innerY + 38, 0xFFE7A7B0, false);
            addButton("传送", "", "teleport_death_point", "", false, innerRight - buttonWidth, innerY + 18, buttonWidth, 28);
        } else {
            drawTextLine(context, coordinates, innerX, innerY + 19, innerRight - innerX);
            context.drawText(textRenderer, Text.literal("5分钟内有效"), innerX, innerY + 38, 0xFFE7A7B0, false);
            addButton("传送到死亡点", "", "teleport_death_point", "", false, innerX, innerY + 58, Math.min(116, innerRight - innerX), 28);
        }
        return y + sectionHeight;
    }

    private int renderSetupPage(DrawContext context, int x, int y, int contentWidth) {
        context.drawText(textRenderer, Text.literal("初始化菜单"), x, y, 0xFFFFFFFF, true);
        y += 20;
        drawTextLine(context, "第一次使用时，请先设置左上角显示的菜单名称。", x, y, contentWidth);
        y += 24;
        y = addInput(context, setupTitleInput, "GUI 名称", x, y, contentWidth);
        addButton("完成初始化", "", "setup_finish", "", true, x, y, 92, 26);
        if (!setupMessage.isBlank()) {
            drawInlineStatus(context, setupMessage, x + 102, y + 8, contentWidth - 104, false);
        }
        return y + 38;
    }

    private int renderTasksPage(DrawContext context, int x, int y, int contentWidth) {
        int buttonWidth = Math.max(72, Math.min(100, (contentWidth - 8) / 2));
        addButton("发布任务", "", "open_create_task", "", true, x, y, buttonWidth, 24);
        addButton("历史任务", "", "open_task_history", "", true, x + buttonWidth + 6, y, buttonWidth, 24);
        addButton(ClientTaskHud.isEnabled() ? "HUD显示中" : "HUD已隐藏", "", "task_hud_toggle", "", true, x, y + 30, buttonWidth, 24);
        addButton("编辑HUD位置", "", "task_hud_edit", "", true, x + buttonWidth + 6, y + 30, buttonWidth, 24);
        y += 62;

        List<ClientTask> activeTasks = tasks.stream()
                .filter(task -> !isHistoricalTask(task))
                .toList();
        if (activeTasks.isEmpty()) {
            drawTextLine(context, "暂无进行中的任务。有什么目标都可以在这里写出来！", x, y, contentWidth);
            y += 16;
            drawTextLine(context, "可以多多发布任务，腐竹看到可能会在任务完成时给予丰厚奖励！", x, y, contentWidth);
            return y + 24;
        }

        for (ClientTask task : activeTasks) {
            y = renderTaskCard(context, task, x, y, contentWidth, false);
            y += 8;
        }
        return y;
    }

    private int renderTaskHistoryPage(DrawContext context, int x, int y, int contentWidth) {
        addButton("返回任务", "", "back_to_tasks", "", true, x, y, 72, 24);
        y += 32;

        List<ClientTask> historyTasks = tasks.stream()
                .filter(task -> isHistoricalTask(task) && task.viewerJoined)
                .toList();
        if (historyTasks.isEmpty()) {
            drawTextLine(context, "暂无你参与过的历史任务。", x, y, contentWidth);
            return y + 24;
        }

        for (ClientTask task : historyTasks) {
            y = renderTaskCard(context, task, x, y, contentWidth, true);
            y += 8;
        }
        return y;
    }

    private int renderTaskDetailPage(DrawContext context, int x, int y, int contentWidth) {
        ClientTask task = findClientTask(selectedTaskId);
        boolean returnToHistory = selectedTaskDetailFromHistory || (task != null && isHistoricalTask(task));
        addButton(returnToHistory ? "返回" : "返回任务", "", returnToHistory ? "back_to_task_history" : "back_to_tasks", "", true, x, y, returnToHistory ? 58 : 72, 24);
        y += 36;

        if (task == null) {
            drawTextLine(context, "找不到这个任务，可能已经结束或不可见。", x, y, contentWidth);
            return y + 24;
        }

        drawTextLine(context, task.title, x, y, contentWidth);
        y += 16;
        drawTextLine(context, "状态：" + taskStatusLabel(task.status) + "  可见：" + taskVisibilityLabel(task.visibility), x, y, contentWidth);
        y += 14;
        drawTextLine(context, "发布者：" + textOr(task.publisherName, "未知") + "  成员：" + task.participantCount + "  完成确认：" + task.voteCount + "/" + task.voteThreshold, x, y, contentWidth);
        y += 14;
        if (!safe(task.reward).isBlank()) {
            drawTextLine(context, "奖励：" + task.reward, x, y, contentWidth);
            y += 14;
        }
        if (!safe(task.description).isBlank()) {
            drawTextLine(context, "说明：" + task.description, x, y, contentWidth);
            y += 18;
        }

        context.drawText(textRenderer, Text.literal("已加入玩家"), x, y, 0xFFFFFFFF, true);
        y += 16;
        String[] participants = task.participants == null ? new String[0] : task.participants;
        if (participants.length == 0) {
            drawTextLine(context, "暂无玩家。", x, y, contentWidth);
            y += 16;
        } else {
            for (String participant : participants) {
                drawTextLine(context, "- " + participant, x + 4, y, contentWidth - 4);
                y += 14;
            }
        }

        if (task.canInvite) {
            y += 4;
            addButton(taskInviteListOpen ? "收起邀请列表" : "邀请玩家加入", "", "task_toggle_invites", "", true, x, y, 96, 24);
            y += 34;
            if (taskInviteListOpen) {
                y = renderInvitePlayerButtons(context, task, x, y, contentWidth);
            }
        }
        return y;
    }

    private int renderTaskHudEditPage(DrawContext context, int x, int y, int contentWidth) {
        addButton("完成编辑", "", "task_hud_done", "", true, x, y, 76, 24);
        y += 34;
        drawTextLine(context, "拖动屏幕上的任务 HUD 调整位置。", x, y, contentWidth);
        y += 16;
        drawTextLine(context, "鼠标放在 HUD 上滚轮缩放，也可以用下面按钮调整。", x, y, contentWidth);
        y += 24;
        addButton("变窄", "", "task_hud_narrow", "", true, x, y, 50, 24);
        addButton("变宽", "", "task_hud_wider", "", true, x + 56, y, 50, 24);
        y += 30;
        addButton("变矮", "", "task_hud_shorter", "", true, x, y, 50, 24);
        addButton("变高", "", "task_hud_taller", "", true, x + 56, y, 50, 24);
        y += 36;
        drawTextLine(context, "当前显示：" + ClientTaskHud.selectedTaskTitle(), x, y, contentWidth);
        return y + 22;
    }

    private void renderFullTaskHudEditPage(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, height, 0xEE0B1016);
        ClientTaskHud.renderPreview(context, textRenderer, width, height);

        Layout fullLayout = new Layout(0, 0, width, height, 0, 0, 0, width, height);
        activeRenderContext = context;
        activeRenderLayout = fullLayout;
        activeMouseX = mouseX;
        activeMouseY = mouseY;

        int doneWidth = 76;
        int controlWidth = 50;
        int gap = 8;
        int rowWidth = controlWidth * 4 + gap * 3;
        int controlsY = Math.max(42, height / 2 - 18);
        String title = "编辑HUD位置";
        context.drawText(textRenderer, Text.literal(title), Math.max(8, (width - textRenderer.getWidth(title)) / 2), controlsY - 24, 0xFFFFFFFF, true);
        if (width >= rowWidth + 24) {
            addButton("完成编辑", "", "task_hud_done", "", true, (width - doneWidth) / 2, controlsY, doneWidth, 24);
            int controlsX = (width - rowWidth) / 2;
            int rowY = controlsY + 30;
            addButton("变窄", "", "task_hud_narrow", "", true, controlsX, rowY, controlWidth, 24);
            addButton("变宽", "", "task_hud_wider", "", true, controlsX + controlWidth + gap, rowY, controlWidth, 24);
            addButton("变矮", "", "task_hud_shorter", "", true, controlsX + (controlWidth + gap) * 2, rowY, controlWidth, 24);
            addButton("变高", "", "task_hud_taller", "", true, controlsX + (controlWidth + gap) * 3, rowY, controlWidth, 24);
        } else {
            int pairWidth = controlWidth * 2 + gap;
            int controlsX = Math.max(8, (width - pairWidth) / 2);
            addButton("完成编辑", "", "task_hud_done", "", true, Math.max(8, (width - doneWidth) / 2), controlsY, doneWidth, 24);
            addButton("变窄", "", "task_hud_narrow", "", true, controlsX, controlsY + 30, controlWidth, 24);
            addButton("变宽", "", "task_hud_wider", "", true, controlsX + controlWidth + gap, controlsY + 30, controlWidth, 24);
            addButton("变矮", "", "task_hud_shorter", "", true, controlsX, controlsY + 58, controlWidth, 24);
            addButton("变高", "", "task_hud_taller", "", true, controlsX + controlWidth + gap, controlsY + 58, controlWidth, 24);
        }
        String selectedTitle = "当前显示：" + ClientTaskHud.selectedTaskTitle();
        int selectedTitleWidth = Math.max(40, width - 24);
        String selectedTitleText = textRenderer.trimToWidth(selectedTitle, selectedTitleWidth);
        int selectedTitleX = 12 + Math.max(0, (selectedTitleWidth - textRenderer.getWidth(selectedTitleText)) / 2);
        int selectedTitleY = controlsY + (width >= rowWidth + 24 ? 64 : 88);
        context.drawText(textRenderer, Text.literal(selectedTitleText), selectedTitleX, selectedTitleY, 0xFFDDE7F0, false);
        renderButtonTooltip(context, mouseX, mouseY, fullLayout);

        activeRenderContext = null;
        activeRenderLayout = null;
        pageContentHeight = 0;
    }

    private int renderCoordinateHudEditPage(DrawContext context, int x, int y, int contentWidth) {
        addButton("完成编辑", "", "coordinate_hud_done", "", true, x, y, 76, 24);
        y += 34;
        addButton("变窄", "", "coordinate_hud_narrow", "", true, x, y, 50, 24);
        addButton("变宽", "", "coordinate_hud_wider", "", true, x + 56, y, 50, 24);
        y += 30;
        addButton("变矮", "", "coordinate_hud_shorter", "", true, x, y, 50, 24);
        addButton("变高", "", "coordinate_hud_taller", "", true, x + 56, y, 50, 24);
        return y + 36;
    }

    private void renderFullCoordinateHudEditPage(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, height, 0xEE0B1016);
        ClientCoordinateHud.renderPreview(context, textRenderer, width, height);

        Layout fullLayout = new Layout(0, 0, width, height, 0, 0, 0, width, height);
        activeRenderContext = context;
        activeRenderLayout = fullLayout;
        activeMouseX = mouseX;
        activeMouseY = mouseY;

        int doneWidth = 76;
        int controlWidth = 50;
        int gap = 8;
        int rowWidth = controlWidth * 4 + gap * 3;
        int controlsY = Math.max(42, height / 2 - 18);
        String title = "编辑坐标HUD位置";
        context.drawText(textRenderer, Text.literal(title), Math.max(8, (width - textRenderer.getWidth(title)) / 2), controlsY - 24, 0xFFFFFFFF, true);
        if (width >= rowWidth + 24) {
            addButton("完成编辑", "", "coordinate_hud_done", "", true, (width - doneWidth) / 2, controlsY, doneWidth, 24);
            int controlsX = (width - rowWidth) / 2;
            int rowY = controlsY + 30;
            addButton("变窄", "", "coordinate_hud_narrow", "", true, controlsX, rowY, controlWidth, 24);
            addButton("变宽", "", "coordinate_hud_wider", "", true, controlsX + controlWidth + gap, rowY, controlWidth, 24);
            addButton("变矮", "", "coordinate_hud_shorter", "", true, controlsX + (controlWidth + gap) * 2, rowY, controlWidth, 24);
            addButton("变高", "", "coordinate_hud_taller", "", true, controlsX + (controlWidth + gap) * 3, rowY, controlWidth, 24);
        } else {
            int pairWidth = controlWidth * 2 + gap;
            int controlsX = Math.max(8, (width - pairWidth) / 2);
            addButton("完成编辑", "", "coordinate_hud_done", "", true, Math.max(8, (width - doneWidth) / 2), controlsY, doneWidth, 24);
            addButton("变窄", "", "coordinate_hud_narrow", "", true, controlsX, controlsY + 30, controlWidth, 24);
            addButton("变宽", "", "coordinate_hud_wider", "", true, controlsX + controlWidth + gap, controlsY + 30, controlWidth, 24);
            addButton("变矮", "", "coordinate_hud_shorter", "", true, controlsX, controlsY + 58, controlWidth, 24);
            addButton("变高", "", "coordinate_hud_taller", "", true, controlsX + controlWidth + gap, controlsY + 58, controlWidth, 24);
        }
        renderButtonTooltip(context, mouseX, mouseY, fullLayout);

        activeRenderContext = null;
        activeRenderLayout = null;
        pageContentHeight = 0;
    }

    private int renderTaskCard(DrawContext context, ClientTask task, int x, int y, int contentWidth, boolean compact) {
        int actionRows = taskActionRows(task, contentWidth);
        int textLines = 3 + (!safe(task.reward).isBlank() ? 1 : 0) + (!compact && !safe(task.description).isBlank() ? 1 : 0);
        int minHeight = compact ? (contentWidth < 260 ? 112 : 88) : (contentWidth < 260 ? 158 : 122);
        int cardHeight = Math.max(minHeight, 22 + textLines * 14 + 10 + actionRows * 26);
        int right = x + contentWidth - 6;
        boolean hovered = activeMouseX >= x && activeMouseX < right && activeMouseY >= y && activeMouseY < y + cardHeight;
        int fillColor = hovered ? 0x8842515E : 0x66303A46;
        int borderColor = hovered ? 0xFF7FC2FF : 0xFF4C5A66;
        context.fill(x, y, right, y + cardHeight, fillColor);
        context.fill(x, y, right, y + 1, borderColor);
        context.fill(x, y + cardHeight - 1, right, y + cardHeight, borderColor);
        context.fill(x, y, x + 1, y + cardHeight, borderColor);
        context.fill(right - 1, y, right, y + cardHeight, borderColor);
        taskClickAreas.add(new TaskClickArea(task.id, x, y, right - x, Math.max(24, cardHeight - actionRows * 26 - 8)));

        int textX = x + 8;
        int lineY = y + 8;
        drawTextLine(context, task.title, textX, lineY, contentWidth - 18);
        lineY += 14;
        drawTextLine(context, "状态：" + taskStatusLabel(task.status) + "  可见：" + taskVisibilityLabel(task.visibility) + "  发布者：" + textOr(task.publisherName, "未知"), textX, lineY, contentWidth - 18);
        lineY += 14;
        drawTextLine(context, "成员：" + task.participantCount + "  完成确认：" + task.voteCount + "/" + task.voteThreshold, textX, lineY, contentWidth - 18);
        lineY += 14;
        if (!safe(task.reward).isBlank()) {
            drawTextLine(context, "奖励：" + task.reward, textX, lineY, contentWidth - 18);
            lineY += 14;
        }
        if (!compact && !safe(task.description).isBlank()) {
            drawTextLine(context, task.description, textX, lineY, contentWidth - 18);
        }

        ButtonCursor cursor = new ButtonCursor(textX, y + cardHeight - actionRows * 26 - 4, right);
        int smallWidth = 50;
        boolean activeTask = !isHistoricalTask(task);
        if (task.canJoin) {
            addTaskCardButton(context, "加入", "task_join", task.id, cursor, smallWidth, "");
        }
        if (task.canLeave) {
            addTaskCardButton(context, "退出", "task_leave", task.id, cursor, smallWidth, "");
        }
        if (task.canVoteComplete) {
            addTaskCardButton(context, "已完成", "task_vote_complete", task.id, cursor, 70, "需要当前任务 50% 玩家点击已完成后，任务才会完成。");
        }
        if (task.viewerJoined && activeTask) {
            addTaskCardButton(context, ClientTaskHud.isSelectedTask(task.id) ? "HUD显示中" : "显示到HUD", "task_hud_select", task.id, cursor, 74, "");
        }
        if (task.canEdit) {
            addTaskCardButton(context, "编辑", "open_edit_task", task.id, cursor, smallWidth, "");
        }
        if (task.canReward) {
            addTaskCardButton(context, "奖励", "task_reward_open", task.id, cursor, smallWidth, "打开任务奖励箱，放入提前准备好的奖励。");
        }
        if (task.canEnd && activeTask) {
            addTaskCardButton(context, "结束任务", "task_end", task.id, cursor, 62, "");
        }
        if (!activeTask && task.viewerJoined) {
            addTaskCardButton(context, "删除", "task_delete_history", task.id, cursor, smallWidth, "从你的历史任务列表中删除。");
        }
        return y + cardHeight;
    }

    private int taskActionRows(ClientTask task, int contentWidth) {
        int right = Math.max(80, contentWidth - 14);
        int cursorX = 0;
        int rows = 1;
        for (int buttonWidth : taskActionButtonWidths(task)) {
            if (cursorX > 0 && cursorX + buttonWidth > right - 8) {
                rows++;
                cursorX = 0;
            }
            cursorX += buttonWidth + 5;
        }
        return rows;
    }

    private List<Integer> taskActionButtonWidths(ClientTask task) {
        List<Integer> widths = new ArrayList<>();
        if (task.canJoin) {
            widths.add(50);
        }
        if (task.canLeave) {
            widths.add(50);
        }
        if (task.canVoteComplete) {
            widths.add(70);
        }
        if (task.viewerJoined && !isHistoricalTask(task)) {
            widths.add(74);
        }
        if (task.canEdit) {
            widths.add(50);
        }
        if (task.canReward) {
            widths.add(50);
        }
        if (task.canEnd && !isHistoricalTask(task)) {
            widths.add(62);
        }
        if (isHistoricalTask(task) && task.viewerJoined) {
            widths.add(50);
        }
        return widths;
    }

    private int renderInvitePlayerButtons(DrawContext context, ClientTask task, int x, int y, int contentWidth) {
        String[] playerNames = serverStatus.playerNames == null ? new String[0] : serverStatus.playerNames;
        boolean renderedAny = false;
        ButtonCursor cursor = new ButtonCursor(x, y, x + contentWidth - 6);
        for (String playerName : playerNames) {
            if (safe(playerName).isBlank() || isTaskParticipant(task, playerName)) {
                continue;
            }
            renderedAny = true;
            String title = textRenderer.trimToWidth("邀请 " + playerName, 86);
            addTaskCardButton(context, title, "task_invite_player", task.id + "|" + playerName, cursor, 92, "");
        }
        if (!renderedAny) {
            drawTextLine(context, "没有可邀请的在线玩家。", x, y, contentWidth);
            return y + 18;
        }
        return cursor.y + 28;
    }

    private void addTaskCardButton(DrawContext context, String title, String actionId, String argument, ButtonCursor cursor, int width, String hoverHint) {
        if (cursor.x + width > cursor.right - 8) {
            cursor.x = cursor.rowStart;
            cursor.y += 26;
        }
        addButton(title, "", actionId, argument, actionId.startsWith("task_hud") || actionId.startsWith("open_"), cursor.x, cursor.y, width, 22);
        cursor.x += width + 5;
    }

    private int renderTaskFormPage(DrawContext context, int x, int y, int contentWidth, boolean editing) {
        taskDraft.ensureDefaults();
        addButton("返回", "", "back_to_tasks", "", true, x, y, 58, 24);
        y += 36;

        y = addInput(context, taskDraft.title, "任务标题", x, y, contentWidth);
        y = addInput(context, taskDraft.description, "任务说明", x, y, contentWidth);

        if (!editing || taskDraft.canChangeVisibility) {
            addButton(taskDraft.publicTask ? "可见性：公开" : "可见性：私人", "", "task_toggle_visibility", "", true, x, y, Math.min(112, contentWidth), 24);
            y += 36;
        } else {
            drawTextLine(context, "可见性：" + (taskDraft.publicTask ? "公开" : "私人"), x, y, contentWidth);
            y += 22;
        }

        addButton(editing ? "保存任务" : "发布任务", "", editing ? "task_submit_edit" : "task_submit_create", "", true, x, y, Math.min(92, contentWidth), 26);
        if (!taskFormMessage.isBlank()) {
            int messageX = x + Math.min(92, contentWidth) + 8;
            drawInlineStatus(context, taskFormMessage, messageX, y + 9, x + contentWidth - messageX, false);
        }
        return y + 38;
    }

    private int renderActivityPage(DrawContext context, int x, int y, int contentWidth) {
        if (activeActivity != null) {
            String activeCategory = safe(activeActivity.category);
            boolean activeNeedsMeetingPoint = activityNeedsMeetingPoint(activeCategory);
            context.drawText(textRenderer, Text.literal("当前活动通知"), x, y, 0xFFFFFFFF, true);
            y += 16;
            drawTextLine(context, "发起人：" + textOr(activeActivity.initiator, "未知"), x, y, contentWidth);
            y += 14;
            drawTextLine(context, "类型：" + activityCategoryLabel(activeActivity.category), x, y, contentWidth);
            y += 14;
            drawTextLine(context, "标题：" + textOr(activeActivity.title, "暂无"), x, y, contentWidth);
            y += 14;
            drawTextLine(context, "说明：" + textOr(activeActivity.description, "暂无"), x, y, contentWidth);
            y += 14;
            if (activeNeedsMeetingPoint) {
                drawTextLine(context, "集合：" + textOr(activeActivity.meetingPoint, "当前位置") + " " + activeActivityCoordinates(), x, y, contentWidth);
                y += 18;
            }
            if (activeActivity.hasEndDate && activityUsesEndDate(activeCategory)) {
                drawTextLine(context, "结束日期：" + textOr(activeActivity.endDateText, "暂无"), x, y, contentWidth);
                y += 14;
            }
            if ("item_give".equals(activeCategory)) {
                drawTextLine(context, "发放物品：" + textOr(activeActivity.itemId, "minecraft:apple") + " x" + Math.max(1, activeActivity.itemCount), x, y, contentWidth);
                y += 14;
                if (activeActivity.itemClaimedByViewer) {
                    drawTextLine(context, "你已领取本次活动物品。", x, y, contentWidth);
                    y += 18;
                } else {
                    addButton("领取物品", "", "activity_claim_item", safe(activeActivity.id), false, x, y, 82, 24);
                    y += 34;
                }
            }
            if ("maintenance".equals(activeCategory)) {
                drawTextLine(context, "维护时间：" + textOr(activeActivity.maintenanceTimeText, "暂无"), x, y, contentWidth);
                y += 14;
                drawTextLine(context, "维护倒计时：" + formatCountdown(maintenanceRemainingSeconds(activeActivity)), x, y, contentWidth);
                y += 14;
            }
            if (activeNeedsMeetingPoint && activeActivity.needsTeleport) {
                addButton("前往集合点", "", "activity_teleport", safe(activeActivity.id), true, x, y, 96, 26);
                y += 36;
            }
            if (canUseAdmin) {
                addButton(activeActivity.hasEndDate ? "提前结束活动" : "结束活动", "", "activity_end", safe(activeActivity.id), false, x, y, 104, 26);
                y += 36;
            }
        }

        if (!canUseAdmin) {
            context.drawText(textRenderer, Text.literal("只有 OP 可以组织活动。"), x, y + 4, 0xFFFFD27D, false);
            return y + 28;
        }

        context.drawText(textRenderer, Text.literal("组织活动"), x, y, 0xFFFFFFFF, true);
        y += 18;
        context.drawText(textRenderer, Text.literal("模板分类：" + activityCategoryLabel(activityDraft.category)), x, y, 0xFF9FB0BF, false);
        y += 14;
        int templateWidth = Math.max(64, Math.min(82, (contentWidth - 12) / 4));
        int templateX = x;
        String[][] templates = {
                {"集合", "gathering"},
                {"发物品", "item_give"},
                {"维护", "maintenance"},
                {"探索", "exploration"}
        };
        for (String[] template : templates) {
            if (templateX + templateWidth > x + contentWidth) {
                templateX = x;
                y += 28;
            }
            addButton(template[0], "", "activity_template", template[1], true, templateX, y, templateWidth, 22);
            templateX += templateWidth + 4;
        }
        y += 34;
        boolean draftNeedsMeetingPoint = activityNeedsMeetingPoint(activityDraft.category);
        boolean draftUsesEndDate = activityUsesEndDate(activityDraft.category);
        y = addInput(context, activityDraft.title, "活动标题", x, y, contentWidth);
        y = addInput(context, activityDraft.description, "活动说明", x, y, contentWidth);
        if (draftNeedsMeetingPoint) {
            y = addInput(context, activityDraft.meetingPoint, "集合地点", x, y, contentWidth);
        }
        if ("item_give".equals(activityDraft.category)) {
            y = addInput(context, activityDraft.itemId, "发放物品 ID", x, y, contentWidth);
            y = addInput(context, activityDraft.itemCount, "发放数量", x, y, Math.min(120, contentWidth));
        }
        if ("maintenance".equals(activityDraft.category)) {
            y = addInput(context, activityDraft.maintenanceTimeText, "维护时间", x, y, contentWidth);
            y = addInput(context, activityDraft.maintenanceCountdownSeconds, "维护倒计时（秒）", x, y, Math.min(140, contentWidth));
        }
        if (draftNeedsMeetingPoint || draftUsesEndDate) {
            int controlX = x;
            int controlY = y + 4;
            if (draftNeedsMeetingPoint) {
                addButton(activityDraft.needsTeleport ? "传送按钮：开" : "传送按钮：关", "", "activity_toggle_teleport", "", true, controlX, controlY, 96, 24);
                controlX += 104;
            }
            if (draftUsesEndDate) {
                if (controlX + 96 > x + contentWidth) {
                    controlX = x;
                    controlY += 30;
                }
                addButton(activityDraft.hasEndDate ? "结束日期：开" : "结束日期：关", "", "activity_toggle_end_date", "", true, controlX, controlY, 96, 24);
            }
            y = controlY + 34;
        }
        if (draftUsesEndDate && activityDraft.hasEndDate) {
            y = addInput(context, activityDraft.endDateText, "结束日期（yyyy-MM-dd HH:mm）", x, y, contentWidth);
        }
        addButton("发送活动通知", "", "activity_submit", "", true, x, y + 4, 112, 24);
        return y + 40;
    }

    private int renderStatusPage(DrawContext context, int x, int y) {
        drawInfo(context, "在线玩家", serverStatus.onlinePlayers + " / " + serverStatus.maxPlayers, x, y);
        drawInfo(context, "当前维度", textOr(serverStatus.dimension, clientDimensionName()), x, y + 28);
        drawInfo(context, "当前坐标", "X: " + serverStatus.x + ", Y: " + serverStatus.y + ", Z: " + serverStatus.z, x, y + 56);
        drawInfo(context, "世界时间", minecraftTime(serverStatus.timeOfDay), x, y + 84);
        drawInfo(context, "天气", textOr(serverStatus.weather, "暂无数据"), x, y + 112);
        drawInfo(context, "MSPT", String.format(Locale.ROOT, "%.2f", serverStatus.mspt), x, y + 140);
        drawInfo(context, "TPS", String.format(Locale.ROOT, "%.2f", serverStatus.tps), x, y + 168);
        return y + 198;
    }

    private int renderAdminPage(DrawContext context, int x, int y, int contentWidth) {
        if (!canUseAdmin) {
            selectedPage = Page.TELEPORT;
            return y;
        }

        int columnWidth = Math.max(92, (contentWidth - 8) / 2);
        addButton("时间管理", "白天、正午、夜晚、午夜", "admin_page", "admin_time", true, x, y, columnWidth, 40);
        addButton("天气管理", "晴天、下雨、雷暴", "admin_page", "admin_weather", true, x + columnWidth + 8, y, columnWidth, 40);
        addButton("玩家管理", "模式、飞行、状态、踢出和权限", "admin_page", "admin_players", true, x, y + 48, columnWidth, 40);
        addButton("清理管理", "清理掉落物、经验球、箭矢和怪物", "admin_page", "admin_cleanup", true, x + columnWidth + 8, y + 48, columnWidth, 40);
        addButton("重载配置", "重新读取 friendservermenu.json", "admin_reload_config", "", false, x, y + 96, contentWidth - 6, 40);
        return y + 144;
    }

    private int renderAdminTimePage(DrawContext context, int x, int y, int contentWidth) {
        addAdminBackButton(x, y);
        y += 34;
        int columnWidth = Math.max(92, (contentWidth - 8) / 2);
        addButton("白天", "设置为 07:00", "admin_day", "", false, x, y, columnWidth, 40);
        addButton("正午", "设置为 12:00", "admin_noon", "", false, x + columnWidth + 8, y, columnWidth, 40);
        addButton("夜晚", "设置为 19:00", "admin_night", "", false, x, y + 48, columnWidth, 40);
        addButton("午夜", "设置为 00:00", "admin_midnight", "", false, x + columnWidth + 8, y + 48, columnWidth, 40);
        return y + 96;
    }

    private int renderAdminWeatherPage(DrawContext context, int x, int y, int contentWidth) {
        addAdminBackButton(x, y);
        y += 34;
        int columnWidth = Math.max(92, (contentWidth - 8) / 2);
        addButton("晴天", "清除雨雪和雷暴", "admin_clear_weather", "", false, x, y, columnWidth, 40);
        addButton("下雨", "让世界开始下雨", "admin_rain", "", false, x + columnWidth + 8, y, columnWidth, 40);
        addButton("雷暴", "让世界进入雷暴天气", "admin_thunder", "", false, x, y + 48, contentWidth - 6, 40);
        return y + 96;
    }

    private int renderAdminPlayersPage(DrawContext context, int x, int y, int contentWidth) {
        addAdminBackButton(x, y);
        y += 34;
        addButton("全员传送到我", "将所有在线玩家传送到当前位置", "admin_tp_all_to_me", "", false, x, y, contentWidth - 6, 40);
        int columnWidth = Math.max(92, (contentWidth - 8) / 2);
        addButton("恢复生命", "给所有玩家恢复满血", "admin_heal_all", "", false, x, y + 48, columnWidth, 40);
        addButton("补满饥饿", "给所有玩家补满饥饿值", "admin_feed_all", "", false, x + columnWidth + 8, y + 48, columnWidth, 40);
        y += 104;

        context.drawText(textRenderer, Text.literal("在线玩家管理"), x, y, 0xFFFFFFFF, true);
        y += 18;
        String[] playerNames = serverStatus.playerNames == null ? new String[0] : serverStatus.playerNames;
        if (playerNames.length == 0) {
            context.drawText(textRenderer, Text.literal("暂无在线玩家数据。"), x, y, 0xFFC9D4DE, false);
            return y + 24;
        }

        int playerButtonHeight = 28;
        for (String playerName : playerNames) {
            addButton(playerName, "", "admin_select_player", playerName, true, x, y, contentWidth - 6, playerButtonHeight);
            y += playerButtonHeight + 6;
        }
        return y;
    }

    private int renderAdminPlayerDetailPage(DrawContext context, int x, int y, int contentWidth) {
        addButton("返回列表", "", "admin_page", "admin_players", true, x, y, 70, 24);
        y += 34;

        String playerName = safe(selectedAdminPlayer);
        context.drawText(textRenderer, Text.literal("目标玩家：" + textOr(playerName, "未选择")), x, y, 0xFFFFFFFF, true);
        y += 20;
        if (playerName.isBlank() || !isPlayerOnline(playerName)) {
            context.drawText(textRenderer, Text.literal("该玩家当前不在线，请返回列表重新选择。"), x, y, 0xFFFFD27D, false);
            return y + 28;
        }

        y = addActionSection(context, "传送", new String[][]{
                {"传到我", "admin_player_tp_to_me", ""},
                {"我去找TA", "admin_player_tp_me_to", ""}
        }, playerName, x, y, contentWidth);
        y = addActionSection(context, "游戏模式", new String[][]{
                {"生存", "admin_player_gamemode", "|survival"},
                {"创造", "admin_player_gamemode", "|creative"},
                {"冒险", "admin_player_gamemode", "|adventure"},
                {"旁观", "admin_player_gamemode", "|spectator"}
        }, playerName, x, y, contentWidth);
        y = addActionSection(context, "状态", new String[][]{
                {"飞行10分", "admin_player_flight", "|grant"},
                {"撤飞行", "admin_player_flight", "|revoke"},
                {"回血", "admin_player_heal", ""},
                {"补饥饿", "admin_player_feed", ""},
                {"清效果", "admin_player_clear_effects", ""},
                {"熄灭", "admin_player_extinguish", ""}
        }, playerName, x, y, contentWidth);
        y = addActionSection(context, "权限", new String[][]{
                {"给OP1", "admin_player_op", "|1"},
                {"给OP2", "admin_player_op", "|2"},
                {"给OP3", "admin_player_op", "|3"},
                {"给OP4", "admin_player_op", "|4"},
                {"撤OP", "admin_player_deop", ""}
        }, playerName, x, y, contentWidth);
        return addActionSection(context, "处罚", new String[][]{
                {"Kick", "admin_player_kick", ""},
                {"Ban", "admin_player_ban", ""}
        }, playerName, x, y, contentWidth);
    }

    private int addActionSection(DrawContext context, String label, String[][] actions, String playerName, int x, int y, int contentWidth) {
        context.drawText(textRenderer, Text.literal(label), x, y, 0xFF9FB0BF, false);
        y += 14;
        int buttonWidth = Math.max(54, Math.min(78, (contentWidth - 12) / 3));
        int buttonX = x;
        int rowY = y;
        for (String[] action : actions) {
            if (buttonX + buttonWidth > x + contentWidth) {
                buttonX = x;
                rowY += 26;
            }
            addButton(action[0], "", action[1], playerName + action[2], false, buttonX, rowY, buttonWidth, 22);
            buttonX += buttonWidth + 6;
        }
        return rowY + 34;
    }

    private int renderAdminCleanupPage(DrawContext context, int x, int y, int contentWidth) {
        addAdminBackButton(x, y);
        y += 34;
        int columnWidth = Math.max(92, (contentWidth - 8) / 2);
        addButton("清理掉落物", "只清理 ItemEntity", "admin_clear_items", "", false, x, y, columnWidth, 40);
        addButton("清理经验球", "只清理 ExperienceOrbEntity", "admin_clear_xp_orbs", "", false, x + columnWidth + 8, y, columnWidth, 40);
        addButton("清理箭矢", "只清理箭矢类投射物", "admin_clear_arrows", "", false, x, y + 48, columnWidth, 40);
        addButton("清理怪物(可能损坏服务器机械)", "只清理敌对生物", "admin_clear_hostiles", "", false, x + columnWidth + 8, y + 48, columnWidth, 40);
        return y + 96;
    }

    private void addAdminBackButton(int x, int y) {
        addButton("返回", "", "admin_page", "admin", true, x, y, 58, 24);
    }

    private int addInput(DrawContext context, TextInput input, String label, int x, int y, int width) {
        context.drawText(textRenderer, Text.literal(label), x, y, 0xFF9FB0BF, false);
        input.setBounds(x, y + 12, width - 6, 22);
        visibleInputs.add(input);
        renderInputIfVisible(input);
        return y + 40;
    }

    private int addInputRow(DrawContext context, TextInput first, String firstLabel, int firstX, int y, int firstWidth, TextInput second, String secondLabel, int secondX, int secondWidth) {
        context.drawText(textRenderer, Text.literal(firstLabel), firstX, y, 0xFF9FB0BF, false);
        context.drawText(textRenderer, Text.literal(secondLabel), secondX, y, 0xFF9FB0BF, false);
        first.setBounds(firstX, y + 12, firstWidth - 6, 22);
        second.setBounds(secondX, y + 12, secondWidth - 6, 22);
        visibleInputs.add(first);
        visibleInputs.add(second);
        renderInputIfVisible(first);
        renderInputIfVisible(second);
        return y + 40;
    }

    private int addCoordinateInput(DrawContext context, TextInput input, String label, String axis, int x, int y, int contentWidth) {
        context.drawText(textRenderer, Text.literal(label), x, y, 0xFF9FB0BF, false);
        int buttonWidth = 36;
        int gap = 5;
        int inputWidth = Math.max(58, contentWidth - 6 - (buttonWidth + gap) * 2);
        input.setBounds(x, y + 12, inputWidth, 22);
        visibleInputs.add(input);
        renderInputIfVisible(input);
        int buttonY = y + 12;
        addButton("-1", "", "location_delta", axis + ":-1", true, x + inputWidth + gap, buttonY, buttonWidth, 22);
        addButton("+1", "", "location_delta", axis + ":1", true, x + inputWidth + gap + buttonWidth + gap, buttonY, buttonWidth, 22);
        return y + 40;
    }

    private void addButton(String title, String description, String actionId, String argument, boolean localOnly, int x, int y, int width, int height) {
        MenuButton menuButton = new MenuButton(title, description, actionId, argument, localOnly);
        menuButton.setBounds(x, y, width, height);
        buttons.add(menuButton);
        if (activeRenderContext != null && activeRenderLayout != null && buttonVisible(menuButton, activeRenderLayout)) {
            menuButton.render(activeRenderContext, textRenderer, activeMouseX, activeMouseY);
        }
    }

    private void renderInputIfVisible(TextInput input) {
        if (activeRenderContext != null && activeRenderLayout != null && inputVisible(input, activeRenderLayout)) {
            input.render(activeRenderContext, textRenderer);
        }
    }

    private void drawInfo(DrawContext context, String label, String value, int x, int y) {
        context.drawText(textRenderer, Text.literal(label), x, y, 0xFF9FB0BF, false);
        context.drawText(textRenderer, Text.literal(value), x + 70, y, 0xFFFFFFFF, false);
    }

    private void drawTextLine(DrawContext context, String text, int x, int y, int width) {
        context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(text, Math.max(20, width - 8))), x, y, 0xFFDDE7F0, false);
    }

    private void drawInlineStatus(DrawContext context, String message, int x, int y, int width, boolean success) {
        if (width <= 12 || message == null || message.isBlank()) {
            return;
        }
        int color = success ? 0xFF77E287 : 0xFFFF8D8D;
        context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(message, Math.max(20, width - 4))), x, y, color, false);
    }

    private void renderButtonTooltip(DrawContext context, int mouseX, int mouseY, Layout layout) {
        for (MenuButton button : buttons) {
            if (!buttonVisible(button, layout) || !button.contains(mouseX, mouseY)) {
                continue;
            }
            String hint = buttonHoverHint(button);
            if (!hint.isBlank()) {
                drawTooltip(context, hint, mouseX, mouseY);
            }
            return;
        }
    }

    private String buttonHoverHint(MenuButton button) {
        return switch (button.actionId()) {
            case "setup_finish" -> "保存菜单名称，以后打开 GUI 时显示在左上角。";
            case "copy_coords" -> "复制当前坐标和维度到系统剪贴板。";
            case "teleport_death_point" -> "传送到最近一次死亡点，传送后这条记录会消失。";
            case "copy_recent_coordinate" -> "复制这条坐标记录，不能传送。";
            case "delete_recent_coordinate" -> "从最近复制列表中删除这条记录。";
            case "send_coords_public" -> "把当前坐标发到聊天栏，并附带绿色传送按钮。";
            case "send_coords_private" -> "只把当前坐标发给自己，方便保存或查看。";
            case "coordinate_hud_toggle" -> "打开或关闭屏幕上的坐标 HUD。";
            case "coordinate_hud_edit" -> "进入独立界面，拖动和缩放坐标 HUD。";
            case "coordinate_hud_done" -> "保存坐标 HUD 位置并返回坐标页。";
            case "coordinate_hud_smaller" -> "缩小坐标 HUD。";
            case "coordinate_hud_larger" -> "放大坐标 HUD。";
            case "coordinate_hud_narrow" -> "只缩小坐标 HUD 宽度，用来调整比例。";
            case "coordinate_hud_wider" -> "只增加坐标 HUD 宽度，用来调整比例。";
            case "coordinate_hud_shorter" -> "只缩小坐标 HUD 高度，用来调整比例。";
            case "coordinate_hud_taller" -> "只增加坐标 HUD 高度，用来调整比例。";
            case "open_add_location" -> "新增一个所有玩家都能看到的公共传送点。";
            case "open_edit_location" -> "修改这个传送点的信息，普通玩家不能改 ID。";
            case "delete_location" -> "删除这个公共传送点，只有创建者或 OP 可以删除。";
            case "teleport_location" -> "请求服务端把你传送到这个公共地点。";
            case "location_use_current" -> "把你当前所在位置填入传送点表单。";
            case "location_dimension_dropdown" -> "选择传送点所在的维度。";
            case "location_delta" -> "微调这个坐标值。";
            case "submit_location" -> "提交后由服务端校验并保存公共传送点。";
            case "submit_location_edit" -> "保存这个公共传送点的修改。";
            case "open_create_task" -> "有什么目标都可以写出来，其他玩家可以一起完成。";
            case "open_task_history" -> "查看你参与过的已完成或已结束任务。";
            case "task_hud_toggle" -> "打开或关闭屏幕上的任务 HUD。";
            case "task_hud_edit" -> "进入独立界面，拖动和缩放任务 HUD。";
            case "task_hud_done" -> "保存 HUD 位置并返回任务页。";
            case "task_hud_smaller" -> "缩小任务 HUD。";
            case "task_hud_larger" -> "放大任务 HUD。";
            case "task_hud_narrow" -> "只缩小任务 HUD 宽度，用来调整比例。";
            case "task_hud_wider" -> "只增加任务 HUD 宽度，用来调整比例。";
            case "task_hud_shorter" -> "只缩小任务 HUD 高度，用来调整比例。";
            case "task_hud_taller" -> "只增加任务 HUD 高度，用来调整比例。";
            case "task_join" -> "加入后可以参与任务完成确认，并可显示到 HUD。";
            case "task_leave" -> "退出后这个任务不会再显示到你的 HUD。";
            case "task_vote_complete" -> "需要当前任务 50% 玩家点击已完成后，任务才会完成。";
            case "task_hud_select" -> "把这个任务显示到你的任务 HUD。";
            case "open_edit_task" -> "修改任务标题、说明和可见性。";
            case "task_reward_open" -> "打开任务奖励箱，放入提前准备好的奖励。";
            case "task_end" -> "只有发布者可以结束任务，结束后进入历史任务。";
            case "task_delete_history" -> "只从你的历史任务列表中删除，不影响其他玩家。";
            case "task_toggle_invites" -> "展开在线玩家列表，邀请他们加入任务。";
            case "task_toggle_visibility" -> "公开任务所有玩家可见，私人任务只对成员和受邀玩家可见。";
            case "task_submit_create" -> "发布任务后，其他可见玩家可以加入。";
            case "task_submit_edit" -> "保存任务标题、说明或可见性修改。";
            case "activity_template" -> "切换活动类型，不同模板会显示不同输入项。";
            case "activity_toggle_teleport" -> "控制聊天通知里是否附带前往集合点按钮。";
            case "activity_toggle_end_date" -> "开启后活动会显示结束日期。";
            case "activity_submit" -> "向玩家发送活动聊天通知。";
            case "activity_claim_item" -> "领取本次发物品活动的奖励。";
            case "activity_teleport" -> "传送到当前活动集合点。";
            case "activity_end" -> "结束当前活动通知。";
            default -> "";
        };
    }

    private void drawTooltip(DrawContext context, String message, int mouseX, int mouseY) {
        int maxTooltipWidth = Math.max(60, Math.min(240, width - 16));
        String text = textRenderer.trimToWidth(message, maxTooltipWidth - 12);
        int tooltipWidth = textRenderer.getWidth(text) + 12;
        int tooltipHeight = 18;
        int tooltipX = clamp(mouseX + 12, 4, Math.max(4, width - tooltipWidth - 4));
        int tooltipY = mouseY + 14;
        if (tooltipY + tooltipHeight > height - 4) {
            tooltipY = mouseY - tooltipHeight - 10;
        }
        tooltipY = clamp(tooltipY, 4, Math.max(4, height - tooltipHeight - 4));

        context.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xEE101821);
        context.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 1, 0xFF7FC2FF);
        context.fill(tooltipX, tooltipY + tooltipHeight - 1, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xFF7FC2FF);
        context.fill(tooltipX, tooltipY, tooltipX + 1, tooltipY + tooltipHeight, 0xFF7FC2FF);
        context.fill(tooltipX + tooltipWidth - 1, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xFF7FC2FF);
        context.drawText(textRenderer, Text.literal(text), tooltipX + 6, tooltipY + 5, 0xFFDDE7F0, false);
    }

    private void runButton(MenuButton button) {
        switch (button.actionId()) {
            case "setup_finish" -> submitSetup();
            case "open_add_location" -> {
                locationDraft.resetNew();
                clearLocationFormMessage();
                selectPage(Page.ADD_LOCATION);
            }
            case "open_edit_location" -> openEditLocation(button.argument());
            case "delete_location" -> ClientPlayNetworking.send(new DeleteLocationPayload(button.argument()));
            case "back_to_teleport" -> selectPage(Page.TELEPORT);
            case "location_use_current" -> {
                locationDraft.resetToCurrentPlayer();
                currentPositionNoticeTicks = 60;
            }
            case "location_delta" -> locationDraft.applyDelta(button.argument());
            case "location_dimension_dropdown" -> locationDraft.dimensionDropdownOpen = !locationDraft.dimensionDropdownOpen;
            case "location_dimension_select" -> locationDraft.setWorld(button.argument());
            case "submit_location" -> submitLocation(false);
            case "submit_location_edit" -> submitLocation(true);
            case "coordinate_hud_toggle" -> ClientCoordinateHud.toggleEnabled();
            case "coordinate_hud_edit" -> selectPage(Page.COORDINATE_HUD_EDIT);
            case "coordinate_hud_done" -> selectPage(Page.COORDINATES);
            case "coordinate_hud_smaller" -> ClientCoordinateHud.resizeBy(-1, width, height);
            case "coordinate_hud_larger" -> ClientCoordinateHud.resizeBy(1, width, height);
            case "coordinate_hud_narrow" -> ClientCoordinateHud.resizeWidthBy(-1, width, height);
            case "coordinate_hud_wider" -> ClientCoordinateHud.resizeWidthBy(1, width, height);
            case "coordinate_hud_shorter" -> ClientCoordinateHud.resizeHeightBy(-1, width, height);
            case "coordinate_hud_taller" -> ClientCoordinateHud.resizeHeightBy(1, width, height);
            case "open_create_task" -> {
                taskDraft.resetNew();
                taskFormMessage = "";
                selectPage(Page.CREATE_TASK);
            }
            case "open_task_history" -> selectPage(Page.TASK_HISTORY);
            case "open_edit_task" -> openEditTask(button.argument());
            case "back_to_tasks" -> selectPage(Page.TASKS);
            case "back_to_task_history" -> selectPage(Page.TASK_HISTORY);
            case "task_toggle_invites" -> taskInviteListOpen = !taskInviteListOpen;
            case "task_toggle_visibility" -> taskDraft.publicTask = !taskDraft.publicTask;
            case "task_submit_create" -> submitTask(false);
            case "task_submit_edit" -> submitTask(true);
            case "task_join" -> ClientPlayNetworking.send(TaskActionPayload.simple("join", button.argument()));
            case "task_leave" -> {
                ClientTaskHud.clearSelectedTask(button.argument());
                ClientPlayNetworking.send(TaskActionPayload.simple("leave", button.argument()));
            }
            case "task_vote_complete" -> ClientPlayNetworking.send(TaskActionPayload.simple("vote_complete", button.argument()));
            case "task_end" -> ClientPlayNetworking.send(TaskActionPayload.simple("end", button.argument()));
            case "task_delete_history" -> ClientPlayNetworking.send(TaskActionPayload.simple("delete_history", button.argument()));
            case "task_invite_player" -> inviteTaskPlayer(button.argument());
            case "task_reward_open" -> ClientPlayNetworking.send(TaskActionPayload.simple("open_rewards", button.argument()));
            case "task_hud_toggle" -> ClientTaskHud.toggleEnabled();
            case "task_hud_edit" -> selectPage(Page.TASK_HUD_EDIT);
            case "task_hud_done" -> selectPage(Page.TASKS);
            case "task_hud_smaller" -> ClientTaskHud.resizeBy(-1, width, height);
            case "task_hud_larger" -> ClientTaskHud.resizeBy(1, width, height);
            case "task_hud_narrow" -> ClientTaskHud.resizeWidthBy(-1, width, height);
            case "task_hud_wider" -> ClientTaskHud.resizeWidthBy(1, width, height);
            case "task_hud_shorter" -> ClientTaskHud.resizeHeightBy(-1, width, height);
            case "task_hud_taller" -> ClientTaskHud.resizeHeightBy(1, width, height);
            case "task_hud_select" -> ClientTaskHud.selectTask(button.argument());
            case "admin_page" -> selectPage(Page.fromId(button.argument()));
            case "admin_select_player" -> {
                selectedAdminPlayer = button.argument();
                selectPage(Page.ADMIN_PLAYER_DETAIL);
            }
            case "activity_template" -> activityDraft.applyTemplate(button.argument());
            case "activity_toggle_teleport" -> activityDraft.needsTeleport = !activityDraft.needsTeleport;
            case "activity_toggle_end_date" -> {
                activityDraft.hasEndDate = !activityDraft.hasEndDate;
                if (activityDraft.hasEndDate && activityDraft.endDateText.value.isBlank()) {
                    activityDraft.endDateText.setSuggestedValue(defaultEndDateText());
                }
            }
            case "activity_submit" -> {
                ClientPlayNetworking.send(new ActivityTemplatePayload(FriendServerMenuMod.GSON.toJson(activityDraft.toSubmittedActivity())));
                close();
            }
            case "activity_teleport" -> {
                ClientPlayNetworking.send(new ActivityTeleportPayload(button.argument()));
                close();
            }
            case "activity_end" -> ClientPlayNetworking.send(new MenuActionPayload(button.actionId(), button.argument()));
            case "activity_claim_item" -> ClientPlayNetworking.send(new MenuActionPayload(button.actionId(), button.argument()));
            case "copy_coords" -> {
                copyCoordinateText(clientCoordinates());
                close();
            }
            case "copy_death_point" -> copyCoordinateText(deathPointCoordinates());
            case "copy_recent_coordinate" -> copyCoordinateText(button.argument());
            case "delete_recent_coordinate" -> recentCopiedCoordinates.remove(button.argument());
            case "teleport_death_point" -> {
                ClientPlayNetworking.send(new MenuActionPayload(button.actionId(), button.argument()));
                close();
            }
            case "delete_death_point" -> ClientPlayNetworking.send(new MenuActionPayload(button.actionId(), button.argument()));
            case "send_coords_public", "send_coords_private" -> {
                ClientPlayNetworking.send(new MenuActionPayload(button.actionId(), button.argument()));
                close();
            }
            default -> {
                ClientPlayNetworking.send(new MenuActionPayload(button.actionId(), button.argument()));
                if (shouldCloseAfterServerAction(button.actionId())) {
                    close();
                }
            }
        }
    }

    private void submitLocation(boolean editing) {
        LocationEntry location = locationDraft.toLocation();
        String validationError = validateDraft(location, editing);
        if (validationError != null) {
            setLocationFormMessage(validationError, false);
            return;
        }

        setLocationFormMessage("正在提交...", true);
        if (editing) {
            ClientPlayNetworking.send(new EditLocationPayload(locationDraft.editingOriginalId, FriendServerMenuMod.GSON.toJson(location)));
        } else {
            ClientPlayNetworking.send(new AddLocationPayload(FriendServerMenuMod.GSON.toJson(location)));
        }
    }

    private void submitTask(boolean editing) {
        TaskSubmission submission = taskDraft.toSubmission(canUseAdmin);
        if (safe(submission.title).isBlank()) {
            taskFormMessage = "任务标题不能为空。";
            return;
        }
        if (editing && safe(taskDraft.editingTaskId).isBlank()) {
            taskFormMessage = "找不到正在编辑的任务。";
            return;
        }

        taskFormMessage = "正在提交...";
        ClientPlayNetworking.send(new TaskActionPayload(
                editing ? "edit" : "create",
                editing ? taskDraft.editingTaskId : "",
                FriendServerMenuMod.GSON.toJson(submission)
        ));
        selectPage(Page.TASKS);
    }

    private void submitSetup() {
        String title = safe(setupTitleInput.value).trim();
        if (title.isBlank()) {
            setupMessage = "GUI 名称不能为空。";
            return;
        }
        setupMessage = "";
        ClientPlayNetworking.send(new MenuActionPayload("setup_finish", title));
    }

    private void inviteTaskPlayer(String argument) {
        String[] parts = safe(argument).split("\\|", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return;
        }
        ClientPlayNetworking.send(new TaskActionPayload("invite", parts[0], parts[1]));
    }

    private void copyCoordinateText(String coordinates) {
        String text = safe(coordinates);
        if (text.isBlank()) {
            return;
        }
        MinecraftClient.getInstance().keyboard.setClipboard(text);
        rememberCopiedCoordinate(text);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.literal("坐标已复制"), true);
        }
    }

    private static void rememberCopiedCoordinate(String coordinates) {
        String text = safe(coordinates).trim();
        if (text.isBlank()) {
            return;
        }
        recentCopiedCoordinates.removeIf(existing -> existing.equals(text));
        recentCopiedCoordinates.add(0, text);
        while (recentCopiedCoordinates.size() > RECENT_COORDINATE_LIMIT) {
            recentCopiedCoordinates.remove(recentCopiedCoordinates.size() - 1);
        }
    }

    private String validateDraft(LocationEntry location, boolean editing) {
        if (safe(location.name).isBlank()) {
            return "传送点名称不能为空。";
        }
        if (canUseAdmin && safe(location.id).isBlank()) {
            return "传送点 ID 生成失败，请修改名称。";
        }
        if (editing && safe(locationDraft.editingOriginalId).isBlank()) {
            return "找不到正在编辑的传送点。";
        }
        if (safe(location.world).isBlank()) {
            return "请选择所在维度。";
        }
        if (!isFinite(location.x) || !isFinite(location.y) || !isFinite(location.z) || !isFinite(location.yaw) || !isFinite(location.pitch)) {
            return "坐标或朝向必须是合法数字。";
        }
        return null;
    }

    private void setLocationFormMessage(String message, boolean success) {
        locationFormMessage = safe(message);
        locationFormMessageSuccess = success;
    }

    private void clearLocationFormMessage() {
        locationFormMessage = "";
        locationFormMessageSuccess = false;
        currentPositionNoticeTicks = 0;
    }

    private boolean shouldCloseAfterServerAction(String actionId) {
        return "teleport_location".equals(actionId) || (safe(actionId).startsWith("admin_") && !"admin_select_player".equals(actionId));
    }

    private void selectPage(Page page) {
        if (page.isAdminPage() && !canUseAdmin) {
            page = Page.TELEPORT;
        }
        ClientTaskHud.setEditMode(page == Page.TASK_HUD_EDIT);
        ClientCoordinateHud.setEditMode(page == Page.COORDINATE_HUD_EDIT);
        taskHudDragging = false;
        coordinateHudDragging = false;
        selectedPage = page;
        contentScroll = 0;
        setFocusedInput(null);
        if (page != Page.ADD_LOCATION && page != Page.EDIT_LOCATION) {
            clearLocationFormMessage();
        }
        if (page != Page.CREATE_TASK && page != Page.EDIT_TASK) {
            taskFormMessage = "";
        }
        if (page != Page.TASK_DETAIL) {
            selectedTaskDetailFromHistory = false;
            taskInviteListOpen = false;
        }
        if (page == Page.ADD_LOCATION) {
            locationDraft.ensureDefaults();
        }
        if (page == Page.CREATE_TASK) {
            taskDraft.ensureDefaults();
        }
        if (page != Page.ADMIN_PLAYER_DETAIL && page != Page.ADMIN_PLAYERS) {
            selectedAdminPlayer = "";
        }
    }

    private void rememberLastPage() {
        Page resumePage = selectedNavPage();
        if (resumePage.isAdminPage() && !canUseAdmin) {
            resumePage = Page.TELEPORT;
        }
        lastClosedPageId = resumePage.id;
    }

    private boolean isPlayerOnline(String playerName) {
        String[] playerNames = serverStatus.playerNames == null ? new String[0] : serverStatus.playerNames;
        for (String onlineName : playerNames) {
            if (safe(onlineName).equals(playerName)) {
                return true;
            }
        }
        return false;
    }

    private void openEditLocation(String id) {
        LocationEntry location = findClientLocation(id);
        if (location != null) {
            locationDraft.load(location);
            clearLocationFormMessage();
            selectPage(Page.EDIT_LOCATION);
        }
    }

    private void openEditTask(String id) {
        ClientTask task = findClientTask(id);
        if (task != null) {
            taskDraft.load(task);
            taskFormMessage = "";
            selectPage(Page.EDIT_TASK);
        }
    }

    private void openTaskDetail(String id) {
        ClientTask task = findClientTask(id);
        if (task != null) {
            selectedTaskId = id;
            selectedTaskDetailFromHistory = selectedPage == Page.TASK_HISTORY || isHistoricalTask(task);
            taskInviteListOpen = false;
            selectPage(Page.TASK_DETAIL);
        }
    }

    private LocationEntry findClientLocation(String id) {
        for (LocationEntry location : locations) {
            if (safe(id).equals(location.id)) {
                return location;
            }
        }
        return null;
    }

    private ClientTask findClientTask(String id) {
        for (ClientTask task : tasks) {
            if (safe(id).equals(task.id)) {
                return task;
            }
        }
        return null;
    }

    private boolean isLocationCreator(LocationEntry location) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null
                && location != null
                && player.getUuid().toString().equals(safe(location.creatorUuid));
    }

    private boolean isTaskParticipant(ClientTask task, String playerName) {
        if (task == null || task.participants == null) {
            return false;
        }
        for (String participant : task.participants) {
            if (safe(participant).equals(playerName)) {
                return true;
            }
        }
        return false;
    }

    private Page tabAt(double mouseX, double mouseY, Layout layout) {
        for (Page page : visiblePages()) {
            if (tabContains(page, mouseX, mouseY, layout)) {
                return page;
            }
        }
        return null;
    }

    private boolean tabContains(Page page, double mouseX, double mouseY, Layout layout) {
        int index = visiblePages().indexOf(page);
        int y = layout.contentY() - navScroll + index * 30;
        return mouseX >= layout.panelX() + 7 && mouseX < layout.panelX() + layout.navWidth() - 8 && mouseY >= y && mouseY < y + 25;
    }

    private List<Page> visiblePages() {
        if (selectedPage == Page.SETUP) {
            return List.of(Page.SETUP);
        }
        List<Page> pages = new ArrayList<>(List.of(Page.TELEPORT, Page.COORDINATES, Page.TASKS, Page.ACTIVITY, Page.STATUS));
        if (canUseAdmin) {
            pages.add(Page.ADMIN);
        }
        return pages;
    }

    private Page selectedNavPage() {
        if (selectedPage == Page.SETUP) {
            return Page.SETUP;
        }
        if (selectedPage == Page.ADD_LOCATION || selectedPage == Page.EDIT_LOCATION) {
            return Page.TELEPORT;
        }
        if (selectedPage == Page.COORDINATE_HUD_EDIT) {
            return Page.COORDINATES;
        }
        if (selectedPage == Page.CREATE_TASK || selectedPage == Page.EDIT_TASK || selectedPage == Page.TASK_HISTORY
                || selectedPage == Page.TASK_DETAIL || selectedPage == Page.TASK_HUD_EDIT) {
            return Page.TASKS;
        }
        return selectedPage.isAdminPage() ? Page.ADMIN : selectedPage;
    }

    private Layout layout() {
        int panelWidth = Math.min(540, Math.max(300, width - 24));
        int panelHeight = Math.min(315, Math.max(220, height - 24));
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int navWidth = Math.min(102, Math.max(84, panelWidth / 4));
        int contentX = panelX + navWidth + 12;
        int contentY = panelY + 38;
        int contentWidth = panelWidth - navWidth - 24;
        int contentBottom = panelY + panelHeight - 10;
        return new Layout(panelX, panelY, panelWidth, panelHeight, navWidth, contentX, contentY, contentWidth, contentBottom);
    }

    private boolean insideNav(double mouseX, double mouseY, Layout layout) {
        return mouseX >= layout.panelX() && mouseX < layout.panelX() + layout.navWidth()
                && mouseY >= layout.contentY() && mouseY < layout.contentBottom();
    }

    private boolean insideContent(double mouseX, double mouseY, Layout layout) {
        return mouseX >= layout.contentX() && mouseX < layout.contentX() + layout.contentWidth()
                && mouseY >= layout.contentY() && mouseY < layout.contentBottom();
    }

    private boolean closeButtonContains(double mouseX, double mouseY, Layout layout) {
        int x = closeButtonX(layout);
        int y = closeButtonY(layout);
        return mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
    }

    private int closeButtonX(Layout layout) {
        return layout.panelX() + layout.panelWidth() - 30;
    }

    private int closeButtonY(Layout layout) {
        return layout.panelY() + 9;
    }

    private boolean buttonVisible(MenuButton button, Layout layout) {
        return button.bottom() > layout.contentY() && button.top() < layout.contentBottom();
    }

    private boolean inputVisible(TextInput input, Layout layout) {
        return input.bottom() > layout.contentY() && input.top() < layout.contentBottom();
    }

    private boolean taskClickAreaVisible(TaskClickArea area, Layout layout) {
        return area.bottom() > layout.contentY() && area.top() < layout.contentBottom();
    }

    private void drawScrollbar(DrawContext context, int x, int y, int width, int height, int contentHeight, int scroll) {
        int maxScroll = maxScroll(contentHeight, height);
        if (maxScroll <= 0) {
            return;
        }
        context.fill(x, y, x + width, y + height, 0x55333A44);
        int thumbHeight = Math.max(18, height * height / Math.max(height + 1, contentHeight));
        int thumbY = y + (height - thumbHeight) * scroll / maxScroll;
        context.fill(x, thumbY, x + width, thumbY + thumbHeight, 0xCC7EA7CC);
    }

    private void playClickSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getSoundManager().play(PositionedSoundInstance.ui(FriendServerMenuMod.GUI_CLICK_SOUND, 1.0F, 1.0F));
    }

    private String clientCoordinates() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return "[未知] X: 0, Y: 0, Z: 0";
        }

        BlockPos pos = player.getBlockPos();
        return "[" + clientDimensionName() + " / " + clientBiomeName(pos) + "] X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ();
    }

    private String deathPointCoordinates() {
        ClientDeathPoint point = serverStatus.deathPoint;
        if (point == null) {
            return "";
        }
        return "[" + dimensionName(point.world) + "] X: " + point.x + ", Y: " + point.y + ", Z: " + point.z;
    }

    private String clientBiomeName(BlockPos pos) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || pos == null) {
            return "未知群系";
        }
        return world.getBiome(pos).getKey()
                .map(key -> biomeName(key.getValue().toString()))
                .orElse("未知群系");
    }

    private String clientBiomeName() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? "未知群系" : clientBiomeName(player.getBlockPos());
    }

    private String clientDimensionName() {
        return dimensionName(clientDimensionId());
    }

    private String clientDimensionId() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return "未知";
        }
        return world.getRegistryKey().getValue().toString();
    }

    private void setFocusedInput(TextInput input) {
        if (focusedInput != null) {
            focusedInput.blur();
        }
        focusedInput = input;
        if (focusedInput != null) {
            focusedInput.focus();
        }
    }

    private void onFocusedInputChanged() {
        if (focusedInput == locationDraft.name && !locationDraft.idEditedManually) {
            locationDraft.id.setSuggestedValue(makeId(locationDraft.name.value));
        } else if (focusedInput == locationDraft.id) {
            locationDraft.idEditedManually = true;
        } else if (focusedInput == activityDraft.maintenanceTimeText) {
            syncMaintenanceCountdownFromTime();
        } else if (focusedInput == activityDraft.maintenanceCountdownSeconds) {
            syncMaintenanceTimeFromCountdown();
        }
    }

    private void syncMaintenanceCountdownFromTime() {
        if (!"maintenance".equals(activityDraft.category)) {
            return;
        }
        try {
            LocalDateTime target = LocalDateTime.parse(activityDraft.maintenanceTimeText.value.trim(), END_DATE_FORMAT);
            long seconds = Math.max(0L, Duration.between(LocalDateTime.now(), target).getSeconds());
            activityDraft.maintenanceCountdownSeconds.setCommittedValue(String.valueOf(Math.min(99999L, seconds)));
        } catch (DateTimeParseException ignored) {
            // While the user is still typing a date, keep the previous countdown.
        }
    }

    private void syncMaintenanceTimeFromCountdown() {
        if (!"maintenance".equals(activityDraft.category)) {
            return;
        }
        String countdownText = activityDraft.maintenanceCountdownSeconds.value.trim();
        if (countdownText.isBlank()) {
            return;
        }
        double parsedSeconds = parseDouble(countdownText, Double.NaN);
        if (!isFinite(parsedSeconds)) {
            return;
        }
        int seconds = clamp((int) Math.round(parsedSeconds), 0, 99999);
        activityDraft.maintenanceTimeText.setCommittedValue(END_DATE_FORMAT.format(LocalDateTime.now().plusSeconds(seconds)));
    }

    private String activeActivityCoordinates() {
        if (activeActivity == null) {
            return "";
        }
        return "[" + dimensionName(activeActivity.world) + "] X:" + Math.round(activeActivity.x)
                + " Y:" + Math.round(activeActivity.y)
                + " Z:" + Math.round(activeActivity.z);
    }

    private static String makeId(String name) {
        String source = safe(name).trim();
        String base = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (!base.isBlank()) {
            return base;
        }
        return source.isBlank() ? "" : "tp_" + Integer.toUnsignedString(source.hashCode(), 36);
    }

    private static String dimensionName(String id) {
        return switch (safe(id)) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> safe(id);
        };
    }

    private static String biomeName(String id) {
        return switch (safe(id)) {
            case "minecraft:badlands" -> "恶地";
            case "minecraft:bamboo_jungle" -> "竹林";
            case "minecraft:basalt_deltas" -> "玄武岩三角洲";
            case "minecraft:beach" -> "沙滩";
            case "minecraft:birch_forest" -> "白桦森林";
            case "minecraft:cherry_grove" -> "樱花树林";
            case "minecraft:cold_ocean" -> "冷水海洋";
            case "minecraft:crimson_forest" -> "绯红森林";
            case "minecraft:dark_forest" -> "黑森林";
            case "minecraft:deep_cold_ocean" -> "冷水深海";
            case "minecraft:deep_dark" -> "深暗之域";
            case "minecraft:deep_frozen_ocean" -> "冰冻深海";
            case "minecraft:deep_lukewarm_ocean" -> "温水深海";
            case "minecraft:deep_ocean" -> "深海";
            case "minecraft:desert" -> "沙漠";
            case "minecraft:dripstone_caves" -> "溶洞";
            case "minecraft:end_barrens" -> "末地荒地";
            case "minecraft:end_highlands" -> "末地高地";
            case "minecraft:end_midlands" -> "末地内陆";
            case "minecraft:eroded_badlands" -> "风蚀恶地";
            case "minecraft:flower_forest" -> "繁花森林";
            case "minecraft:forest" -> "森林";
            case "minecraft:frozen_ocean" -> "冰冻海洋";
            case "minecraft:frozen_peaks" -> "冰封山峰";
            case "minecraft:frozen_river" -> "冰冻河流";
            case "minecraft:grove" -> "雪林";
            case "minecraft:ice_spikes" -> "冰刺平原";
            case "minecraft:jagged_peaks" -> "尖峭山峰";
            case "minecraft:jungle" -> "丛林";
            case "minecraft:lukewarm_ocean" -> "温水海洋";
            case "minecraft:lush_caves" -> "繁茂洞穴";
            case "minecraft:mangrove_swamp" -> "红树林沼泽";
            case "minecraft:meadow" -> "草甸";
            case "minecraft:mushroom_fields" -> "蘑菇岛";
            case "minecraft:nether_wastes" -> "下界荒地";
            case "minecraft:ocean" -> "海洋";
            case "minecraft:old_growth_birch_forest" -> "原始白桦森林";
            case "minecraft:old_growth_pine_taiga" -> "原始松木针叶林";
            case "minecraft:old_growth_spruce_taiga" -> "原始云杉针叶林";
            case "minecraft:pale_garden" -> "苍白花园";
            case "minecraft:plains" -> "平原";
            case "minecraft:river" -> "河流";
            case "minecraft:savanna" -> "热带草原";
            case "minecraft:savanna_plateau" -> "热带高原";
            case "minecraft:small_end_islands" -> "末地小型岛屿";
            case "minecraft:snowy_beach" -> "积雪沙滩";
            case "minecraft:snowy_plains" -> "雪原";
            case "minecraft:snowy_slopes" -> "积雪山坡";
            case "minecraft:snowy_taiga" -> "积雪针叶林";
            case "minecraft:soul_sand_valley" -> "灵魂沙峡谷";
            case "minecraft:sparse_jungle" -> "稀疏丛林";
            case "minecraft:stony_peaks" -> "裸岩山峰";
            case "minecraft:stony_shore" -> "石岸";
            case "minecraft:sunflower_plains" -> "向日葵平原";
            case "minecraft:swamp" -> "沼泽";
            case "minecraft:taiga" -> "针叶林";
            case "minecraft:the_end" -> "末地";
            case "minecraft:warm_ocean" -> "暖水海洋";
            case "minecraft:warped_forest" -> "诡异森林";
            case "minecraft:windswept_forest" -> "风袭森林";
            case "minecraft:windswept_gravelly_hills" -> "风袭沙砾丘陵";
            case "minecraft:windswept_hills" -> "风袭丘陵";
            case "minecraft:windswept_savanna" -> "风袭热带草原";
            case "minecraft:wooded_badlands" -> "疏林恶地";
            default -> safe(id).isBlank() ? "未知群系" : safe(id);
        };
    }

    private static String activityCategoryLabel(String category) {
        return switch (safe(category)) {
            case "item_give" -> "发物品";
            case "maintenance" -> "维护通知";
            case "exploration" -> "探索/副本";
            case "custom" -> "自由通知";
            default -> "集合活动";
        };
    }

    private static String taskStatusLabel(String status) {
        return switch (safe(status)) {
            case "voting" -> "确认中";
            case "completed" -> "已完成";
            case "ended" -> "已结束";
            default -> "进行中";
        };
    }

    private static boolean isHistoricalTask(ClientTask task) {
        return task != null && isHistoricalTaskStatus(task.status);
    }

    private static boolean isHistoricalTaskStatus(String status) {
        return "completed".equals(safe(status)) || "ended".equals(safe(status));
    }

    private static String taskVisibilityLabel(String visibility) {
        return "private".equals(safe(visibility)) ? "私人" : "公开";
    }

    private static boolean activityNeedsMeetingPoint(String category) {
        return switch (safe(category)) {
            case "item_give", "maintenance" -> false;
            default -> true;
        };
    }

    private static boolean activityUsesEndDate(String category) {
        return !"maintenance".equals(safe(category));
    }

    private static String defaultEndDateText() {
        return END_DATE_FORMAT.format(LocalDateTime.now().plusHours(1));
    }

    private static String defaultMaintenanceTimeText() {
        return END_DATE_FORMAT.format(LocalDateTime.now().plusMinutes(10));
    }

    private int maintenanceRemainingSeconds(ActiveActivity activity) {
        if (activity != null && activity.maintenanceRemainingSeconds > 0) {
            long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - activeActivityReceivedAtMillis) / 1000L);
            return Math.max(0, activity.maintenanceRemainingSeconds - (int) elapsedSeconds);
        }
        if (activity == null || activity.maintenanceEndsAtMillis <= 0L) {
            return 0;
        }
        long remainingMillis = activity.maintenanceEndsAtMillis - System.currentTimeMillis();
        return Math.max(0, (int) Math.ceil(remainingMillis / 1000.0D));
    }

    private static String formatCountdown(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        int hours = safeSeconds / 3600;
        int minutes = (safeSeconds % 3600) / 60;
        int remainingSeconds = safeSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, remainingSeconds);
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String minecraftTime(long timeOfDay) {
        long ticks = Math.floorMod(timeOfDay + 6000L, 24000L);
        long totalMinutes = ticks * 1440L / 24000L;
        return String.format(Locale.ROOT, "%02d:%02d", totalMinutes / 60L, totalMinutes % 60L);
    }

    private static String normalizeTitle(String value) {
        return value == null || value.isBlank() || "朋友服控制台".equals(value) || "小铭的控制台".equals(value) ? DEFAULT_TITLE : value;
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int maxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private enum Page {
        SETUP("setup", "初始化"),
        TELEPORT("teleport", "传送"),
        ADD_LOCATION("add_location", "新增公共传送点"),
        EDIT_LOCATION("edit_location", "编辑公共传送点"),
        COORDINATES("coordinates", "坐标"),
        COORDINATE_HUD_EDIT("coordinate_hud_edit", "编辑坐标HUD位置"),
        TASKS("tasks", "任务"),
        TASK_HISTORY("task_history", "历史任务"),
        TASK_DETAIL("task_detail", "任务详情"),
        CREATE_TASK("create_task", "发布任务"),
        EDIT_TASK("edit_task", "编辑任务"),
        TASK_HUD_EDIT("task_hud_edit", "编辑HUD位置"),
        ACTIVITY("activity", "活动"),
        STATUS("status", "状态"),
        ADMIN("admin", "服主管理"),
        ADMIN_TIME("admin_time", "时间管理"),
        ADMIN_WEATHER("admin_weather", "天气管理"),
        ADMIN_PLAYERS("admin_players", "玩家管理"),
        ADMIN_PLAYER_DETAIL("admin_player_detail", "玩家操作"),
        ADMIN_CLEANUP("admin_cleanup", "清理管理");

        private final String id;
        private final String label;

        Page(String id, String label) {
            this.id = id;
            this.label = label;
        }

        static Page fromId(String id) {
            for (Page page : values()) {
                if (page.id.equals(id)) {
                    return page;
                }
            }
            return TELEPORT;
        }

        boolean isAdminPage() {
            return this == ADMIN || this == ADMIN_TIME || this == ADMIN_WEATHER || this == ADMIN_PLAYERS || this == ADMIN_PLAYER_DETAIL || this == ADMIN_CLEANUP;
        }
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight, int navWidth, int contentX, int contentY, int contentWidth, int contentBottom) {
        int contentHeight() {
            return contentBottom - contentY;
        }

        int navHeight() {
            return contentHeight();
        }
    }

    private class LocationDraft {
        final TextInput name = new TextInput("新传送点", 40);
        final TextInput id = new TextInput("new_location", 64);
        final TextInput description = new TextInput("", 120);
        String world = "minecraft:overworld";
        final TextInput x = new TextInput("0", 24);
        final TextInput y = new TextInput("0", 24);
        final TextInput z = new TextInput("0", 24);
        final TextInput yaw = new TextInput("0", 16);
        final TextInput pitch = new TextInput("0", 16);
        boolean initialized;
        boolean idEditedManually;
        boolean dimensionDropdownOpen;
        String editingOriginalId = "";

        void ensureDefaults() {
            if (!initialized) {
                resetNew();
            }
        }

        void resetNew() {
            name.setSuggestedValue("新传送点");
            id.setSuggestedValue(makeId(name.value));
            description.setCommittedValue("");
            idEditedManually = false;
            dimensionDropdownOpen = false;
            editingOriginalId = "";
            resetToCurrentPlayer();
        }

        void resetToCurrentPlayer() {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                BlockPos pos = player.getBlockPos();
                world = clientDimensionId();
                x.setSuggestedValue(String.valueOf(pos.getX()));
                y.setSuggestedValue(String.valueOf(pos.getY()));
                z.setSuggestedValue(String.valueOf(pos.getZ()));
                yaw.setSuggestedValue(String.format(Locale.ROOT, "%.1f", player.getYaw()));
                pitch.setSuggestedValue(String.format(Locale.ROOT, "%.1f", player.getPitch()));
            }
            initialized = true;
        }

        void applyDelta(String argument) {
            String[] parts = safe(argument).split(":", 2);
            if (parts.length != 2) {
                return;
            }
            TextInput target = switch (parts[0]) {
                case "x" -> x;
                case "y" -> y;
                case "z" -> z;
                default -> null;
            };
            if (target == null) {
                return;
            }
            target.setCommittedValue(format(parseDouble(target.value, 0.0D) + parseDouble(parts[1], 0.0D)));
        }

        void setWorld(String worldId) {
            world = safe(worldId).isBlank() ? "minecraft:overworld" : worldId;
            dimensionDropdownOpen = false;
        }

        void load(LocationEntry location) {
            name.setCommittedValue(safe(location.name));
            id.setCommittedValue(safe(location.id));
            description.setCommittedValue(safe(location.description));
            world = safe(location.world).isBlank() ? "minecraft:overworld" : location.world;
            x.setCommittedValue(format(location.x));
            y.setCommittedValue(format(location.y));
            z.setCommittedValue(format(location.z));
            yaw.setCommittedValue(format(location.yaw));
            pitch.setCommittedValue(format(location.pitch));
            initialized = true;
            idEditedManually = true;
            dimensionDropdownOpen = false;
            editingOriginalId = safe(location.id);
        }

        LocationEntry toLocation() {
            LocationEntry location = new LocationEntry();
            location.name = name.value;
            location.id = id.value;
            location.description = description.value;
            location.world = world;
            location.x = parseDouble(x.value, Double.NaN);
            location.y = parseDouble(y.value, Double.NaN);
            location.z = parseDouble(z.value, Double.NaN);
            location.yaw = (float) parseDouble(yaw.value, Double.NaN);
            location.pitch = (float) parseDouble(pitch.value, Double.NaN);
            return location;
        }
    }

    private static class ActivityDraft {
        final TextInput title = new TextInput("集合啦", 40);
        final TextInput description = new TextInput("准备一起打活动，请大家到指定地点集合。", 160);
        final TextInput meetingPoint = new TextInput("当前位置", 80);
        final TextInput endDateText = new TextInput(defaultEndDateText(), 32);
        final TextInput itemId = new TextInput("minecraft:apple", 80);
        final TextInput itemCount = new TextInput("1", 4);
        final TextInput maintenanceTimeText = new TextInput(defaultMaintenanceTimeText(), 40);
        final TextInput maintenanceCountdownSeconds = new TextInput("600", 5);
        String category = "gathering";
        boolean needsTeleport = true;
        boolean hasEndDate;

        void reset() {
            applyTemplate("gathering");
            hasEndDate = false;
            endDateText.setSuggestedValue(defaultEndDateText());
            maintenanceTimeText.setSuggestedValue(defaultMaintenanceTimeText());
            maintenanceCountdownSeconds.setSuggestedValue("600");
        }

        void applyTemplate(String template) {
            category = switch (safe(template)) {
                case "item_give", "maintenance", "exploration", "custom" -> template;
                default -> "gathering";
            };
            hasEndDate = false;
            endDateText.setSuggestedValue(defaultEndDateText());
            switch (category) {
                case "item_give" -> {
                    title.setSuggestedValue("发物品啦");
                    description.setSuggestedValue("服务器给在线玩家发放活动物品，请检查背包。");
                    meetingPoint.setSuggestedValue("无需集合");
                    itemId.setSuggestedValue("minecraft:diamond");
                    itemCount.setSuggestedValue("1");
                    needsTeleport = false;
                }
                case "maintenance" -> {
                    title.setSuggestedValue("服务器维护通知");
                    description.setSuggestedValue("服务器即将进行维护，请大家尽快保存进度。");
                    meetingPoint.setSuggestedValue("无需集合");
                    maintenanceTimeText.setSuggestedValue(defaultMaintenanceTimeText());
                    maintenanceCountdownSeconds.setSuggestedValue("600");
                    needsTeleport = false;
                }
                case "exploration" -> {
                    title.setSuggestedValue("一起探索");
                    description.setSuggestedValue("准备一起探索或打副本，请大家到集合点。");
                    meetingPoint.setSuggestedValue("当前位置");
                    needsTeleport = true;
                }
                case "custom" -> {
                    title.setSuggestedValue("活动通知");
                    description.setSuggestedValue("请查看本次活动说明。");
                    meetingPoint.setSuggestedValue("当前位置");
                    needsTeleport = true;
                }
                default -> {
                    title.setSuggestedValue("集合啦");
                    description.setSuggestedValue("准备一起打活动，请大家到指定地点集合。");
                    meetingPoint.setSuggestedValue("当前位置");
                    needsTeleport = true;
                }
            }
        }

        ActivitySubmission toSubmittedActivity() {
            ActivitySubmission submission = new ActivitySubmission();
            boolean needsMeetingPoint = activityNeedsMeetingPoint(category);
            boolean usesEndDate = activityUsesEndDate(category);
            submission.title = title.value;
            submission.description = description.value;
            submission.meetingPoint = needsMeetingPoint ? meetingPoint.value : "";
            submission.category = category;
            submission.needsTeleport = needsMeetingPoint && needsTeleport;
            submission.hasEndDate = usesEndDate && hasEndDate;
            submission.endDateText = usesEndDate ? endDateText.value : "";
            submission.itemId = itemId.value;
            submission.itemCount = (int) parseDouble(itemCount.value, 1.0D);
            submission.maintenanceTimeText = maintenanceTimeText.value;
            submission.maintenanceCountdownSeconds = (int) parseDouble(maintenanceCountdownSeconds.value, 600.0D);
            return submission;
        }
    }

    private static class TaskDraft {
        final TextInput title = new TextInput("完成刷怪塔", 48);
        final TextInput description = new TextInput("一起完成这个服务器任务。", 180);
        final TextInput reward = new TextInput("", 120);
        boolean publicTask = true;
        boolean initialized;
        boolean canChangeVisibility = true;
        String editingTaskId = "";

        void ensureDefaults() {
            if (!initialized) {
                resetNew();
            }
        }

        void resetNew() {
            title.setSuggestedValue("完成刷怪塔");
            description.setSuggestedValue("一起完成这个服务器任务。");
            reward.setCommittedValue("");
            publicTask = true;
            canChangeVisibility = true;
            editingTaskId = "";
            initialized = true;
        }

        void load(ClientTask task) {
            title.setCommittedValue(safe(task.title));
            description.setCommittedValue(safe(task.description));
            reward.setCommittedValue(safe(task.reward));
            publicTask = !"private".equals(safe(task.visibility));
            canChangeVisibility = task.canChangeVisibility;
            editingTaskId = safe(task.id);
            initialized = true;
        }

        TaskSubmission toSubmission(boolean includeReward) {
            TaskSubmission submission = new TaskSubmission();
            submission.title = title.value;
            submission.description = description.value;
            submission.visibility = publicTask ? "public" : "private";
            submission.reward = "";
            return submission;
        }
    }

    private static class TaskSubmission {
        String title;
        String description;
        String visibility;
        String reward;
    }

    private static class ClientTask {
        String id;
        String title;
        String description;
        String visibility;
        String publisherName;
        String reward;
        String status;
        String[] participants;
        int participantCount;
        int voteCount;
        int voteThreshold;
        boolean viewerJoined;
        boolean viewerPublisher;
        boolean viewerVotedComplete;
        boolean canJoin;
        boolean canLeave;
        boolean canVoteComplete;
        boolean canEdit;
        boolean canChangeVisibility;
        boolean canEnd;
        boolean canReward;
        boolean canInvite;
    }

    private record TaskClickArea(String taskId, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        int top() {
            return y;
        }

        int bottom() {
            return y + height;
        }
    }

    private static class ButtonCursor {
        final int rowStart;
        final int right;
        int x;
        int y;

        ButtonCursor(int x, int y, int right) {
            this.rowStart = x;
            this.x = x;
            this.y = y;
            this.right = right;
        }
    }

    private static class TextInput {
        String value;
        String hint;
        final int maxLength;
        int x;
        int y;
        int width;
        int height;
        boolean focused;
        boolean clearOnFocus;

        TextInput(String value, int maxLength) {
            this.maxLength = maxLength;
            setSuggestedValue(value);
        }

        void setSuggestedValue(String value) {
            this.value = trimToMax(value);
            this.hint = this.value;
            this.clearOnFocus = !this.value.isBlank();
        }

        void setCommittedValue(String value) {
            this.value = trimToMax(value);
            this.hint = this.value;
            this.clearOnFocus = false;
        }

        void focus() {
            focused = true;
            if (clearOnFocus) {
                value = "";
                clearOnFocus = false;
            }
        }

        void blur() {
            focused = false;
        }

        void setBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        void render(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer) {
            int border = focused ? 0xFF7FC2FF : 0xFF55616B;
            context.fill(x, y, x + width, y + height, 0xAA111821);
            context.fill(x, y, x + width, y + 1, border);
            context.fill(x, y + height - 1, x + width, y + height, border);
            context.fill(x, y, x + 1, y + height, border);
            context.fill(x + width - 1, y, x + width, y + height, border);
            String display = value;
            int color = 0xFFFFFFFF;
            if (display.isEmpty() && !focused && hint != null && !hint.isBlank()) {
                display = hint;
                color = 0xFF8D9AA6;
            } else if (clearOnFocus) {
                color = 0xFF9FB0BF;
            }
            String visible = textRenderer.trimToWidth(display, Math.max(10, width - 12));
            context.drawText(textRenderer, Text.literal(visible + (focused ? "_" : "")), x + 5, y + 7, color, false);
        }

        boolean charTyped(CharInput input) {
            if (!input.isValidChar()) {
                return false;
            }
            String text = input.asString();
            if (text == null || text.isEmpty()) {
                return false;
            }
            beginUserInput();
            if (value.length() + text.length() > maxLength) {
                value += text.substring(0, Math.max(0, maxLength - value.length()));
            } else {
                value += text;
            }
            return true;
        }

        boolean keyPressed(KeyInput input) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                beginUserInput();
                if (!value.isEmpty()) {
                    value = value.substring(0, value.length() - 1);
                }
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_DELETE) {
                beginUserInput();
                value = "";
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                return true;
            }
            return false;
        }

        private void beginUserInput() {
            if (clearOnFocus) {
                value = "";
                clearOnFocus = false;
            }
        }

        private String trimToMax(String text) {
            String safeText = text == null ? "" : text;
            return safeText.length() <= maxLength ? safeText : safeText.substring(0, maxLength);
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        int top() {
            return y;
        }

        int bottom() {
            return y + height;
        }
    }

    private static class ClientStatus {
        int onlinePlayers;
        int maxPlayers;
        String[] playerNames;
        String dimension;
        int x;
        int y;
        int z;
        long timeOfDay;
        String weather;
        double mspt;
        double tps;
        ClientDeathPoint deathPoint;

        static ClientStatus empty() {
            return new ClientStatus();
        }
    }

    private static class ClientDeathPoint {
        String world;
        int x;
        int y;
        int z;
        long createdAtMillis;
    }

    private static class ActivitySubmission {
        String title;
        String description;
        String meetingPoint;
        String category;
        boolean needsTeleport;
        boolean hasEndDate;
        String endDateText;
        String itemId;
        int itemCount;
        String maintenanceTimeText;
        int maintenanceCountdownSeconds;
    }

    private static class ActiveActivity {
        String id;
        String initiator;
        String category;
        String title;
        String description;
        String meetingPoint;
        boolean needsTeleport;
        boolean hasEndDate;
        String endDateText;
        String itemId;
        int itemCount;
        String maintenanceTimeText;
        int maintenanceCountdownSeconds;
        long maintenanceEndsAtMillis;
        int maintenanceRemainingSeconds;
        boolean itemClaimedByViewer;
        String world;
        double x;
        double y;
        double z;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(safe(value).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
