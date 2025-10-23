// 檔案路徑: .../app/src/main/java/com/example/fmap/ui/chat/ChatMessage.java

package com.example.fmap.ui.home.chat;

public class ChatMessage {

    public enum Sender {
        USER, AI
    }

    public final String text;
    public final Sender sender;

    public ChatMessage(String text, Sender sender) {
        this.text = text;
        this.sender = sender;
    }
}
