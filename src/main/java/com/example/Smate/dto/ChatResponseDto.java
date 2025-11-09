package com.example.Smate.dto;

public class ChatResponseDto {
    private String reply; // AI가 사용자에게 말한 응답
    private TaskDto task; // 알람 정보 (없으면 null)

    public ChatResponseDto() {
    }

    public ChatResponseDto(String reply, TaskDto task) {
        this.reply = reply;
        this.task = task;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public TaskDto getTask() { return task; }
    public void setTask(TaskDto task) { this.task = task; }
}