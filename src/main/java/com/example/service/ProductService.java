package com.example.service;

import com.example.dto.CreateProductRequest;
import com.example.dto.ProductDTO;
import com.example.dto.UpdateProductRequest;
import com.example.event.ProductCreatedEvent;
import com.example.event.ProductUpdatedEvent;
import com.example.exception.ResourceNotFoundException;
import com.example.model.Product;
import com.example.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional(readOnly = true)
    public Page<ProductDTO> findAll(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(ProductDTO::from);
    }

    @Transactional(readOnly = true)
    public ProductDTO findById(Long id) {
        return productRepository.findById(id)
                .map(ProductDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> findByIds(List<Long> ids) {
        return productRepository.findAllById(ids).stream()
                .map(ProductDTO::from)
                .toList();
    }

    public ProductDTO create(CreateProductRequest request) {
        log.info("Creating product: {}", request.getName());

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .build();

        product = productRepository.save(product);

        // Publish event
        publishEvent("products.created", new ProductCreatedEvent(
                product.getId(),
                product.getName(),
                product.getPrice()
        ));

        log.info("Product created with ID: {}", product.getId());
        return ProductDTO.from(product);
    }

    public ProductDTO update(Long id, UpdateProductRequest request) {
        log.info("Updating product: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getStock() != null) {
            product.setStock(request.getStock());
        }

        product = productRepository.save(product);

        // Publish event
        publishEvent("products.updated", new ProductUpdatedEvent(
                product.getId(),
                product.getName(),
                product.getPrice()
        ));

        log.info("Product updated: {}", id);
        return ProductDTO.from(product);
    }

    public void delete(Long id) {
        log.info("Deleting product: {}", id);

        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", id);
        }

        productRepository.deleteById(id);
        log.info("Product deleted: {}", id);
    }

    public void updateStock(Long id, int quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        int newStock = product.getStock() + quantity;
        if (newStock < 0) {
            throw new IllegalArgumentException("Insufficient stock");
        }

        product.setStock(newStock);
        productRepository.save(product);

        log.info("Stock updated for product {}: {} -> {}", id, product.getStock() - quantity, newStock);
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> findAvailable() {
        return productRepository.findByStockGreaterThan(0).stream()
                .map(ProductDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return productRepository.count();
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return productRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> findByNameContaining(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword).stream()
                .map(ProductDTO::from)
                .toList();
    }

    private void publishEvent(String topic, Object event) {
        try {
            kafkaTemplate.send(topic, event);
        } catch (Exception e) {
            log.error("Failed to publish event to {}: {}", topic, e.getMessage());
        }
    }
}
