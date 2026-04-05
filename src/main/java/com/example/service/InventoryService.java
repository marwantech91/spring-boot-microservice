package com.example.service;

import com.example.exception.ResourceNotFoundException;
import com.example.model.Product;
import com.example.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Map<Long, Integer> getStockLevels(List<Long> productIds) {
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getStock));
    }

    @Transactional(readOnly = true)
    public List<Product> getLowStockProducts(int threshold) {
        log.info("Checking for products with stock below {}", threshold);
        return productRepository.findAll().stream()
                .filter(p -> p.getStock() <= threshold)
                .toList();
    }

    public void replenish(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Replenish quantity must be positive");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        int oldStock = product.getStock();
        product.setStock(oldStock + quantity);
        productRepository.save(product);

        log.info("Replenished product {}: {} -> {}", productId, oldStock, product.getStock());
    }

    public boolean reserve(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (product.getStock() < quantity) {
            log.warn("Insufficient stock for product {}: requested={}, available={}",
                    productId, quantity, product.getStock());
            return false;
        }

        product.setStock(product.getStock() - quantity);
        productRepository.save(product);

        log.info("Reserved {} units of product {}", quantity, productId);
        return true;
    }

    public void releaseReservation(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        product.setStock(product.getStock() + quantity);
        productRepository.save(product);

        log.info("Released {} units back to product {}", quantity, productId);
    }

    @Transactional(readOnly = true)
    public boolean isInStock(Long productId) {
        return productRepository.findById(productId)
                .map(p -> p.getStock() > 0)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<Long, Boolean> checkAvailability(List<Long> productIds) {
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p.getStock() > 0));
    }
}
