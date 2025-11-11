package com.example.Smate.dto;

public class ChatResponseDto {
    private String reply; // AI가 사용자에게 말한 응답
    private TaskDto task; // 알람 정보 (없으면 null)
    private String type;  // ⭐️ [추가] "CHAT" (일반) 또는 "SYSTEM_REMARK" (시스템 혼잣말)

    public ChatResponseDto() {
        // (기존 기본 생성자)
    }

    // ⭐️ [수정] 기존 생성자는 type에 "CHAT"을 기본값으로 설정
    public ChatResponseDto(String reply, TaskDto task) {
        this.reply = reply;
        this.task = task;
        this.type = "CHAT"; // ⭐️ 기본값 설정
    }

    // ⭐️ [신규] type을 직접 지정하는 새 생성자 추가
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

    // ⭐️ [추가] type 필드의 Getter / Setter
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}