package com.xm6680.friendservermenu.client.gui;

import com.xm6680.friendservermenu.FriendServerMenuMod;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FriendMenuScreen extends Screen {
    private static final String DEFAULT_TITLE = "小铭的服务器菜单";
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final DateTimeFormatter END_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<MenuButton> buttons = new ArrayList<>();
    private final List<TextInput> visibleInputs = new ArrayList<>();
    private final LocationDraft locationDraft = new LocationDraft();
    private final ActivityDraft activityDraft = new ActivityDraft();
    private final TaskDraft taskDraft = new TaskDraft();

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
    private boolean taskHudDragging;
    private int currentPositionNoticeTicks;
    private String selectedAdminPlayer = "";

    public FriendMenuScreen(String titleText, boolean canUseAdmin, String initialPage) {
        super(Text.literal(normalizeTitle(titleText)));
        this.titleText = normalizeTitle(titleText);
        this.canUseAdmin = canUseAdmin;
        this.selectedPage = Page.fromId(initialPage);
        if (this.selectedPage.isAdminPage() && !canUseAdmin) {
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
        visibleInputs.clear();

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
            case ADD_LOCATION -> renderLocationFormPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth(), false);
            case EDIT_LOCATION -> renderLocationFormPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth(), true);
            case COORDINATES -> renderCoordinatePage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case TASKS -> renderTasksPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth());
            case CREATE_TASK -> renderTaskFormPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth(), false);
            case EDIT_TASK -> renderTaskFormPage(context, layout.contentX(), layout.contentY() - contentScroll, layout.contentWidth(), true);
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
        if (ClientTaskHud.isEditMode()) {
            ClientTaskHud.renderPreview(context, textRenderer, width, height);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        Layout layout = layout();

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
            } else {
                setFocusedInput(null);
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = layout();
        if (ClientTaskHud.isEditMode() && ClientTaskHud.contains(mouseX, mouseY)) {
            ClientTaskHud.resizeBy(verticalAmount > 0 ? 1 : -1, width, height);
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
        return super.mouseDragged(click, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(Click click) {
        taskHudDragging = false;
        return super.mouseReleased(click);
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
            String description = safe(location.description) + "  " + dimensionName(location.world) + "  X:" + format(location.x) + " Y:" + format(location.y) + " Z:" + format(location.z);
            if (contentWidth >= 248) {
                int actionWidth = canUseAdmin ? 98 : 50;
                int cardWidth = contentWidth - actionWidth;
                addButton(safe(location.name), description, "teleport_location", safe(location.id), false, x, y, cardWidth, cardHeight);
                addButton("编辑", "", "open_edit_location", safe(location.id), true, x + cardWidth + 6, y, 42, 20);
                if (canUseAdmin) {
                    addButton("删除", "", "delete_location", safe(location.id), true, x + cardWidth + 6, y + 24, 42, 20);
                }
                y += cardHeight + 7;
            } else {
                addButton(safe(location.name), description, "teleport_location", safe(location.id), false, x, y, contentWidth - 6, cardHeight);
                y += cardHeight + 6;
                addButton("编辑", "", "open_edit_location", safe(location.id), true, x, y, 44, 22);
                if (canUseAdmin) {
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

        y = addInput(context, locationDraft.name, "传送点名称", x, y, contentWidth);
        if (canUseAdmin) {
            y = addInput(context, locationDraft.id, "传送点 ID", x, y, contentWidth);
        } else {
            context.drawText(textRenderer, Text.literal("传送点 ID 将由服务器自动生成。"), x, y + 4, 0xFF9FB0BF, false);
            y += 24;
        }
        y = addInput(context, locationDraft.description, "描述", x, y, contentWidth);
        y = addDimensionDropdown(context, x, y, contentWidth);

        y = addCoordinateInput(context, locationDraft.x, "X 坐标", "x", x, y, contentWidth);
        y = addCoordinateInput(context, locationDraft.y, "Y 坐标", "y", x, y, contentWidth);
        y = addCoordinateInput(context, locationDraft.z, "Z 坐标", "z", x, y, contentWidth);

        int half = Math.max(80, (contentWidth - 8) / 2);
        y = addInputRow(context, locationDraft.yaw, "yaw", x, y, half, locationDraft.pitch, "pitch", x + half + 8, half);

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
        context.drawText(textRenderer, Text.literal("当前位置"), x, y, 0xFFFFFFFF, true);
        context.drawText(textRenderer, Text.literal(clientCoordinates()), x, y + 18, 0xFFDDE7F0, false);
        y += 46;

        int buttonWidth = Math.max(68, Math.min(84, (contentWidth - 12) / 3));
        int buttonHeight = 26;
        int currentX = x;
        int currentY = y;
        String[][] actions = {
                {"复制坐标", "copy_coords", "复制当前坐标和维度到系统剪贴板。"},
                {"公开坐标", "send_coords_public", "把当前坐标发到聊天栏，并附带绿色传送按钮。"},
                {"私发坐标", "send_coords_private", "只把当前坐标发给自己，方便保存或查看。"}
        };
        String hoverHint = "";
        for (String[] action : actions) {
            if (currentX + buttonWidth > x + contentWidth) {
                currentX = x;
                currentY += buttonHeight + 6;
            }
            if (activeMouseX >= currentX && activeMouseX < currentX + buttonWidth && activeMouseY >= currentY && activeMouseY < currentY + buttonHeight) {
                hoverHint = action[2];
            }
            addButton(action[0], "", action[1], "", "copy_coords".equals(action[1]), currentX, currentY, buttonWidth, buttonHeight);
            currentX += buttonWidth + 6;
        }
        int bottom = currentY + buttonHeight;
        if (!hoverHint.isBlank()) {
            drawTextLine(context, hoverHint, x, bottom + 10, contentWidth);
            bottom += 26;
        }
        return bottom + 8;
    }

    private int renderTasksPage(DrawContext context, int x, int y, int contentWidth) {
        int buttonWidth = Math.max(72, Math.min(92, (contentWidth - 12) / 3));
        addButton("发布任务", "", "open_create_task", "", true, x, y, buttonWidth, 24);
        addButton(ClientTaskHud.isEnabled() ? "HUD：开" : "HUD：关", "", "task_hud_toggle", "", true, x + buttonWidth + 6, y, buttonWidth, 24);
        addButton(ClientTaskHud.isEditMode() ? "完成布局" : "拖动HUD", "", "task_hud_edit", "", true, x + (buttonWidth + 6) * 2, y, buttonWidth, 24);
        y += 32;

        if (ClientTaskHud.isEditMode()) {
            addButton("缩小HUD", "", "task_hud_smaller", "", true, x, y, buttonWidth, 22);
            addButton("放大HUD", "", "task_hud_larger", "", true, x + buttonWidth + 6, y, buttonWidth, 22);
            drawTextLine(context, "拖动屏幕上的任务 HUD 调整位置，滚轮或按钮调整大小。", x, y + 30, contentWidth);
            y += 50;
        }

        if (tasks.isEmpty()) {
            drawTextLine(context, "暂无可见任务。公开任务会显示在这里，私人任务只对成员可见。", x, y, contentWidth);
            return y + 24;
        }

        for (ClientTask task : tasks) {
            y = renderTaskCard(context, task, x, y, contentWidth);
            y += 8;
        }
        return y;
    }

    private int renderTaskCard(DrawContext context, ClientTask task, int x, int y, int contentWidth) {
        int cardHeight = contentWidth < 260 ? 166 : 122;
        int right = x + contentWidth - 6;
        context.fill(x, y, right, y + cardHeight, 0x66303A46);
        context.fill(x, y, right, y + 1, 0xFF4C5A66);
        context.fill(x, y + cardHeight - 1, right, y + cardHeight, 0xFF4C5A66);
        context.fill(x, y, x + 1, y + cardHeight, 0xFF4C5A66);
        context.fill(right - 1, y, right, y + cardHeight, 0xFF4C5A66);

        int textX = x + 8;
        int lineY = y + 8;
        drawTextLine(context, task.title, textX, lineY, contentWidth - 18);
        lineY += 14;
        drawTextLine(context, "状态：" + taskStatusLabel(task.status) + "  可见：" + taskVisibilityLabel(task.visibility) + "  发布者：" + textOr(task.publisherName, "未知"), textX, lineY, contentWidth - 18);
        lineY += 14;
        drawTextLine(context, "成员：" + task.participantCount + "  投票：" + task.voteCount + "/" + task.voteThreshold, textX, lineY, contentWidth - 18);
        lineY += 14;
        if (!safe(task.reward).isBlank()) {
            drawTextLine(context, "奖励：" + task.reward, textX, lineY, contentWidth - 18);
            lineY += 14;
        }
        if (!safe(task.description).isBlank()) {
            drawTextLine(context, task.description, textX, lineY, contentWidth - 18);
        }

        ButtonCursor cursor = new ButtonCursor(textX, y + cardHeight - (contentWidth < 260 ? 55 : 29), right);
        int smallWidth = 50;
        if (task.canJoin) {
            addTaskCardButton("加入", "task_join", task.id, cursor, smallWidth);
        }
        if (task.canLeave) {
            addTaskCardButton("退出", "task_leave", task.id, cursor, smallWidth);
        }
        if (task.canVoteComplete) {
            addTaskCardButton("voting".equals(task.status) ? "投票通过" : "已完成", "task_vote_complete", task.id, cursor, 70);
        }
        if (task.viewerJoined) {
            addTaskCardButton(ClientTaskHud.isSelectedTask(task.id) ? "HUD中" : "HUD显示", "task_hud_select", task.id, cursor, 62);
        }
        if (task.canEdit) {
            addTaskCardButton("编辑", "open_edit_task", task.id, cursor, smallWidth);
        }
        if (task.canEnd) {
            addTaskCardButton("结束", "task_end", task.id, cursor, smallWidth);
        }
        return y + cardHeight;
    }

    private void addTaskCardButton(String title, String actionId, String argument, ButtonCursor cursor, int width) {
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

        if (canUseAdmin) {
            y = addInput(context, taskDraft.reward, "任务奖励（仅 OP 可改）", x, y, contentWidth);
        } else if (!safe(taskDraft.reward.value).isBlank()) {
            drawTextLine(context, "奖励：" + taskDraft.reward.value, x, y, contentWidth);
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

    private void runButton(MenuButton button) {
        switch (button.actionId()) {
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
            case "open_create_task" -> {
                taskDraft.resetNew();
                taskFormMessage = "";
                selectPage(Page.CREATE_TASK);
            }
            case "open_edit_task" -> openEditTask(button.argument());
            case "back_to_tasks" -> selectPage(Page.TASKS);
            case "task_toggle_visibility" -> taskDraft.publicTask = !taskDraft.publicTask;
            case "task_submit_create" -> submitTask(false);
            case "task_submit_edit" -> submitTask(true);
            case "task_join" -> ClientPlayNetworking.send(TaskActionPayload.simple("join", button.argument()));
            case "task_leave" -> ClientPlayNetworking.send(TaskActionPayload.simple("leave", button.argument()));
            case "task_vote_complete" -> ClientPlayNetworking.send(TaskActionPayload.simple("vote_complete", button.argument()));
            case "task_end" -> ClientPlayNetworking.send(TaskActionPayload.simple("end", button.argument()));
            case "task_hud_toggle" -> ClientTaskHud.toggleEnabled();
            case "task_hud_edit" -> ClientTaskHud.setEditMode(!ClientTaskHud.isEditMode());
            case "task_hud_smaller" -> ClientTaskHud.resizeBy(-1, width, height);
            case "task_hud_larger" -> ClientTaskHud.resizeBy(1, width, height);
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
                    activityDraft.endDateText.value = defaultEndDateText();
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
                MinecraftClient.getInstance().keyboard.setClipboard(clientCoordinates());
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player != null) {
                    player.sendMessage(Text.literal("坐标已复制"), true);
                }
                close();
            }
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
        selectedPage = page;
        contentScroll = 0;
        setFocusedInput(null);
        if (page != Page.ADD_LOCATION && page != Page.EDIT_LOCATION) {
            clearLocationFormMessage();
        }
        if (page != Page.CREATE_TASK && page != Page.EDIT_TASK) {
            taskFormMessage = "";
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
        List<Page> pages = new ArrayList<>(List.of(Page.TELEPORT, Page.COORDINATES, Page.TASKS, Page.ACTIVITY, Page.STATUS));
        if (canUseAdmin) {
            pages.add(Page.ADMIN);
        }
        return pages;
    }

    private Page selectedNavPage() {
        if (selectedPage == Page.ADD_LOCATION || selectedPage == Page.EDIT_LOCATION) {
            return Page.TELEPORT;
        }
        if (selectedPage == Page.CREATE_TASK || selectedPage == Page.EDIT_TASK) {
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
        return "[" + clientDimensionName() + "] X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ();
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
            focusedInput.focused = false;
        }
        focusedInput = input;
        if (focusedInput != null) {
            focusedInput.focused = true;
        }
    }

    private void onFocusedInputChanged() {
        if (focusedInput == locationDraft.name && !locationDraft.idEditedManually) {
            locationDraft.id.value = makeId(locationDraft.name.value);
        } else if (focusedInput == locationDraft.id) {
            locationDraft.idEditedManually = true;
        }
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
            case "voting" -> "投票中";
            case "completed" -> "已完成";
            default -> "进行中";
        };
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
        TELEPORT("teleport", "传送"),
        ADD_LOCATION("add_location", "新增公共传送点"),
        EDIT_LOCATION("edit_location", "编辑公共传送点"),
        COORDINATES("coordinates", "坐标"),
        TASKS("tasks", "任务"),
        CREATE_TASK("create_task", "发布任务"),
        EDIT_TASK("edit_task", "编辑任务"),
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
            name.value = "新传送点";
            id.value = makeId(name.value);
            description.value = "";
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
                x.value = String.valueOf(pos.getX());
                y.value = String.valueOf(pos.getY());
                z.value = String.valueOf(pos.getZ());
                yaw.value = String.format(Locale.ROOT, "%.1f", player.getYaw());
                pitch.value = String.format(Locale.ROOT, "%.1f", player.getPitch());
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
            target.value = format(parseDouble(target.value, 0.0D) + parseDouble(parts[1], 0.0D));
        }

        void setWorld(String worldId) {
            world = safe(worldId).isBlank() ? "minecraft:overworld" : worldId;
            dimensionDropdownOpen = false;
        }

        void load(LocationEntry location) {
            name.value = safe(location.name);
            id.value = safe(location.id);
            description.value = safe(location.description);
            world = safe(location.world).isBlank() ? "minecraft:overworld" : location.world;
            x.value = format(location.x);
            y.value = format(location.y);
            z.value = format(location.z);
            yaw.value = format(location.yaw);
            pitch.value = format(location.pitch);
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
            endDateText.value = defaultEndDateText();
            maintenanceTimeText.value = defaultMaintenanceTimeText();
            maintenanceCountdownSeconds.value = "600";
        }

        void applyTemplate(String template) {
            category = switch (safe(template)) {
                case "item_give", "maintenance", "exploration", "custom" -> template;
                default -> "gathering";
            };
            hasEndDate = false;
            endDateText.value = defaultEndDateText();
            switch (category) {
                case "item_give" -> {
                    title.value = "发物品啦";
                    description.value = "服务器给在线玩家发放活动物品，请检查背包。";
                    meetingPoint.value = "无需集合";
                    itemId.value = "minecraft:diamond";
                    itemCount.value = "1";
                    needsTeleport = false;
                }
                case "maintenance" -> {
                    title.value = "服务器维护通知";
                    description.value = "服务器即将进行维护，请大家尽快保存进度。";
                    meetingPoint.value = "无需集合";
                    maintenanceTimeText.value = defaultMaintenanceTimeText();
                    maintenanceCountdownSeconds.value = "600";
                    needsTeleport = false;
                }
                case "exploration" -> {
                    title.value = "一起探索";
                    description.value = "准备一起探索或打副本，请大家到集合点。";
                    meetingPoint.value = "当前位置";
                    needsTeleport = true;
                }
                case "custom" -> {
                    title.value = "活动通知";
                    description.value = "请查看本次活动说明。";
                    meetingPoint.value = "当前位置";
                    needsTeleport = true;
                }
                default -> {
                    title.value = "集合啦";
                    description.value = "准备一起打活动，请大家到指定地点集合。";
                    meetingPoint.value = "当前位置";
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
            title.value = "完成刷怪塔";
            description.value = "一起完成这个服务器任务。";
            reward.value = "";
            publicTask = true;
            canChangeVisibility = true;
            editingTaskId = "";
            initialized = true;
        }

        void load(ClientTask task) {
            title.value = safe(task.title);
            description.value = safe(task.description);
            reward.value = safe(task.reward);
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
            submission.reward = includeReward ? reward.value : "";
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
        final int maxLength;
        int x;
        int y;
        int width;
        int height;
        boolean focused;

        TextInput(String value, int maxLength) {
            this.value = value;
            this.maxLength = maxLength;
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
            String visible = textRenderer.trimToWidth(value, Math.max(10, width - 12));
            context.drawText(textRenderer, Text.literal(visible + (focused ? "_" : "")), x + 5, y + 7, 0xFFFFFFFF, false);
        }

        boolean charTyped(CharInput input) {
            if (!input.isValidChar()) {
                return false;
            }
            String text = input.asString();
            if (text == null || text.isEmpty()) {
                return false;
            }
            if (value.length() + text.length() > maxLength) {
                value += text.substring(0, Math.max(0, maxLength - value.length()));
            } else {
                value += text;
            }
            return true;
        }

        boolean keyPressed(KeyInput input) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!value.isEmpty()) {
                    value = value.substring(0, value.length() - 1);
                }
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_DELETE) {
                value = "";
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                return true;
            }
            return false;
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

        static ClientStatus empty() {
            return new ClientStatus();
        }
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
