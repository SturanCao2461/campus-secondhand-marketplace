package nz.ac.waikato.campusmarketplace.controller;

import nz.ac.waikato.campusmarketplace.dto.CategoryResponse;
import nz.ac.waikato.campusmarketplace.entity.Category;
import nz.ac.waikato.campusmarketplace.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock CategoryRepository categories;
    @InjectMocks CategoryController controller;

    @Test
    void listReturnsItemsWrappedInResponseBody() {
        Category books = Category.builder()
                .code("BOOKS").nameEn("Books & Textbooks").nameZh("书籍教材")
                .sortOrder(10).active(true).build();
        Category other = Category.builder()
                .code("OTHER").nameEn("Other").nameZh("其他")
                .sortOrder(99).active(true).build();
        when(categories.findAllByActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(books, other));

        ResponseEntity<Map<String, List<CategoryResponse>>> response = controller.list();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsOnlyKeys("items");
        List<CategoryResponse> items = response.getBody().get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).isEqualTo(new CategoryResponse("BOOKS", "Books & Textbooks", "书籍教材"));
        assertThat(items.get(1)).isEqualTo(new CategoryResponse("OTHER", "Other", "其他"));
    }

    @Test
    void listReturnsEmptyItemsWhenRepositoryEmpty() {
        when(categories.findAllByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());

        ResponseEntity<Map<String, List<CategoryResponse>>> response = controller.list();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("items")).isEmpty();
    }

    @Test
    void listPreservesRepositoryOrderAndOnlyMapsRequiredFields() {
        Category electronics = Category.builder()
                .code("ELECTRONICS").nameEn("Electronics").nameZh("电子产品")
                .sortOrder(20).active(true).build();
        Category furniture = Category.builder()
                .code("FURNITURE").nameEn("Furniture").nameZh("家具")
                .sortOrder(30).active(true).build();
        when(categories.findAllByActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(electronics, furniture));

        List<CategoryResponse> items = controller.list().getBody().get("items");

        assertThat(items).extracting(CategoryResponse::code)
                .containsExactly("ELECTRONICS", "FURNITURE");
        assertThat(items).extracting(CategoryResponse::nameEn)
                .containsExactly("Electronics", "Furniture");
        assertThat(items).extracting(CategoryResponse::nameZh)
                .containsExactly("电子产品", "家具");
    }
}
