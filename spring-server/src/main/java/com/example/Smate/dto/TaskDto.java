package com.example.Smate.dto;

public class TaskDto {
    private String time;
    private String text;

    public TaskDto() {
    }

    public TaskDto(String time, String text) {
        this.time = time;
        this.text = text;
    }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}