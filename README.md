# Spring Boot Microservice

![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat-square&logo=spring-boot)
![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)

Production-ready Spring Boot 3 microservice template with JPA, Kafka, and distributed tracing.

## Features

- **Spring Boot 3.2** - Latest framework
- **Java 21** - Virtual threads
- **JPA/Hibernate** - Data access
- **Kafka** - Event streaming
- **OpenAPI** - API documentation
- **Actuator** - Health & metrics
- **Distributed Tracing** - Micrometer + Zipkin
- **Docker** - Containerization

## Quick Start

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Docker
docker-compose up -d
```

## Project Structure

```
src/main/java/
├── config/          # Configuration classes
├── controller/      # REST controllers
├── service/         # Business logic
├── repository/      # Data access
├── model/           # Entities & DTOs
├── event/           # Kafka events
└── exception/       # Exception handling
```

## REST API

```java
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product management APIs")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List all products")
    public ResponseEntity<Page<ProductDTO>> findAll(Pageable pageable) {
        return ResponseEntity.ok(productService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create product")
    public ResponseEntity<ProductDTO> create(@Valid @RequestBody CreateProductRequest request) {
        ProductDTO created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

## Service Layer

```java
@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final KafkaProducer kafkaProducer;

    public ProductDTO create(CreateProductRequest request) {
        Product product = Product.builder()
            .name(request.getName())
            .price(request.getPrice())
            .stock(request.getStock())
            .build();

        product = productRepository.save(product);

        // Publish event
        kafkaProducer.send(new ProductCreatedEvent(product.getId(), product.getName()));

        return ProductDTO.from(product);
    }

    @Transactional(readOnly = true)
    public ProductDTO findById(Long id) {
        return productRepository.findById(id)
            .map(ProductDTO::from)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }
}
```

## Kafka Events

```java
// Producer
@Component
public class KafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(ProductCreatedEvent event) {
        kafkaTemplate.send("products", event.productId().toString(), event);
    }
}

// Consumer
@Component
public class KafkaConsumer {

    @KafkaListener(topics = "orders", groupId = "product-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received order event: {}", event);
        // Update stock
    }
}

// Events
public record ProductCreatedEvent(Long productId, String name) {}
public record OrderCreatedEvent(Long orderId, List<OrderItem> items) {}
```

## JPA Entities

```java
@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
```

## Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", String.join(", ", errors)));
    }
}
```

## Configuration

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/products
    username: ${DB_USER}
    password: ${DB_PASSWORD}

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0
```

## Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateProduct() throws Exception {
        var request = """
            {"name": "Test Product", "price": 99.99, "stock": 10}
            """;

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Product"));
    }
}
```

## License

MIT
