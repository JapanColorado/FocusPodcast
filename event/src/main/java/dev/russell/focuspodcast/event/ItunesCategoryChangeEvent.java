package dev.russell.focuspodcast.event;

public class ItunesCategoryChangeEvent {
    private final int code;

    public ItunesCategoryChangeEvent(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
