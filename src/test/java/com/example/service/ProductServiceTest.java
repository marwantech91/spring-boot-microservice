package com.example.service;

import com.example.dto.CreateProductRequest;
import com.example.dto.ProductDTO;
import com.example.dto.UpdateProductRequest;
import com.example.exception.ResourceNotFoundException;
import com.example.model.Product;
import com.example.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L)
                .name("Test Widget")
                .description("A test product")
                .price(new BigDecimal("29.99"))
                .stock(100)
                .build();
    }

    @Test
    void findById_existingProduct_returnsDTO() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        ProductDTO result = productService.findById(1L);

        assertNotNull(result);
        assertEquals("Test Widget", result.getName());
    }

    @Test
    void findById_nonExistingProduct_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.findById(99L));
    }

    @Test
    void findAll_returnsPaginatedResults() {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(productRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<ProductDTO> result = productService.findAll(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void create_savesAndPublishesEvent() {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("New Product");
        request.setDescription("Description");
        request.setPrice(new BigDecimal("19.99"));
        request.setStock(50);

        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductDTO result = productService.create(request);

        assertNotNull(result);
        verify(productRepository).save(any(Product.class));
        verify(kafkaTemplate).send(eq("products.created"), any());
    }

    @Test
    void update_existingProduct_updatesFields() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Updated Name");
        request.setPrice(new BigDecimal("39.99"));

        ProductDTO result = productService.update(1L, request);

        assertNotNull(result);
        verify(kafkaTemplate).send(eq("products.updated"), any());
    }

    @Test
    void delete_existingProduct_deletesSuccessfully() {
        when(productRepository.existsById(1L)).thenReturn(true);

        assertDoesNotThrow(() -> productService.delete(1L));
        verify(productRepository).deleteById(1L);
    }

    @Test
    void delete_nonExistingProduct_throwsException() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> productService.delete(99L));
    }

    @Test
    void updateStock_validQuantity_updatesStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        assertDoesNotThrow(() -> productService.updateStock(1L, 10));
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void updateStock_insufficientStock_throwsException() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        assertThrows(IllegalArgumentException.class,
                () -> productService.updateStock(1L, -200));
    }

    @Test
    void countAll_returnsCount() {
        when(productRepository.count()).thenReturn(42L);

        assertEquals(42L, productService.countAll());
    }

    @Test
    void existsById_existingProduct_returnsTrue() {
        when(productRepository.existsById(1L)).thenReturn(true);

        assertTrue(productService.existsById(1L));
    }
}
