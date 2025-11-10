package com.example.fmap.ui.home;// Event.java
/**
 * 用於 LiveData，確保事件只被消費一次
 * @param <T> a
 */
public class Event<T> {

    private T content;
    private boolean hasBeenHandled = false;

    public Event(T content) {
        this.content = content;
    }

    /**
     * 如果事件尚未被處理，則返回內容，否則返回 null
     */
    public T getContentIfNotHandled() {
        if (hasBeenHandled) {
            return null;
        } else {
            hasBeenHandled = true;
            return content;
        }
    }

    /**
     * 返回內容，即使它已經被處理過
     */
    public T peekContent() {
        return content;
    }
}
