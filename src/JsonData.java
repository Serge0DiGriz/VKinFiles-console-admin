import java.util.LinkedList;

class JsonData {
    int offset;
    int lastMessage;
    LinkedList<Attachment> items;

    JsonData(int offset, LinkedList<Attachment> items, int lastMessage) {
        this.offset = offset;
        this.items = items;
        this.lastMessage = lastMessage;
    }
}
