package com.pfe.platform.ms_gestion.service;

/**
 * Curated, class-agnostic "gold" test exemplars injected into the LLM prompt as few-shot
 * examples. They are NOT tied to any target class — they show the exact STRUCTURE the model
 * must imitate (annotations, @BeforeMethod/@AfterMethod, mock/DB setup, SecurityUtils handling,
 * Given/When/Then) for each (testType x scenarioType). The fictional classes (ProductService,
 * etc.) must never be imported by the generated test; the model adapts the structure to the
 * real source code provided in the prompt.
 */
public final class TestExemplarLibrary {

    private TestExemplarLibrary() {}

    /** Picks the best exemplar for the given test type and scenario. Falls back to the happy-path. */
    public static String select(String type, String scenarioType) {
        String t = type == null ? "" : type.trim().toUpperCase();
        String s = scenarioType == null ? "" : scenarioType.trim().toUpperCase();
        boolean exceptionLike = s.equals("EXCEPTION") || s.equals("NULL_INPUT") || s.equals("WRONG_INPUT");

        if ("INTEGRATION".equals(t)) {
            return exceptionLike ? INTEGRATION_EXCEPTION : INTEGRATION_HAPPY;
        }
        // Default to UNIT (covers UNIT, BOUNDARY, and unknown types).
        return exceptionLike ? UNIT_EXCEPTION : UNIT_HAPPY;
    }

    private static final String UNIT_HAPPY = """
            package suites.unit;

            import com.example.demo.dto.CreateProductRequest;
            import com.example.demo.entity.Category;
            import com.example.demo.entity.Product;
            import com.example.demo.repository.CategoryRepository;
            import com.example.demo.repository.ProductRepository;
            import com.example.demo.security.SecurityUtils;
            import com.example.demo.service.ProductService;

            import org.mockito.InjectMocks;
            import org.mockito.Mock;
            import org.mockito.MockedStatic;
            import org.mockito.MockitoAnnotations;
            import org.testng.annotations.AfterMethod;
            import org.testng.annotations.BeforeMethod;
            import org.testng.annotations.Test;

            import java.util.Optional;

            import static org.mockito.ArgumentMatchers.any;
            import static org.mockito.Mockito.*;
            import static org.testng.Assert.*;

            public class ProductServiceHappyTest {

                @Mock private ProductRepository productRepository;
                @Mock private CategoryRepository categoryRepository;

                @InjectMocks private ProductService productService;

                private AutoCloseable mocks;
                private MockedStatic<SecurityUtils> mockedSecurity;

                @BeforeMethod
                public void setUp() {
                    mocks = MockitoAnnotations.openMocks(this);
                    mockedSecurity = mockStatic(SecurityUtils.class);
                    mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
                }

                @AfterMethod
                public void tearDown() throws Exception {
                    mockedSecurity.close();
                    mocks.close();
                }

                @Test
                public void test_createProduct_happyPath() {
                    // Given
                    CreateProductRequest request = new CreateProductRequest();
                    request.setName("Laptop");
                    request.setCategoryId(10L);

                    Category category = new Category();
                    category.setId(10L);
                    when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
                    when(productRepository.existsByName("Laptop")).thenReturn(false);

                    Product saved = new Product();
                    saved.setId(100L);
                    saved.setName("Laptop");
                    saved.setCategory(category);
                    when(productRepository.save(any(Product.class))).thenReturn(saved);

                    // When
                    Product result = productService.createProduct(request);

                    // Then
                    assertNotNull(result);
                    assertEquals(result.getId(), Long.valueOf(100L));
                    assertEquals(result.getName(), "Laptop");
                    verify(productRepository).save(any(Product.class));
                }
            }
            """;

    private static final String UNIT_EXCEPTION = """
            package suites.unit;

            import com.example.demo.dto.CreateProductRequest;
            import com.example.demo.repository.CategoryRepository;
            import com.example.demo.repository.ProductRepository;
            import com.example.demo.security.SecurityUtils;
            import com.example.demo.service.ProductService;

            import org.mockito.InjectMocks;
            import org.mockito.Mock;
            import org.mockito.MockedStatic;
            import org.mockito.MockitoAnnotations;
            import org.testng.annotations.AfterMethod;
            import org.testng.annotations.BeforeMethod;
            import org.testng.annotations.Test;

            import java.util.Optional;

            import static org.mockito.Mockito.*;

            public class ProductServiceExceptionTest {

                @Mock private ProductRepository productRepository;
                @Mock private CategoryRepository categoryRepository;

                @InjectMocks private ProductService productService;

                private AutoCloseable mocks;
                private MockedStatic<SecurityUtils> mockedSecurity;

                @BeforeMethod
                public void setUp() {
                    mocks = MockitoAnnotations.openMocks(this);
                    mockedSecurity = mockStatic(SecurityUtils.class);
                    mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
                }

                @AfterMethod
                public void tearDown() throws Exception {
                    mockedSecurity.close();
                    mocks.close();
                }

                // The exception is triggered because the referenced category does not exist.
                @Test(expectedExceptions = RuntimeException.class)
                public void test_createProduct_exception() {
                    // Given
                    CreateProductRequest request = new CreateProductRequest();
                    request.setName("Laptop");
                    request.setCategoryId(10L);
                    when(categoryRepository.findById(10L)).thenReturn(Optional.empty());

                    // When — expected to throw
                    productService.createProduct(request);
                }
            }
            """;

