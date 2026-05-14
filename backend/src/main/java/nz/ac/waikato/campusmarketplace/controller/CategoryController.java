package nz.ac.waikato.campusmarketplace.controller;

import nz.ac.waikato.campusmarketplace.dto.CategoryResponse;
import nz.ac.waikato.campusmarketplace.repository.CategoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    public ResponseEntity<Map<String, List<CategoryResponse>>> list() {
        List<CategoryResponse> items = categories.findAllByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(CategoryResponse::from)
                .toList();
        return ResponseEntity.ok(Map.of("items", items));
    }
}
