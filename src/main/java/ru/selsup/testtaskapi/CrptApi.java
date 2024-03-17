package ru.selsup.testtaskapi;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;

public class CrptApi {

    private long delayValueInNanos;

    private HttpClient httpClient;

    private ScheduledThreadPoolExecutor service;

    private Instant prevCall;

    private String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    public CrptApi(TimeUnit timeUnit, int requestLimit) throws Exception {
        if (timeUnit == TimeUnit.NANOSECONDS) throw new IllegalArgumentException("NANOSECONDS cannot be divided to subintervals");
        if (requestLimit <= 0) throw new IllegalArgumentException("requestLimit <= 0");
        delayValueInNanos = TimeUnit.NANOSECONDS.convert(1, timeUnit) / requestLimit;
        this.service = new ScheduledThreadPoolExecutor(requestLimit);
        this.service.setMaximumPoolSize(requestLimit);
        httpClient = HttpClient.newHttpClient();
        prevCall = Instant.now().minusNanos(delayValueInNanos);
    }


    /**
     * Условие интерпретировано так, что количество направленных Http-запросов не должно превышать отношения
     * requestLimit / timeUnit. Т.е. метод должен посылать Http-запрос не чаще в интервал времени timeUnit / requestLimit
     * @param document Документ
     * @param signature подпись, авторизующая отправителя
     * @implNote из открытых источников не удалось получить информацию, в качестве какого параметра Http-запроса
     * использовать подпись
     * @return Ответный Http-статус
     */
    public int postDocument(Document document, String signature) {

        String jsonDocument = DocumentJsonWriter.writeDocument(document);

        Instant currentCall;
        long actualDelayCall;
        long actualInterval;

        synchronized (this) {
            currentCall = Instant.now();
            actualInterval = Duration.between(prevCall, currentCall).toNanos();
            actualDelayCall = actualInterval >= delayValueInNanos
                    ? 0L
                    : delayValueInNanos - actualInterval;
            prevCall = currentCall;
        }

        ScheduledFuture<Integer> responseStatus = service.schedule(
                () -> {
                    HttpRequest request = HttpRequest.newBuilder(new URI(url))
                            //TODO для правильного использования параметра signature необходима подробная информация об API
                            .header("X-signature", signature)
                            .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    return response.statusCode();
                }
                , actualDelayCall, TimeUnit.NANOSECONDS);

        try {
            return responseStatus.get();
        } catch (Exception e) {
            return 400;
        }

    }

    /**
     * Класс сериализации Document-класса в JSON
     * @implNote использует библиотеку gSon
     */
     /* Maven:
     <dependency>
         <groupId>com.google.code.gson</groupId>
         <artifactId>gson</artifactId>
         <version>2.10.1</version>
     </dependency>*/

    public static class DocumentJsonWriter {
            static public String writeDocument(Document document) {
                Gson gson = new GsonBuilder()
                        //.setPrettyPrinting()
                        .serializeNulls()
                        .registerTypeAdapter(LocalDate.class, new JsonSerializer<LocalDate>() {

                                    @Override
                                    public JsonElement serialize(LocalDate localDate, Type type, JsonSerializationContext jsonSerializationContext) {
                                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                                        return new JsonPrimitive(localDate.format(formatter));
                                    }
                                }
                                ).create();
                return gson.toJson(document);
            }
    }

    /**
     * Классы, представляющий POJO Document согласно JSON представлению
     * @implNote все поля сделаны публичными для сокращения
     */
    public static class Document {
        public Description description;
        @SerializedName("doc_id")
        public String docId;
        @SerializedName("doc_status")
        public String docStatus;
        @SerializedName("doc_type")
        public String docType;
        public boolean importRequest;
        @SerializedName("owner_inn")
        public String ownerInn;
        @SerializedName("participant_inn")
        public String participantInn;
        @SerializedName("producer_inn")
        public String producerInn;
        @SerializedName("production_date")
        public LocalDate productionDate;
        @SerializedName("production_type")
        public LocalDate productionType;
        public List<Product> products;
        @SerializedName("reg_date")
        public LocalDate regDate;
        @SerializedName("reg_number")
        public String regNumber;
    }

    public static class Description {
        public String participantInn;
    }

    public static class Product {

        @SerializedName("certificate_document")
        public String certificateDocument;
        @SerializedName("certificate_document_date")
        public LocalDate certificateDocumentDate;
        @SerializedName("certificate_document_number")
        public String certificateDocumentNumber;
        @SerializedName("owner_inn")
        public String ownerInn;
        @SerializedName("producer_inn")
        public String producerInn;
        @SerializedName("production_date")
        public LocalDate productionDate;
        @SerializedName("tnved_code")
        public String tnvedCode;

        @SerializedName("uit_code")
        public String uitCode;
        @SerializedName("uitu_code")
        public String uituCode;
    }
}