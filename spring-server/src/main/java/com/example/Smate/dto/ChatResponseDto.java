package com.example.Smate.dto;

public class ChatResponseDto {
    private String reply; // AI가 사용자에게 말한 응답
    private TaskDto task; // 알람 정보 (없으면 null)
    private String type;

    public ChatResponseDto() {
        // (기존 기본 생성자)
    }


    public ChatResponseDto(String reply, TaskDto task) {
        this.reply = reply;
        this.task = task;
        this.type = "CHAT";
    }


    public ChatResponseDto(String reply, TaskDto task, String type) {
        this.reply = reply;
        this.task = task;
        this.type = type;
    }

    // --- (기존 Getter / Setter) ---
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public TaskDto getTask() { return task; }
    public void setTask(TaskDto task) { this.task = task; }


    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}