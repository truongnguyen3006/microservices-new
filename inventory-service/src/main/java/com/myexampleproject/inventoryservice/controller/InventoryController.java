package com.myexampleproject.inventoryservice.controller;

import com.myexampleproject.inventoryservice.service.InventoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@Slf4j
public class InventoryController {

    private final StreamsBuilderFactoryBean factoryBean;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.kafka.streams.properties.application.server.id}")
    private String applicationServerConfig;
    private HostInfo thisHostInfo;

    @Autowired
    public InventoryController(StreamsBuilderFactoryBean factoryBean) {
        this.factoryBean = factoryBean;
    }

    private HostInfo getThisHostInfo() {
        if (thisHostInfo == null) {
            try {
                String host = applicationServerConfig.split(":")[0];
                int port = Integer.parseInt(applicationServerConfig.split(":")[1]);
                this.thisHostInfo = new HostInfo(host, port);
            } catch (Exception e) {
                log.error("Lỗi phân tích 'application.server.id': [{}]. Cần có dạng 'host:port'", applicationServerConfig);
                throw new RuntimeException("Cấu hình 'application.server.id' không hợp lệ", e);
            }
        }
        return thisHostInfo;
    }

    @GetMapping("/{skuCode}")
    public ResponseEntity<?> getInventory(@PathVariable String skuCode) {

        KafkaStreams streams = factoryBean.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            return ResponseEntity.status(503).body(Map.of("error", "Kafka Streams chưa sẵn sàng (State: " + (streams == null ? "NULL" : streams.state()) + ")"));
        }

        // ========================================================
        // === SỬA LẠI LOGIC: HÃY THỬ TRUY VẤN CỤC BỘ TRƯỚC ===
        // ========================================================

        ReadOnlyKeyValueStore<String, Integer> store;
        try {
            // 1. Lấy State Store CỤC BỘ
            store = streams.store(
                    StoreQueryParameters.fromNameAndType(
                            InventoryService.INVENTORY_STORE_NAME,
                            QueryableStoreTypes.keyValueStore()
                    )
            );
        } catch (InvalidStateStoreException e) {
            // Store chưa sẵn sàng, đây có thể là lỗi 503
            log.warn("InvalidStateStore (chưa sẵn sàng) khi truy vấn {}: {}", skuCode, e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", "State Store chưa sẵn sàng, hãy thử lại sau giây lát."));
        }

        // 2. Thử LẤY key
        Integer quantity = store.get(skuCode);

        if (quantity != null) {
            // === TRƯỜNG HỢP 1: TÌM THẤY! KEY NẰM TRÊN INSTANCE NÀY ===
            log.info("Query local store: TÌM THẤY key {} tại {}", skuCode, getThisHostInfo());
            return ResponseEntity.ok(Map.of("skuCode", skuCode, "quantity", quantity));
        }

        // === TRƯỜNG HỢP 2: KHÔNG TÌM THẤY CỤC BỘ. HÃY KIỂM TRA METADATA ===
        // Key này có thể thuộc về một instance khác.
        log.warn("Query local store: KHÔNG tìm thấy {}. Tìm kiếm metadata...", skuCode);

        KeyQueryMetadata metadata = streams.queryMetadataForKey(
                InventoryService.INVENTORY_STORE_NAME,
                skuCode,
                Serdes.String().serializer()
        );

        if (metadata == null || metadata.activeHost() == null || metadata.activeHost().port() == -1) {
            // Lỗi "Không tìm thấy metadata" của bạn nằm ở đây.
            log.warn("Không tìm thấy metadata cho key {}", skuCode);
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Không tìm thấy thông tin cho SKU: " + skuCode));
        }

        HostInfo activeHost = metadata.activeHost();
        HostInfo thisInstance = getThisHostInfo();

        if (activeHost.equals(thisInstance)) {
            // Lỗi logic: Metadata nói key ở đây, nhưng local store không thấy?
            // (Có thể là do độ trễ rất nhỏ)
            log.error("Lỗi logic: Metadata nói key {} ở đây, nhưng local store không tìm thấy.", skuCode);
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Không tìm thấy (Lỗi logic Metadata): " + skuCode));
        } else {

            // === TRƯỜNG HỢP 3: KEY NẰM Ở INSTANCE KHÁC -> CHUYỂN TIẾP (PROXY) ===
            log.info("Key {} nằm ở {}. Chuyển tiếp request từ {}...",
                    skuCode, activeHost, thisInstance);

            URI redirectUri = UriComponentsBuilder.fromUriString(
                    String.format("http://%s:%d/api/inventory/%s",
                            activeHost.host(),
                            activeHost.port(),
                            skuCode)
            ).build().toUri();

            return restTemplate.getForEntity(redirectUri, String.class);
        }
    }
}