import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.*;


public class CrptApi {
    /**
     * Класс дря работы с сервисом
     *
     * @param timeUnit - промежуток времени
     * @param requestLimit - лимит запросов
     */
    private final Semaphore semaphore; // Ограничитель
    private final ScheduledExecutorService scheduler; // Планировщик
    private final HttpClient httpClient; // HTTP клиент

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        semaphore = new Semaphore(requestLimit);
        httpClient = HttpClient.newHttpClient();
        scheduler = Executors.newSingleThreadScheduledExecutor();

        long delay = timeUnit.toMillis(1); // Значение задержки в миллисекундах

        // Отнимаем от максималного кол-ва запросов текущее кол-во доступных запросов
        scheduler.scheduleAtFixedRate(() -> {semaphore.release(requestLimit - semaphore.availablePermits());},
                delay, delay, TimeUnit.MILLISECONDS);
    }

    public void createDocument(String documentJson, String signature) throws InterruptedException {
        semaphore.acquire();
//        String signedDocumentJson = addSignatureToDocumentJson(documentJson, signature);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(documentJson))
                    .timeout(Duration.ofSeconds(30)) // Ждём 30 секунд
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString()); // Ожидаем string в ответе
            System.out.println("Status code of response: " + response.statusCode());
            System.out.println("Response text: " + response.body());

        } catch (Exception e) {
            System.out.println("Something went wrong! Error massage: " + e.getMessage());

        } finally {
            semaphore.release(); // освобождаем поток
        }
    }

    /*
    МБ так подпись стоит добавлять
     */
//    private String addSignatureToDocumentJson(String documentJson, String signature) {
//        return documentJson.substring(0, documentJson.length() - 1) + ", \"signature\": \"" + signature + "\"}";
//    }

    public void close() {
        scheduler.shutdown(); // перестаем принимать новые запросы

        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) { // Ждём пока все потоки закончат работу (30 сек)

                scheduler.shutdownNow(); // Принудительно завершаем, если не успел
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            scheduler.shutdownNow(); // принудительное завершение
        }
    }

    public static void main(String[] args) {
        CrptApi api = null;
        try {
            api = new CrptApi(TimeUnit.SECONDS, 5);

            String json = "{" +
                    "\"description\": \"description\", " +
                    "\"participantInn\": \"string\", " +
                    "\"doc_id\": \"string\", " +
                    "\"doc_status\": \"string\", " +
                    "\"doc_type\": \"LP_INTRODUCE_GOODS\", " +
                    "\"importRequest\": true, " +
                    "\"owner_inn\": \"string\", " +
                    "\"participant_inn\": \"string\", " +
                    "\"producer_inn\": \"string\", " +
                    "\"production_date\": \"2020-01-23\", " +
                    "\"production_type\": \"string\", " +
                    "\"products\": [" +
                    "{ " +
                    "\"certificate_document\": \"string\"," +
                    " \"certificate_document_date\": \"2020-01-23\", " +
                    "\"certificate_document_number\": \"string\", " +
                    "\"owner_inn\": \"string\", " +
                    "\"producer_inn\": \"string\", " +
                    "\"production_date\": \"2020-01-23\", " +
                    "\"tnved_code\": \"string\", " +
                    "\"uit_code\": \"string\", " +
                    "\"uitu_code\": \"string\" " +
                    "}" +
                    "], \"reg_date\": \"2020-01-23\", " +
                    "\"reg_number\": \"string\"}";

            api.createDocument(json, "signature");

        } catch (Exception e) {
            e.printStackTrace(); // в контексте задания, просто выводим в консоль

        } finally {
            if (api != null) {
                api.close();
            }
        }
    }
}
