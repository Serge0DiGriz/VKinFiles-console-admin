class Attachment {
    MediaType type;
    int messageId;
    String title;
    String url;

    Attachment(MediaType type, String title, String url) {
        this.type = type; this.title = title; this.url = url;
    }

    @Override
    public String toString() {
        return String.format("{type: %s, title: %s, url: %s}", type, title, url);
    }
}