    private static final String INTEGRATION_HAPPY = """
            package suites.integration;

            import com.example.demo.DemoApplication;
            import com.example.demo.dto.CreateProductRequest;
            import com.example.demo.entity.Category;
            import com.example.demo.entity.Product;
            import com.example.demo.repository.CategoryRepository;
            import com.example.demo.repository.ProductRepository;
            import com.example.demo.service.ProductService;

            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
            import org.springframework.security.core.context.SecurityContextHolder;
            import org.springframework.security.core.context.SecurityContextImpl;
            import org.springframework.test.annotation.Rollback;
            import org.springframework.test.context.ActiveProfiles;
            import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
            import org.springframework.transaction.annotation.Transactional;
            import org.testng.annotations.AfterMethod;
            import org.testng.annotations.BeforeMethod;
            import org.testng.annotations.Test;

            import java.util.Collections;

            import static org.testng.Assert.*;

            // classes = ... is MANDATORY: the test lives outside the app's package tree.
            // N'ajoute PAS de @TestPropertySource : la base de test est fournie par le runner
            // (application-test.properties), via @ActiveProfiles("test").
            @SpringBootTest(classes = DemoApplication.class)
            @ActiveProfiles("test")
            @Transactional
            @Rollback(true)
            public class ProductServiceIT extends AbstractTestNGSpringContextTests {

                @Autowired private ProductService productService;
                @Autowired private CategoryRepository categoryRepository;
                @Autowired private ProductRepository productRepository;

                private Long categoryId;

                @BeforeMethod
                public void setUp() {
                    // Insert prerequisite rows so the service call succeeds.
                    Category category = new Category();
                    category.setName("Electronics");
                    category = categoryRepository.save(category);
                    categoryId = category.getId();

                    // userId here must match the data the service expects (e.g. a ProjectMember row).
                    SecurityContextHolder.setContext(new SecurityContextImpl(
                        new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList())));
                }

                @AfterMethod
                public void tearDown() {
                    SecurityContextHolder.clearContext();
                }

                @Test
                public void test_createProduct_happyPath() {
                    // Given
                    CreateProductRequest request = new CreateProductRequest();
                    request.setName("Laptop");
                    request.setCategoryId(categoryId);

                    // When
                    Product result = productService.createProduct(request);

                    // Then
                    assertNotNull(result);
                    assertNotNull(result.getId());
                    assertEquals(result.getName(), "Laptop");
                    assertTrue(productRepository.findById(result.getId()).isPresent());
                }
            }
            """;

    private static final String INTEGRATION_EXCEPTION = """
            package suites.integration;

            import com.example.demo.DemoApplication;
            import com.example.demo.dto.CreateProductRequest;
            import com.example.demo.repository.CategoryRepository;
            import com.example.demo.repository.ProductRepository;
            import com.example.demo.service.ProductService;

            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
            import org.springframework.security.core.context.SecurityContextHolder;
            import org.springframework.security.core.context.SecurityContextImpl;
            import org.springframework.test.annotation.Rollback;
            import org.springframework.test.context.ActiveProfiles;
            import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
            import org.springframework.transaction.annotation.Transactional;
            import org.testng.annotations.AfterMethod;
            import org.testng.annotations.BeforeMethod;
            import org.testng.annotations.Test;

            import java.util.Collections;

            // classes = ... is MANDATORY: the test lives outside the app's package tree.
            // N'ajoute PAS de @TestPropertySource : la base de test est fournie par le runner
            // (application-test.properties), via @ActiveProfiles("test").
            @SpringBootTest(classes = DemoApplication.class)
            @ActiveProfiles("test")
            @Transactional
            @Rollback(true)
            public class ProductServiceExceptionIT extends AbstractTestNGSpringContextTests {

                @Autowired private ProductService productService;
                @Autowired private CategoryRepository categoryRepository;
                @Autowired private ProductRepository productRepository;

                @BeforeMethod
                public void setUp() {
                    SecurityContextHolder.setContext(new SecurityContextImpl(
                        new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList())));
                }

                @AfterMethod
                public void tearDown() {
                    SecurityContextHolder.clearContext();
                }

                // No Category is inserted, so the service must reject the request.
                @Test(expectedExceptions = RuntimeException.class)
                public void test_createProduct_exception() {
                    CreateProductRequest request = new CreateProductRequest();
                    request.setName("Laptop");
                    request.setCategoryId(999L);

                    productService.createProduct(request);
                }
            }
            """;
}
