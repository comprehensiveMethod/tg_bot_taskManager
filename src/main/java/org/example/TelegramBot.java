package org.example;

import org.example.models.Category;
import org.example.models.Task;
import org.example.models.TaskStatus;
import org.example.services.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private TaskService taskService;

    private final String botToken;
    private final String botUsername;

    // users States + user Data
    private Map<Long, UserState> userStates = new HashMap<>();
    private Map<Long, TaskCreationData> taskCreationData = new HashMap<>();
    private Map<Long, Long> editingTasks = new HashMap<>();


    //TODO добавить EDITING_TASK_CATEGORY и соответствующий хендлер
    private enum UserState {
        MAIN_MENU,
        ADDING_TASK_NAME,
        ADDING_TASK_DESCRIPTION,
        ADDING_TASK_DEADLINE,
        EDITING_TASK_NAME,
        EDITING_TASK_DESCRIPTION,
        EDITING_TASK_DEADLINE
    }

    private static class TaskCreationData {
        String name;
        String description;
        Date deadline;
        TaskStatus status = TaskStatus.BACKLOG;
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}");

    public TelegramBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.name}") String botUsername) {
        this.botToken = botToken;
        this.botUsername = botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            Long userId = update.getMessage().getFrom().getId();

            if (messageText.equals("/start")) {
                sendWelcomeMessage(chatId);
                userStates.put(userId, UserState.MAIN_MENU);
            } else {
                handleUserInput(messageText, chatId, userId);
            }
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        try {
            if (callbackData.startsWith("complete_")) {
                Long taskId = Long.parseLong(callbackData.replace("complete_", ""));
                completeTask(chatId, userId, taskId, messageId);

            } else if (callbackData.startsWith("edit_")) {
                Long taskId = Long.parseLong(callbackData.replace("edit_", ""));
                startEditingTask(chatId, userId, taskId);

            } else if (callbackData.startsWith("status_")) {
                String[] parts = callbackData.split("_");
                if (parts.length >= 3) {
                    Long taskId = Long.parseLong(parts[1]);
                    String statusStr = parts[2];

                    TaskStatus newStatus;
                    try {
                        if ("IN".equals(statusStr)) {
                            newStatus = TaskStatus.IN_PROGRESS;
                        } else if ("BACKLOG".equalsIgnoreCase(statusStr)) {
                            newStatus = TaskStatus.BACKLOG;
                        } else if ("DONE".equalsIgnoreCase(statusStr)) {
                            newStatus = TaskStatus.DONE;
                        } else {
                            newStatus = TaskStatus.valueOf(statusStr.toUpperCase());
                        }

                        changeTaskStatus(chatId, userId, taskId, newStatus, messageId);
                    } catch (IllegalArgumentException e) {
                        sendMessage(chatId, "❌ Неизвестный статус: " + statusStr);
                    }
                }

            } else if (callbackData.startsWith("delete_")) {
                Long taskId = Long.parseLong(callbackData.replace("delete_", ""));
                deleteTask(chatId, userId, taskId, messageId);

            } else if (callbackData.equals("back_to_tasks")) {
                showUserTasks(chatId, userId);

            } else if (callbackData.equals("back_to_main")) {
                sendWelcomeMessage(chatId);
                userStates.put(userId, UserState.MAIN_MENU);

            } else if (callbackData.equals("separator")) {
                sendMessage(chatId, "А не надо на сепаратор кликать\uD83D\uDE04");
                userStates.put(userId, UserState.MAIN_MENU);
            }

            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            execute(answer);

        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Произошла ошибка при обработке запроса");
        }
    }

    private void handleUserInput(String messageText, Long chatId, Long userId) {
        UserState currentState = userStates.getOrDefault(userId, UserState.MAIN_MENU);

        switch (currentState) {
            case MAIN_MENU:
                handleMainMenu(messageText, chatId, userId);
                break;
            case ADDING_TASK_NAME:
                handleTaskNameInput(messageText, chatId, userId);
                break;
            case ADDING_TASK_DESCRIPTION:
                handleTaskDescriptionInput(messageText, chatId, userId);
                break;
            case ADDING_TASK_DEADLINE:
                handleTaskDeadlineInput(messageText, chatId, userId);
                break;
            case EDITING_TASK_NAME:
                handleEditTaskNameInput(messageText, chatId, userId);
                break;
            case EDITING_TASK_DESCRIPTION:
                handleEditTaskDescriptionInput(messageText, chatId, userId);
                break;
            case EDITING_TASK_DEADLINE:
                handleEditTaskDeadlineInput(messageText, chatId, userId);
                break;
        }
    }

    private void handleMainMenu(String messageText, Long chatId, Long userId) {
        switch (messageText) {
            case "📝 Добавить задачу":
                startAddingTask(chatId, userId);
                break;

            case "📋 Мои задачи":
                showUserTasks(chatId, userId);
                break;

            case "⏰ Ближайшие дедлайны":
                showUpcomingDeadlines(chatId, userId);
                break;

            case "✅ Выполненные":
                showCompletedTasks(chatId, userId);
                break;

            case "🔄 В процессе":
                showInProgressTasks(chatId, userId);
                break;

            case "📥 Бэклог":
                showBacklogTasks(chatId, userId);
                break;

            case "⚙️ Настройки":
                showSettings(chatId);
                break;

            case "❓ Помощь":
                showHelp(chatId);
                break;

            case "🔙 Назад":
                sendWelcomeMessage(chatId);
                userStates.put(userId, UserState.MAIN_MENU);
                break;

            default:
                sendMessage(chatId, "Используйте кнопки меню 👆");
        }
    }

    private void startAddingTask(Long chatId, Long userId) {
        taskCreationData.put(userId, new TaskCreationData());
        userStates.put(userId, UserState.ADDING_TASK_NAME);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Введите название задачи:");
        message.setReplyMarkup(createBackKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleTaskNameInput(String taskName, Long chatId, Long userId) {
        if (taskName.equals("🔙 Назад")) {
            sendWelcomeMessage(chatId);
            userStates.put(userId, UserState.MAIN_MENU);
            taskCreationData.remove(userId);
            return;
        }

        TaskCreationData data = taskCreationData.get(userId);
        data.name = taskName;
        userStates.put(userId, UserState.ADDING_TASK_DESCRIPTION);

        sendMessage(chatId, "Теперь введите описание задачи:");
    }

    private void handleTaskDescriptionInput(String description, Long chatId, Long userId) {
        if (description.equals("🔙 Назад")) {
            userStates.put(userId, UserState.ADDING_TASK_NAME);
            sendMessage(chatId, "Введите название задачи:");
            return;
        }

        TaskCreationData data = taskCreationData.get(userId);
        data.description = description;
        userStates.put(userId, UserState.ADDING_TASK_DEADLINE);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Введите дедлайн в формате: ДД.ММ.ГГГГ ЧЧ:MM\nНапример: 25.12.2025 15:30\n\nИли отправьте 'нет' если дедлайн не нужен");
        message.setReplyMarkup(createBackKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleTaskDeadlineInput(String deadlineInput, Long chatId, Long userId) {
        if (deadlineInput.equals("🔙 Назад")) {
            userStates.put(userId, UserState.ADDING_TASK_DESCRIPTION);
            sendMessage(chatId, "Введите описание задачи:");
            return;
        }

        TaskCreationData data = taskCreationData.get(userId);

        try {
            if (deadlineInput.equalsIgnoreCase("нет")) {
                data.deadline = null;
            } else if (DATE_PATTERN.matcher(deadlineInput).matches()) {
                data.deadline = DATE_FORMAT.parse(deadlineInput);

                if (data.deadline.before(new Date())) {
                    sendMessage(chatId, "❌ Дата не может быть в прошлом. Введите корректную дату:");
                    return;
                }
            } else {
                sendMessage(chatId, "❌ Неверный формат даты. Используйте: ДД.ММ.ГГГГ ЧЧ:MM\nНапример: 25.12.2025 15:30");
                return;
            }

            Task task = taskService.createTask(
                    data.name,
                    data.description,
                    userId,
                    data.status,
                    data.deadline,
                    Category.ANALYTICS
            );

            String taskInfo = "✅ Задача создана!\n\n" +
                    "📝 Название: " + task.getName() + "\n" +
                    "📋 Описание: " + task.getDescription() + "\n" +
                    "📊 Статус: " + task.getTaskStatus() + "\n" +
                    "📊 Категория: " + task.getCategory() + "\n";

            if (task.getDeadlineTime() != null) {
                taskInfo += "⏰ Дедлайн: " + DATE_FORMAT.format(task.getDeadlineTime()) + "\n";
            }

            taskInfo += "🆔 ID: " + task.getId();

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(taskInfo);
            message.setReplyMarkup(createMainKeyboard());

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

            userStates.put(userId, UserState.MAIN_MENU);
            taskCreationData.remove(userId);

        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при создании задачи. Попробуйте еще раз.");
            e.printStackTrace();
        }
    }

    private void completeTask(Long chatId, Long userId, Long taskId, Integer messageId) {
        try {
            Task task = taskService.getTaskById(taskId).get(); //TODO заменить get на orElse
            if (task.getUserOwnerId().equals(userId)) {
                task.setTaskStatus(TaskStatus.DONE);
                taskService.updateTask(task);

                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId.toString());
                editMessage.setMessageId(messageId);
                editMessage.setText("✅ Задача '" + task.getName() + "' выполнена!");
                editMessage.setReplyMarkup(createBackToTasksKeyboard());
                execute(editMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Ошибка при выполнении задачи");
        }
    }

    private void changeTaskStatus(Long chatId, Long userId, Long taskId, TaskStatus newStatus, Integer messageId) {
        try {
            Task task = taskService.getTaskById(taskId).get();
            if (task != null && task.getUserOwnerId().equals(userId)) {
                task.setTaskStatus(newStatus);
                taskService.updateTask(task);

                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId.toString());
                editMessage.setMessageId(messageId);
                editMessage.setText("📊 Статус задачи '" + task.getName() + "' изменен на: " + newStatus);
                editMessage.setReplyMarkup(createBackToTasksKeyboard());
                execute(editMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Ошибка при изменении статуса");
        }
    }

    private void deleteTask(Long chatId, Long userId, Long taskId, Integer messageId) {
        try {
            Task task = taskService.getTaskById(taskId).get();
            if (task != null && task.getUserOwnerId().equals(userId)) {
                taskService.deleteTask(taskId);

                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId.toString());
                editMessage.setMessageId(messageId);
                editMessage.setText("🗑️ Задача '" + task.getName() + "' удалена!");
                editMessage.setReplyMarkup(createBackToTasksKeyboard());
                execute(editMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Ошибка при удалении задачи");
        }
    }

    private void startEditingTask(Long chatId, Long userId, Long taskId) {
        editingTasks.put(userId, taskId);
        userStates.put(userId, UserState.EDITING_TASK_NAME);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Введите новое название задачи:");
        message.setReplyMarkup(createBackKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleEditTaskNameInput(String newName, Long chatId, Long userId) {
        if (newName.equals("🔙 Назад")) {
            showUserTasks(chatId, userId);
            userStates.put(userId, UserState.MAIN_MENU);
            editingTasks.remove(userId);
            return;
        }

        try {
            Long taskId = editingTasks.get(userId);
            Task task = taskService.getTaskById(taskId).get();
            if (task != null && task.getUserOwnerId().equals(userId)) {
                task.setName(newName);
                taskService.updateTask(task);

                userStates.put(userId, UserState.EDITING_TASK_DESCRIPTION);
                sendMessage(chatId, "Название обновлено! Теперь введите новое описание:");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Ошибка при обновлении названия");
        }
    }

    private void handleEditTaskDescriptionInput(String newDescription, Long chatId, Long userId) {
        if (newDescription.equals("🔙 Назад")) {
            userStates.put(userId, UserState.EDITING_TASK_NAME);
            sendMessage(chatId, "Введите новое название задачи:");
            return;
        }

        try {
            Long taskId = editingTasks.get(userId);
            Task task = taskService.getTaskById(taskId).get();
            if (task.getUserOwnerId().equals(userId)) {
                task.setDescription(newDescription);
                taskService.updateTask(task);

                userStates.put(userId, UserState.EDITING_TASK_DEADLINE);
                sendMessage(chatId, "Описание обновлено! Теперь введите новый дедлайн или 'нет' для удаления:");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Ошибка при обновлении описания");
        }
    }

    private void handleEditTaskDeadlineInput(String deadlineInput, Long chatId, Long userId) {
        if (deadlineInput.equals("🔙 Назад")) {
            userStates.put(userId, UserState.EDITING_TASK_DESCRIPTION);
            sendMessage(chatId, "Введите новое описание задачи:");
            return;
        }

        try {
            Long taskId = editingTasks.get(userId);
            Task task = taskService.getTaskById(taskId).get();
            if (task.getUserOwnerId().equals(userId)) {
                if (deadlineInput.equalsIgnoreCase("нет")) {
                    task.setDeadlineTime(null);
                } else if (DATE_PATTERN.matcher(deadlineInput).matches()) {
                    Date newDeadline = DATE_FORMAT.parse(deadlineInput);
                    if (newDeadline.before(new Date())) {
                        sendMessage(chatId, "❌ Дата не может быть в прошлом. Введите корректную дату:");
                        return;
                    }
                    task.setDeadlineTime(newDeadline);
                } else {
                    sendMessage(chatId, "❌ Неверный формат даты. Используйте: ДД.ММ.ГГГГ ЧЧ:MM");
                    return;
                }

                taskService.updateTask(task);
                sendMessage(chatId, "✅ Задача полностью обновлена!");
                showUserTasks(chatId, userId);

                userStates.put(userId, UserState.MAIN_MENU);
                editingTasks.remove(userId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Ошибка при обновлении дедлайна");
        }
    }

    private void showUpcomingDeadlines(Long chatId, Long userId) {
        Date threeDaysLater = new Date(System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000);
        List<Task> upcomingTasks = taskService.getTaskListByUser(userId).stream()
                .filter(task -> task.getDeadlineTime() != null && task.getDeadlineTime().before(threeDaysLater))
                .sorted(Comparator.comparing(Task::getDeadlineTime))
                .collect(Collectors.toList());

        if (upcomingTasks.isEmpty()) {
            sendMessage(chatId, "🎉 Нет задач с ближайшими дедлайнами!");
            return;
        }

        StringBuilder messageText = new StringBuilder("⏰ Ближайшие дедлайны:\n\n");

        for (Task task : upcomingTasks) {
            long hoursLeft = (task.getDeadlineTime().getTime() - System.currentTimeMillis()) / (60 * 60 * 1000);
            String timeLeft = hoursLeft < 24 ? "(" + hoursLeft + " часов)" : "(" + (hoursLeft / 24) + " дней)";

            messageText.append("• ").append(task.getName())
                    .append(" - ").append(DATE_FORMAT.format(task.getDeadlineTime()))
                    .append(" ").append(timeLeft)
                    .append("\n");
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText.toString());
        message.setReplyMarkup(createMainKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showUserTasks(Long chatId, Long userId) {
        List<Task> tasks = taskService.getTaskListByUser(userId);

        if (tasks.isEmpty()) {
            sendMessage(chatId, "📋 У вас пока нет задач");
            return;
        }

        StringBuilder tasksText = new StringBuilder("📋 Ваши задачи:\n\n");

        for (Task task : tasks) {
            tasksText.append("• ").append(task.getName())
                    .append(" [").append(task.getTaskStatus()).append("]");

            if (task.getDeadlineTime() != null) {
                tasksText.append(" - ⏰ ").append(DATE_FORMAT.format(task.getDeadlineTime()));
            }

            tasksText.append("\n");
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(tasksText.toString());
        message.setReplyMarkup(createTasksKeyboard(tasks));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showCompletedTasks(Long chatId, Long userId) {
        List<Task> tasks = taskService.getTaskByUserIdAndStatus(userId, TaskStatus.DONE);

        StringBuilder tasksText = new StringBuilder("✅ Выполненные задачи:\n\n");

        if (tasks.isEmpty()) {
            tasksText.append("Нет выполненных задач");
        } else {
            for (Task task : tasks) {
                tasksText.append("• ").append(task.getName()).append(" ✓\n");
            }
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(tasksText.toString());
        message.setReplyMarkup(createMainKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showInProgressTasks(Long chatId, Long userId) {
        List<Task> tasks = taskService.getTaskByUserIdAndStatus(userId, TaskStatus.IN_PROGRESS);

        StringBuilder tasksText = new StringBuilder("🔄 Задачи в процессе:\n\n");

        if (tasks.isEmpty()) {
            tasksText.append("Нет задач в процессе выполнения");
        } else {
            for (Task task : tasks) {
                tasksText.append("• ").append(task.getName()).append("\n");
            }
        }

        sendMessage(chatId, tasksText.toString());
    }

    private void showBacklogTasks(Long chatId, Long userId) {
        List<Task> tasks = taskService.getTaskByUserIdAndStatus(userId, TaskStatus.BACKLOG);

        StringBuilder tasksText = new StringBuilder("📥 Задачи в бэклоге:\n\n");

        if (tasks.isEmpty()) {
            tasksText.append("Нет задач в бэклоге");
        } else {
            for (Task task : tasks) {
                tasksText.append("• ").append(task.getName()).append("\n");
            }
        }

        sendMessage(chatId, tasksText.toString());
    }

    private void showSettings(Long chatId) {
        String settingsText = "⚙️ Настройки:\n\n" +
                "• Уведомления: ✅ Вкл\n" +
                "• Время напоминаний: 09:00\n" +
                "• Язык: Русский\n" +
                "• Часовой пояс: UTC+3";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(settingsText);
        message.setReplyMarkup(createMainKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showHelp(Long chatId) {
        String helpText = "❓ Помощь:\n\n" +
                "• Добавьте задачу через кнопку \"📝 Добавить задачу\"\n" +
                "• Укажите дедлайн в формате: ДД.ММ.ГГГГ ЧЧ:MM\n" +
                "• Просматривайте задачи в \"📋 Мои задачи\"\n" +
                "• Следите за ближайшими дедлайнами в \"⏰ Ближайшие дедлайны\"\n" +
                "• Отмечайте выполненные задачи\n\n" +
                "📅 Формат даты: 25.12.2025 15:30";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(helpText);
        message.setReplyMarkup(createMainKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendWelcomeMessage(Long chatId) {
        String welcomeText = "👋 Добро пожаловать в Task Manager Bot!\n\n" +
                "Управляйте своими задачами с помощью кнопок ниже:";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(welcomeText);
        message.setReplyMarkup(createMainKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Клавиатуры
    private ReplyKeyboardMarkup createMainKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📝 Добавить задачу");
        row1.add("📋 Мои задачи");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("⏰ Ближайшие дедлайны");
        row2.add("✅ Выполненные");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("🔄 В процессе");
        row3.add("📥 Бэклог");

        KeyboardRow row4 = new KeyboardRow();
        row4.add("⚙️ Настройки");
        row4.add("❓ Помощь");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup createTasksKeyboard(List<Task> tasks) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Task task : tasks) {
            List<InlineKeyboardButton> infoRow = new ArrayList<>();
            InlineKeyboardButton infoButton = new InlineKeyboardButton();
            infoButton.setText("📝 " + task.getName() + " [" + task.getTaskStatus() + "]");
            infoButton.setCallbackData("info_" + task.getId());
            infoRow.add(infoButton);
            rows.add(infoRow);

            List<InlineKeyboardButton> actionRow = new ArrayList<>();

            InlineKeyboardButton completeButton = new InlineKeyboardButton();
            completeButton.setText("✅ Выполнено");
            completeButton.setCallbackData("complete_" + task.getId());
            actionRow.add(completeButton);

            InlineKeyboardButton editButton = new InlineKeyboardButton();
            editButton.setText("✏️ Редактировать");
            editButton.setCallbackData("edit_" + task.getId());
            actionRow.add(editButton);

            rows.add(actionRow);

            List<InlineKeyboardButton> statusRow = new ArrayList<>();

            if (task.getTaskStatus() != TaskStatus.IN_PROGRESS) {
                InlineKeyboardButton inProgressButton = new InlineKeyboardButton();
                inProgressButton.setText("🔄 В процессе");
                inProgressButton.setCallbackData("status_" + task.getId() + "_IN_PROGRESS");
                statusRow.add(inProgressButton);
            }

            if (task.getTaskStatus() != TaskStatus.BACKLOG) {
                InlineKeyboardButton backlogButton = new InlineKeyboardButton();
                backlogButton.setText("📥 В бэклог");
                backlogButton.setCallbackData("status_" + task.getId() + "_BACKLOG");
                statusRow.add(backlogButton);
            }

            if (!statusRow.isEmpty()) {
                rows.add(statusRow);
            }

            List<InlineKeyboardButton> deleteRow = new ArrayList<>();
            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText("❌ Удалить");
            deleteButton.setCallbackData("delete_" + task.getId());
            deleteRow.add(deleteButton);
            rows.add(deleteRow);

            List<InlineKeyboardButton> separatorRow = new ArrayList<>();
            InlineKeyboardButton separatorButton = new InlineKeyboardButton();
            separatorButton.setText("────────────");
            separatorButton.setCallbackData("separator");
            separatorRow.add(separatorButton);
            rows.add(separatorRow);
        }

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("🔙 Назад в меню");
        backButton.setCallbackData("back_to_main");
        backRow.add(backButton);
        rows.add(backRow);

        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup createBackToTasksKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("🔙 К списку задач");
        backButton.setCallbackData("back_to_tasks");
        row.add(backButton);

        rows.add(row);
        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup createBackKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("🔙 Назад");

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}