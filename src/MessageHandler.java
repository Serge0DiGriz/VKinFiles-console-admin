import com.google.gson.Gson;
import com.vk.api.sdk.client.Lang;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.audio.AudioFull;
import com.vk.api.sdk.objects.base.Link;
import com.vk.api.sdk.objects.docs.Doc;
import com.vk.api.sdk.objects.messages.Dialog;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.messages.MessageAttachment;
import com.vk.api.sdk.objects.photos.Photo;
import com.vk.api.sdk.objects.users.UserXtrCounters;
import com.vk.api.sdk.queries.users.UserField;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class MessageHandler {

    private static int GROUP_ID = 179103201;
    private static String ACCESS_TOKEN =
            "6dbc1bd443d56c389a4a1052c5c42543f91051740cf321b918b484c1a8ac76619175fa2343fb4200d00eb";

    private int userId = -1;
    private GroupActor actor = new GroupActor(GROUP_ID, ACCESS_TOKEN);
    private VkApiClient vk = new VkApiClient(HttpTransportClient.getInstance());

    private LinkedList<Attachment> userData;
    private HashMap<Integer, HashMap<String, String>> users;
    private File dataZip = new File("dataFiles.zip");
    private File downloadingDir = new File("DownloadedFiles");
    private Gson gson = new Gson();

    private int photoCounter = 0;
    private int countThreads = 4;



    MessageHandler() {
        System.out.println("version: " + vk.getVersion());
        // Получаем список доступных пользователей
        try {
            users = getUsers();
        } catch (ClientException error) {
            System.out.println("Client Error: " + error.getMessage());
        } catch (ApiException error) {
            System.out.println("Api Error: " + error.getMessage());
        }

        // Создаём архив для хранения данных пользователей
        boolean createFile = false;
        try {
            createFile = dataZip.createNewFile();
        } catch (IOException error) {
            System.out.println("Error with creating data's archive: " + error.getMessage());
        }

        if (createFile) {
            // Создаём файлы с данными пользователей
            try {
                createDataFiles();
                System.out.println("Archive was created");
            } catch (IOException error) {
                System.out.println("Error with creating user's files: " + error.getMessage());
            }
        } else {
            // Обновляем файлы с данными пользователей
            try {
                System.out.println("Update archive: " + (updateDataFiles() ? "Done!" : "Fail!"));
            } catch (IOException error) {
                System.out.println("Error with updating user's files: " + error.getMessage());
            }
        }
    }

    private HashMap<String, String> getUserInformation(int userId) throws ClientException, ApiException {
        HashMap<String, String> info = new HashMap<>();
        UserXtrCounters user = vk.users().get(actor)
                .userIds(String.valueOf(userId))
                .fields(UserField.ONLINE, UserField.DOMAIN)
                .lang(Lang.RU).execute().get(0);

        info.put("name", user.getLastName() + " " + user.getFirstName());
        info.put("address", user.getDomain());

        return info;
    }

    private HashMap<Integer, HashMap<String, String>> getUsers() throws ClientException, ApiException {
        HashMap<Integer, HashMap<String, String>> users = new HashMap<>();

        int count = vk.messages().getDialogs(actor).count(0).execute().getCount();
        for (int i=0; (i+1)*200 < count+200; i++) {
            List<Dialog> dialogs = vk.messages().getDialogs(actor).count(200).offset(i*200)
                    .lang(Lang.RU).execute().getItems();
            for (Dialog dialog: dialogs) {
                int id = dialog.getMessage().getUserId();
                users.put(id, getUserInformation(id));
            }
        }

        return users;
    }

    private void createDataFiles() throws IOException {
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(dataZip));
        for (int id: users.keySet()) {
            zipOut.putNextEntry(new ZipEntry(id+".json"));
            zipOut.write(gson.toJson(new JsonData(0, null, 0)).getBytes());
            zipOut.closeEntry();
        } zipOut.close();
    }

    private boolean updateDataFiles() throws  IOException {
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(dataZip)); ZipEntry entry;
        File newZip = new File("newData.zip");
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(newZip));

        LinkedList<Integer> ids = new LinkedList<>(users.keySet()); // список всех пользователей

        // отбор файлов пользователей, имеющихся в списке
        while ((entry = zipIn.getNextEntry()) != null) {
            String fName = entry.getName();
            Integer id = Integer.valueOf(fName.substring(0, fName.length()-5));
            if (ids.contains(id)) {
                ids.remove(id);

                zipOut.putNextEntry(entry);
                int c; while ((c = zipIn.read()) != -1)
                    zipOut.write(c);
                zipOut.closeEntry();

            } zipIn.closeEntry();
        } zipIn.close();

        // добавление недостающих файлов пользователей
        for (int id: ids) {
            zipOut.putNextEntry(new ZipEntry(id+".json"));
            zipOut.write(gson.toJson(new JsonData(0, null, 0)).getBytes());
            zipOut.closeEntry();
        } zipOut.close();

        // удаление старой версии
        return dataZip.delete() && newZip.renameTo(dataZip);
    }



    // Корректировка имени файла
    private String correctTitle(String title) {
        StringBuilder correct = new StringBuilder();
        for (char symbol: title.trim().toCharArray()) {
            switch (symbol) {
                case '/': correct.append('¦'); break;
                case '\\': correct.append('¦'); break;
                case '|': correct.append('¦'); break;
                case ':': correct.append('¦'); break;
                case '<': correct.append('«'); break;
                case '>': correct.append('»'); break;
                case '"': correct.append('\''); break;
                case '*': correct.append('×'); break;
                case '?': correct.append('‽'); break;
                case '!': correct.append('‽'); break;
                case '%': correct.append('‰'); break;
                case '@': correct.append('©'); break;
                case '+': correct.append('±'); break;

                default: correct.append(symbol);
            }
        }

        return correct.toString();
    }



    // получаем данные пользователя
    private JsonData getUserData(int id) throws IOException {
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(dataZip));

        while (!zipIn.getNextEntry().getName().equals(id+".json"))
            zipIn.closeEntry();
        Scanner scan = new Scanner(zipIn);
        StringBuilder json = new StringBuilder();
        while (scan.hasNextLine())
            json.append(scan.nextLine());
        zipIn.closeEntry(); zipIn.close();

        return gson.fromJson(json.toString(), JsonData.class);
    }

    // сохранение данных
    private boolean saveUserData(int count, int lastMessage) throws IOException {
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(dataZip));
        ZipEntry entry;

        File newZip = new File("newData.zip");
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(newZip));

        while ((entry = zipIn.getNextEntry()) != null) {
            String fName = entry.getName();
            if (fName.equals(userId+".json")) { // перезапись
                zipOut.putNextEntry(entry);
                zipOut.write(gson.toJson(new JsonData(count, userData, lastMessage)).getBytes());
            } else { // копирование
                zipOut.putNextEntry(entry);
                int c; while ((c = zipIn.read()) != -1)
                    zipOut.write(c);
            }
            zipIn.closeEntry();
            zipOut.closeEntry();
        } zipIn.close(); zipOut.close();

        return dataZip.delete() && newZip.renameTo(dataZip);
    }

    // обработка сообщений
    private LinkedList<Attachment> messageProcessing(int messageId, Message message) {
        LinkedList<Attachment> attachments = getAttachments(message.getAttachments(), messageId);
        List<Message> fwdMessages = message.getFwdMessages();
        if (fwdMessages != null)
            for (Message fwdMess : fwdMessages)
                attachments.addAll(messageProcessing(messageId, fwdMess));

        return attachments;
    }

    // Обработка вложений
    private LinkedList<Attachment> getAttachments(List<MessageAttachment> attachments, int messId) {
        LinkedList<Attachment> list = new LinkedList<>();
        if (attachments != null) {
            for (MessageAttachment attachment: attachments) {
                Attachment item = null;
                switch (attachment.getType()) {
                    case PHOTO:
                        item = getPhoto(attachment.getPhoto());
                        break;
                    case DOC:
                        item = getDocument(attachment.getDoc());
                        break;
                    case AUDIO:
                        item = getAudio(attachment.getAudio());
                        break;
                    case LINK:
                        item = getLink(attachment.getLink());
                        break;
                }
                if (item != null) {
                    item.messageId = messId;
                    list.add(item);
                }
            }
        }
        return list;
    }
    private Attachment getPhoto(Photo photo) {
        String title = "Image " + (++photoCounter) + ".jpg",
                url = "";
        for (String size: new String[]{photo.getPhoto2560(), photo.getPhoto1280(),
                photo.getPhoto604(), photo.getPhoto130(), photo.getPhoto75()}) {
            if (size != null) {
                url = size;
                break;
            }

        } return new Attachment(MediaType.PHOTO, title, url);
    }
    private Attachment getDocument(Doc doc) {
        String title = correctTitle(doc.getTitle()),
                ext = doc.getExt(),
                url = doc.getUrl();
        title += (title.endsWith(ext) ? "" : "."+ext);
        return new Attachment(MediaType.DOC, title, url);
    }
    private Attachment getAudio(AudioFull audio) {
        String title = correctTitle(audio.getTitle()) + ".mp3",
                url = audio.getUrl();
        return new Attachment(MediaType.AUDIO, title, url);
    }
    private Attachment getLink(Link link) {
        String url = link.getUrl();
        if (url.contains("audio_playlist"))
            return new Attachment(MediaType.PLAYLIST, correctTitle(link.getTitle()), url);
        return null;
    }

    // выбор пользователя
    boolean selectUser(int id) throws ClientException, ApiException, IOException {
        if (users.keySet().contains(id)) {
            userId = id;

            // Получаем данные пользователя
            JsonData data = getUserData(userId);
            userData = data.items;

            // Обновляем данные
            int count = vk.messages().getHistory(actor).count(0).userId(userId)
                    .execute().getCount(); // кол-во сообщений
            int lastMessage = vk.messages().getHistory(actor).count(1).userId(userId).rev(false).execute()
                    .getItems().get(0).getId(); // последнее сообщение

            if (count != data.offset | lastMessage != data.lastMessage)
                data.offset = 0;
            if (data.offset == 0 | userData == null)
                userData = new LinkedList<>();

            for (int i=0; (i+1)*200 < count+200-data.offset; i++) {
                List<Message> messages = vk.messages().getHistory(actor).userId(userId)
                        .count(200).offset(i*200 + data.offset).rev(true)
                        .lang(Lang.RU).execute().getItems();
                // Обрабатываем сообщения
                for (Message message: messages) {
                    int messageId = message.getId();
                    userData.addAll(messageProcessing(messageId, message));
                }
            }

            // Сохраняем данные
            System.out.println("Updating data: " + (saveUserData(count, lastMessage) ? "Done!" : "Fail!"));

        } else return false;

        return true;
    }



    // завершение скачивния и проверка
    void finishDownloading() {
        if (Downloading.counter == 0)
            System.out.println("All files downloaded!");
    }

    // скачивание аудио плейлиста
    private void downloadPlaylist(Attachment playlist)
            throws IOException, ApiException, ClientException, InterruptedException {
        Document html = Jsoup.connect(playlist.url).get();
        LinkedList<String> audiosIds = new LinkedList<>();
        LinkedList<Integer> messagesIds = new LinkedList<>();
        for (Element item: html.select("div.audio_item")) {
            audiosIds.add(item.id().substring(0, item.id().indexOf("_playlist")));
            if (audiosIds.size() == 10) {
                messagesIds.add(vk.messages().send(actor).userId(userId).attachment(audiosIds).execute());
                audiosIds.clear();
            }
        }
        if (audiosIds.size() != 0)
            messagesIds.add(vk.messages().send(actor).userId(userId).attachment(audiosIds).execute());

        LinkedList<Attachment> audios = new LinkedList<>();
        for (Message message: vk.messages().getById(actor, messagesIds).lang(Lang.RU).execute().getItems())
            audios.addAll(getAttachments(message.getAttachments(), 0));
        vk.messages().delete(actor, messagesIds).unsafeParam("delete_for_all", 1).execute();

        Downloading downloading;
        for (Attachment audio: audios) {
            downloading = new Downloading(this, audio,
                    downloadingDir.getPath() + "/audios/" + playlist.title + "/");
            downloading.start();
            if (Downloading.counter > countThreads)
                downloading.join();

        }

    }

    // варианты скачивание
    void downloadAll()
            throws IOException, ApiException, ClientException, InterruptedException {
        Downloading.counter++;
        Downloading downloading;
        for (Attachment item: userData) {
            switch (item.type) {
                case PHOTO:
                    downloading = new Downloading(this, item,
                            downloadingDir.getPath()+"/photos/");
                    downloading.start();
                    if (Downloading.counter > countThreads)
                        downloading.join();
                    break;
                case DOC:
                    downloading = new Downloading(this, item,
                            downloadingDir.getPath()+"/documents/");
                    downloading.start();
                    if (Downloading.counter > countThreads)
                        downloading.join();
                    break;
                case AUDIO:
                    downloading = new Downloading(this, item,
                            downloadingDir.getPath()+"/audios/");
                    downloading.start();
                    if (Downloading.counter > countThreads)
                        downloading.join();
                    break;
                case PLAYLIST:
                    downloadPlaylist(item);
                    break;
            }
        }
        Downloading.counter--; finishDownloading();
    }

    void download(MediaType type)
            throws IOException, ApiException, ClientException, InterruptedException {
        Downloading.counter++;
        Downloading downloading;
        for (Attachment item: userData) {
            if (item.type == type) {
                switch (item.type) {
                    case PHOTO:
                        downloading = new Downloading(this, item,
                                downloadingDir.getPath()+"/photos/");
                        downloading.start();
                        if (Downloading.counter > countThreads)
                            downloading.join();
                        break;
                    case DOC:
                        downloading = new Downloading(this, item,
                                downloadingDir.getPath()+"/documents/");
                        downloading.start();
                        if (Downloading.counter > countThreads)
                            downloading.join();
                        break;
                    case AUDIO:
                        downloading = new Downloading(this, item,
                                downloadingDir.getPath()+"/audios/");
                        downloading.start();
                        if (Downloading.counter > countThreads)
                            downloading.join();
                        break;
                    case PLAYLIST:
                        downloadPlaylist(item);
                        break;
                }
            }
        }
        Downloading.counter--; finishDownloading();

    }

    void download(List<Integer> messagesIds)
            throws IOException, ApiException, ClientException, InterruptedException {
        Downloading.counter++;
        Downloading downloading;
        for (Attachment item: userData) {
            if (messagesIds.contains(item.messageId)) {
                switch (item.type) {
                    case PHOTO:
                        downloading = new Downloading(this, item,
                                downloadingDir.getPath()+"/photos/");
                        downloading.start();
                        if (Downloading.counter > countThreads)
                            downloading.join();
                        break;
                    case DOC:
                        downloading = new Downloading(this, item,
                                downloadingDir.getPath()+"/documents/");
                        downloading.start();
                        if (Downloading.counter > countThreads)
                            downloading.join();
                        break;
                    case AUDIO:
                        downloading = new Downloading(this, item,
                                downloadingDir.getPath()+"/audios/");
                        downloading.start();
                        if (Downloading.counter > countThreads)
                            downloading.join();
                        break;
                    case PLAYLIST:
                        downloadPlaylist(item);
                        break;
                }
            }
        }
        Downloading.counter--; finishDownloading();

    }



    // краткая информация о пользователях
    HashMap<Integer, HashMap<String, String>> getUsersInfo() {
        return users;
    }

}
